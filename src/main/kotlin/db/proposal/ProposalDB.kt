package org.rdm.online.db.proposal

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.rdm.online.db.user.UserDB.Users

object ProposalDB {
    /**
     * Immutable proposal facts, append-only lifecycle events, and SQL projections.
     * Repeals and resubmissions create records; they never rewrite proposal text.
     */

    enum class ProposalMajority {
        SIMPLE,
        SUPER,
        EMERGENCY,
    }

    /*
    Fully Immutable
     */
    object Proposals : Table("proposals") {
        val id = long("id").autoIncrement()
        val title = varchar("title", 255)
        val description = text("description")
        val createdAt = datetime("created_at")
        val authorId = long("author_id").references(Users.id)
        val originProposalId = long("origin_proposal_id").nullable()
        val contentHash = varchar("content_hash", 128)
        val legalArea = varchar("legal_area", 100)
        val emergency = bool("emergency").default(false)

        override val primaryKey = PrimaryKey(id)
    }

    enum class ProposalEventType {
        CREATED,
        UPDATED,
        SUBMITTED,
        APPROVED,
        APPROVED_EMERGENCY,
        INCONCLUSION,
        SUBMITTED_NO_ABSTAIN,
        REJECTED,
    }
    //SUBMITTED ->

    // UPDATED -> APPROVED | APPROVED_EMERGENCY | INCONCLUSION | SUBMITTED_NO_ABSTAIN | REJECTED
    // APPROVED_EMERGENCY -> (next time) SUBMITTED -> UPDATED



    object ProposalEvents : Table("proposal_events") {
        val id = long("id").autoIncrement()
        val proposalId = long("proposal_id").references(Proposals.id)
        val eventType = varchar("event_type", 50)
        val eventData = text("event_data")
        val createdAt = datetime("created_at")
        val actorId = long("actor_id").references(Users.id).nullable()
        val sequenceNumber = integer("sequence_number")
        val sessionId = long("session_id").references(ProposalSessions.id).nullable()

        override val primaryKey = PrimaryKey(id)
        init {
            uniqueIndex(proposalId, sequenceNumber)
        }
    }

    object ProposalSessions : Table("proposal_sessions") {
        val id = long("id").autoIncrement()
        val regularSessionId = long("regular_session_id").nullable()
        val noAbstain = bool("no_abstain").default(false)
        val minimumVotes = integer("minimum_votes")
        val startedAt = datetime("started_at")
        val endedAt = datetime("ended_at").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object ProposalVotes : Table("proposal_votes") {
        val id = long("id").autoIncrement()
        val proposalId = long("proposal_id").references(Proposals.id)
        val sessionId = long("session_id").references(ProposalSessions.id)
        val voterId = long("voter_id").references(Users.id)
        val vote = varchar("vote", 20)
        val createdAt = datetime("created_at")
        override val primaryKey = PrimaryKey(id)
        init { uniqueIndex(proposalId, sessionId, voterId) }
    }

    object CurrentState : Table("proposal_current_state") {
        val proposalId = long("proposal_id").references(Proposals.id)
        val state = varchar("state", 40)
        val effective = bool("effective").default(false).index()
        val latestEventId = long("latest_event_id").references(ProposalEvents.id)
        val updatedAt = datetime("updated_at")
        override val primaryKey = PrimaryKey(proposalId)
    }

    object VetoPermissions : Table("proposal_veto_permissions") {
        val id = long("id").autoIncrement()
        val userId = long("user_id").references(Users.id)
        val legalArea = varchar("legal_area", 100)
        val roleName = varchar("role_name", 100).nullable()
        val active = bool("active").default(true)
        val grantedByProposalId = long("granted_by_proposal_id").references(Proposals.id).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object VetoEvents : Table("proposal_veto_events") {
        val id = long("id").autoIncrement()
        val vetoPermissionId = long("veto_permission_id").references(VetoPermissions.id)
        val proposalId = long("proposal_id").references(Proposals.id)
        val actorId = long("actor_id").references(Users.id)
        val eventType = varchar("event_type", 30)
        val createdAt = datetime("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    fun nextSequence(proposalId: Long): Int =
        (ProposalEvents.select(ProposalEvents.sequenceNumber)
            .where { ProposalEvents.proposalId eq proposalId }
            .maxOfOrNull { it[ProposalEvents.sequenceNumber] } ?: 0) + 1
}