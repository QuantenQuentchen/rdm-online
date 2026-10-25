package org.rdm.online.db.perms

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.rdm.online.db.funding.FundableDB.Fundables
import org.rdm.online.db.proposal.ProposalDB
import org.rdm.online.db.user.UserDB.Users

object ScopedPermDB {


    object FundingPermLink : Table("perms_funding_link") {
        val fundableID = long("fundable_id").references(Fundables.id)
        val userID = long("user_id").references(Users.id)
        val origin = varchar("origin", 255) //this is currently a string, it will not remain a string

        override val primaryKey = PrimaryKey(fundableID, userID)
    }


    object PermissionOrigins : Table("perms_origins") {
        val id = long("id").autoIncrement()
        val proposalID = long("proposal_id").references(ProposalDB.Proposals.id)

        val roleActionID = long("role_action_id").references(ProposalDB.ProposalEvents.id)

    }

    //Transaction caller handled
    fun canEdit(userID: Long, fundableID: Long): Boolean {
        return FundingPermLink
            .select(FundingPermLink.fundableID)
            .where { (FundingPermLink.fundableID eq fundableID) and (FundingPermLink.userID eq userID) }
            .firstOrNull() != null
    }

    //Transaction caller handled
    fun addPermission(userID: Long, fundableID: Long, origin: String) {
        FundingPermLink.insert {
            it[FundingPermLink.fundableID] = fundableID
            it[FundingPermLink.userID] = userID
            it[FundingPermLink.origin] = origin
        }
    }

    //Transaction caller handled
    fun revokePermission(userID: Long, fundableID: Long) {
        FundingPermLink.deleteWhere { (FundingPermLink.fundableID eq fundableID) and (FundingPermLink.userID eq userID) }
    }

}