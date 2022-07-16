package net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.modules

import dev.kord.gateway.Event
import dev.kord.gateway.MessageCreate
import kotlinx.datetime.Instant
import net.perfectdreams.loritta.cinnamon.common.emotes.Emotes
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.DiscordGatewayEventsProcessor
import kotlin.reflect.KClass
import kotlin.time.Duration

class OwOGatewayModule(private val m: DiscordGatewayEventsProcessor) : ProcessDiscordEventsModule() {
    override suspend fun processEvent(
        shardId: Int,
        receivedAt: Instant,
        event: Event,
        durations: Map<KClass<*>, Duration>
    ): ModuleResult {
        when (event) {
            // ===[ CHANNEL CREATE ]===
            is MessageCreate -> {
                handleOwOGateway(event)
            }
            else -> {}
        }
        return ModuleResult.Continue
    }

    private suspend fun handleOwOGateway(
        messageCreate: MessageCreate
    ) {
        val guildId = messageCreate.message.guildId.value ?: return
        val contentInLowerCase = messageCreate.message.content.lowercase()

        val isMessage = contentInLowerCase == "<@${m.config.discord.applicationId}> owo" || contentInLowerCase == "<@!${m.config.discord.applicationId}> owo"
        if (!isMessage)
            return

        val canTalk = m.cache.getLazyCachedLorittaPermissions(guildId, messageCreate.message.channelId).canTalk()
        if (!canTalk)
            return

        m.rest.channel.createMessage(
            messageCreate.message.channelId
        ) {
            content = "UwU! ${Emotes.LoriLick}"
        }
    }
}