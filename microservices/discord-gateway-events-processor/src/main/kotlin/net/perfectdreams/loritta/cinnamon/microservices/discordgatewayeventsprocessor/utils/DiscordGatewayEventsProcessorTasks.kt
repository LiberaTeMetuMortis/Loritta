package net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.utils

import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.microservices.discordgatewayeventsprocessor.DiscordGatewayEventsProcessor
import java.util.concurrent.Executors

class DiscordGatewayEventsProcessorTasks(private val m: DiscordGatewayEventsProcessor) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private val executorService = Executors.newScheduledThreadPool(2)

    private val pendingMessageProcessor = ProcessDiscordGatewayEvents(m, m.services)

    fun start() {
        executorService.submit(pendingMessageProcessor)
    }

    fun shutdown() {
        executorService.shutdown()
    }
}