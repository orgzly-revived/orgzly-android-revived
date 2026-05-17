package com.orgzly.android.query.user

import android.util.Log
import com.orgzly.android.query.Condition
import com.orgzly.android.query.DateCondition
import com.orgzly.android.query.GenericDateCondition
import com.orgzly.android.query.Options
import com.orgzly.android.query.Query
import com.orgzly.android.query.QueryInterval
import com.orgzly.android.query.QueryTokenizer
import com.orgzly.android.query.Relation
import com.orgzly.android.query.RelativeDateOption
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.SimpleFilterBuilder
import com.orgzly.android.query.SimpleQuery
import com.orgzly.android.query.SimpleSortOrder
import com.orgzly.android.query.SortOrder
import com.orgzly.android.query.StateType
import com.orgzly.android.query.getTodayDateConditionRelationshipForType
import com.orgzly.android.query.relativeDateOptionRelations
import javax.inject.Inject

class SimpleFilterMapper @Inject constructor() {

    companion object {
        private val TAG = SimpleFilterMapper::class.java.name
    }

    fun fromQuery(query: Query): SimpleQuery {
        Log.d(TAG, "Mapping $query")

        val result = SimpleFilterBuilder()
        val reader = QueryPullReader(query)

        val search = generateSequence {
            if (reader.hasNextConditionOfType<Condition.HasText>())
                reader.nextConditionOfType<Condition.HasText>()
            else null
        }.joinToString(" ") {
            when (it.isQuoted) {
                true -> "\"${it.text}\""
                else -> it.text
            }
        }

        result.apply {
            event = mapDateConditions<Condition.Event>(reader)
            scheduled = mapDateConditions<Condition.Scheduled>(reader)
            deadline = mapDateConditions<Condition.Deadline>(reader)
            closed = mapDateConditions<Condition.Closed>(reader)
            created = mapDateConditions<Condition.Created>(reader)
        }

        if (reader.hasNextConditionOfType<Condition.HasOwnTag>())
            throw UnsupportedSimpleFilterException(
                "Unsupported condition: ${Condition.HasOwnTag::class}"
            )

        if (reader.hasNextConditionOfType<Condition.HasSetPriority>())
            throw UnsupportedSimpleFilterException(
                "Unsupported condition: ${Condition.HasSetPriority::class}"
            )

        while (reader.hasNextCondition()) {
            when (val c = reader.nextCondition()) {
                is Condition.InBook -> {
                    rejectIf(c.not)
                    result.books += c.name
                }

                is Condition.HasState -> {
                    if (reader.hasNextConditionOfType<Condition.HasState>())
                        throw UnsupportedSimpleFilterException("Cannot represent multiple HasState")

                    rejectIf(c.not)
                    result.state = c.state
                }

                is Condition.HasStateType -> {
                    if (c.type == StateType.DONE && c.not) {
                        result.excludeDone = true
                    } else {
                        throw UnsupportedSimpleFilterException("Cannot have non \".it.done\" state types")
                    }
                }

                is Condition.HasPriority -> {
                    if (reader.hasNextConditionOfType<Condition.HasPriority>())
                        throw UnsupportedSimpleFilterException("Cannot represent multiple HasPriority")

                    rejectIf(c.not)
                    result.priority = c.priority
                }

                is Condition.HasTag -> {
                    rejectIf(c.not)
                    result.tags += c.tag
                }

                else -> {
                    throw UnsupportedSimpleFilterException(
                        "Unsupported condition: $c"
                    )
                }
            }
        }

        if (reader.hasNextSortOrder()) {
            val sortOrder = reader.nextSortOrder()
            if (reader.hasNextSortOrder()) {
                throw UnsupportedSimpleFilterException("Cannot represent more than one sort order")
            }

            result.sortOrder = mapSortOrder(sortOrder)
            result.sortDescending = sortOrder.desc
        }

        result.agendaDays = reader.agendaDays()

        if (reader.hasNextCondition()) {
            throw UnsupportedSimpleFilterException("Unconsumed conditions remain ${reader.nextCondition()}")
        }

        if (reader.hasNextSortOrder()) {
            throw UnsupportedSimpleFilterException("Unconsumed sort orders remain ${reader.nextSortOrder()}")
        }

        return SimpleQuery(search, result.build())
    }

