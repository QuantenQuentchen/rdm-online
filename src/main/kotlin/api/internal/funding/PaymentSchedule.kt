package org.rdm.online.api.internal.funding

sealed interface PaymentSchedule {
    data object OneTime : PaymentSchedule

    data class Recurring(
        val intervalMonths: Long,
        val occurrences: Long? = null
    ) : PaymentSchedule
}