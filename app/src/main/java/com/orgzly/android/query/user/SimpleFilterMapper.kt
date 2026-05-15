package com.orgzly.android.query.user

import android.util.Log
import com.orgzly.android.query.Condition
import com.orgzly.android.query.Options
import com.orgzly.android.query.Query
import com.orgzly.android.query.QueryInterval
import com.orgzly.android.query.QueryTokenizer
import com.orgzly.android.query.Relation
import com.orgzly.android.query.RelativeDateOption
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.SimpleQuery
import com.orgzly.android.query.SimpleFilterBuilder
import com.orgzly.android.query.SimpleSortOrder
import com.orgzly.android.query.SortOrder
import com.orgzly.android.query.StateType
import javax.inject.Inject
import kotlin.collections.forEach
import kotlin.reflect.KClass

class SimpleFilterMapper @Inject constructor() {

    companion object {
        val TAG = SimpleFilterMapper::class.java.name
    }

    fun fromQuery(query: Query): SimpleQuery {
        Log.d(TAG, "Mapping $query")

        val result = SimpleFilterBuilder()
        val flattenedConditions = query.condition?.let { flattenCondition(it) }
        val search = flattenedConditions?.filterIsInstance<Condition.HasText>()
            ?.joinToString(" ") {
                when (it.isQuoted) {
                    true -> "\"${it.text}\""
                    else -> it.text
                }
            } ?: ""

        flattenedConditions?.let {
            rejectIf(
                it.count { it is Condition.Event } > 1,
                "Cannot have greater than 1 Condition.Event"
            )
            rejectIf(
                it.count { it is Condition.Scheduled } > 1,
                "Cannot have greater than 1 Condition.Scheduled"
            )
            rejectIf(
                it.count { it is Condition.Deadline } > 1,
                "Cannot have greater than 1 Condition.Deadline"
            )
            rejectIf(
                it.count { it is Condition.Closed } > 1,
                "Cannot have greater than 1 Condition.Closed"
            )
            rejectIf(
                it.count { it is Condition.Created } > 1,
                "Cannot have greater than 1 Condition.Created"
            )
            rejectIf(
                it.count { it is Condition.HasPriority } > 1,
                "Cannot have greater than 1 Condition.HasPriority"
            )
            rejectIf(
                it.count { it is Condition.HasState } > 1,
                "Cannot have greater than 1 Condition.HasState"
            )
        }

        flattenedConditions?.forEach { c ->
            when (c) {
                is Condition.InBook -> {
                    rejectIf(c.not)
                    result.books += c.name
                }

                is Condition.HasState -> {
                    rejectIf(c.not)
                    result.state = c.state
                }

                is Condition.HasStateType -> {
                    if (c.type == StateType.DONE && c.not) {
                        result.excludeDone = true
                    } else {
                        rejectIf(true, "Cannot have non \".it.done\" state types")
                    }
                }

                is Condition.HasPriority -> {
                    rejectIf(c.not)
                    result.priority = c.priority
                }

                is Condition.HasSetPriority -> {
                    rejectIf(true, "Cannot have set priorities")
                }

                is Condition.HasTag -> {
                    rejectIf(c.not)
                    result.tags += c.tag
                }

                is Condition.HasOwnTag -> {
                    rejectIf(true, "Cannot have own tags")
                }

                is Condition.Event -> {
                    result.event = mapDate(c.interval, c.relation, Condition.Event::class)
                }

                is Condition.Scheduled -> {
                    result.scheduled = mapDate(c.interval, c.relation, Condition.Scheduled::class)
                }

                is Condition.Deadline -> {
                    result.deadline = mapDate(c.interval, c.relation, Condition.Deadline::class)
                }

                is Condition.Closed -> {
                    result.closed = mapDate(c.interval, c.relation, Condition.Closed::class)
                }

                is Condition.Created -> {
                    result.created = mapDate(c.interval, c.relation, Condition.Created::class)
                }

                is Condition.HasText -> {}

                else -> {
                    throw UnsupportedSimpleFilterException(
                        "Unsupported condition: $c"
                    )
                }
            }
        }

        when (query.sortOrders.size) {
            0 -> {}
            1 -> {
                result.sortOrder = mapSortOrder(query.sortOrders.first())
                result.sortDescending = query.sortOrders.first().desc
            }
            else -> throw UnsupportedSimpleFilterException(
                "Cannot represent more than one sort order"
            )
        }

        result.agendaDays = query.options.agendaDays.takeIf { it > 0 }

        return SimpleQuery(
            search,
            result.build()
        )
    }

