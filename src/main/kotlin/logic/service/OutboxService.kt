package org.rdm.online.logic.service

import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.rdm.online.db.events.OutboxDB
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class OutboxService {
    fun enqueue(
        eventType: String,
        aggregateType: String,
        aggregateId: Long,
        payload: JsonElement,
    ): Long = transaction {
        OutboxDB.Events.insert {
            it[OutboxDB.Events.eventType] = eventType
            it[OutboxDB.Events.aggregateType] = aggregateType
            it[OutboxDB.Events.aggregateId] = aggregateId
            it[OutboxDB.Events.payload] = payload.toString()
            it[OutboxDB.Events.createdAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        } get OutboxDB.Events.id
    }
}
