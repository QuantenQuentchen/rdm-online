package org.rdm.online.db.user

import org.rdm.online.api.internal.data.User
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.rdm.online.db.dbQuery

object UserDB {

    object Users : Table("users") {
        val id = long("id").autoIncrement()
        val discordId = text("discord_id").uniqueIndex()
        override val primaryKey = PrimaryKey(id)
    }

    /** Atomic within the (IMMEDIATE) transaction, so concurrent first logins can't race. */
    suspend fun getOrCreateUser(discordId: String): User = dbQuery {
        Users.insertIgnore { it[Users.discordId] = discordId }
        Users.selectAll().where { Users.discordId eq discordId }.single().toUser()
    }

    suspend fun userById(id: Long): User? = dbQuery {
        Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }


    private fun ResultRow.toUser() = User(this[Users.id], this[Users.discordId])
}