package net.perfectdreams.loritta.morenitta.website.routes.api.v1.twitter

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.JsonObject
import net.perfectdreams.loritta.morenitta.website.LoriWebCode
import net.perfectdreams.loritta.morenitta.website.WebsiteAPIException
import io.ktor.server.application.*
import io.ktor.http.*
import mu.KotlinLogging
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.website.utils.WebsiteUtils
import net.perfectdreams.loritta.morenitta.website.utils.extensions.respondJson
import net.perfectdreams.sequins.ktor.BaseRoute
import twitter4j.TwitterFactory
import twitter4j.conf.Configuration
import twitter4j.conf.ConfigurationBuilder
import java.util.concurrent.TimeUnit
import kotlin.collections.set

class GetShowTwitterUserRoute(val loritta: LorittaBot) : BaseRoute("/api/v1/twitter/users/show") {
	companion object {
		private val logger = KotlinLogging.logger {}
	}

	val cachedUsersById = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10_000).build<Long, JsonObject>().asMap()
	val cachedUsersByScreenName = Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10_000).build<String, JsonObject>().asMap()

	override suspend fun onRequest(call: ApplicationCall) {
		val tf = TwitterFactory(buildTwitterConfig())
		val twitter = tf.instance

		val screenName = call.parameters["screenName"]

		if (screenName != null) {
			val cachedResponse = cachedUsersByScreenName[screenName]
			if (cachedResponse != null) {
				call.respondJson(cachedResponse)
				return
			}
		}

		val accountId = call.parameters["userId"]

		val twitterUser = if (accountId != null) {
			val accountIdAsLong = accountId.toLong()
			val cachedResponse = cachedUsersById[accountIdAsLong]
			if (cachedResponse != null) {
				call.respondJson(cachedResponse)
				return
			}

			twitter.users().showUser(accountId.toLong())
		} else if (screenName != null) {
			val cachedResponse = cachedUsersByScreenName[screenName]
			if (cachedResponse != null) {
				call.respondJson(cachedResponse)
				return
			}

			twitter.users().showUser(screenName)
		} else {
			throw WebsiteAPIException(
					HttpStatusCode.NotFound,
					WebsiteUtils.createErrorPayload(
							loritta,
							LoriWebCode.ITEM_NOT_FOUND,
							"Unknown Twitter Type"
					)
			)
		}

		if (twitterUser == null) {
			throw WebsiteAPIException(
					HttpStatusCode.NotFound,
					WebsiteUtils.createErrorPayload(
							loritta,
							LoriWebCode.ITEM_NOT_FOUND,
							"Unknown Twitter User"
					)
			)
		} else {
			val payload = jsonObject(
					"id" to twitterUser.id,
					"name" to twitterUser.name,
					"screenName" to twitterUser.screenName,
					"avatarUrl" to twitterUser.profileImageURLHttps
			)

			cachedUsersByScreenName[twitterUser.screenName] = payload
			cachedUsersById[twitterUser.id] = payload

			call.respondJson(payload)
		}
	}

	fun buildTwitterConfig(): Configuration {
		val cb = ConfigurationBuilder()
		cb.setDebugEnabled(true)
				.setOAuthConsumerKey(loritta.config.loritta.twitter.oAuthConsumerKey)
				.setOAuthConsumerSecret(loritta.config.loritta.twitter.oAuthConsumerSecret)
				.setOAuthAccessToken(loritta.config.loritta.twitter.oAuthAccessToken)
				.setOAuthAccessTokenSecret(loritta.config.loritta.twitter.oAuthAccessTokenSecret)

		return cb.build()
	}
}