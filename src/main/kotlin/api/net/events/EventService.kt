package org.rdm.online.api.net.events

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class EventService(
    private val queueCapacity: Int = 64
) {
    private data class Subscriber(
        val id: String,
        val userId: Long,
        val authorization: EventAuthorization,
        val scopes: Set<EventScope>,
        val channel: Channel<ApplicationEvent>
    )

    class Subscription internal constructor(
        val id: String,
        val stream: Flow<ApplicationEvent>,
        private val closeSubscription: () -> Unit
    ) : AutoCloseable {
        override fun close() = closeSubscription()
    }

    private val subscribers = ConcurrentHashMap<String, Subscriber>()

    fun subscribe(
        userId: Long,
        authorization: EventAuthorization,
        scopes: Set<EventScope>
    ): Subscription {
        require(scopes.isNotEmpty()) { "At least one event scope is required" }
        require(queueCapacity > 0) { "Queue capacity must be positive" }
        require(
            authorization == EventAuthorization.ADMIN ||
                scopes.none { it.type == EventScopeType.ADMIN }
        ) { "Admin scope requires admin authorization" }

        val id = UUID.randomUUID().toString()
        val subscriber = Subscriber(
            id = id,
            userId = userId,
            authorization = authorization,
            scopes = scopes,
            channel = Channel(queueCapacity)
        )
        subscribers[id] = subscriber
        val eventFlow: Flow<ApplicationEvent> = subscriber.channel.receiveAsFlow()
        return Subscription(id, eventFlow) {
            if (subscribers.remove(id) != null) subscriber.channel.close()
        }
    }

    fun dispatch(event: ApplicationEvent) {
        subscribers.values.forEach { subscriber ->
            if (!subscriber.canReceive(event)) return@forEach
            if (subscriber.channel.trySend(event).isSuccess) return@forEach

            while (subscriber.channel.tryReceive().isSuccess) {
                // A coherent refetch is safer than replaying a partial event stream.
            }
            subscriber.channel.trySend(
                ApplicationEvent.Resync(event.scope, subscriber.authorization)
            )
            subscriber.channel.close()
        }
    }

    fun subscriberCount(): Int = subscribers.size

    private fun Subscriber.canReceive(event: ApplicationEvent): Boolean =
        authorization.ordinal >= event.authorization.ordinal &&
            scopes.any { it.includes(event.scope) }
}
