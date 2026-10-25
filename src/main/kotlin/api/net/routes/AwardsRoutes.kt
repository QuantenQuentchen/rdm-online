package org.rdm.online.api.net.routes

import org.rdm.online.api.internal.exceptions.BadRequestException
import org.rdm.online.api.internal.exceptions.InternalServerErrorException
import org.rdm.online.api.net.rest.data.CategoriesReply
import org.rdm.online.api.net.rest.data.CategoryDeletionRequest
import org.rdm.online.api.net.rest.data.CategoryUpsertRequest
import org.rdm.online.api.net.rest.data.RichSuggestionReply
import org.rdm.online.api.net.rest.data.RichSuggestionsReply
import org.rdm.online.api.net.rest.data.SuggestionsReply
import org.rdm.online.api.net.rest.data.SuggestionsRequest
import org.rdm.online.api.net.rest.data.toCategory
import org.rdm.online.api.net.rest.data.toReply
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.rdm.online.rdm.auth.AuthService
import org.rdm.online.db.awards.AwardsDB
import org.rdm.online.api.internal.data.awards.NominatedSuggestion
import org.rdm.online.api.internal.exceptions.AwardsRuleViolation
import org.rdm.online.rdm.discord.DiscordService
import org.rdm.online.api.net.anyMethod
import org.rdm.online.api.net.receiveOrBadRequest
import org.rdm.online.api.net.events.ApplicationEvent
import org.rdm.online.api.net.events.EventService

fun Route.awardsRoutes(auth: AuthService, discord: DiscordService, events: EventService) {

    /** Lists award categories. */
    anyMethod("/api/awards/categories") {
        call.respond(CategoriesReply(AwardsDB.categories().map { it.toReply() }))
    }

    route("/api/awards/categories/{categoryID}/suggestions") {
        /** Lists suggestions in a category with Discord profile enrichment. */
        get {
            auth.requireUser(call)
            val categoryId = call.categoryId()
            AwardsDB.category(categoryId) ?: throw NotFoundException("category not found")

            call.respond(RichSuggestionsReply(discord.rich(AwardsDB.suggestionsIn(categoryId))))
        }

        /** Replaces the authenticated user's suggestions for a category. */
        put {
            val userId = auth.requireUser(call)
            val categoryId = call.categoryId()
            val req = call.receiveOrBadRequest<SuggestionsRequest>()

            val saved = try {
                AwardsDB.replaceSuggestions(categoryId, userId, req.suggestions.map { it.text })
            } catch (e: AwardsRuleViolation) {
                // The Go handler answered 500 for every failure, including locked / limit / missing category.
                // Switch to Locked / Conflict / NotFound here if the frontend can cope.
                throw InternalServerErrorException("failed to upsert suggestions")
            }
            events.dispatch(ApplicationEvent.AwardsSuggestionsChanged(categoryId))
            call.respond(SuggestionsReply(saved.map { it.toReply() }))
        }
    }

    /** Lists suggestions submitted by the authenticated user. */
    anyMethod("/api/awards/suggestions/mine") {
        val userId = auth.requireUser(call)
        call.respond(SuggestionsReply(AwardsDB.suggestionsOf(userId).map { it.toReply() }))
    }

    // ---- admin ------------------------------------------------------------

    /** Lists all suggestions; restricted to administrators. */
    anyMethod("/api/admin/viewSuggestions") {
        auth.requireAdmin(call)
        call.respond(RichSuggestionsReply(discord.rich(AwardsDB.allSuggestions())))
    }

    /** Creates or updates an award category; restricted to administrators. */
    anyMethod("/api/admin/upsert/Categories") {
        auth.requireAdmin(call)
        val req = call.receiveOrBadRequest<CategoryUpsertRequest>()
        val category = req.category.toCategory(id = req.id ?: req.category.id)
        AwardsDB.upsertCategory(category)
        events.dispatch(ApplicationEvent.AwardsCategoryChanged(category.id))
        call.respond(HttpStatusCode.OK)
    }

    /** Deletes an award category; restricted to administrators. */
    anyMethod("/api/admin/delete/Categories") {
        auth.requireAdmin(call)
        val req = call.receiveOrBadRequest<CategoryDeletionRequest>()
        AwardsDB.deleteCategory(req.id)
        events.dispatch(ApplicationEvent.AwardsCategoryChanged(req.id))
        call.respond(HttpStatusCode.OK)
    }
}

private fun ApplicationCall.categoryId(): Long =
    parameters["categoryID"]?.toLongOrNull() ?: throw BadRequestException("invalid category ID")

/** Attaches the nominator's Discord profile (looked up once per distinct nominator, in parallel). */
private suspend fun DiscordService.rich(items: List<NominatedSuggestion>): List<RichSuggestionReply> =
    coroutineScope {
        val users = items.map { it.nominatorDiscordId }.distinct()
            .map { id -> async { id to userReply(id) } }
            .awaitAll().toMap()
        items.map { RichSuggestionReply(it.suggestion.toReply(), users.getValue(it.nominatorDiscordId)) }
    }
