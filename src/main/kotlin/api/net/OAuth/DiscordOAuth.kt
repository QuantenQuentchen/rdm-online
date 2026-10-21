package org.rdm.online.rdm.auth

import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.auth.*
import org.rdm.online.Env
import io.ktor.util.NonceManager
import org.rdm.online.api.net.setCookie
import java.time.Duration
import kotlin.time.Duration.Companion.minutes

const val DISCORD_OAUTH = "discord"

/**
 * One-time, 5-minute OAuth `state` values (replaces Go's LoginAttemptStore + eviction loop).
 * Ktor's default nonce manager never verifies `state`, so we plug in a real one.
 */
class LoginStateStore : NonceManager {
    private val states = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build<String, Boolean>()

    override suspend fun newNonce(): String = randomToken().also { states.put(it, true) }

    // remove() makes every state single-use
    override suspend fun verifyNonce(nonce: String): Boolean = states.asMap().remove(nonce) != null
}

fun AuthenticationConfig.discordOAuth(http: HttpClient, states: LoginStateStore) {
    oauth(DISCORD_OAUTH) {
        client = http
        urlProvider = { "${Env.baseUrl}/api/auth/discord/callback" }
        providerLookup = {
            OAuthServerSettings.OAuth2ServerSettings(
                name = "discord",
                authorizeUrl = "https://discord.com/oauth2/authorize",
                accessTokenUrl = "https://discord.com/api/v10/oauth2/token",
                requestMethod = HttpMethod.Post,
                clientId = Env.discordClientId,
                clientSecret = Env.discordClientSecret,
                defaultScopes = listOf("identify"),
                accessTokenRequiresBasicAuth = true, // == oauth2.AuthStyleInHeader
                nonceManager = states,
                // Bind the flow to this browser: the callback must present the same state in a cookie.
                onStateCreated = { call, state -> call.setCookie(LOGIN_COOKIE, state, 5.minutes) },
            )
        }
    }
}
