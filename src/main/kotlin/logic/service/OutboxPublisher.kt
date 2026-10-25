package org.rdm.online.logic.service

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.rdm.online.db.events.OutboxDB
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class OutboxRecord(
    val id: Long,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: Long,
    val payload: String,
)

/**
 * Delivers committed SQL events to an ephemeral SSE dispatcher.
 * The callback should be idempotent; rows remain durable until marked published.
 */
class OutboxPublisher(
    private val deliver: (OutboxRecord) -> Unit,
) {
    /**
     * Delivers a bounded batch of committed events. Delivery is intentionally
     * outside the write transaction; the outbox row remains durable until the
     * callback returns successfully.
     */
    fun publishBatch(limit: Int = 100): Int {
        val rows = transaction {
            OutboxDB.Events.selectAll()
                .where { OutboxDB.Events.publishedAt.isNull() }
                .limit(limit)
                .map {
                    OutboxRecord(
                        id = it[OutboxDB.Events.id],
                        eventType = it[OutboxDB.Events.eventType],
                        aggregateType = it[OutboxDB.Events.aggregateType],
                        aggregateId = it[OutboxDB.Events.aggregateId],
                        payload = it[OutboxDB.Events.payload],
                    )
                }
        }
        rows.forEach { record ->
            deliver(record)
            transaction {
                OutboxDB.Events.update({
                    (OutboxDB.Events.id eq record.id) and
                        OutboxDB.Events.publishedAt.isNull()
                }) {
                    it[OutboxDB.Events.publishedAt] =
                        Clock.System.now().toLocalDateTime(TimeZone.UTC)
                    it[OutboxDB.Events.attempts] = 1
                }
            }
        }
        return rows.size
    }
}
