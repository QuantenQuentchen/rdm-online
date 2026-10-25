package org.rdm.online.db.funding

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object FundableHistoryDB {
    /** Immutable audit trail consumed by the fundable history UI. */
    object Edits : Table("fundable_edits") {
        val id = long("id").autoIncrement()
        // Deliberately not a foreign key: edit history survives a fundable deletion.
        val fundableId = long("fundable_id").index()
        val actorId = long("actor_id")
        val operation = varchar("operation", 50)
        val payload = text("payload")
        val createdAt = datetime("created_at")
        override val primaryKey = PrimaryKey(id)
    }
}
