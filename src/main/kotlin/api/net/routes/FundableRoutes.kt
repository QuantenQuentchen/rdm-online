package org.rdm.online.api.net.routes

import org.rdm.online.api.net.rest.data.CommitmentRequest
import org.rdm.online.api.net.rest.data.CreateFundableRequest
import org.rdm.online.api.net.rest.data.ErrorReply
import org.rdm.online.api.net.rest.data.FundableComponentReply
import org.rdm.online.api.net.rest.data.FundableComponentRequest
import org.rdm.online.api.net.rest.data.FundableReply
import org.rdm.online.api.net.rest.data.IdReply
import org.rdm.online.api.net.rest.data.InterestRequest
import org.rdm.online.api.net.rest.data.PaymentScheduleReply
import org.rdm.online.api.net.rest.data.PaymentScheduleRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.Route
import org.rdm.online.api.internal.funding.Fundable
import org.rdm.online.api.internal.funding.FundableComponent
import org.rdm.online.api.internal.funding.PaymentSchedule
import org.rdm.online.db.funding.FundableDB
import org.rdm.online.state.FundableDerived
import org.rdm.online.rdm.auth.AuthService
import org.rdm.online.api.net.events.ApplicationEvent
import org.rdm.online.api.net.events.EventService

fun Route.fundableRoutes(auth: AuthService, events: EventService) {
    route("/api/rest/fundable") {
        get {
            auth.requireUser(call)
            call.respond(
                FundableDB.listFundableIds()
                    .map { FundableDerived.getCachedFundable(it).toReply() }
            )
        }

        post {
            auth.requireAdmin(call)
            val request = call.receive<CreateFundableRequest>()
            val id = FundableDB.addFundable(
                userID = 0L,
                fundable = request.toInternal()
            )
            events.dispatch(ApplicationEvent.FundableAdded(id))
            call.respond(HttpStatusCode.Created, IdReply(id))
        }

        route("/{fundableId}") {
            get {
                auth.requireUser(call)
                val fundableId = call.parameters["fundableId"].asIdOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid fundable id")
                    )
                call.respondSnapshot(fundableId)
            }

            //I don't know if this endpoint should exist at all
            delete {
                val userID = auth.requireUser(call)
                val fundableId = call.parameters["fundableId"].asIdOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid fundable id")
                    )
                FundableDB.removeFundable(userID, fundableId)
                events.dispatch(ApplicationEvent.FundableRemoved(fundableId))
                call.respond(HttpStatusCode.NoContent)
            }

            post("/components") {
                val userID = auth.requireUser(call)
                val fundableId = call.parameters["fundableId"].asIdOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid fundable id")
                    )
                val request = call.receive<FundableComponentRequest>()
                val componentId = FundableDB.addFundableComponent(userID, fundableId, request.toInternal(fundableId))
                events.dispatch(ApplicationEvent.FundableComponentChanged(fundableId, componentId))
                call.respond(HttpStatusCode.Created, FundableDerived.getCachedFundable(fundableId).toReply())
            }
        }

        route("/components/{componentId}") {
            get {
                auth.requireUser(call)
                val componentId = call.parameters["componentId"].asIdOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid component id")
                    )
                call.respond(FundableDerived.getCachedFundableComponent(componentId).toReply())
            }

            delete {
                val userID = auth.requireUser(call)
                val componentId = call.parameters["componentId"].asIdOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid component id")
                    )
                val fundableId = FundableDerived.getCachedFundableComponent(componentId).fundableId
                FundableDB.removeFundableComponent(userID, componentId)
                events.dispatch(ApplicationEvent.FundableComponentChanged(fundableId, componentId))
                call.respond(HttpStatusCode.NoContent)
            }

            post("/commitments") {
                val userId = auth.requireUser(call)
                val componentId = call.parameters["componentId"].asIdOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorReply("Invalid component id"))
                val fundableId = FundableDerived.getCachedFundableComponent(componentId).fundableId
                val request = call.receive<CommitmentRequest>()
                val commitmentId = FundableDB.commitToComponent(
                    userId,
                    componentId,
                    request.amountCents,
                    request.paymentSchedule.toInternal()
                )
                events.dispatch(ApplicationEvent.CommitmentChanged(fundableId, componentId, commitmentId))
                call.respond(HttpStatusCode.Created, IdReply(commitmentId))
            }

            post("/interests") {
                val userId = auth.requireUser(call)
                val componentId = call.parameters["componentId"].asIdOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid component id")
                    )
                val fundableId = FundableDerived.getCachedFundableComponent(componentId).fundableId
                FundableDB.addInterest(userId, componentId)
                events.dispatch(ApplicationEvent.InterestChanged(fundableId, componentId))
                call.respond(FundableDerived.getCachedFundableComponent(componentId).toReply())
            }

            delete("/interests/{userId}") {
                val authenticatedUserId = auth.requireUser(call)
                val componentId = call.parameters["componentId"].asIdOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid component id")
                    )
                val userId = call.parameters["userId"].asIdOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest,
                        ErrorReply("Invalid user id")
                    )
                if (userId != authenticatedUserId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, ErrorReply("Cannot remove another user's interest"))
                }
                val fundableId = FundableDerived.getCachedFundableComponent(componentId).fundableId
                FundableDB.removeInterest(userId, componentId)
                events.dispatch(ApplicationEvent.InterestChanged(fundableId, componentId))
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("/commitments/{commitmentId}/withdraw") {
            auth.requireUser(call)
            val commitmentId = call.parameters["commitmentId"].asIdOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest,
                    ErrorReply("Invalid commitment id")
                )
            val componentId = FundableDB.commitmentComponentId(commitmentId)
            FundableDB.withdrawCommitment(commitmentId)
            events.dispatch(
                ApplicationEvent.CommitmentChanged(
                    FundableDerived.getCachedFundableComponent(componentId).fundableId,
                    componentId,
                    commitmentId
                )
            )
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun ApplicationCall.respondSnapshot(id: Long) {
    try {
        respond(FundableDerived.getCachedFundable(id).toReply())
    } catch (_: NoSuchElementException) {
        respond(HttpStatusCode.NotFound,
            ErrorReply("Fundable not found")
        )
    }
}

