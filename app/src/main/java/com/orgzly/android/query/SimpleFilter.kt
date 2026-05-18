package com.orgzly.android.query

import androidx.compose.runtime.Immutable
import kotlin.reflect.KClass

data class SimpleQuery(
    val search: String,
    val filter: SimpleFilter,
)

@Immutable
data class SimpleFilter(
    val books: Set<String> = emptySet(),

    val excludeDone: Boolean = false,
    val state: String? = null,

    val priority: String? = null,

    val tags: Set<String> = emptySet(),

    val event: RelativeDateOption? = null,
    val scheduled: RelativeDateOption? = null,
    val deadline: RelativeDateOption? = null,
    val closed: RelativeDateOption? = null,
    val created: RelativeDateOption? = null,

    val sortOrder: SimpleSortOrder = SimpleSortOrder.DEFAULT,
    val sortDescending: Boolean = true,

    val agendaDays: Int? = null
)

data class SimpleFilterBuilder(
    val books: MutableSet<String> = mutableSetOf(),

    var excludeDone: Boolean = false,
    var state: String? = null,

    var priority: String? = null,

    val tags: MutableSet<String> = mutableSetOf(),

    var event: RelativeDateOption? = null,
    var scheduled: RelativeDateOption? = null,
    var deadline: RelativeDateOption? = null,
    var closed: RelativeDateOption? = null,
    var created: RelativeDateOption? = null,

    var sortOrder: SimpleSortOrder = SimpleSortOrder.DEFAULT,
    var sortDescending: Boolean = true,

    var agendaDays: Int? = null
) {
    fun build(): SimpleFilter = SimpleFilter(
        books = books.toSet(),
        excludeDone = excludeDone,
        state = state,
        priority = priority,
        tags = tags.toSet(),
        event = event,
        scheduled = scheduled,
        deadline = deadline,
        closed = closed,
        created = created,
        sortOrder = sortOrder,
        sortDescending = sortDescending,
        agendaDays = agendaDays
    )
}

enum class RelativeDateOption {
    FUTURE,
    PAST,

    TODAY,
    TOMORROW,

    THIS_WEEK,
    THIS_MONTH
}

enum class SimpleSortOrder {
    DEFAULT,
    BOOK,
    TITLE,
    SCHEDULED,
    DEADLINE,
    EVENT,
    CLOSED,
    CREATED,
    PRIORITY,
    STATE
}

val relativeDateOptionRelations = listOf(
    DateConditionRelationship(
        listOf(
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.DAY,
                    1
                ),
                Relation.EQ
            )
        ),
        RelativeDateOption.TOMORROW
    ),
    DateConditionRelationship(
        listOf(
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.DAY,
                    0
                ),
                Relation.GE
            )
        ),
        RelativeDateOption.FUTURE
    ),
    DateConditionRelationship(
        listOf(
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.DAY,
                    0
                ),
                Relation.LT
            )
        ),
        RelativeDateOption.PAST
    ),
    DateConditionRelationship(
        listOf(
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.WEEK,
                    0
                ),
                Relation.GE
            ),
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.WEEK,
                    1
                ),
                Relation.LT
            )
        ),
        RelativeDateOption.THIS_WEEK
    ),
    DateConditionRelationship(
        listOf(
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.MONTH,
                    0
                ),
                Relation.GE
            ),
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.MONTH,
                    1
                ),
                Relation.LT
            )
        ),
        RelativeDateOption.THIS_MONTH
    ),
)

private data class GenericDateCondition(
    override val interval: QueryInterval,
    override val relation: Relation
): DateCondition {

    companion object {
        fun fromDateCondition(condition: DateCondition) = GenericDateCondition(
            condition.interval,
            condition.relation
        )
    }

}

data class DateConditionRelationship(
    val conditions: List<DateCondition>,
    val relative: RelativeDateOption
) {

    fun matches(conditions: List<DateCondition>): Boolean {
        val generic = conditions.map { GenericDateCondition.fromDateCondition(it) }.distinct()
        return this.conditions.containsAll(generic) &&
                this.conditions.size == generic.size
    }

}

fun getTodayDateConditionRelationshipForType(type: KClass<*>): DateConditionRelationship =
    DateConditionRelationship(
        listOf(
            GenericDateCondition(
                QueryInterval(
                    QueryInterval.Unit.DAY,
                    0
                ),
                when (type) {
                    Condition.Closed::class -> Relation.EQ
                    Condition.Event::class -> Relation.EQ
                    else -> Relation.LE
                }
            )
        ),
        RelativeDateOption.TODAY
    )