    fun toQuery(search: String, filter: SimpleFilter) = Query(
        Condition.And(
            buildList {
                addAll(
                    filter.books.map {
                        Condition.InBook(it)
                    }
                )

                if (filter.excludeDone) {
                    add(
                        Condition.HasStateType(
                            StateType.DONE,
                            true
                        )
                    )
                }

                filter.state?.let {
                    add(
                        Condition.HasState(
                            it,
                            false
                        )
                    )
                }

                filter.priority?.let {
                    add(Condition.HasPriority(it))
                }

                addAll(
                    filter.tags.map {
                        Condition.HasTag(it)
                    }
                )

                filter.event?.let {
                    val (i,r) = it.toIntervalAndRelation(Condition.Event::class)
                    add(
                        Condition.Event(
                            interval = i,
                            relation = r
                        )
                    )
                }

                filter.scheduled?.let {
                    val (i,r) = it.toIntervalAndRelation(Condition.Scheduled::class)
                    add(
                        Condition.Scheduled(
                            interval = i,
                            relation = r
                        )
                    )
                }

                filter.deadline?.let {
                    val (i,r) = it.toIntervalAndRelation(Condition.Deadline::class)
                    add(
                        Condition.Deadline(
                            interval = i,
                            relation = r
                        )
                    )
                }

                filter.closed?.let {
                    val (i,r) = it.toIntervalAndRelation(Condition.Closed::class)
                    add(
                        Condition.Closed(
                            interval = i,
                            relation = r
                        )
                    )
                }

                filter.created?.let {
                    val (i,r) = it.toIntervalAndRelation(Condition.Created::class)
                    add(
                        Condition.Created(
                            interval = i,
                            relation = r
                        )
                    )
                }

                search.let {
                    val tokenizer = QueryTokenizer(it, "(", ")")

                    addAll(
                        tokenizer.tokens.map {
                            val unquoted = QueryTokenizer.unquote(it)
                            Condition.HasText(
                                unquoted,
                                unquoted != it
                            )
                        }
                    )
                }
            }
        ),
        listOfNotNull(mapSimpleSortOrder(filter.sortOrder, filter.sortDescending)),
        Options(
            agendaDays = filter.agendaDays ?: 0
        )
    )

    private fun rejectIf(value: Boolean, explanation: String? = null) {
        if (value) {
            throw UnsupportedSimpleFilterException(
                explanation ?: "NOT conditions are unsupported"
            )
        }
    }
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

private fun getDefaultEQForType(type: KClass<*>): Relation = when (type) {
    Condition.Closed::class -> Relation.EQ
    Condition.Event::class -> Relation.EQ
    else -> Relation.LE
}

private fun RelativeDateOption.toIntervalAndRelation(
    type: KClass<*>
): Pair<QueryInterval, Relation> =
    when (this) {
        RelativeDateOption.FUTURE ->
            QueryInterval(QueryInterval.Unit.DAY) to Relation.GE

        RelativeDateOption.PAST ->
            QueryInterval(QueryInterval.Unit.DAY) to Relation.LT

        RelativeDateOption.TODAY ->
            QueryInterval(QueryInterval.Unit.DAY, 0) to getDefaultEQForType(type)

        RelativeDateOption.TOMORROW ->
            QueryInterval(QueryInterval.Unit.DAY, 1) to Relation.EQ

        RelativeDateOption.NEXT_7_DAYS ->
            QueryInterval(QueryInterval.Unit.DAY, 7) to Relation.LE

        RelativeDateOption.NEXT_30_DAYS ->
            QueryInterval(QueryInterval.Unit.DAY, 30) to Relation.LE
    }

private fun mapDate(
    interval: QueryInterval,
    relation: Relation,
    type: KClass<*>
): RelativeDateOption {

    return when {
        interval.unit == QueryInterval.Unit.DAY &&
                interval.value == 0 &&
                relation == getDefaultEQForType(type) ->
            RelativeDateOption.TODAY

        interval.unit == QueryInterval.Unit.DAY &&
                interval.value == 1 &&
                relation == Relation.EQ ->
            RelativeDateOption.TOMORROW

        interval.unit == QueryInterval.Unit.DAY &&
                relation == Relation.GE ->
            RelativeDateOption.FUTURE

        interval.unit == QueryInterval.Unit.DAY &&
                relation == Relation.LT ->
            RelativeDateOption.PAST

        relation == Relation.GE &&
                interval.value == 7 &&
                interval.unit == QueryInterval.Unit.WEEK ->
            RelativeDateOption.NEXT_7_DAYS

        relation == Relation.GE &&
                interval.value == 1 &&
                interval.unit == QueryInterval.Unit.MONTH ->
            RelativeDateOption.NEXT_30_DAYS

        else ->
            throw UnsupportedSimpleFilterException(
                "Unsupported date filter: $relation $interval"
            )
    }
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
    else -> throw UnsupportedSimpleFilterException(
        "Unsupported sort order: ${sortOrder::class.java.name}"
    )
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

class UnsupportedSimpleFilterException(
    message: String
) : RuntimeException(message)