package org.rdm.online.db.funding

import org.rdm.online.db.nullableLong
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.coalesce
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.div
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.rdm.online.api.internal.exceptions.ForbiddenException
import org.rdm.online.api.internal.funding.Fundable
import org.rdm.online.api.internal.funding.FundableComponent
import org.rdm.online.api.internal.funding.PaymentSchedule
import org.rdm.online.db.mustPositive
import org.rdm.online.db.mustPositiveOrNull
import org.rdm.online.db.perms.ScopedPermDB
import org.rdm.online.db.sqliteMax
import org.rdm.online.state.FundableDerived
import org.rdm.online.logic.service.FundableHistoryService
import kotlinx.serialization.json.JsonPrimitive

object FundableDB {
    private val history = FundableHistoryService()

    object Fundables : Table("fundables") {
        val id = long("id").autoIncrement().uniqueIndex()
        val name = text("name")
        val title = text("title")
        val description = text("description").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object Components : Table("fundables_component") {
        val fundableComponentId = long("fundable_component_id").autoIncrement().uniqueIndex()
        val fundableId = long("fundable_id").references(Fundables.id, onDelete = ReferenceOption.CASCADE).index()
        val name = text("name")
        val description = text("description").nullable()
        val amountCent = long("amount_cent").mustPositive()
        val paymentScheduleId = long("payment_schedule_id").references(PaymentSchedules.fundablePaymentScheduleId).index()
        override val primaryKey = PrimaryKey(fundableComponentId)
    }

    object PaymentSchedules : Table("fundables_payment_schedule") {
        val fundablePaymentScheduleId = long("fundable_payment_schedule_id").autoIncrement().uniqueIndex()

        val type = enumerationByName<PaymentScheduleType>("type", length = 20)

        val intervalMonths = long("interval_months").nullable().mustPositiveOrNull()
        val occurrences = long("occurrences").nullable().mustPositiveOrNull()

        override val primaryKey = PrimaryKey(fundablePaymentScheduleId)

        init {
            check("ck_payment_schedule_variant") {
                (
                    (type eq PaymentScheduleType.ONE_TIME) and
                    (intervalMonths.isNull()) and
                    (occurrences.isNull())
                ) or (
                    (type eq PaymentScheduleType.RECURRING) and
                    (intervalMonths.isNotNull())
                )
            }
        }

        //COMPUTED
        val isIndefinite: Op<Boolean>
            get() = (type eq PaymentScheduleType.RECURRING) and occurrences.isNull()

        val installmentCount: Expression<Long?>
            get() = Case()
                .When(type eq PaymentScheduleType.ONE_TIME, longLiteral(1L).nullableLong())
                .When(type eq PaymentScheduleType.RECURRING and occurrences.isNotNull(), occurrences)
                .Else(null)
    }

    enum class PaymentScheduleType {
        ONE_TIME,
        RECURRING
    }

    object ComponentInterests : Table("fundables_component_interest") {
        val fundableComponentId = long("fundable_component_id").references(Components.fundableComponentId, onDelete = ReferenceOption.CASCADE).index()
        val interestedUser = long("interested_user").index()

        override val primaryKey = PrimaryKey(fundableComponentId, interestedUser)
    }

    object ComponentCommitments : Table("fundables_component_comittment") {
        val fundableComponentId = long("fundable_component_id").references(Components.fundableComponentId, onDelete = ReferenceOption.CASCADE).index()
        val commitmentId = long("commitment_id").autoIncrement().uniqueIndex()
        val commitedUser = long("commited_user").index()
        val amountCommitedCents = long("amount_committed_cents")
        val paymentScheduleId = long("payment_schedule_id").references(PaymentSchedules.fundablePaymentScheduleId).index()
        val withdrawn = bool("withdrawn").default(false)

        override val primaryKey = PrimaryKey(commitmentId)

        //COMPUTED
        val active: Op<Boolean>
            get() = withdrawn eq false

        //METHODS
        fun activeOf(fundableComponentId: Long): Op<Boolean> =
            active and (this.fundableComponentId eq fundableComponentId)
    }

    object Categories : Table("fundables_categories") {
        val categoryId = long("category_id").autoIncrement().uniqueIndex()
        val name = text("name")
        val title = text("title")
        val description = text("description").nullable()

