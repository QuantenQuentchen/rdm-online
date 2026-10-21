package org.rdm.online

object Env {

    val discordClientSecret: String = System.getenv("DISCORD_CLIENT_SECRET") ?: throw IllegalArgumentException("DISCORD_CLIENT_SECRET environment variable is not set")
    val discordClientId: String = System.getenv("DISCORD_CLIENT_ID") ?: throw IllegalArgumentException("DISCORD_CLIENT_ID environment variable is not set")
    val baseUrl: String = System.getenv("BASE_URL") ?: "http://localhost:8080"
    val guildID: String = "776823258385088552"

    val discordBotToken: String = System.getenv("DISCORD_BOT_TOKEN") ?: throw IllegalArgumentException("DISCORD_BOT_TOKEN environment variable is not set")

    val viceAdminRoleId: String = "1242232096370462751"

    val adminRoleId: String = "1242213719795171378"

    val memberRoleId: String = "1242196287676223538"

    val local: Boolean = System.getenv("LOCAL")?.toBoolean() ?: false
}