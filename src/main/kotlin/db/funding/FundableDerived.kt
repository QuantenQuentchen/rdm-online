package org.rdm.online.state

import org.rdm.online.api.internal.funding.PaymentSchedule
import org.rdm.online.db.funding.FundableDB
import java.util.concurrent.ConcurrentHashMap

object FundableDerived {

    data class CachedFundableComponent(
        val id: Long,
        val fundableId: Long,
        val name: String,
        val description: String?,
        val amountCent: Long,
        val paymentSchedule: PaymentSchedule,
        val interestedCount: Int,
        val committedCount: Int,
        val totalCommittedAmount: Long,
        val fullyFunded: Boolean,
        val amountCommitted: Long,
        val lifetimeCost: Long,
        val equalDivisionOnRemaining: Long,
        val projectedOverFundReturnPP: Long?
    )

    data class CachedFundable(
        val id: Long,
        val name: String,
        val title: String,
        val description: String?,
        val components: List<CachedFundableComponent>,
        val fullyFunded: Boolean
    )

    private val componentCache = ConcurrentHashMap<Long, CachedFundableComponent>()
    private val fundableCache = ConcurrentHashMap<Long, CachedFundable>()

    fun getCachedFundableComponent(fundableComponentId: Long): CachedFundableComponent =
        componentCache[fundableComponentId] ?: refreshFundableComponent(fundableComponentId)

    fun getCachedFundable(fundableId: Long): CachedFundable =
        fundableCache[fundableId] ?: refreshFundable(fundableId)

    fun invalidateFundableComponent(fundableComponentId: Long) {
        componentCache.remove(fundableComponentId)?.let { fundableCache.remove(it.fundableId) }
    }

    fun invalidateFundable(fundableId: Long) {
        fundableCache.remove(fundableId)
        componentCache.entries.removeIf { it.value.fundableId == fundableId }
    }

    fun refreshFundableComponent(fundableComponentId: Long): CachedFundableComponent {
        val cached = FundableDB.getComponentSnapshot(fundableComponentId).toCached()
        componentCache[fundableComponentId] = cached
        fundableCache.remove(cached.fundableId)
        return cached
    }

    fun refreshFundable(fundableId: Long): CachedFundable {
        val snapshot = FundableDB.getFundableSnapshot(fundableId)
        val cachedComponents = snapshot.components.map { it.toCached() }
        cachedComponents.forEach { componentCache[it.id] = it }
        return CachedFundable(
            id = snapshot.id,
            name = snapshot.name,
            title = snapshot.title,
            description = snapshot.description,
            components = cachedComponents,
            fullyFunded = snapshot.fullyFunded
        ).also { fundableCache[fundableId] = it }
    }

    fun recomputeFundableComponent(fundableComponentID: Long) =
        refreshFundableComponent(fundableComponentID)

    private fun FundableDB.ComponentSnapshot.toCached() = CachedFundableComponent(
        id = id,
        fundableId = fundableId,
        name = name,
        description = description,
        amountCent = amountCent,
        paymentSchedule = paymentSchedule,
        interestedCount = interestedCount,
        committedCount = committedCount,
        totalCommittedAmount = totalCommittedAmount,
        fullyFunded = fullyFunded,
        amountCommitted = amountCommitted,
        lifetimeCost = lifetimeCost,
        equalDivisionOnRemaining = equalDivisionOnRemaining,
        projectedOverFundReturnPP = projectedOverFundReturnPP
    )
}
