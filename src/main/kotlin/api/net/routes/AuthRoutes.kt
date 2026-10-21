package org.rdm.online.api.net.routes

import org.rdm.online.api.internal.exceptions.ForbiddenException
import org.rdm.online.api.internal.exceptions.InternalServerErrorException
import org.rdm.online.api.internal.exceptions.NotFoundException
import org.rdm.online.api.net.anyMethod
import org.rdm.online.api.net.rest.data.IsAdminReply
import org.rdm.online.api.net.rest.data.SessionReply
import org.rdm.online.api.net.setCookie
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.rdm.online.db.user.UserDB
import org.rdm.online.rdm.auth.*
import org.rdm.online.rdm.discord.DiscordService
import java.security.MessageDigest

fun Route.authRoutes(auth: AuthService, discord: DiscordService) {

    authenticate(DISCORD_OAUTH) {
        // Ktor's OAuth provider answers this itself with the redirect to Discord.
        anyMethod("/api/auth/discord/login") {}

        // The provider has already verified `state` and exchanged the code by the time we get here.
        anyMethod("/api/auth/discord/callback") {
            val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                ?: throw BadRequestException("login failed")

            val cookie = call.request.cookies[LOGIN_COOKIE]
                ?: throw BadRequestException("missing login attempt")
            call.response.cookies.append(
                name = LOGIN_COOKIE,
                value = "",
                maxAge = 0,
                path = "/"
            )
            val state = principal.state ?: throw BadRequestException("missing oauth state")
            if (!MessageDigest.isEqual(cookie.toByteArray(), state.toByteArray())) {
                throw BadRequestException("invalid state")
            }

            val discordId = discord.oauthUserId(principal.accessToken)
            val member = discord.memberForLogin(discordId)
            if (!member.isVoter) throw ForbiddenException("not eligible for login")

            val user = UserDB.getOrCreateUser(discordId)
            call.setCookie(SESSION_COOKIE, auth.startSession(user.id), SESSION_COOKIE_MAX_AGE)
            call.respondRedirect("/") // 302, like http.StatusFound
        }
    }

    anyMethod("/api/auth/session") {
        val id = auth.requireUser(call, "nope!")
        val user = UserDB.userById(id)
            ?: throw InternalServerErrorException("failed to get user")
        call.respond(SessionReply(discord.userReply(user.discordId)))
    }

    anyMethod("/api/auth/logout") {
        val token = call.request.cookies[SESSION_COOKIE]
            ?: throw BadRequestException("no authorization token provided")
        if (token.isEmpty()) throw BadRequestException("authorization token is empty")

        auth.endSession(token)
        call.response.cookies.append(
            name = SESSION_COOKIE,
            value = "",
            maxAge = 0,
            path = "/"
        )
        call.respond(HttpStatusCode.OK)
    }

    anyMethod("/api/auth/admin") {
        val id = auth.requireUser(call, "nope!")
        val user = UserDB.userById(id) ?: throw NotFoundException("user not found")
        call.respond(IsAdminReply(discord.isAdmin(user.discordId)))
    }
}
