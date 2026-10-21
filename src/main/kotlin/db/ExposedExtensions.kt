package org.rdm.online.db

import kotlin.jvm.JvmName
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table.Dual.check
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or


@JvmName("mustPositiveLong")
fun Column<Long>.mustPositive() : Column<Long> = check { it greater 0L }
@JvmName("mustPositiveInt")
fun Column<Int>.mustPositive() : Column<Int> = check { it greater 0 }

@JvmName("mustPositiveNullableLong")
fun Column<Long?>.mustPositive() : Column<Long?> = check { (it greater 0L) }
@JvmName("mustPositiveNullableInt")
fun Column<Int?>.mustPositive() : Column<Int?> = check { (it greater 0) }

@JvmName("mustPositiveOrNullLong")
fun Column<Long?>.mustPositiveOrNull() : Column<Long?> = check { (it greater 0L) or it.isNull() }
@JvmName("mustPositiveOrNullInt")
fun Column<Int?>.mustPositiveOrNull() : Column<Int?> = check { (it greater 0) or it.isNull() }

fun sqliteMax(a: Expression<Long>, b: Expression<Long>) =
    CustomFunction<Long>("MAX", LongColumnType(), a, b)


fun Expression<Long>.nullableLong(): ExpressionWithColumnType<Long?> {
    val source = this

    return object : ExpressionWithColumnType<Long?>() {
        override val columnType = LongColumnType()

        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            source.toQueryBuilder(queryBuilder)
        }
    }
}