    fun toQuery(search: String, filter: SimpleFilter): Query {
        val conditions = buildList {
            addAll(filter.books.map { Condition.InBook(it) })

            if (filter.excludeDone) {
                add(Condition.HasStateType(StateType.DONE, true))
            }

            filter.state?.let { add(Condition.HasState(it, false)) }
            filter.priority?.let { add(Condition.HasPriority(it)) }
            addAll(filter.tags.map { Condition.HasTag(it) })

            addDateConditions(filter.event) { i, r -> Condition.Event(i, r) }
            addDateConditions(filter.scheduled) { i, r -> Condition.Scheduled(i, r) }
            addDateConditions(filter.deadline) { i, r -> Condition.Deadline(i, r) }
            addDateConditions(filter.closed) { i, r -> Condition.Closed(i, r) }
            addDateConditions(filter.created) { i, r -> Condition.Created(i, r) }

            if (search.isNotBlank()) {
                val tokenizer = QueryTokenizer(search, "(", ")")
                addAll(tokenizer.tokens.map { token ->
                    val unquoted = QueryTokenizer.unquote(token)
                    Condition.HasText(unquoted, unquoted != token)
                })
            }
        }

        return Query(
            condition = Condition.And(conditions),
            sortOrders = listOfNotNull(mapSimpleSortOrder(filter.sortOrder, filter.sortDescending)),
            options = Options(agendaDays = filter.agendaDays ?: 0)
        )
    }

    private inline fun <reified T> MutableList<Condition>.addDateConditions(
        option: RelativeDateOption?,
        noinline construct: (QueryInterval, Relation) -> T
    ) where T : Condition, T : DateCondition {
        option?.let {
            addAll(mapRelativeDateOption(it, construct))
        }
    }

    private inline fun <reified T : DateCondition> mapDateConditions(
        reader: QueryPullReader
    ): RelativeDateOption? {
        if (!reader.hasNextConditionOfType<T>()) return null

        val tokens = generateSequence {
            if (reader.hasNextConditionOfType<T>()) reader.nextConditionOfType<T>() else null
        }.map { GenericDateCondition.fromDateCondition(it) }.toHashSet()

        val today = getTodayDateConditionRelationshipForType(T::class)

        return (relativeDateOptionRelations + today)
            .firstOrNull { it.conditions == tokens }
            ?.relative
            ?: throw UnsupportedSimpleFilterException("Unsupported date condition configuration")
    }

    private inline fun <reified T : DateCondition> mapRelativeDateOption(
        option: RelativeDateOption,
        construct: (QueryInterval, Relation) -> T
    ): List<T> = (relativeDateOptionRelations + getTodayDateConditionRelationshipForType(T::class))
        .first { it.relative == option }
        .conditions
        .map { construct(it.interval, it.relation) }

    private fun rejectIf(value: Boolean) {
        if (value) throw UnsupportedSimpleFilterException("NOT conditions are unsupported")
    }

    private fun mapSortOrder(sortOrder: SortOrder) = when (sortOrder) {
        is SortOrder.Book -> SimpleSortOrder.BOOK
        is SortOrder.Created -> SimpleSortOrder.CREATED
        is SortOrder.Closed -> SimpleSortOrder.CLOSED
        is SortOrder.Deadline -> SimpleSortOrder.DEADLINE
        is SortOrder.Event -> SimpleSortOrder.EVENT
        is SortOrder.Scheduled -> SimpleSortOrder.SCHEDULED
        is SortOrder.Priority -> SimpleSortOrder.PRIORITY
        is SortOrder.State -> SimpleSortOrder.STATE
        is SortOrder.Title -> SimpleSortOrder.TITLE
        else -> throw UnsupportedSimpleFilterException("Unsupported sort order: ${sortOrder::class.java.name}")
    }

    private fun mapSimpleSortOrder(
        simpleSortOrder: SimpleSortOrder,
        descending: Boolean
    ) = when (simpleSortOrder) {
        SimpleSortOrder.BOOK -> SortOrder.Book(descending)
        SimpleSortOrder.CREATED -> SortOrder.Created(descending)
        SimpleSortOrder.CLOSED -> SortOrder.Closed(descending)
        SimpleSortOrder.DEADLINE -> SortOrder.Deadline(descending)
        SimpleSortOrder.EVENT -> SortOrder.Event(descending)
        SimpleSortOrder.SCHEDULED -> SortOrder.Scheduled(descending)
        SimpleSortOrder.PRIORITY -> SortOrder.Priority(descending)
        SimpleSortOrder.STATE -> SortOrder.State(descending)
        SimpleSortOrder.TITLE -> SortOrder.Title(descending)
        SimpleSortOrder.DEFAULT -> null
    }
}

class UnsupportedSimpleFilterException(
    message: String
) : RuntimeException(message)