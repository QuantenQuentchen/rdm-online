package org.rdm.online.api.net.rest.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PaymentScheduleRequest {
    @Serializable
    @SerialName("one_time")
    data object OneTime : PaymentScheduleRequest

    @Serializable
    @SerialName("recurring")
    data class Recurring(
        val intervalMonths: Long,
        val occurrences: Long? = null
    ) : PaymentScheduleRequest
}

@Serializable
data class FundableComponentRequest(
    val name: String,
    val description: String? = null,
    val amountCent: Long,
    val paymentSchedule: PaymentScheduleRequest
)

@Serializable
data class CreateFundableRequest(
    val name: String,
    val title: String,
    val description: String? = null,
    val components: List<FundableComponentRequest> = emptyList()
)

@Serializable
data class CommitmentRequest(
    val userId: Long,
    val amountCents: Long,
    val paymentSchedule: PaymentScheduleRequest
)

@Serializable
data class InterestRequest(val userId: Long)

@Serializable
data class PaymentScheduleReply(
    val type: String,
    val intervalMonths: Long? = null,
    val occurrences: Long? = null
)

@Serializable
data class FundableComponentReply(
    val id: Long,
    val fundableId: Long,
    val name: String,
    val description: String?,
    val amountCent: Long,
    val paymentSchedule: PaymentScheduleReply,
    val interestedCount: Int,
    val committedCount: Int,
    val totalCommittedAmount: Long,
    val fullyFunded: Boolean,
    val amountCommitted: Long,
    val lifetimeCost: Long,
    val equalDivisionOnRemaining: Long,
    val projectedOverFundReturnPP: Long?
)

@Serializable
data class FundableReply(
    val id: Long,
    val name: String,
    val title: String,
    val description: String?,
    val components: List<FundableComponentReply>,
    val fullyFunded: Boolean
)

@Serializable
data class IdReply(val id: Long)

@Serializable
data class ErrorReply(val message: String)
