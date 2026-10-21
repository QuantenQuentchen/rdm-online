package org.rdm.online.db.auth

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.rdm.online.db.user.UserDB.Users
import org.rdm.online.db.dbQuery
import java.time.Instant


object AuthDB {

    object Sessions : Table("sessions") {
        val tokenHash = binary("token_hash")
        val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
            .index("idx_sessions_user_id")
        val createdAt = timestamp("created_at")
        val expiresAt = timestamp("expires_at").index("idx_sessions_expires_at")
        override val primaryKey = PrimaryKey(tokenHash)
    }

    suspend fun createSession(userId: Long, tokenHash: ByteArray, expiresAt: Instant) {
        dbQuery {
            Sessions.insert {
                it[Sessions.tokenHash] = tokenHash
                it[Sessions.userId] = userId
                it[createdAt] = Instant.now()
                it[Sessions.expiresAt] = expiresAt
            }
        }
    }

    /** The owner of a live (non-expired) session, or null. */
    suspend fun sessionUserId(tokenHash: ByteArray): Long? = dbQuery {
        Sessions.select(Sessions.userId)
            .where { (Sessions.tokenHash eq tokenHash) and (Sessions.expiresAt greater Instant.now()) }
            .singleOrNull()?.get(Sessions.userId)
    }

    suspend fun deleteSession(tokenHash: ByteArray) {
        dbQuery { Sessions.deleteWhere { Sessions.tokenHash eq tokenHash } }
    }

}