        override val primaryKey = PrimaryKey(categoryId)
    }

    object ToCategories : Table("fundables_to_categories") {
        val fundableId = long("fundable_id").references(Fundables.id, onDelete = ReferenceOption.CASCADE).index()
        val categoryId = long("category_id").references(Categories.categoryId, onDelete = ReferenceOption.CASCADE).index()

        override val primaryKey = PrimaryKey(fundableId, categoryId)
    }

    fun isComponentFunded(fundableComponentId: Long): Boolean {
        return transaction {
            val totalCommittedCt = ComponentCommitments.select(ComponentCommitments.fundableComponentId)
                .where { ComponentCommitments.activeOf(fundableComponentId) }
                .sumOf { it[ComponentCommitments.amountCommitedCents] }
            val componentCt = Components.select(Components.amountCent)
                .where { Components.fundableComponentId eq fundableComponentId }.single()
            totalCommittedCt >= componentCt[Components.amountCent]
        }
    }

    fun isFundableFullyFunded(fundableId: Long): Boolean {
        return transaction {
            val components = Components.select(Components.fundableComponentId)
                .where { Components.fundableId eq fundableId }
                .map { it[Components.fundableComponentId] }
            components.all { isComponentFunded(it) }
        }
    }

    //Derived state, technically can be eventually consistent, but for now we will keep it in sync with the rest of the database
    /*
    Does not account for comittments
     */
    fun getEqualDivisionOnInterest(fundableComponentId: Long): Long {
        return transaction {
            val totalInterested = ComponentInterests.select(ComponentInterests.fundableComponentId)
                                .where{ ComponentInterests.fundableComponentId eq fundableComponentId }.count()
            val component = Components.selectAll()
                            .where { Components.fundableComponentId eq fundableComponentId }.single()
            val amountCent = component[Components.amountCent]
            if (totalInterested == 0L) return@transaction amountCent
            amountCent / totalInterested
        }
    }

    fun getEqualDivisionOnRemaining(fundableComponentId: Long): Long {
        return transaction {
            val totalInterested = ComponentInterests.select(ComponentInterests.fundableComponentId)
                                .where{ ComponentInterests.fundableComponentId eq fundableComponentId }.count()
            val totalCommittedCt = ComponentCommitments.select(ComponentCommitments.fundableComponentId)
                                .where{ (ComponentCommitments.fundableComponentId eq fundableComponentId) and (ComponentCommitments.withdrawn eq false) }
                                .sumOf { it[ComponentCommitments.amountCommitedCents] }
            val componentCt = Components.select(Components.amountCent)
                            .where { Components.fundableComponentId eq fundableComponentId }.single()
            val remainingCt = componentCt[Components.amountCent] - totalCommittedCt
            if (remainingCt <= 0) return@transaction 0L
            if (totalInterested == 0L) return@transaction remainingCt
            remainingCt / totalInterested
        }
    }

    fun getFundableComponentAmount(fundableComponentId: Long): Long {
        return transaction {
            val componentCt = Components.select(Components.amountCent)
                .where { Components.fundableComponentId eq fundableComponentId }.single()
            componentCt[Components.amountCent]
        }
    }

    fun getAmountCommitted(fundableComponentId: Long): Long {
        return transaction {
            val totalCommittedCt = ComponentCommitments.select(ComponentCommitments.fundableComponentId)
                .where { ComponentCommitments.activeOf(fundableComponentId) }
                .sumOf { it[ComponentCommitments.amountCommitedCents] }
            totalCommittedCt
        }
    }

    fun getProjectedOverFundReturnPerPerson(fundableComponentId: Long): Long {
        val activeCount = transaction {
            ComponentCommitments.select(ComponentCommitments.commitmentId)
                .where { ComponentCommitments.activeOf(fundableComponentId) }
                .count()
        }
        if (activeCount == 0L) return 0L
        val comCNT = ComponentCommitments.fundableComponentId.count()
        val committed = coalesce(
            ComponentCommitments.amountCommitedCents.sum(),
            longLiteral(0)
        )
        val target = transaction {
            Components
                .select(Components.amountCent)
                .where { Components.fundableComponentId eq fundableComponentId }
                .single()[Components.amountCent]
        }

        val clamped = sqliteMax(longLiteral(0), committed - longLiteral(target))

        val perCommitment = Case()
            .When(comCNT eq 0L, longLiteral(0))
            .Else(clamped / comCNT)
        return ComponentCommitments
            .select(perCommitment)
            .where {
                (ComponentCommitments.fundableComponentId eq fundableComponentId) and
                        (ComponentCommitments.withdrawn eq false)
            }
            .single()[perCommitment]
    }

