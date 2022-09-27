package net.perfectdreams.loritta.cinnamon.discord.utils.profiles

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import net.perfectdreams.i18nhelper.core.I18nContext
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.cinnamon.discord.utils.DateUtils
import net.perfectdreams.loritta.cinnamon.discord.utils.UserId
import net.perfectdreams.loritta.cinnamon.discord.utils.images.*
import net.perfectdreams.loritta.cinnamon.discord.utils.toLong
import net.perfectdreams.loritta.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.pudding.entities.PuddingUserProfile
import net.perfectdreams.loritta.cinnamon.pudding.tables.Reputations
import net.perfectdreams.loritta.cinnamon.pudding.tables.servers.GuildProfiles
import net.perfectdreams.loritta.cinnamon.pudding.utils.exposed.selectFirstOrNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage

open class NostalgiaProfileCreator(loritta: LorittaBot, internalName: String, val folderName: String) : StaticProfileCreator(loritta, internalName) {
	class NostalgiaDarkProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultDark", "dark")
	class NostalgiaBlurpleProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultBlurple", "blurple")
	class NostalgiaRedProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultRed", "red")
	class NostalgiaBlueProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultBlue", "blue")
	class NostalgiaGreenProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultGreen", "green")
	class NostalgiaPurpleProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultPurple", "purple")
	class NostalgiaPinkProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultPink", "pink")
	class NostalgiaYellowProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultYellow", "yellow")
	class NostalgiaOrangeProfileCreator(loritta: LorittaBot) : NostalgiaProfileCreator(loritta, "defaultOrange", "orange")

	override suspend fun create(
		sender: ProfileUserInfoData,
		user: ProfileUserInfoData,
		userProfile: PuddingUserProfile,
		guild: Guild?,
		badges: List<BufferedImage>,
		locale: I18nContext,
		background: BufferedImage,
		aboutMe: String,
		allowedDiscordEmojis: List<Snowflake>?
	): BufferedImage {
		val profileWrapper = readImageFromResources("/profile/nostalgia/profile_wrapper_$folderName.png")

		val base = BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB) // Base
		val graphics = base.createGraphics().withTextAntialiasing()

		val avatar = ImageUtils.downloadImage(user.avatarUrl)!!.getResizedInstance(152, 152, InterpolationType.BILINEAR)

		graphics.drawImage(background.getResizedInstance(800, 600, InterpolationType.BILINEAR), 0, 0, null)

		graphics.drawImage(profileWrapper, 0, 0, null)
		graphics.drawImage(ImageUtils.makeRoundedCorners(avatar, 152), 4, 4, null)

		val oswaldRegular = loritta.graphicsFonts.oswaldRegular
		val oswaldRegular50 = oswaldRegular.deriveFont(50F)
		val oswaldRegular42 = oswaldRegular.deriveFont(42F)
		val oswaldRegular29 = oswaldRegular.deriveFont(29F)

		val reputations = loritta.pudding.transaction {
			Reputations.select { Reputations.receivedById eq user.id.toLong() }.count()
		}

