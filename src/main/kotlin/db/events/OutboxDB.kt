package org.rdm.online.db.events

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object OutboxDB {
    /** Durable post-commit event handoff from SQL transactions to SSE delivery. */
    object Events : Table("domain_outbox") {
        val id = long("id").autoIncrement()
        val eventType = varchar("event_type", 100)
        val aggregateType = varchar("aggregate_type", 50)
        val aggregateId = long("aggregate_id")
        val payload = text("payload")
        val createdAt = datetime("created_at")
        val publishedAt = datetime("published_at").nullable()
        val attempts = integer("attempts").default(0)
        override val primaryKey = PrimaryKey(id)
    }
}