    data class ComponentSnapshot(
        val id: Long,
        val fundableId: Long,
        val name: String,
        val description: String?,
        val amountCent: Long,
        val paymentSchedule: PaymentSchedule,
        val interestedCount: Int,
        val committedCount: Int,
        val totalCommittedAmount: Long,
        val fullyFunded: Boolean,
        val amountCommitted: Long,
        val lifetimeCost: Long,
        val equalDivisionOnRemaining: Long,
        val projectedOverFundReturnPP: Long?
    )

    data class FundableSnapshot(
        val id: Long,
        val name: String,
        val title: String,
        val description: String?,
        val components: List<ComponentSnapshot>,
        val fullyFunded: Boolean
    )

    fun getComponentSnapshot(fundableComponentId: Long): ComponentSnapshot = transaction {
        val row = (Components innerJoin PaymentSchedules)
            .selectAll()
            .where { Components.fundableComponentId eq fundableComponentId }
            .single()
        val amount = row[Components.amountCent]
        val schedule = toPaymentSchedule(row)
        val interested = ComponentInterests.select(ComponentInterests.fundableComponentId)
            .where { ComponentInterests.fundableComponentId eq fundableComponentId }.count().toInt()
        val activeCommitments = ComponentCommitments.select(ComponentCommitments.fundableComponentId)
            .where { ComponentCommitments.activeOf(fundableComponentId) }.toList()
        val committed = activeCommitments.sumOf { it[ComponentCommitments.amountCommitedCents] }
        val overFund = if (activeCommitments.isEmpty()) 0L
        else maxOf(0L, committed - amount) / activeCommitments.size
        ComponentSnapshot(
            id = row[Components.fundableComponentId],
            fundableId = row[Components.fundableId],
            name = row[Components.name],
            description = row[Components.description],
            amountCent = amount,
            paymentSchedule = schedule,
            interestedCount = interested,
            committedCount = activeCommitments.size,
            totalCommittedAmount = committed,
            fullyFunded = committed >= amount,
            amountCommitted = committed,
            lifetimeCost = lifetimeCost(amount, schedule),
            equalDivisionOnRemaining = equalDivisionOnRemainingInTransaction(fundableComponentId, amount, committed, interested),
            projectedOverFundReturnPP = overFund.takeIf { it > 0L }
        )
    }

    fun getFundableSnapshot(fundableId: Long): FundableSnapshot = transaction {
        val fundable = Fundables.selectAll().where { Fundables.id eq fundableId }.single()
        val components = Components.select(Components.fundableComponentId)
            .where { Components.fundableId eq fundableId }
            .map { it[Components.fundableComponentId] }
            .map { getComponentSnapshot(it) }
        FundableSnapshot(
            id = fundable[Fundables.id],
            name = fundable[Fundables.name],
            title = fundable[Fundables.title],
            description = fundable[Fundables.description],
            components = components,
            fullyFunded = components.all { it.fullyFunded }
        )
    }

    fun listFundableSnapshots(): List<FundableSnapshot> = transaction {
        Fundables.select(Fundables.id)
            .map { it[Fundables.id] }
            .map(::getFundableSnapshot)
    }

    fun listFundableIds(): List<Long> = transaction {
        Fundables.select(Fundables.id).map { it[Fundables.id] }
    }

    private fun equalDivisionOnRemainingInTransaction(
        fundableComponentId: Long,
        amount: Long,
        committed: Long,
        interested: Int
    ): Long {
        val remaining = (amount - committed).coerceAtLeast(0L)
        return if (interested == 0) remaining else remaining / interested
    }

    private fun lifetimeCost(amount: Long, schedule: PaymentSchedule): Long = when (schedule) {
        PaymentSchedule.OneTime -> amount
        is PaymentSchedule.Recurring -> schedule.occurrences?.let {
            if (it > Long.MAX_VALUE / amount) Long.MAX_VALUE else amount * it
        } ?: Long.MAX_VALUE
    }

