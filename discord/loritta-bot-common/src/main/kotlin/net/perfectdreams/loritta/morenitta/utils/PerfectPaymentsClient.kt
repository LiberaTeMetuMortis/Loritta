package net.perfectdreams.loritta.morenitta.utils

import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.perfectdreams.loritta.morenitta.dao.DonationKey
import net.perfectdreams.loritta.morenitta.tables.DonationKeys
import io.ktor.client.request.*
import io.ktor.client.statement.*
import mu.KotlinLogging
import net.perfectdreams.loritta.morenitta.dao.Payment
import net.perfectdreams.loritta.morenitta.LorittaBot
import net.perfectdreams.loritta.morenitta.utils.payments.PaymentGateway
import net.perfectdreams.loritta.morenitta.utils.payments.PaymentReason
import java.util.*

class PerfectPaymentsClient(val url: String) {
    private val logger = KotlinLogging.logger {}

    /**
     * Creates a payment in PerfectPayments, creates a entry in Loritta's payment table and returns the payment URL
     *
     * @return the payment URL
     */
    suspend fun createPayment(
        loritta: LorittaBot,
        userId: Long,
        paymentTitle: String,
        amount: Long,
        storedAmount: Long,
        paymentReason: PaymentReason,
        externalReference: String,
        discount: Double? = null,
        metadata: JsonObject? = null
    ): String {
        logger.info { "Requesting PerfectPayments payment URL for $userId" }
        val payments = loritta.http.post("${url}api/v1/payments") {
            header("Authorization", loritta.config.loritta.perfectPayments.token)

            setBody(
                jsonObject(
                    "title" to paymentTitle,
                    "callbackUrl" to "${loritta.config.loritta.website.url}api/v1/callbacks/perfect-payments",
                    "amount" to amount,
                    "currencyId" to "BRL",
                    "externalReference" to externalReference
                ).toString()
            )
        }.bodyAsText()

        val paymentResponse = JsonParser.parseString(payments)
            .obj

        val partialPaymentId = UUID.fromString(paymentResponse["id"].string)
        val paymentUrl = paymentResponse["paymentUrl"].string

        logger.info { "Payment successfully created for $userId! ID: $partialPaymentId" }
        loritta.newSuspendedTransaction {
            DonationKey.find {
                DonationKeys.expiresAt greaterEq System.currentTimeMillis()
            }

            Payment.new {
                this.userId = userId
                this.gateway = PaymentGateway.PERFECTPAYMENTS
                this.reason = paymentReason

                if (discount != null)
                    this.discount = discount

                if (metadata != null)
                    this.metadata = metadata

                this.money = (storedAmount.toDouble() / 100).toBigDecimal()
                this.createdAt = System.currentTimeMillis()
                this.referenceId = partialPaymentId
            }
        }

        return paymentUrl
    }
}