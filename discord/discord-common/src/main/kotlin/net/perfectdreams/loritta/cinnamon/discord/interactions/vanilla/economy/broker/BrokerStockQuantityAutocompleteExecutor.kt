package net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.economy.broker

import net.perfectdreams.discordinteraktions.common.autocomplete.FocusedCommandOption
import net.perfectdreams.discordinteraktions.common.commands.options.OptionReference
import net.perfectdreams.loritta.cinnamon.utils.text.TextUtils.shortenAndStripCodeBackticks
import net.perfectdreams.loritta.cinnamon.utils.text.TextUtils.shortenWithEllipsis
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.discord.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.discord.interactions.autocomplete.AutocompleteContext
import net.perfectdreams.loritta.cinnamon.discord.interactions.vanilla.economy.declarations.BrokerCommand
import net.perfectdreams.loritta.cinnamon.discord.interactions.autocomplete.CinnamonAutocompleteHandler
import net.perfectdreams.loritta.cinnamon.discord.utils.DiscordResourceLimits
import net.perfectdreams.loritta.cinnamon.discord.utils.NumberUtils

class BrokerStockQuantityAutocompleteExecutor(loritta: LorittaCinnamon, val tickerOption: OptionReference<String>) : CinnamonAutocompleteHandler<String>(loritta) {
    override suspend fun handle(context: AutocompleteContext, focusedOption: FocusedCommandOption): Map<String, String> {
        val currentInput = focusedOption.value

        val ticker = context.getArgument(tickerOption)
        val tickerInfo = loritta.services.bovespaBroker.getTicker(ticker.uppercase()) ?: error("User is trying to autocomplete \"$ticker\", but that ticker doesn't exist!")

        val quantity = NumberUtils.convertShortenedNumberToLong(context.i18nContext, currentInput) ?: return mapOf(
            context.i18nContext.get(
                I18nKeysData.Commands.InvalidNumber(currentInput)
            ).shortenAndStripCodeBackticks(DiscordResourceLimits.Command.Options.Description.Length) to "invalid_number"
        )

        return mapOf(
            context.i18nContext.get(
                BrokerCommand.I18N_PREFIX.SharesCountWithPrice(
                    quantity,
                    quantity * tickerInfo.value
                )
            ).shortenWithEllipsis(DiscordResourceLimits.Command.Options.Description.Length) to quantity.toString()
        )
    }
}