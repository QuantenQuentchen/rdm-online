package org.rdm.online.api.net

import org.rdm.online.api.net.routes.fundableRoutes
import org.rdm.online.api.net.routes.eventRoutes
import org.rdm.online.api.net.events.EventService
import org.rdm.online.api.net.routes.authRoutes
import org.rdm.online.api.net.routes.awardsRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import org.rdm.online.api.internal.exceptions.NotFoundException
import io.ktor.client.HttpClient
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import kotlinx.serialization.json.Json
import org.rdm.online.rdm.auth.AuthService
import org.rdm.online.rdm.auth.LoginStateStore
import org.rdm.online.rdm.auth.discordOAuth
import org.rdm.online.rdm.discord.DiscordService

fun Application.mainModule() {
    val httpClient = HttpClient {
        install(ClientContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }
    println("Starting RDM Online API server...")
    install(ContentNegotiation) {
        json()
    }
    install(SSE)
    install(Authentication) {
        discordOAuth(httpClient, LoginStateStore())
    }

    install(StatusPages) {
        exception<NotFoundException> { call, cause ->
            call.respondText(
                text = "Not found: ${cause.message}",
                status = io.ktor.http.HttpStatusCode.NotFound
            )
        }

        exception<Throwable> { call, cause ->
            call.respondText(
                text = "Internal server error: ${cause.message}",
                status = io.ktor.http.HttpStatusCode.InternalServerError
            )
        }
    }
    val discord = DiscordService(httpClient)
    val auth = AuthService(discord)
    val events = EventService()

    routing {
        staticResources("/", "generated/dist")
        authRoutes(auth, discord)
        awardsRoutes(auth, discord, events)
        fundableRoutes(auth, events)
        eventRoutes(auth, events)
    }
}