package com.orgzly.android.query.user

import com.orgzly.android.query.Condition
import com.orgzly.android.query.Query
import com.orgzly.android.query.SortOrder
import kotlin.reflect.KClass

internal class QueryPullReader(
    private val query: Query
) {

    private val conditions: MutableList<Condition> =
        query.condition?.let { flattenCondition(it) }?.toMutableList() ?: mutableListOf()
    private val sortOrders: MutableList<SortOrder> =
        query.sortOrders.toMutableList()

    fun nextCondition(): Condition = conditions.removeAt(0)
    fun hasNextCondition(): Boolean = conditions.isNotEmpty()

    inline fun <reified T> nextConditionOfType(): T {
        val index = conditions.indexOfFirst {
            it is T
        }
        return conditions.removeAt(index) as T
    }
    inline fun <reified T> hasNextConditionOfType(): Boolean = conditions.any { it is T }

    fun nextSortOrder(): SortOrder = sortOrders.removeAt(0)
    fun hasNextSortOrder(): Boolean = sortOrders.isNotEmpty()

    fun isAgenda(): Boolean = query.isAgenda()
    fun agendaDays(): Int? = query.options.agendaDays.takeIf { isAgenda() }

}

private fun flattenCondition(condition: Condition): List<Condition> =
    when (condition) {
        is Condition.And ->
            condition.operands.flatMap(::flattenCondition)

        is Condition.Or ->
            throw UnsupportedSimpleFilterException(
                "OR conditions are unsupported"
            )

        else ->
            listOf(condition)
    }