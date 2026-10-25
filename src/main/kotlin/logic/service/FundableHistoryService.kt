package org.rdm.online.logic.service

import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.rdm.online.db.funding.FundableHistoryDB
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FundableHistoryService {
    /** Appends an immutable fundable edit for history views and audit tooling. */
    fun record(
        fundableId: Long,
        actorId: Long,
        operation: String,
        payload: JsonElement,
    ): Long = transaction {
        FundableHistoryDB.Edits.insert {
            it[FundableHistoryDB.Edits.fundableId] = fundableId
            it[FundableHistoryDB.Edits.actorId] = actorId
            it[FundableHistoryDB.Edits.operation] = operation
            it[FundableHistoryDB.Edits.payload] = payload.toString()
            it[FundableHistoryDB.Edits.createdAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        } get FundableHistoryDB.Edits.id
    }
}
