package org.rdm.online.api.internal.funding

data class FundableComponent(
    val id: Long?,
    val fundableID: Long,
    val name: String,
    val description: String?,
    val amountCent: Long,
    val paymentSchedule: PaymentSchedule
)

data class Fundable(
    val id: Long?,
    val name: String,
    val title: String,
    val description: String?,
    val components: List<FundableComponent>?
)

data class FundableComponentCommitment(
    val id: Long?,
    val fundableComponentId: Long,
    val commitedUser: Long,
    val amountCommitedCents: Long,
    val paymentSchedule: PaymentSchedule,
    val withdrawn: Boolean
)

sealed interface Event {
    data class FundableAdded(val fundable: Fundable) : Event
    data class FundableRemoved(val fundableId: Long) : Event
    data class FundableComponentAdded(val fundableComponent: FundableComponent) : Event
    data class FundableComponentRemoved(val fundableComponentId: Long) : Event
    data class CommitmentMade(val commitment: FundableComponentCommitment) : Event
    data class CommitmentWithdrawn(val commitmentId: Long) : Event
    data class InterestExpressed(val userId: Long, val fundableComponentId: Long) : Event
    data class InterestWithdrawn(val userId: Long, val fundableComponentId: Long) : Event
}