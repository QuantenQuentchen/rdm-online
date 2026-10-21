package org.rdm.online.api.internal.data.awards

data class Category(
    val id: Long,
    val name: String,
    val order: Int,
    val description: String?,
    val criteria: String?,
    val locked: Boolean,
    val maxSuggestions: Int?,
)

