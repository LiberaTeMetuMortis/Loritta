package net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor

import dev.kord.gateway.Event
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.common.locale.LanguageManager
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.gatewayproxy.GatewayProxy
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.gatewayproxy.GatewayProxyEvent
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.gatewayproxy.GatewayProxyEventWrapper
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.modules.*
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils.BomDiaECia
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils.DiscordGatewayEventsProcessorTasks
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils.KordDiscordEventUtils
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils.config.RootConfig
import net.perfectdreams.loritta.cinnamon.platform.LorittaDiscordStuff
import net.perfectdreams.loritta.cinnamon.pudding.Pudding
import java.security.SecureRandom
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class DiscordGatewayEventsProcessor(
    val config: RootConfig,
    services: Pudding,
    val languageManager: LanguageManager,
    val replicaId: Int
) : LorittaDiscordStuff(config.discord, services) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val starboardModule = StarboardModule(this)
    private val addFirstToNewChannelsModule = AddFirstToNewChannelsModule(this)
    private val discordCacheModule = DiscordCacheModule(this)
    private val bomDiaECiaModule = BomDiaECiaModule(this)
    private val debugGatewayModule = DebugGatewayModule(this)
    private val owoGatewayModule = OwOGatewayModule(this)
    private val inviteBlockerModule = InviteBlockerModule(this)
    private val afkModule = AFKModule(this)

    // This is executed sequentially!
    val modules = listOf(
        discordCacheModule,
        inviteBlockerModule,
        afkModule,
        addFirstToNewChannelsModule,
        starboardModule,
        owoGatewayModule,
        debugGatewayModule
    )

    val bomDiaECia = BomDiaECia(this)
    val random = SecureRandom()
    val activeEvents = ConcurrentLinkedQueue<Job>()

    private val onMessageReceived: (GatewayProxyEventWrapper) -> (Unit) = {
        val (eventType, discordEvent) = parseEvent(it.data)

        // We will call a method that doesn't reference the "discordEventAsJsonObject" nor the "it" object, this makes it veeeery clear to the JVM that yes, you can GC the "discordEventAsJsonObject" and "it" objects
        // (Will it really GC the object? idk, but I hope it will)
        launchEventProcessorJob(it.shardId, it.receivedAt, eventType, discordEvent)
    }

    val gatewayProxies = config.gatewayProxies.filter { it.replicaId == replicaId }.map {
        GatewayProxy(it.url, it.authorizationToken, onMessageReceived)
    }

    // This needs to be initialized AFTER the gatewayProxies above
    val tasks = DiscordGatewayEventsProcessorTasks(this)

    fun start() {
        gatewayProxies.forEachIndexed { index, gatewayProxy ->
            logger.info { "Starting Gateway Proxy $index (${gatewayProxy.url})" }
            gatewayProxy.start()
        }

        tasks.start()

        // bomDiaECia.startBomDiaECiaTask()
    }

    private fun parseEvent(discordGatewayEvent: GatewayProxyEvent): Pair<String, Event?> {
        val discordEventAsJsonObject = discordGatewayEvent.event
        val eventType = discordEventAsJsonObject["t"]?.jsonPrimitive?.content ?: "UNKNOWN"
        val discordEvent = KordDiscordEventUtils.parseEventFromJsonObject(discordEventAsJsonObject)

        return Pair(eventType, discordEvent)
    }

    private fun launchEventProcessorJob(shardId: Int, receivedAt: Instant, type: String, discordEvent: Event?) {
        if (discordEvent != null)
            launchEventProcessorJob(shardId, receivedAt, discordEvent)
        else
            logger.warn { "Unknown Discord event received $type! We are going to ignore the event... kthxbye!" }
    }

    @OptIn(ExperimentalTime::class)
    private fun launchEventProcessorJob(shardId: Int, receivedAt: Instant, discordEvent: Event) {
        val coroutineName = "Event ${discordEvent::class.simpleName}"
        launchEventJob(coroutineName) {
            try {
                for (module in modules) {
                    val (result, duration) = measureTimedValue { module.processEvent(shardId, receivedAt, discordEvent, it) }
                    it[module::class] = duration

                    when (result) {
                        ModuleResult.Cancel -> {
                            // Module asked us to stop processing the events
                            return@launchEventJob
                        }
                        ModuleResult.Continue -> {
                            // Module asked us to continue processing the events
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.warn(e) { "Something went wrong while trying to process $coroutineName! We are going to ignore..." }
            }
        }
    }

    private fun launchEventJob(coroutineName: String, block: suspend CoroutineScope.(MutableMap<KClass<*>, Duration>) -> Unit) {
        val start = System.currentTimeMillis()
        val durations = mutableMapOf<KClass<*>, Duration>()

        val job = GlobalScope.launch(
            CoroutineName(coroutineName),
            block = {
                block.invoke(this, durations)
            }
        )

        activeEvents.add(job)

        // Yes, the order matters, since sometimes the invokeOnCompletion would be invoked before the job was
        // added to the list, causing leaks.
        // invokeOnCompletion is also invoked even if the job was already completed at that point, so no worries!
        job.invokeOnCompletion {
            activeEvents.remove(job)

            val diff = System.currentTimeMillis() - start
            if (diff >= 60_000) {
                logger.warn { "Coroutine $job ($coroutineName) took too long to process! ${diff}ms - Module Durations: $durations" }
            }
        }
    }
}