package net.perfectdreams.loritta.cinnamon.microservices.dailytax.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import mu.KotlinLogging
import net.perfectdreams.loritta.cinnamon.microservices.dailytax.DailyTax
import net.perfectdreams.loritta.cinnamon.pudding.data.UserDailyTaxTaxedDirectMessage
import net.perfectdreams.loritta.cinnamon.pudding.data.UserDailyTaxWarnDirectMessage
import net.perfectdreams.loritta.cinnamon.pudding.data.UserId
import net.perfectdreams.loritta.cinnamon.pudding.tables.DailyTaxPendingDirectMessages
import net.perfectdreams.loritta.cinnamon.pudding.tables.DailyTaxSonhosTransactionsLog
import net.perfectdreams.loritta.cinnamon.pudding.tables.Profiles
import net.perfectdreams.loritta.cinnamon.pudding.tables.SonhosTransactionsLog
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset

class DailyTaxCollector(val m: DailyTax) : RunnableCoroutineWrapper() {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override suspend fun runCoroutine() {
        try {
            logger.info { "Collecting tax from inactive daily users..." }

            val now = Clock.System.now()
            val nextTrigger = LocalDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC)
                .toKotlinInstant()

            val alreadyWarnedThatTheyWereTaxed = mutableSetOf<Long>()

            m.services.transaction {
                // Delete all pending direct messages because we will replace with newer messages
                DailyTaxPendingDirectMessages.deleteAll()

                DailyTaxUtils.getAndProcessInactiveDailyUsers(m.config.discord.applicationId, 0) { threshold, inactiveDailyUser ->
                    alreadyWarnedThatTheyWereTaxed.add(inactiveDailyUser.id)

                    Profiles.update({ Profiles.id eq inactiveDailyUser.id }) {
                        with(SqlExpressionBuilder) {
                            it.update(Profiles.money, Profiles.money - inactiveDailyUser.moneyToBeRemoved)
                        }
                    }

                    val timestampLogId = SonhosTransactionsLog.insertAndGetId {
                        it[SonhosTransactionsLog.user] = inactiveDailyUser.id
                        it[SonhosTransactionsLog.timestamp] = now.toJavaInstant()
                    }

                    DailyTaxSonhosTransactionsLog.insert {
                        it[DailyTaxSonhosTransactionsLog.timestampLog] = timestampLogId
                        it[DailyTaxSonhosTransactionsLog.sonhos] = inactiveDailyUser.moneyToBeRemoved
                        it[DailyTaxSonhosTransactionsLog.maxDayThreshold] = threshold.maxDayThreshold
                        it[DailyTaxSonhosTransactionsLog.minimumSonhosForTrigger] = threshold.minimumSonhosForTrigger
                        it[DailyTaxSonhosTransactionsLog.tax] = threshold.tax
                    }

                    m.services.users._insertPendingDailyTaxDirectMessage(
                        UserId(inactiveDailyUser.id),
                        UserDailyTaxTaxedDirectMessage(
                            now,
                            nextTrigger,
                            inactiveDailyUser.money,
                            inactiveDailyUser.moneyToBeRemoved,
                            threshold.maxDayThreshold,
                            threshold.minimumSonhosForTrigger,
                            threshold.tax
                        )
                    )
                }
            }

            logger.info { "Successfully collected today's daily tax!" }
            logger.info { "Checking how many users would be affected if they didn't get the daily between today and tomorrow..." }

            val tomorrowAtMidnight = LocalDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant(ZoneOffset.UTC)
                .toKotlinInstant()

            m.services.transaction {
                DailyTaxUtils.getAndProcessInactiveDailyUsers(m.config.discord.applicationId, 1) { threshold, inactiveDailyUser ->
                    // Don't warn them about the tax if they were already taxed before
                    if (!alreadyWarnedThatTheyWereTaxed.contains(inactiveDailyUser.id)) {
                        m.services.users._insertPendingDailyTaxDirectMessage(
                            UserId(inactiveDailyUser.id),
                            UserDailyTaxWarnDirectMessage(
                                tomorrowAtMidnight,
                                now,
                                inactiveDailyUser.money,
                                inactiveDailyUser.moneyToBeRemoved,
                                threshold.maxDayThreshold,
                                threshold.minimumSonhosForTrigger,
                                threshold.tax
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn { "Something went wrong while collecting tax from inactive daily users!" }
        }
    }
}