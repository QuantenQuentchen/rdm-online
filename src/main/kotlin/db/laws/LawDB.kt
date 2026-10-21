package org.rdm.online.db.laws

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object LawDB {

    object Paragraph : Table("law_paragraphs") {
        val id = long("id").autoIncrement()
        val lawId = long("law_id")
        val number = integer("number")
        val text = text("text")
        override val primaryKey = PrimaryKey(id)
    }

    object ParagraphChanges : Table("law_paragraph_changes") {
        val id = long("id").autoIncrement()
        val paragraphId = reference("paragraph_id", Paragraph.id, onDelete = ReferenceOption.CASCADE)
        val changeType = text("change_type")
        val changeText = text("change_text")
        override val primaryKey = PrimaryKey(id)
    }

    object ParagraphLinks : Table("law_paragraph_links") {
        val parent = reference("parent_id", Paragraph.id, onDelete = ReferenceOption.CASCADE)
        val child = reference("child_id", Paragraph.id, onDelete = ReferenceOption.CASCADE)
        override val primaryKey = PrimaryKey(parent, child)
    }
}