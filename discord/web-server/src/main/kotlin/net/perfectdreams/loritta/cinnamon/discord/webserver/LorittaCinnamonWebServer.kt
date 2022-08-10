package net.perfectdreams.loritta.cinnamon.discord.webserver

import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.discord.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.discord.utils.EventAnalyticsTask
import net.perfectdreams.loritta.cinnamon.discord.webserver.gateway.ProcessDiscordGatewayEvents
import net.perfectdreams.loritta.cinnamon.discord.webserver.gateway.ProxyDiscordGatewayManager
import net.perfectdreams.loritta.cinnamon.discord.webserver.utils.config.RootConfig
import net.perfectdreams.loritta.cinnamon.discord.webserver.webserver.InteractionsServer
import net.perfectdreams.loritta.cinnamon.locale.LanguageManager
import net.perfectdreams.loritta.cinnamon.pudding.Pudding

class LorittaCinnamonWebServer(
    val config: RootConfig,
    private val languageManager: LanguageManager,
    private val services: Pudding,
    private val queueConnection: HikariDataSource,
    private val http: HttpClient,
    private val replicaId: Int
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val replicaInstance = config.replicas.instances.firstOrNull { it.replicaId == replicaId } ?: error("Missing replica configuration for replica ID $replicaId")

    private val proxyDiscordGatewayManager = ProxyDiscordGatewayManager(
        config.discordShards.totalShards,
        replicaInstance,
        services
    )

    private val discordGatewayEventsProcessors = (0 until config.queueDatabase.connections).map {
        ProcessDiscordGatewayEvents(
            config.totalEventsPerBatch,
            config.queueDatabase.connections,
            it,
            replicaInstance,
            queueConnection,
            proxyDiscordGatewayManager.gateways
        )
    }

    private val stats = mutableMapOf<Int, Pair<Long, Long>>()

    fun start() {
        // To avoid initializing Exposed for our "queueConnection" just to create a table, we will create the table manually with SQL statements (woo, scary!)
        // It is more cumbersome, but hey, it works!
        queueConnection.connection.use {
            val statement = it.createStatement()
            // We will create a UNLOGGED table because it is faster, but if PostgreSQL crashes, all data will be lost
            // Because it is a table that holds gateway events, we don't really care if we can lose all data, as long as it is fast!
            val sql = buildString {
                append("""
                CREATE UNLOGGED TABLE IF NOT EXISTS ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE} (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY,
                    type TEXT NOT NULL,
                    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    shard INTEGER NOT NULL,
                    payload JSONB NOT NULL,
                    PRIMARY KEY (id, shard)
                ) PARTITION BY RANGE (shard);
                
                CREATE INDEX IF NOT EXISTS ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE}_type ON ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE} (type);
                CREATE INDEX IF NOT EXISTS ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE}_shard ON ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE} (shard);
            """.trimIndent())

                // Now create a partition for each shard
                repeat(config.discordShards.totalShards) { shardId ->
                    append("""
                    CREATE UNLOGGED TABLE IF NOT EXISTS ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE}_shard_$shardId PARTITION OF ${ProcessDiscordGatewayEvents.DISCORD_GATEWAY_EVENTS_TABLE}
                        FOR VALUES FROM ($shardId) TO (${shardId + 1});
                """.trimIndent())
                }
            }

            statement.executeUpdate(sql)
            it.commit()
        }

        val cinnamon = LorittaCinnamon(
            proxyDiscordGatewayManager,
            config.cinnamon,
            languageManager,
            services,
            http
        )

        cinnamon.start()

        // Start processing gateway events
        for (processor in discordGatewayEventsProcessors) {
            Thread(
                null,
                processor,
                "GatewayEventsPoller-${processor.totalEventsProcessed}"
            ).start()
        }

        cinnamon.addAnalyticHandler {
            val statsValues = stats.values
            val previousEventsProcessed = statsValues.sumOf { it.first }
            val previousPollLoopsCheck = statsValues.sumOf { it.second }

            val totalEventsProcessed = discordGatewayEventsProcessors.sumOf { it.totalEventsProcessed }
            val totalPollLoopsCheck = discordGatewayEventsProcessors.sumOf { it.totalPollLoopsCount }

            logger.info { "Total Discord Events processed: $totalEventsProcessed; (+${totalEventsProcessed - previousEventsProcessed})" }
            logger.info { "Total Poll Loops: $totalPollLoopsCheck; (+${totalPollLoopsCheck - previousPollLoopsCheck})" }
            for (processor in discordGatewayEventsProcessors) {
                val previousStats = stats[processor.connectionId] ?: Pair(0L, 0L)
                logger.info { "Processor shardId % ${processor.totalConnections} == ${processor.connectionId}: Discord Events processed: ${processor.totalEventsProcessed} (+${processor.totalEventsProcessed - previousStats.first}); Current Poll Loops Count: ${processor.totalPollLoopsCount} (+${processor.totalPollLoopsCount - previousStats.second}); Last poll took ${processor.lastPollDuration} to complete" }
                stats[processor.connectionId] = Pair(processor.totalEventsProcessed, processor.totalPollLoopsCount)
            }
        }

        val interactionsServer = InteractionsServer(
            cinnamon.interaKTions,
            config.httpInteractions.publicKey
        )

        interactionsServer.start()
    }
}