@file:OptIn(ExperimentalSerializationApi::class)

package org.rdm.online.api.net.rest.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import org.rdm.online.api.internal.data.awards.Category

// Defaults + ignoreUnknownKeys mirror Go's lenient decoding (missing field -> zero value, extras ignored).

@Serializable
@JsonIgnoreUnknownKeys
data class SuggestionInput(val text: String = "")

@Serializable
@JsonIgnoreUnknownKeys
data class SuggestionsRequest(val suggestions: List<SuggestionInput> = emptyList())

@Serializable
@JsonIgnoreUnknownKeys
data class CategoryInput(
    val id: Long = 0,
    val name: String = "",
    val order: Int = 0,
    val description: String? = null,
    val criteria: String? = null,
    val locked: Boolean = false,
    val maxSuggestions: Int? = null,
)

@Serializable
@JsonIgnoreUnknownKeys
data class CategoryUpsertRequest(val id: Long? = null, val category: CategoryInput = CategoryInput())

@Serializable
@JsonIgnoreUnknownKeys
data class CategoryDeletionRequest(val id: Long = 0)

fun CategoryInput.toCategory(id: Long) = Category(id, name, order, description, criteria, locked, maxSuggestions)
