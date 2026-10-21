package org.rdm.online.api.internal.data.awards

/** A suggestion plus the Discord id of its nominator (so callers can resolve names/avatars). */
data class NominatedSuggestion(val suggestion: Suggestion, val nominatorDiscordId: String)