		graphics.color = Color.WHITE
		graphics.font = oswaldRegular50
		ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, user.name, 159, 46) // Nome do usuário
		graphics.font = oswaldRegular42
		ImageUtils.drawCenteredString(loritta, graphics, "$reputations reps", Rectangle(598, 54, 202, 54), oswaldRegular42)
		graphics.font = oswaldRegular29
		ImageUtils.drawCenteredString(loritta, graphics, locale.get(I18nKeysData.Profiles.AboutMe), Rectangle(0, 465, 132, 38), oswaldRegular29)

		var x = 162
		for (badge in badges) {
			graphics.drawImage(badge.getResizedInstance(34, 34, InterpolationType.BILINEAR), x, 466, null)
			x += 42
		}

		val latoRegular = loritta.graphicsFonts.latoRegular
		val latoBold = loritta.graphicsFonts.latoBold
		val latoBlack = loritta.graphicsFonts.latoBlack

		val latoBold38 = latoBold.deriveFont(38f)
		val latoBold22 = latoBold.deriveFont(22f)
		val latoBold20 = latoBold.deriveFont(20f)
		val latoBlack20 = latoBlack.deriveFont(20f)

		graphics.font = latoBold22

		drawAboutMeWrapSpaces(graphics, graphics.fontMetrics, aboutMe, 6, 522, 800 - 6, 600, allowedDiscordEmojis)

		val shiftY = 42

		graphics.font = latoBlack20
		ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "Global", 159, 21 + shiftY, 800 - 6)
		graphics.font = latoBold20
		// TODO: Think if this will be kept or not
		// val globalPosition = ProfileUtils.getGlobalExperiencePosition(userProfile)
		/* if (globalPosition != null)
			graphics.drawText("#$globalPosition / ${userProfile.xp} XP", 159, 39  + shiftY, 800 - 6)
		else
			graphics.drawText("${userProfile.xp} XP", 159, 39  + shiftY, 800 - 6) */

		if (guild != null) {
			val localProfile = loritta.pudding.transaction {
				GuildProfiles.selectFirstOrNull { (GuildProfiles.guildId eq guild.id.toLong()) and (GuildProfiles.userId eq user.id.toLong()) }
			}

			val localPosition = getLocalExperiencePosition(localProfile)

			val xpLocal = localProfile?.get(GuildProfiles.xp)

			graphics.font = latoBlack20
			ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, guild.name, 159, 61 + shiftY, 800 - 6)
			graphics.font = latoBold20
			if (xpLocal != null) {
				if (localPosition != null)
					ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "#$localPosition / $xpLocal XP", 159, 78 + shiftY, 800 - 6)
				else
					ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "$xpLocal XP", 159, 78 + shiftY, 800 - 6)
			} else {
				ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "???", 159, 78 + shiftY, 800 - 6)
			}
		}

		val globalEconomyPosition = getGlobalEconomyPosition(userProfile)

		graphics.font = latoBlack20
		ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "Sonhos", 159, 98  + shiftY, 800 - 6)
		graphics.font = latoBold20
		if (globalEconomyPosition != null)
			ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "#$globalEconomyPosition / ${userProfile.money}", 159, 116  + shiftY, 800 - 6)
		else
			ImageUtils.drawStringAndShortenWithEllipsisIfOverflow(graphics, "${userProfile.money}", 159, 116  + shiftY, 800 - 6)

		val marriage = loritta.pudding.marriages.getMarriageByUser(UserId(user.id))

		if (marriage != null) {
			val marriedWithId = if (marriage.user1 == UserId(user.id)) {
				marriage.user2
			} else {
				marriage.user1
			}

			val marrySection = readImageFromResources("/profile/nostalgia/marry.png")
			graphics.drawImage(marrySection, 0, 0, null)
			val marriedWith = loritta.getCachedUserInfo(marriedWithId)

			if (marriedWith != null) {
				val whitneySemiBold16 = latoBold.deriveFont(16f)
				val whitneyMedium20 = latoBold22.deriveFont(20f)
				graphics.font = whitneySemiBold16
				ImageUtils.drawCenteredString(loritta, graphics, locale.get(I18nKeysData.Profiles.MarriedWith), Rectangle(545, 108, 256, 14), whitneySemiBold16)
				graphics.font = whitneyMedium20
				ImageUtils.drawCenteredString(loritta, graphics, marriedWith.name + "#" + marriedWith.discriminator, Rectangle(545, 108 + 14, 256, 18), whitneyMedium20)
				graphics.font = whitneySemiBold16
				ImageUtils.drawCenteredString(loritta, graphics, DateUtils.formatDateDiff(locale, marriage.marriedSince.toEpochMilliseconds(), System.currentTimeMillis(), maxParts = 3), Rectangle(545, 108 + 14  + 18, 256, 14), whitneySemiBold16)
			}
		}

		return base
	}
}