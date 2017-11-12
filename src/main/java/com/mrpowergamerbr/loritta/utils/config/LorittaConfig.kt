package com.mrpowergamerbr.loritta.utils.config

import net.dv8tion.jda.core.entities.Game

data class LorittaConfig(
	val shards: Int,
	val clientToken: String,
	val clientId: String,
	val clientSecret: String,
	val youtubeKeys: List<String>,
	val ownerId: String,
	val websiteUrl: String,
	val frontendFolder: String,
	val mercadoPagoClientId: String,
	val mercadoPagoClientToken: String,
	val mashapeKey: String,
	val discordBotsKey: String,
	val discordBotsOrgKey: String,
	val openWeatherMapKey: String,
	val aminoEmail: String,
	val aminoPassword: String,
	val aminoDeviceId: String,
	val facebookToken: String,
	val googleVisionKey: String,
	val simsimiKey: String,
	val patreonClientId: String,
	val patreonClientSecret: String,
	val patreonAccessToken: String,
	val patreonRefreshToken: String,
	val fanArtExtravaganza: Boolean,
	val fanArts: List<LorittaFanArts>,
	val currentlyPlaying: List<LorittaGameStatus>) {
	constructor() : this(2,
			"Token do Bot",
			"Client ID do Bot",
			"Client Secret do Bot",
			listOf(),
			"ID do dono do bot, usado para alguns comandos \"especiais\"",
			"Website do Bot",
			"Pasta da frontend do Bot",
			"Client ID do MercadoPago",
			"Client Token do MercadoPago",
			"Key do Mashape",
			"Key do Discord Bots",
			"Key do Discord Bots (discordbots.org)",
			"Key do Open Weather Map",
			"Email de uma conta do Amino",
			"Senha de uma conta do Amino",
			"Device ID de uma conta do Amino",
			"Token da API do Facebook",
			"Key do Google Vision",
			"Key do Simsimi",
			"Client ID do Patreon",
			"Client Secret do Patreon",
			"Access Token do Patreon",
			"Refresh Token do Patreon",
			true,
			listOf<LorittaFanArts>(),
			listOf(LorittaGameStatus("Shantae: Half-Genie Hero", Game.GameType.DEFAULT.name)))

	class LorittaGameStatus(val name: String, val type: String)

	class LorittaFanArts(val fileName: String, val artist: String)
}