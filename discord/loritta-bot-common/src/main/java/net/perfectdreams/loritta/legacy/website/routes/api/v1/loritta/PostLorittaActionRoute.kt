package net.perfectdreams.loritta.legacy.website.routes.api.v1.loritta

import com.github.salomonbrys.kotson.bool
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import io.ktor.server.application.*
import io.ktor.server.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.perfectdreams.loritta.legacy.platform.discord.LorittaDiscord
import net.perfectdreams.loritta.legacy.utils.PaymentUtils
import net.perfectdreams.loritta.legacy.website.routes.api.v1.RequiresAPIAuthenticationRoute
import net.perfectdreams.loritta.legacy.website.utils.extensions.respondJson
import java.io.File
import java.util.*
import kotlin.concurrent.thread

class PostLorittaActionRoute(loritta: LorittaDiscord) : RequiresAPIAuthenticationRoute(loritta, "/api/v1/loritta/action/{actionType}") {
	override suspend fun onAuthenticatedRequest(call: ApplicationCall) {
		val body = withContext(Dispatchers.IO) { call.receiveText() }
		val actionType = call.parameters["actionType"]

		val json = JsonParser.parseString(body)

		when (actionType) {
			"economy" -> {
				PaymentUtils.economyEnabled = json["enabled"].bool
			}
		}

		call.respondJson(jsonObject())
	}
}