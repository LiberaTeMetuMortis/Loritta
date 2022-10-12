package net.perfectdreams.loritta.morenitta.website.routes.dashboard

import net.perfectdreams.loritta.morenitta.dao.ServerConfig
import net.perfectdreams.loritta.morenitta.utils.GuildLorittaUser
import net.perfectdreams.loritta.morenitta.utils.LorittaPermission
import net.perfectdreams.loritta.morenitta.utils.LorittaUser
import net.perfectdreams.loritta.common.locale.BaseLocale
import net.perfectdreams.loritta.morenitta.website.LorittaWebsite
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import mu.KotlinLogging
import dev.kord.common.entity.Permission
import net.perfectdreams.loritta.deviousfun.entities.Guild
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.utils.DiscordUtils
import net.perfectdreams.loritta.morenitta.website.routes.RequiresDiscordLoginLocalizedRoute
import net.perfectdreams.loritta.morenitta.website.session.LorittaJsonWebSession
import net.perfectdreams.loritta.morenitta.website.utils.extensions.hostFromHeader
import net.perfectdreams.loritta.morenitta.website.utils.extensions.legacyVariables
import net.perfectdreams.loritta.morenitta.website.utils.extensions.redirect
import net.perfectdreams.loritta.morenitta.website.utils.extensions.urlQueryString
import net.perfectdreams.temmiediscordauth.TemmieDiscordAuth

abstract class RequiresGuildAuthLocalizedRoute(loritta: LorittaBot, originalDashboardPath: String) : RequiresDiscordLoginLocalizedRoute(loritta, "/guild/{guildId}$originalDashboardPath") {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	abstract suspend fun onGuildAuthenticatedRequest(call: ApplicationCall, locale: BaseLocale, discordAuth: TemmieDiscordAuth, userIdentification: LorittaJsonWebSession.UserIdentification, guild: Guild, serverConfig: ServerConfig)

	override suspend fun onAuthenticatedRequest(call: ApplicationCall, locale: BaseLocale, discordAuth: TemmieDiscordAuth, userIdentification: LorittaJsonWebSession.UserIdentification) {
		var start = System.currentTimeMillis()
		val guildId = call.parameters["guildId"] ?: return

		val shardId = DiscordUtils.getShardIdFromGuildId(loritta, guildId.toLong())

		val host = call.request.hostFromHeader()
		val scheme = LorittaWebsite.WEBSITE_URL.split(":").first()

		val loriShardId = DiscordUtils.getLorittaClusterIdForShardId(loritta, shardId)
		val theNewUrl = DiscordUtils.getUrlForLorittaClusterId(loritta, loriShardId)

		logger.info { "Getting some stuff lol: ${System.currentTimeMillis() - start}" }
		start = System.currentTimeMillis()

		if (host != theNewUrl)
			redirect("$scheme://$theNewUrl${call.request.path()}${call.request.urlQueryString}", false)

		val jdaGuild = loritta.lorittaShards.getGuildById(guildId)
				?: redirect(loritta.config.loritta.discord.addBotUrl + "&guild_id=$guildId", false)

		logger.info { "JDA Guild get and check: ${System.currentTimeMillis() - start}" }
		start = System.currentTimeMillis()

		val serverConfig = loritta.getOrCreateServerConfig(guildId.toLong()) // get server config for guild

		logger.info { "Getting legacy config: ${System.currentTimeMillis() - start}" }
		start = System.currentTimeMillis()

		val id = userIdentification.id
		val member = jdaGuild.retrieveMemberOrNullById(id)
		var canAccessDashboardViaPermission = false

		logger.info { "OG Perm Check: ${System.currentTimeMillis() - start}" }

		if (member != null) {
			start = System.currentTimeMillis()
			val lorittaUser = GuildLorittaUser(loritta, member, LorittaUser.loadMemberLorittaPermissions(loritta, serverConfig, member), loritta.getOrCreateLorittaProfile(id.toLong()))

			canAccessDashboardViaPermission = lorittaUser.hasPermission(LorittaPermission.ALLOW_ACCESS_TO_DASHBOARD)
			logger.info { "Lori User Perm Check: ${System.currentTimeMillis() - start}" }
		}

		val canBypass = loritta.isOwner(userIdentification.id) || canAccessDashboardViaPermission
		if (!canBypass && !(member?.hasPermission(Permission.Administrator) == true || member?.hasPermission(Permission.ManageGuild) == true || jdaGuild.ownerId == userIdentification.id)) {
			call.respondText("Você não tem permissão!")
			return
		}

		// variables["serverConfig"] = legacyServerConfig
		// TODO: Remover isto quando for removido o "server-config-json" do website
		start = System.currentTimeMillis()
		val variables = call.legacyVariables(loritta, locale)
		logger.info { "Legacy Vars Creation: ${System.currentTimeMillis() - start}" }
		variables["guild"] = jdaGuild

		return onGuildAuthenticatedRequest(call, locale, discordAuth, userIdentification, jdaGuild, serverConfig)
	}
}