    //CRUD operations
    fun addFundable(userID: Long, fundable: Fundable): Long {
        if (fundable.id != null) throw IllegalArgumentException("Fundable already has an ID, cannot add to database")
        val fundableId = transaction {
            val fundableID = Fundables.insert {
                it[name] = fundable.name
                it[title] = fundable.title
                it[description] = fundable.description
            } get Fundables.id
            if (!ScopedPermDB.canEdit(userID, fundableID)) throw ForbiddenException("User does not have permission to edit this fundable")

            if (fundable.components == null) return@transaction fundableID
            for (comp in fundable.components) addFundableComponentInTransaction(userID, fundableID, comp)

            fundableID
        }
        FundableDerived.refreshFundable(fundableId)
        history.record(fundableId, userID, "CREATED", JsonPrimitive("fundable created"))
        return fundableId
    }

    fun removeFundable(userID: Long, fundableId: Long) {
        if (!ScopedPermDB.canEdit(userID, fundableId)) throw ForbiddenException("User does not have permission to edit this fundable")
        transaction {
            val componentIds = Components.select(Components.fundableComponentId)
                .where { Components.fundableId eq fundableId }
                .map { it[Components.fundableComponentId] }
            val commitmentSchedules = ComponentCommitments.select(ComponentCommitments.paymentScheduleId)
                .where { ComponentCommitments.fundableComponentId inList componentIds }
                .map { it[ComponentCommitments.paymentScheduleId] }
            val componentSchedules = Components.select(Components.paymentScheduleId)
                .where { Components.fundableId eq fundableId }
                .map { it[Components.paymentScheduleId] }
            ComponentCommitments.deleteWhere { ComponentCommitments.fundableComponentId inList componentIds }
            Components.deleteWhere { Components.fundableId eq fundableId }
            Fundables.deleteWhere { Fundables.id eq fundableId }
            PaymentSchedules.deleteWhere {
                PaymentSchedules.fundablePaymentScheduleId inList (commitmentSchedules + componentSchedules).distinct()
            }
        }
        FundableDerived.invalidateFundable(fundableId)
        history.record(fundableId, userID, "DELETED", JsonPrimitive("fundable deleted"))
    }

    fun addFundableComponent(userID: Long, fundableId: Long, component: FundableComponent): Long {
        if (!ScopedPermDB.canEdit(userID, fundableId)) throw ForbiddenException("User does not have permission to edit this fundable")
        if (component.id != null) throw IllegalArgumentException("Fundable component already has an ID, cannot add to database")
        val componentId = transaction {
            addFundableComponentInTransaction(userID, fundableId, component)
        }
        FundableDerived.refreshFundable(fundableId)
        history.record(fundableId, userID, "COMPONENT_ADDED", JsonPrimitive(componentId))
        return componentId
    }

    private fun addFundableComponentInTransaction(userID: Long, fundableId: Long, component: FundableComponent): Long {
        val paymentScheduleId = addPaymentSchedule(component.paymentSchedule)
        if (!ScopedPermDB.canEdit(userID, fundableId)) throw ForbiddenException("User does not have permission to edit this fundable")
        return Components.insert {
            it[Components.fundableId] = fundableId
            it[Components.name] = component.name
            it[Components.description] = component.description
            it[Components.amountCent] = component.amountCent
            it[Components.paymentScheduleId] = paymentScheduleId
        } get Components.fundableComponentId
    }

