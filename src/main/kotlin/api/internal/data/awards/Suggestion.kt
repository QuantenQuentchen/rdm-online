package org.rdm.online.api.internal.data.awards

import java.time.Instant

data class Suggestion(
    val id: Long,
    val categoryId: Long,
    val text: String,
    val updatedAt: Instant,
    val nominatorId: Long,
)
