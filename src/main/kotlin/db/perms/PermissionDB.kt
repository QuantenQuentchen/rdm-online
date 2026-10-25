package org.rdm.online.db.perms

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import org.rdm.online.db.proposal.ProposalDB
import org.rdm.online.db.user.UserDB.Users
import org.rdm.online.db.funding.FundableDB

object PermissionDB {
    /**
     * Append-only permission grants and their validity projection.
     * Invalidation is represented by events rather than destructive deletes.
     */
    object Grants : Table("permission_grants") {
        val id = long("id").autoIncrement()
        val permissionKey = varchar("permission_key", 150)
        val subjectUserId = long("subject_user_id").references(Users.id)
        val resourceType = varchar("resource_type", 50)
        val resourceId = long("resource_id")
        val grantType = varchar("grant_type", 20)
        val proposalId = long("proposal_id").references(ProposalDB.Proposals.id).nullable()
        val grantingUserId = long("granting_user_id").references(Users.id).nullable()
        val grantingRole = varchar("granting_role", 100).nullable()
        val parentGrantId = long("parent_grant_id").references(id).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Events : Table("permission_events") {
        val id = long("id").autoIncrement()
        val grantId = long("grant_id").references(Grants.id)
        val eventType = varchar("event_type", 30)
        val causeType = varchar("cause_type", 30)
        val causeId = long("cause_id").nullable()
        val actorId = long("actor_id").references(Users.id).nullable()
        val createdAt = datetime("created_at")
        override val primaryKey = PrimaryKey(id)
    }

    object CurrentState : Table("permission_current_state") {
        val grantId = long("grant_id").references(Grants.id)
        val valid = bool("valid").default(true).index()
        val invalidReason = text("invalid_reason").nullable()
        val latestEventId = long("latest_event_id").references(Events.id)
        override val primaryKey = PrimaryKey(grantId)
    }

    object Dependencies : Table("permission_dependencies") {
        val ancestorGrantId = long("ancestor_grant_id").references(Grants.id)
        val descendantGrantId = long("descendant_grant_id").references(Grants.id)
        val depth = integer("depth")
        override val primaryKey = PrimaryKey(ancestorGrantId, descendantGrantId)
    }

    object RoleAssignments : Table("role_assignments") {
        val id = long("id").autoIncrement()
        val userId = long("user_id").references(Users.id)
        val roleName = varchar("role_name", 100)
        val active = bool("active").default(true)
        val grantedByProposalId = long("granted_by_proposal_id").references(ProposalDB.Proposals.id).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object RoleRemovalConditions : Table("role_removal_conditions") {
        val id = long("id").autoIncrement()
        val roleName = varchar("role_name", 100)
        val userId = long("user_id").references(Users.id)
        val proposalId = long("proposal_id").references(ProposalDB.Proposals.id)
        val active = bool("active").default(true)
        override val primaryKey = PrimaryKey(id)
    }
}