    fun removeFundableComponent(userID: Long, fundableComponentId: Long) {
        val fundableId = transaction {
            val component = Components.select(Components.fundableId, Components.paymentScheduleId)
                .where { Components.fundableComponentId eq fundableComponentId }.single()
            val fundableId = component[Components.fundableId]
            if (!ScopedPermDB.canEdit(userID, fundableId)) throw ForbiddenException("User does not have permission to edit this fundable")
            val schedules = ComponentCommitments.select(ComponentCommitments.paymentScheduleId)
                .where { ComponentCommitments.fundableComponentId eq fundableComponentId }
                .map { it[ComponentCommitments.paymentScheduleId] }
            val componentSchedule = component[Components.paymentScheduleId]
            ComponentCommitments.deleteWhere { ComponentCommitments.fundableComponentId eq fundableComponentId }
            Components.deleteWhere { Components.fundableComponentId eq fundableComponentId }
            PaymentSchedules.deleteWhere {
                PaymentSchedules.fundablePaymentScheduleId inList (schedules + componentSchedule).distinct()
            }
            component[Components.fundableId]
        }
        FundableDerived.refreshFundable(fundableId)
        history.record(fundableId, userID, "COMPONENT_REMOVED", JsonPrimitive(fundableComponentId))
    }

/*
* User committments and interests
*/
    fun withdrawCommitment(userID: Long, fundableComponentId: Long) {
        transaction {
            ComponentCommitments.update(
            {
                (ComponentCommitments.fundableComponentId eq fundableComponentId) and
                (ComponentCommitments.commitedUser eq userID)
            }) { it[withdrawn] = true }
        }
        FundableDerived.refreshFundableComponent(fundableComponentId)
    }

    fun withdrawCommitment(commitmentId: Long) {
        val componentId = transaction {
            val row = ComponentCommitments.select(ComponentCommitments.fundableComponentId)
                .where { ComponentCommitments.commitmentId eq commitmentId }.single()
            ComponentCommitments.update({ ComponentCommitments.commitmentId eq commitmentId }) {
                it[withdrawn] = true
            }
            row[ComponentCommitments.fundableComponentId]
        }
        FundableDerived.refreshFundableComponent(componentId)
    }

    fun commitmentComponentId(commitmentId: Long): Long = transaction {
        ComponentCommitments.select(ComponentCommitments.fundableComponentId)
            .where { ComponentCommitments.commitmentId eq commitmentId }
            .single()[ComponentCommitments.fundableComponentId]
    }

    fun commitToComponent(userID: Long, fundableComponentId: Long, amountCents: Long, paymentSchedule: PaymentSchedule): Long {
        val commitmentId = transaction {
            val paymentScheduleId = addPaymentSchedule(paymentSchedule)
            ComponentCommitments.insert {
                it[ComponentCommitments.fundableComponentId] = fundableComponentId
                it[ComponentCommitments.commitedUser] = userID
                it[ComponentCommitments.amountCommitedCents] = amountCents
                it[ComponentCommitments.paymentScheduleId] = paymentScheduleId
                it[ComponentCommitments.withdrawn] = false
            } get ComponentCommitments.commitmentId
        }
        FundableDerived.refreshFundableComponent(fundableComponentId)
        return commitmentId
    }

    fun addInterest(userID: Long, fundableComponentId: Long) {
        transaction {
            ComponentInterests.insert {
                it[ComponentInterests.fundableComponentId] = fundableComponentId
                it[ComponentInterests.interestedUser] = userID
            }
        }
        FundableDerived.refreshFundableComponent(fundableComponentId)
    }

    fun removeInterest(userID: Long, fundableComponentId: Long) {
        transaction {
            ComponentInterests.deleteWhere {
                (ComponentInterests.fundableComponentId eq fundableComponentId) and
                (ComponentInterests.interestedUser eq userID)
            }
        }
        FundableDerived.refreshFundableComponent(fundableComponentId)
    }

    private fun addPaymentSchedule(paymentSchedule: PaymentSchedule): Long {
        return transaction {
            PaymentSchedules.insert {
                when (paymentSchedule) {
                    is PaymentSchedule.OneTime -> {
                        it[type] = PaymentScheduleType.ONE_TIME
                        it[intervalMonths] = null
                        it[occurrences] = null
                    }
                    is PaymentSchedule.Recurring -> {
                        it[type] = PaymentScheduleType.RECURRING
                        it[intervalMonths] = paymentSchedule.intervalMonths
                        it[occurrences] = paymentSchedule.occurrences
                    }
                }
            } get PaymentSchedules.fundablePaymentScheduleId
        }
    }

    private fun toPaymentSchedule(result: ResultRow): PaymentSchedule {
        val type = result[PaymentSchedules.type]
        return when (type) {
            PaymentScheduleType.ONE_TIME -> PaymentSchedule.OneTime
            PaymentScheduleType.RECURRING -> PaymentSchedule.Recurring(
                intervalMonths = result[PaymentSchedules.intervalMonths]!!,
                occurrences = result[PaymentSchedules.occurrences]
            )
        }
    }

}