private fun String?.asIdOrNull(): Long? = this?.toLongOrNull()?.takeIf { it > 0 }

private fun CreateFundableRequest.toInternal() = Fundable(
    id = null,
    name = name,
    title = title,
    description = description,
    components = components.map { it.toInternal(0L) }
)

private fun FundableComponentRequest.toInternal(fundableId: Long) = FundableComponent(
    id = null,
    fundableID = fundableId,
    name = name,
    description = description,
    amountCent = amountCent,
    paymentSchedule = paymentSchedule.toInternal()
)

private fun PaymentScheduleRequest.toInternal(): PaymentSchedule = when (this) {
    PaymentScheduleRequest.OneTime -> PaymentSchedule.OneTime
    is PaymentScheduleRequest.Recurring -> PaymentSchedule.Recurring(intervalMonths, occurrences)
}

private fun PaymentSchedule.toReply() = when (this) {
    PaymentSchedule.OneTime -> PaymentScheduleReply("one_time")
    is PaymentSchedule.Recurring -> PaymentScheduleReply(
        "recurring",
        intervalMonths,
        occurrences
    )
}

private fun FundableDB.ComponentSnapshot.toReply() =
    FundableComponentReply(
        id, fundableId, name, description, amountCent, paymentSchedule.toReply(),
        interestedCount, committedCount, totalCommittedAmount, fullyFunded, amountCommitted,
        lifetimeCost, equalDivisionOnRemaining, projectedOverFundReturnPP
    )

private fun FundableDB.FundableSnapshot.toReply() = FundableReply(
    id, name, title, description, components.map { it.toReply() }, fullyFunded
)

private fun FundableDerived.CachedFundableComponent.toReply() =
    FundableComponentReply(
        id, fundableId, name, description, amountCent, paymentSchedule.toReply(),
        interestedCount, committedCount, totalCommittedAmount, fullyFunded, amountCommitted,
        lifetimeCost, equalDivisionOnRemaining, projectedOverFundReturnPP
    )

private fun FundableDerived.CachedFundable.toReply() = FundableReply(
    id, name, title, description, components.map { it.toReply() }, fullyFunded
)
