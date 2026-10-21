package org.rdm.online.api.net.events

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
enum class EventAuthorization {
    USER,
    ADMIN
}

@Serializable
enum class EventScopeType {
    FUNDABLE,
    FUNDABLE_COMPONENT,
    AWARDS,
    ADMIN
}

@Serializable
data class EventScope(
    val type: EventScopeType,
    val id: String? = null
) {
    fun includes(other: EventScope): Boolean =
        type == other.type && (id == null || id == other.id)
}

@Serializable
sealed interface ApplicationEvent {
    val scope: EventScope
    val authorization: EventAuthorization

    @Serializable
    @SerialName("event")
    data class Generic(
        val name: String,
        override val scope: EventScope,
        override val authorization: EventAuthorization = EventAuthorization.USER,
        val data: JsonElement? = null
    ) : ApplicationEvent

    @Serializable
    @SerialName("resync")
    data class Resync(
        override val scope: EventScope,
        override val authorization: EventAuthorization = EventAuthorization.USER
    ) : ApplicationEvent

    @Serializable
    @SerialName("fundable_added")
    data class FundableAdded(val fundableId: Long) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.FUNDABLE, fundableId.toString())
        override val authorization = EventAuthorization.USER
    }

    @Serializable
    @SerialName("fundable_removed")
    data class FundableRemoved(val fundableId: Long) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.FUNDABLE, fundableId.toString())
        override val authorization = EventAuthorization.USER
    }

    @Serializable
    @SerialName("fundable_component_changed")
    data class FundableComponentChanged(
        val fundableId: Long,
        val componentId: Long
    ) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.FUNDABLE_COMPONENT, componentId.toString())
        override val authorization = EventAuthorization.USER
    }

    @Serializable
    @SerialName("commitment_changed")
    data class CommitmentChanged(
        val fundableId: Long,
        val componentId: Long,
        val commitmentId: Long
    ) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.FUNDABLE_COMPONENT, componentId.toString())
        override val authorization = EventAuthorization.USER
    }

    @Serializable
    @SerialName("interest_changed")
    data class InterestChanged(
        val fundableId: Long,
        val componentId: Long
    ) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.FUNDABLE_COMPONENT, componentId.toString())
        override val authorization = EventAuthorization.USER
    }

    @Serializable
    @SerialName("awards_suggestions_changed")
    data class AwardsSuggestionsChanged(val categoryId: Long) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.AWARDS, categoryId.toString())
        override val authorization = EventAuthorization.USER
    }

    @Serializable
    @SerialName("awards_category_changed")
    data class AwardsCategoryChanged(val categoryId: Long) : ApplicationEvent {
        override val scope = EventScope(EventScopeType.AWARDS, categoryId.toString())
        override val authorization = EventAuthorization.ADMIN
    }
}
