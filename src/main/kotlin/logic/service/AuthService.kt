package org.rdm.online.rdm.auth

import org.rdm.online.api.internal.exceptions.ForbiddenException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import org.rdm.online.db.auth.AuthDB
import org.rdm.online.db.user.UserDB
import org.rdm.online.rdm.discord.DiscordService
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

const val SESSION_COOKIE = "session_token"
const val LOGIN_COOKIE = "discord_login_attempt"

// Kept from the Go version: the DB row expires after 24h, the cookie after 7 days.
private val SESSION_TTL = 24.hours
val SESSION_COOKIE_MAX_AGE = 7.days

private val random = SecureRandom()

internal fun randomToken(bytes: Int = 32): String {
    val b = ByteArray(bytes).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}

internal fun hashToken(token: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray())

class AuthService(private val discord: DiscordService) {

    /** Creates a session row and returns the raw token for the cookie (only its hash is stored). */
    suspend fun startSession(userId: Long): String {
        val token = randomToken()
        AuthDB.createSession(userId, hashToken(token), Instant.now().plus(SESSION_TTL.toJavaDuration()))
        return token
    }

    suspend fun endSession(token: String) = AuthDB.deleteSession(hashToken(token))

    suspend fun userId(call: ApplicationCall): Long? {
        val token = call.request.cookies[SESSION_COOKIE]?.takeIf { it.isNotEmpty() } ?: return null
        return AuthDB.sessionUserId(hashToken(token))
    }

    /** 403 when there's no valid session. The message differs per endpoint in the Go version. */
    suspend fun requireUser(call: ApplicationCall, message: String = "invalid request body"): Long =
        userId(call) ?: throw ForbiddenException(message)

    /** 403 unless the caller has a valid session *and* currently holds an admin role on Discord. */
    suspend fun requireAdmin(call: ApplicationCall): Long {
        val id = requireUser(call)
        val user = UserDB.userById(id)
        if (user == null || !discord.isAdmin(user.discordId)) {
            throw ForbiddenException("invalid request body")
        }
        return id
    }
}
