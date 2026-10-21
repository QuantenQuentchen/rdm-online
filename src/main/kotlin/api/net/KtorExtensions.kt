package org.rdm.online.api.net

import org.rdm.online.api.internal.exceptions.BadRequestException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.time.Duration
import org.rdm.online.Env

/** Go's `mux.HandleFunc("/path", ...)`: matches every HTTP method. */
fun Route.anyMethod(path: String, body: suspend RoutingContext.() -> Unit): Route =
    route(path) { handle(body) }

suspend inline fun <reified T : Any> ApplicationCall.receiveOrBadRequest(): T =
    try {
        receive<T>()
    } catch (e: ContentTransformationException) {
        //TODO: Log this, with exception, reason, origin, etc. etc. etc.
        throw BadRequestException("invalid request body")
    }

fun ApplicationCall.setCookie(name: String, value: String, maxAge: Duration) =
    response.cookies.append(
        name = name,
        value = value,
        maxAge = maxAge.inWholeSeconds,
        path = "/",
        secure = !Env.local,
        httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    )
