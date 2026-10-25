package org.rdm.online.logic.service

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.rdm.online.db.perms.PermissionDB
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class PermissionService {
    /** Creates an immutable permission whose validity depends on an approved proposal. */
    fun grantFromProposal(
        subjectUserId: Long,
        permissionKey: String,
        resourceType: String,
        resourceId: Long,
        proposalId: Long,
        actorId: Long,
    ): Long = transaction {
        val grantId = PermissionDB.Grants.insert {
            it[PermissionDB.Grants.permissionKey] = permissionKey
            it[PermissionDB.Grants.subjectUserId] = subjectUserId
            it[PermissionDB.Grants.resourceType] = resourceType
            it[PermissionDB.Grants.resourceId] = resourceId
            it[PermissionDB.Grants.grantType] = "PROPOSAL"
            it[PermissionDB.Grants.proposalId] = proposalId
        } get PermissionDB.Grants.id
        appendGrantEvent(grantId, "GRANTED", "PROPOSAL", proposalId, actorId)
        grantId
    }

    /** Creates an immutable permission whose validity depends on a role grantor. */
    fun grantFromRole(
        subjectUserId: Long,
        permissionKey: String,
        resourceType: String,
        resourceId: Long,
        grantingUserId: Long,
        roleName: String,
        actorId: Long,
    ): Long = transaction {
        val grantId = PermissionDB.Grants.insert {
            it[PermissionDB.Grants.permissionKey] = permissionKey
            it[PermissionDB.Grants.subjectUserId] = subjectUserId
            it[PermissionDB.Grants.resourceType] = resourceType
            it[PermissionDB.Grants.resourceId] = resourceId
            it[PermissionDB.Grants.grantType] = "ROLE"
            it[PermissionDB.Grants.grantingUserId] = grantingUserId
            it[PermissionDB.Grants.grantingRole] = roleName
        } get PermissionDB.Grants.id
        appendGrantEvent(grantId, "GRANTED", "ROLE", grantingUserId, actorId)
        grantId
    }

    /** Performs an indexed SQL-first validity check for an authorization decision. */
    fun isGranted(subjectUserId: Long, permissionKey: String, resourceType: String, resourceId: Long): Boolean =
        transaction {
            (PermissionDB.Grants innerJoin PermissionDB.CurrentState)
                .select(PermissionDB.Grants.id)
                .where {
                    (PermissionDB.Grants.subjectUserId eq subjectUserId) and
                        (PermissionDB.Grants.permissionKey eq permissionKey) and
                        (PermissionDB.Grants.resourceType eq resourceType) and
                        (PermissionDB.Grants.resourceId eq resourceId) and
                        (PermissionDB.CurrentState.valid eq true)
                }.any()
        }

    /** Invalidates proposal-origin grants and recursively invalidates descendants. */
    fun invalidateForProposal(proposalId: Long, reason: String = "proposal repealed") {
        transaction {
            PermissionDB.Grants.select(PermissionDB.Grants.id)
                .where { PermissionDB.Grants.proposalId eq proposalId }
                .forEach { invalidate(it[PermissionDB.Grants.id], "PROPOSAL", proposalId, reason) }
        }
    }

    /** Invalidates grants made under a role after that role is removed. */
    fun invalidateForRole(userId: Long, roleName: String, reason: String = "granting role removed") {
        transaction {
            PermissionDB.Grants.select(PermissionDB.Grants.id)
                .where {
                    (PermissionDB.Grants.grantingUserId eq userId) and
                        (PermissionDB.Grants.grantingRole eq roleName)
                }
                .forEach { invalidate(it[PermissionDB.Grants.id], "ROLE", userId, reason) }
        }
    }

    /** Associates a proposal with a possible no-confidence role removal. */
    fun addRoleRemovalCondition(userId: Long, roleName: String, proposalId: Long): Long =
        transaction {
            PermissionDB.RoleRemovalConditions.insert {
                it[PermissionDB.RoleRemovalConditions.userId] = userId
                it[PermissionDB.RoleRemovalConditions.roleName] = roleName
                it[PermissionDB.RoleRemovalConditions.proposalId] = proposalId
            } get PermissionDB.RoleRemovalConditions.id
        }

    /** Applies a proposal-driven role removal and cascades permission invalidation. */
    fun removeRoleForProposal(userId: Long, roleName: String, proposalId: Long) {
        transaction {
            PermissionDB.RoleAssignments.update({
                (PermissionDB.RoleAssignments.userId eq userId) and
                    (PermissionDB.RoleAssignments.roleName eq roleName)
            }) {
                it[PermissionDB.RoleAssignments.active] = false
            }
            PermissionDB.RoleRemovalConditions.update({
                (PermissionDB.RoleRemovalConditions.userId eq userId) and
                    (PermissionDB.RoleRemovalConditions.roleName eq roleName) and
                    (PermissionDB.RoleRemovalConditions.proposalId eq proposalId)
            }) {
                it[PermissionDB.RoleRemovalConditions.active] = false
            }
        }
        invalidateForRole(userId, roleName)
    }

    private fun invalidate(grantId: Long, causeType: String, causeId: Long, reason: String) {
        val eventId = appendGrantEvent(grantId, "INVALIDATED", causeType, causeId, null)
        PermissionDB.CurrentState.update({ PermissionDB.CurrentState.grantId eq grantId }) {
            it[valid] = false
            it[invalidReason] = reason
            it[latestEventId] = eventId
        }
        PermissionDB.Dependencies
            .select(PermissionDB.Dependencies.descendantGrantId)
            .where { PermissionDB.Dependencies.ancestorGrantId eq grantId }
            .map { it[PermissionDB.Dependencies.descendantGrantId] }
            .distinct()
            .forEach { descendant ->
                val alreadyInvalid = PermissionDB.CurrentState
                    .select(PermissionDB.CurrentState.valid)
                    .where { PermissionDB.CurrentState.grantId eq descendant }
                    .firstOrNull()
                    ?.get(PermissionDB.CurrentState.valid) == false
                if (!alreadyInvalid) invalidate(descendant, causeType, causeId, reason)
            }
    }

    private fun appendGrantEvent(
        grantId: Long,
        type: String,
        causeType: String,
        causeId: Long?,
        actorId: Long?,
    ): Long {
        val eventId = PermissionDB.Events.insert {
            it[PermissionDB.Events.grantId] = grantId
            it[eventType] = type
            it[PermissionDB.Events.causeType] = causeType
            it[PermissionDB.Events.causeId] = causeId
            it[PermissionDB.Events.actorId] = actorId
            it[PermissionDB.Events.createdAt] = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        } get PermissionDB.Events.id
        val exists = PermissionDB.CurrentState.selectAll()
            .where { PermissionDB.CurrentState.grantId eq grantId }
            .any()
        if (!exists) PermissionDB.CurrentState.insert {
            it[PermissionDB.CurrentState.grantId] = grantId
            it[PermissionDB.CurrentState.valid] = type != "INVALIDATED"
            it[PermissionDB.CurrentState.latestEventId] = eventId
        }
        return eventId
    }
}
