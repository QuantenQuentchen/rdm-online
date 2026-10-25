package org.rdm.online.logic.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.rdm.online.db.proposal.ProposalDB
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class ProposalVote { SUPPORT, OPPOSE, ABSTAIN }
enum class ProposalState { DRAFT, SUBMITTED, APPROVED, APPROVED_EMERGENCY, INCONCLUSIVE, RE_ELECTION, REJECTED, REPEALED }

@Serializable
data class ProposalPayload(
    val title: String,
    val description: String,
    val legalArea: String,
    val contentHash: String,
    val emergency: Boolean = false,
)

class ProposalService(
    private val outbox: OutboxService = OutboxService(),
    private val permissionService: PermissionService = PermissionService(),
    private val json: Json = Json,
) {
    /** Creates an immutable proposal and records its initial event. */
    fun createProposal(authorId: Long, payload: ProposalPayload, originProposalId: Long? = null): Long =
        transaction {
            val now = now()
            val proposalId = ProposalDB.Proposals.insert {
                it[ProposalDB.Proposals.title] = payload.title
                it[ProposalDB.Proposals.description] = payload.description
                it[ProposalDB.Proposals.createdAt] = now
                it[ProposalDB.Proposals.authorId] = authorId
                it[ProposalDB.Proposals.originProposalId] = originProposalId
                it[contentHash] = payload.contentHash
                it[legalArea] = payload.legalArea
                it[emergency] = payload.emergency
            } get ProposalDB.Proposals.id
            appendEvent(proposalId, "CREATED", authorId, null, JsonObject(emptyMap()))
            proposalId
        }

    /**
     * Creates a new proposal identity with the same content and links it to the
     * original proposal. The supplied session keeps a no-change resubmission in
     * the same conceptual council session.
     */
    fun resubmitWithoutChanges(
        proposalId: Long,
        authorId: Long,
        sessionId: Long,
    ): Long = transaction {
        val source = ProposalDB.Proposals.selectAll()
            .where { ProposalDB.Proposals.id eq proposalId }
            .single()
        val copyId = ProposalDB.Proposals.insert {
            it[ProposalDB.Proposals.title] = source[ProposalDB.Proposals.title]
            it[ProposalDB.Proposals.description] = source[ProposalDB.Proposals.description]
            it[ProposalDB.Proposals.createdAt] = now()
            it[ProposalDB.Proposals.authorId] = authorId
            it[ProposalDB.Proposals.originProposalId] = source[ProposalDB.Proposals.originProposalId] ?: proposalId
            it[ProposalDB.Proposals.contentHash] = source[ProposalDB.Proposals.contentHash]
            it[ProposalDB.Proposals.legalArea] = source[ProposalDB.Proposals.legalArea]
            it[ProposalDB.Proposals.emergency] = source[ProposalDB.Proposals.emergency]
        } get ProposalDB.Proposals.id
        appendEvent(copyId, "RESUBMITTED", authorId, sessionId, JsonObject(emptyMap()))
        copyId
    }

    /** Opens a voting session; re-election sessions normally set [noAbstain]. */
    fun startSession(
        regularSessionId: Long? = null,
        noAbstain: Boolean = false,
        minimumVotes: Int = if (noAbstain) 3 else 1,
    ): Long = transaction {
        ProposalDB.ProposalSessions.insert {
            it[ProposalDB.ProposalSessions.regularSessionId] = regularSessionId
            it[ProposalDB.ProposalSessions.noAbstain] = noAbstain
            it[ProposalDB.ProposalSessions.minimumVotes] = minimumVotes
            it[ProposalDB.ProposalSessions.startedAt] = now()
        } get ProposalDB.ProposalSessions.id
    }

    /** Records one immutable vote and rejects abstentions in no-abstain sessions. */
    fun recordVote(proposalId: Long, sessionId: Long, voterId: Long, vote: ProposalVote) {
        transaction {
            val noAbstain = ProposalDB.ProposalSessions
                .select(ProposalDB.ProposalSessions.noAbstain)
                .where { ProposalDB.ProposalSessions.id eq sessionId }
                .single()[ProposalDB.ProposalSessions.noAbstain]
            require(!(noAbstain && vote == ProposalVote.ABSTAIN)) {
                "Abstention is not permitted in this re-election session"
            }
            ProposalDB.ProposalVotes.insert {
                it[ProposalDB.ProposalVotes.proposalId] = proposalId
                it[ProposalDB.ProposalVotes.sessionId] = sessionId
                it[ProposalDB.ProposalVotes.voterId] = voterId
                it[ProposalDB.ProposalVotes.vote] = vote.name
                it[ProposalDB.ProposalVotes.createdAt] = now()
            }
            appendEvent(proposalId, vote.name, voterId, sessionId, JsonObject(emptyMap()))
        }
    }

    /** Appends a repeal event and invalidates proposal-derived permissions. */
    fun repeal(proposalId: Long, actorId: Long) {
        transaction {
            appendEvent(proposalId, ProposalState.REPEALED.name, actorId, null, JsonObject(emptyMap()))
            permissionService.invalidateForProposal(proposalId)
        }
    }

    /** Grants a legal-area-scoped veto capability. */
    fun grantVetoPermission(
        userId: Long,
        legalArea: String,
        roleName: String? = null,
        grantedByProposalId: Long? = null,
    ): Long = transaction {
        ProposalDB.VetoPermissions.insert {
            it[ProposalDB.VetoPermissions.userId] = userId
            it[ProposalDB.VetoPermissions.legalArea] = legalArea
            it[ProposalDB.VetoPermissions.roleName] = roleName
            it[ProposalDB.VetoPermissions.grantedByProposalId] = grantedByProposalId
        } get ProposalDB.VetoPermissions.id
    }

    /**
     * Records a veto after checking legal area and self-targeting restrictions.
     * A role cannot veto changes to its own rules, and a user cannot veto their
     * own role removal.
     */
    fun veto(
        proposalId: Long,
        actorId: Long,
        vetoPermissionId: Long,
        mutatesRole: String? = null,
        removesUserId: Long? = null,
    ) {
        require(canVeto(actorId, proposalId, mutatesRole, removesUserId)) {
            "User is not authorized to veto this proposal"
        }
        transaction {
            ProposalDB.VetoEvents.insert {
                it[ProposalDB.VetoEvents.vetoPermissionId] = vetoPermissionId
                it[ProposalDB.VetoEvents.proposalId] = proposalId
                it[ProposalDB.VetoEvents.actorId] = actorId
                it[ProposalDB.VetoEvents.eventType] = "VETOED"
                it[ProposalDB.VetoEvents.createdAt] = now()
            }
            appendEvent(proposalId, "VETOED", actorId, null, JsonObject(emptyMap()))
        }
    }

    fun canVeto(actorId: Long, proposalId: Long, mutatesRole: String? = null, removesUserId: Long? = null): Boolean =
        transaction {
            val proposal = ProposalDB.Proposals.selectAll()
                .where { ProposalDB.Proposals.id eq proposalId }.single()
            if (removesUserId == actorId) return@transaction false
            ProposalDB.VetoPermissions.selectAll().where {
                (ProposalDB.VetoPermissions.userId eq actorId) and
                    (ProposalDB.VetoPermissions.legalArea eq proposal[ProposalDB.Proposals.legalArea]) and
                    (ProposalDB.VetoPermissions.active eq true)
            }.any { row ->
                val role = row[ProposalDB.VetoPermissions.roleName]
                role == null || role != mutatesRole
            }
        }

    private fun appendEvent(
        proposalId: Long,
        type: String,
        actorId: Long?,
        sessionId: Long?,
        payload: JsonObject,
    ) {
        val eventId = ProposalDB.ProposalEvents.insert {
            it[ProposalDB.ProposalEvents.proposalId] = proposalId
            it[eventType] = type
            it[eventData] = json.encodeToString(JsonObject.serializer(), payload)
            it[ProposalDB.ProposalEvents.createdAt] = now()
            it[ProposalDB.ProposalEvents.actorId] = actorId
            it[sequenceNumber] = ProposalDB.nextSequence(proposalId)
            it[ProposalDB.ProposalEvents.sessionId] = sessionId
        } get ProposalDB.ProposalEvents.id
        val effective = type == ProposalState.APPROVED.name || type == ProposalState.APPROVED_EMERGENCY.name
        val existing = ProposalDB.CurrentState.selectAll()
            .where { ProposalDB.CurrentState.proposalId eq proposalId }
            .firstOrNull()
        if (existing == null) {
            ProposalDB.CurrentState.insert {
                it[ProposalDB.CurrentState.proposalId] = proposalId
                it[ProposalDB.CurrentState.state] = type
                it[ProposalDB.CurrentState.effective] = effective
                it[ProposalDB.CurrentState.latestEventId] = eventId
                it[ProposalDB.CurrentState.updatedAt] = now()
            }
        } else {
            ProposalDB.CurrentState.update({ ProposalDB.CurrentState.proposalId eq proposalId }) {
                it[ProposalDB.CurrentState.state] = type
                it[ProposalDB.CurrentState.effective] = effective
                it[ProposalDB.CurrentState.latestEventId] = eventId
                it[ProposalDB.CurrentState.updatedAt] = now()
            }
        }
        outbox.enqueue(
            eventType = "PROPOSAL_STATE_CHANGED",
            aggregateType = "PROPOSAL",
            aggregateId = proposalId,
            payload = payload,
        )
    }

    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.UTC)
}
