package org.rdm.online.api.net.routes

import org.rdm.online.api.internal.exceptions.ForbiddenException
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.sse.sse
import kotlinx.serialization.json.Json
import org.rdm.online.api.net.events.ApplicationEvent
import org.rdm.online.api.net.events.EventAuthorization
import org.rdm.online.api.net.events.EventScope
import org.rdm.online.api.net.events.EventScopeType
import org.rdm.online.api.net.events.EventService
import org.rdm.online.rdm.auth.AuthService

fun Route.eventRoutes(auth: AuthService, events: EventService) {
    /**
     * Opens an authenticated Server-Sent Events stream.
     *
     * Repeat the `scope` query parameter to subscribe to several scopes, for
     * example `scope=fundable:12&scope=awards`. If a bounded subscriber queue
     * overflows, the service emits `Resync` and closes the stream.
     */
    route("/api/events") {
        sse {
            val userId = auth.requireUser(call)
            val authorization = try {
                auth.requireAdmin(call)
                EventAuthorization.ADMIN
            } catch (_: ForbiddenException) {
                EventAuthorization.USER
            }
            val scopes = call.request.queryParameters.getAll("scope")
                ?.mapNotNull(String::toEventScope)
                ?.toSet()
                ?: emptySet()
            val subscription = events.subscribe(userId, authorization, scopes)

            try {
                subscription.stream.collect { event: ApplicationEvent ->
                    val encodedEvent: String =
                        Json.encodeToString(ApplicationEvent.serializer(), event)
                    send(data = encodedEvent, event = event::class.simpleName)
                    if (event is ApplicationEvent.Resync) {
                        subscription.close()
                    }
                }
            } finally {
                subscription.close()
            }
        }
    }
}

private fun String.toEventScope(): EventScope? {
    val parts = split(':', limit = 2)
    val id = parts.getOrNull(1)
    return when (parts[0].lowercase()) {
        "fundable" -> EventScope(EventScopeType.FUNDABLE, id)
        "component", "fundable_component" ->
            id?.toLongOrNull()?.let { EventScope(EventScopeType.FUNDABLE_COMPONENT, it.toString()) }
        "awards", "award" -> EventScope(EventScopeType.AWARDS, id)
        "admin" -> EventScope(EventScopeType.ADMIN)
        else -> null
    }
}
