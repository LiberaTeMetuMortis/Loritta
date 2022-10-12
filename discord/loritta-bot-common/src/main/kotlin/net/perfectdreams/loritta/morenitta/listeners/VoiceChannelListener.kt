package net.perfectdreams.loritta.morenitta.listeners

import com.github.benmanes.caffeine.cache.Caffeine
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.utils.debug.DebugLog
import net.perfectdreams.loritta.morenitta.utils.eventlog.EventLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import net.perfectdreams.loritta.deviousfun.entities.Channel
import net.perfectdreams.loritta.deviousfun.entities.Member
import net.perfectdreams.loritta.deviousfun.events.guild.voice.GuildVoiceJoinEvent
import net.perfectdreams.loritta.deviousfun.events.guild.voice.GuildVoiceLeaveEvent
import net.perfectdreams.loritta.deviousfun.events.guild.voice.GuildVoiceMoveEvent
import net.perfectdreams.loritta.deviousfun.hooks.ListenerAdapter
import java.util.concurrent.TimeUnit

class VoiceChannelListener(val loritta: LorittaBot) : ListenerAdapter() {
	companion object {
		private val logger = KotlinLogging.logger {}
		private val mutexes = Caffeine.newBuilder()
				.expireAfterAccess(60, TimeUnit.SECONDS)
				.build<Long, Mutex>()
				.asMap()
	}

	override fun onGuildVoiceJoin(event: GuildVoiceJoinEvent) {
		if (DebugLog.cancelAllEvents)
			return

		if (loritta.rateLimitChecker.checkIfRequestShouldBeIgnored())
			return

		onVoiceChannelConnect(event.member, event.channelJoined)
	}

	override fun onGuildVoiceMove(event: GuildVoiceMoveEvent) {
		if (DebugLog.cancelAllEvents)
			return

		if (loritta.rateLimitChecker.checkIfRequestShouldBeIgnored())
			return

		onVoiceChannelLeave(event.member, event.channelLeft)
		onVoiceChannelConnect(event.member, event.channelJoined)
	}

	override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
		if (DebugLog.cancelAllEvents)
			return

		if (loritta.rateLimitChecker.checkIfRequestShouldBeIgnored())
			return

		onVoiceChannelLeave(event.member, event.channelLeft)
	}

	fun onVoiceChannelConnect(member: Member, channelJoined: Channel) {
		GlobalScope.launch(loritta.coroutineDispatcher) {
			val mutex = mutexes.getOrPut(channelJoined.idLong) { Mutex() }

			mutex.withLock {
				// Carregar a configuração do servidor
				val serverConfig = loritta.getOrCreateServerConfig(channelJoined.guild.idLong)
				EventLog.onVoiceJoin(loritta, serverConfig, member, channelJoined)
			}
		}
	}

	fun onVoiceChannelLeave(member: Member, channelLeft: Channel) {
		GlobalScope.launch(loritta.coroutineDispatcher) {
			val mutex = mutexes.getOrPut(channelLeft.idLong) { Mutex() }

			mutex.withLock {
				val serverConfig = loritta.getOrCreateServerConfig(channelLeft.guild.idLong)
				EventLog.onVoiceLeave(loritta, serverConfig, member, channelLeft)
			}
		}
	}
}