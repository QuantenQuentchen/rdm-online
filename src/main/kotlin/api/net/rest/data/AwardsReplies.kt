package org.rdm.online.api.net.rest.data

import kotlinx.serialization.Serializable
import org.rdm.online.api.internal.data.awards.Category
import org.rdm.online.api.internal.data.awards.Suggestion

// No default values on purpose: kotlinx.serialization skips defaults on encode,
// and the frontend expects every key (including nulls) to be present.

@Serializable
data class CategoryReply(
    val id: Long,
    val name: String,
    val order: Int,
    val description: String?,
    val criteria: String?,
    val locked: Boolean,
    val maxSuggestions: Int?,
)

@Serializable
data class CategoriesReply(val categories: List<CategoryReply>)

@Serializable
data class SuggestionReply(
    val id: Long,
    val categoryId: Long,
    val text: String,
    val updatedAt: String, // ISO-8601 / RFC 3339 instant
    val nominatorId: Long,
)

/** Used for PUT .../suggestions and GET /suggestions/mine. */
@Serializable
data class SuggestionsReply(val suggestions: List<SuggestionReply>)

@Serializable
data class RichSuggestionReply(val suggestion: SuggestionReply, val user: UserReply)

/** Used for GET .../suggestions and the admin view-all endpoint. */
@Serializable
data class RichSuggestionsReply(val suggestions: List<RichSuggestionReply>)

fun Category.toReply() = CategoryReply(id, name, order, description, criteria, locked, maxSuggestions)

fun Suggestion.toReply() = SuggestionReply(id, categoryId, text, updatedAt.toString(), nominatorId)
