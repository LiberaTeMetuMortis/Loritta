package net.perfectdreams.loritta.cinnamon.platform.commands.economy.broker

import net.perfectdreams.loritta.cinnamon.common.emotes.Emotes
import net.perfectdreams.loritta.cinnamon.common.utils.LorittaBovespaBrokerUtils
import net.perfectdreams.loritta.cinnamon.i18n.I18nKeysData
import net.perfectdreams.loritta.cinnamon.platform.commands.ApplicationCommandContext
import net.perfectdreams.loritta.cinnamon.platform.commands.CinnamonSlashCommandExecutor
import net.perfectdreams.loritta.cinnamon.platform.LorittaCinnamon
import net.perfectdreams.loritta.cinnamon.platform.commands.economy.declarations.BrokerCommand
import net.perfectdreams.loritta.cinnamon.platform.commands.options.LocalizedApplicationCommandOptions
import net.perfectdreams.discordinteraktions.common.commands.options.SlashCommandArguments
import net.perfectdreams.loritta.cinnamon.platform.commands.styled
import net.perfectdreams.loritta.cinnamon.platform.utils.NumberUtils
import net.perfectdreams.loritta.cinnamon.platform.utils.SonhosUtils.userHaventGotDailyTodayOrUpsellSonhosBundles
import net.perfectdreams.loritta.cinnamon.pudding.data.UserId
import net.perfectdreams.loritta.cinnamon.pudding.services.BovespaBrokerService

class BrokerBuyStockExecutor(loritta: LorittaCinnamon) : CinnamonSlashCommandExecutor(loritta) {
    inner class Options : LocalizedApplicationCommandOptions(loritta) {
        val ticker = string("ticker", BrokerCommand.I18N_PREFIX.Buy.Options.Ticker.Text) {
            LorittaBovespaBrokerUtils.trackedTickerCodes.toList().sortedBy { it.first }.forEach { (tickerId, tickerTitle) ->
                choice("$tickerTitle ($tickerId)", tickerId.lowercase())
            }
        }

        val quantity = optionalString("quantity", BrokerCommand.I18N_PREFIX.Buy.Options.Quantity.Text) {
            autocomplete(BrokerStockQuantityAutocompleteExecutor(loritta, ticker))
        }
    }

    override val options = Options()

    override suspend fun execute(context: ApplicationCommandContext, args: SlashCommandArguments) {
        context.deferChannelMessageEphemerally()

        val tickerId = args[options.ticker].uppercase()
        val quantityAsString = args[options.quantity] ?: "1"

        // This should *never* happen because the values are validated on Discord side BUT who knows
        if (tickerId !in LorittaBovespaBrokerUtils.validStocksCodes)
            context.failEphemerally(context.i18nContext.get(BrokerCommand.I18N_PREFIX.ThatIsNotAnValidStockTicker))

        val quantity = NumberUtils.convertShortenedNumberToLong(context.i18nContext, quantityAsString) ?: context.failEphemerally(
            context.i18nContext.get(
                I18nKeysData.Commands.InvalidNumber(quantityAsString)
            )
        )

        val (_, boughtQuantity, value) = try {
            context.loritta.services.bovespaBroker.buyStockShares(
                context.user.id.value.toLong(),
                tickerId,
                quantity
            )
        } catch (e: BovespaBrokerService.TransactionActionWithLessThanOneShareException) {
            context.failEphemerally(
                context.i18nContext.get(
                    when (quantity) {
                        0L -> BrokerCommand.I18N_PREFIX.Buy.TryingToBuyZeroShares
                        else -> BrokerCommand.I18N_PREFIX.Buy.TryingToBuyLessThanZeroShares
                    }
                )
            )
        } catch (e: BovespaBrokerService.StaleTickerDataException) {
            context.failEphemerally(context.i18nContext.get(BrokerCommand.I18N_PREFIX.StaleTickerData))
        } catch (e: BovespaBrokerService.OutOfSessionException) {
            context.failEphemerally(
                context.i18nContext.get(
                    BrokerCommand.I18N_PREFIX.StockMarketClosed(
                        LorittaBovespaBrokerUtils.TIME_OPEN_DISCORD_TIMESTAMP,
                        LorittaBovespaBrokerUtils.TIME_CLOSING_DISCORD_TIMESTAMP
                    )
                )
            )
        } catch (e: BovespaBrokerService.NotEnoughSonhosException) {
            context.failEphemerally {
                styled(
                    context.i18nContext.get(BrokerCommand.I18N_PREFIX.Buy.YouDontHaveEnoughSonhos),
                    Emotes.LoriSob
                )

                userHaventGotDailyTodayOrUpsellSonhosBundles(
                    context.loritta,
                    context.i18nContext,
                    UserId(context.user.id.value),
                    "lori-broker",
                    "buy-shares-not-enough-sonhos"
                )
            }
        } catch (e: BovespaBrokerService.TooManySharesException) {
            context.failEphemerally(
                context.i18nContext.get(
                    BrokerCommand.I18N_PREFIX.Buy.TooManyShares(
                        LorittaBovespaBrokerUtils.MAX_STOCK_SHARES_PER_USER
                    )
                )
            )
        }

        context.sendEphemeralReply(
            context.i18nContext.get(
                BrokerCommand.I18N_PREFIX.Buy.SuccessfullyBought(
                    sharesCount = boughtQuantity,
                    ticker = tickerId,
                    price = value
                )
            ),
            Emotes.LoriRich
        )
    }
}