package org.rdm.online.db.awards

import org.rdm.online.api.internal.data.awards.Category
import org.rdm.online.api.internal.data.awards.NominatedSuggestion
import org.rdm.online.api.internal.data.awards.Suggestion
import org.rdm.online.api.internal.exceptions.AwardsRuleViolation
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.rdm.online.db.dbQuery
import org.rdm.online.db.user.UserDB.Users
import java.time.Instant

object AwardsDB {

    object Categories : Table("categories") {
        val id = long("id").autoIncrement()
        val name = text("name")
        val order = integer("order")
        val description = text("description").nullable()
        val criteria = text("criteria").nullable()
        val locked = bool("locked").default(false)
        val maxSuggestions = integer("max_suggestions").nullable()
        override val primaryKey = PrimaryKey(id)
    }

    object Suggestions : Table("suggestions") {
        val id = long("id").autoIncrement()
        val categoryId = reference("category_id", Categories.id, onDelete = ReferenceOption.CASCADE)
            .index("idx_suggestions_category_id")
        val body = text("text")
        val updatedAt = timestamp("updated_at")
        val nominatorId = reference("nominator_id", Users.id, onDelete = ReferenceOption.CASCADE)
            .index("idx_suggestions_nominator_id")
        override val primaryKey = PrimaryKey(id)
    }


    // ---- categories -------------------------------------------------------

    suspend fun categories(): List<Category> = dbQuery {
        Categories.selectAll().orderBy(Categories.order).map { it.toCategory() }
    }

    suspend fun category(categoryId: Long): Category? = dbQuery { findCategory(categoryId) }

    /** id == 0 inserts, anything else updates (updating a missing id is a silent no-op, as before). */
    suspend fun upsertCategory(c: Category) {
        dbQuery {
            if (c.id == 0L) Categories.insert { it.fill(c) }
            else Categories.update({ Categories.id eq c.id }) { it.fill(c) }
        }
    }

    /** Suggestions go with it via ON DELETE CASCADE. */
    suspend fun deleteCategory(categoryId: Long) {
        dbQuery { Categories.deleteWhere { Categories.id eq categoryId } }
    }

    // ---- suggestions ------------------------------------------------------

    suspend fun suggestionsIn(categoryId: Long): List<NominatedSuggestion> = dbQuery {
        Suggestions.innerJoin(Users).selectAll()
            .where { Suggestions.categoryId eq categoryId }
            .orderBy(Suggestions.updatedAt to SortOrder.ASC, Suggestions.id to SortOrder.ASC)
            .map { it.toNominated() }
    }

    suspend fun allSuggestions(): List<NominatedSuggestion> = dbQuery {
        Suggestions.innerJoin(Users).selectAll().orderBy(Suggestions.id).map { it.toNominated() }
    }

    suspend fun suggestionsOf(userId: Long): List<Suggestion> = dbQuery {
        Suggestions.selectAll().where { Suggestions.nominatorId eq userId }
            .orderBy(Suggestions.id).map { it.toSuggestion() }
    }

    /**
     * Replaces [userId]'s suggestions in [categoryId] with [texts], atomically.
     * Other users' suggestions are never touched. An empty list clears the user's suggestions.
     * Returns the user's resulting suggestions in that category.
     */
    suspend fun replaceSuggestions(categoryId: Long, userId: Long, texts: List<String>): List<Suggestion> =
        dbQuery {
            val category = findCategory(categoryId) ?: throw AwardsRuleViolation("category not found")
            if (category.locked) throw AwardsRuleViolation("category is locked")
            if (category.maxSuggestions != null && texts.size > category.maxSuggestions) {
                throw AwardsRuleViolation("too many suggestions for this category")
            }

            val mine = (Suggestions.categoryId eq categoryId) and (Suggestions.nominatorId eq userId)
            Suggestions.deleteWhere { mine }

            val now = Instant.now()
            texts.forEach { t ->
                Suggestions.insert {
                    it[Suggestions.categoryId] = categoryId
                    it[Suggestions.body] = t
                    it[Suggestions.updatedAt] = now
                    it[Suggestions.nominatorId] = userId
                }
            }

            Suggestions.selectAll().where { mine }.orderBy(Suggestions.id).map { it.toSuggestion() }
        }

    // ---- mapping ----------------------------------------------------------

    private fun findCategory(categoryId: Long): Category? =
        Categories.selectAll().where { Categories.id eq categoryId }.singleOrNull()?.toCategory()

    private fun UpdateBuilder<*>.fill(c: Category) {
        this[Categories.name] = c.name
        this[Categories.order] = c.order
        this[Categories.description] = c.description
        this[Categories.criteria] = c.criteria
        this[Categories.locked] = c.locked
        this[Categories.maxSuggestions] = c.maxSuggestions
    }

    private fun ResultRow.toCategory() = Category(
        id = this[Categories.id],
        name = this[Categories.name],
        order = this[Categories.order],
        description = this[Categories.description],
        criteria = this[Categories.criteria],
        locked = this[Categories.locked],
        maxSuggestions = this[Categories.maxSuggestions],
    )

    private fun ResultRow.toSuggestion() = Suggestion(
        id = this[Suggestions.id],
        categoryId = this[Suggestions.categoryId],
        text = this[Suggestions.body],
        updatedAt = this[Suggestions.updatedAt],
        nominatorId = this[Suggestions.nominatorId],
    )

    private fun ResultRow.toNominated() = NominatedSuggestion(toSuggestion(), this[Users.discordId])
}
