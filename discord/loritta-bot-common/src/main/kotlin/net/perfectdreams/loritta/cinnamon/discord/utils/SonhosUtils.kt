package net.perfectdreams.loritta.cinnamon.discord.utils

import dev.minn.jda.ktx.messages.InlineMessage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toKotlinLocalDateTime
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.perfectdreams.discordinteraktions.common.builder.message.MessageBuilder
import net.perfectdreams.i18nhelper.core.I18nContext
import net.perfectdreams.loritta.cinnamon.emotes.Emotes
import net.perfectdreams.loritta.common.utils.GACampaigns
import net.perfectdreams.loritta.i18n.I18nKeysData
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.cinnamon.discord.interactions.InteractionContext
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.styled
import net.perfectdreams.loritta.cinnamon.pudding.data.UserId
import net.perfectdreams.loritta.cinnamon.pudding.entities.PuddingUserProfile
import net.perfectdreams.loritta.cinnamon.pudding.tables.EconomyState
import net.perfectdreams.loritta.morenitta.commands.CommandContext
import net.perfectdreams.loritta.morenitta.interactions.CommandContextCompat
import net.perfectdreams.loritta.morenitta.interactions.commands.ApplicationCommandContext
import net.perfectdreams.loritta.morenitta.platform.discord.legacy.commands.DiscordCommandContext
import org.jetbrains.exposed.sql.select
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

object SonhosUtils {
    val DISABLED_ECONOMY_ID = UUID.fromString("3da6d95b-edb4-4ae9-aa56-4b13e91f3844")

    val HANGLOOSE_EMOTES = listOf(
        Emotes.LoriHanglooseRight,
        Emotes.GabrielaHanglooseRight,
        Emotes.PantufaHanglooseRight,
        Emotes.PowerHanglooseRight
    )

    fun insufficientSonhos(profile: PuddingUserProfile?, howMuch: Long) = insufficientSonhos(profile?.money ?: 0L, howMuch)
    fun insufficientSonhos(sonhos: Long, howMuch: Long) = I18nKeysData.Commands.InsufficientFunds(howMuch, howMuch - sonhos)

    suspend fun MessageBuilder.appendUserHaventGotDailyTodayOrUpsellSonhosBundles(
        loritta: LorittaBot,
        i18nContext: I18nContext,
        userId: UserId,
        upsellMedium: String,
        upsellCampaignContent: String
    ) {
        // TODO: Do not hardcode the timezone
        val now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val todayDailyReward = loritta.pudding.sonhos.getUserLastDailyRewardReceived(
            userId,
            now.toKotlinLocalDateTime().toInstant(TimeZone.of("America/Sao_Paulo"))
        )

        if (todayDailyReward != null) {
             // Already got their daily reward today, show our sonhos bundles!
            styled(
                i18nContext.get(
                    GACampaigns.sonhosBundlesUpsellDiscordMessage(
                        loritta.config.loritta.website.url,
                        upsellMedium,
                        upsellCampaignContent
                    )
                ),
                Emotes.CreditCard
            )
        } else {
            // Recommend the user to get their daily reward
            styled(
                i18nContext.get(I18nKeysData.Commands.WantingMoreSonhosDaily(loritta.commandMentions.daily)),
                Emotes.Gift
            )
        }
    }

    suspend fun InlineMessage<MessageCreateData>.appendUserHaventGotDailyTodayOrUpsellSonhosBundles(
        loritta: LorittaBot,
        i18nContext: I18nContext,
        userId: UserId,
        upsellMedium: String,
        upsellCampaignContent: String
    ) {
        // TODO: Do not hardcode the timezone
        val now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val todayDailyReward = loritta.pudding.sonhos.getUserLastDailyRewardReceived(
            userId,
            now.toKotlinLocalDateTime().toInstant(TimeZone.of("America/Sao_Paulo"))
        )

        if (todayDailyReward != null) {
            // Already got their daily reward today, show our sonhos bundles!
            styled(
                i18nContext.get(
                    GACampaigns.sonhosBundlesUpsellDiscordMessage(
                        loritta.config.loritta.website.url,
                        upsellMedium,
                        upsellCampaignContent
                    )
                ),
                Emotes.CreditCard
            )
        } else {
            // Recommend the user to get their daily reward
            styled(
                i18nContext.get(I18nKeysData.Commands.WantingMoreSonhosDaily(loritta.commandMentions.daily)),
                Emotes.Gift
            )
        }
    }

    suspend fun sendEphemeralMessageIfUserHaventGotDailyRewardToday(
        loritta: LorittaBot,
        context: InteractionContext,
        userId: UserId
    ) {
        // TODO: Do not hardcode the timezone
        val now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)

        val todayDailyReward = loritta.pudding.sonhos.getUserLastDailyRewardReceived(
            userId,
            now.toKotlinLocalDateTime().toInstant(TimeZone.of("America/Sao_Paulo"))
        )

        if (todayDailyReward != null)
            return

        context.sendEphemeralMessage {
            styled(
                context.i18nContext.get(I18nKeysData.Commands.WantingMoreSonhosDaily(loritta.commandMentions.daily)),
                Emotes.Gift
            )
        }
    }

    fun getSonhosEmojiOfQuantity(quantity: Long) = when {
        quantity >= 1_000_000_000 -> Emotes.Sonhos6
        quantity >= 10_000_000 -> Emotes.Sonhos5
        quantity >= 1_000_000 -> Emotes.Sonhos4
        quantity >= 100_000 -> Emotes.Sonhos3
        quantity >= 10_000 -> Emotes.Sonhos2
        else -> Emotes.Sonhos1
    }

    suspend fun isEconomyDisabled(loritta: LorittaBot): Boolean {
        return loritta.transaction {
            EconomyState.select {
                EconomyState.id eq DISABLED_ECONOMY_ID
            }.count() == 1L
        }
    }

    suspend fun checkIfEconomyIsDisabled(context: CommandContext) = checkIfEconomyIsDisabled(CommandContextCompat.LegacyMessageCommandContextCompat(context))
    suspend fun checkIfEconomyIsDisabled(context: DiscordCommandContext) = checkIfEconomyIsDisabled(CommandContextCompat.LegacyDiscordCommandContextCompat(context))
    suspend fun checkIfEconomyIsDisabled(context: ApplicationCommandContext) = checkIfEconomyIsDisabled(CommandContextCompat.InteractionsCommandContextCompat(context))

    suspend fun checkIfEconomyIsDisabled(context: CommandContextCompat): Boolean {
        if (isEconomyDisabled(context.loritta)) {
            context.reply(true) {
                styled(
                    context.i18nContext.get(I18nKeysData.Commands.EconomyIsDisabled),
                    Emotes.LoriSob
                )
            }
            return true
        }
        return false
    }

    suspend fun checkIfEconomyIsDisabled(context: net.perfectdreams.loritta.cinnamon.discord.interactions.commands.ApplicationCommandContext): Boolean {
        if (isEconomyDisabled(context.loritta)) {
            context.sendEphemeralMessage {
                styled(
                    context.i18nContext.get(I18nKeysData.Commands.EconomyIsDisabled),
                    Emotes.LoriSob
                )
            }
            return true
        }
        return false
    }
}