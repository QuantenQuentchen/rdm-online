package org.rdm.online.db.user

import org.jetbrains.exposed.v1.core.Table

object User {

    object Connections : Table("users") {
        val id = long("id").autoIncrement()
        val discordId = long("discord_id")

        override val primaryKey = PrimaryKey(id)
    }

    

}