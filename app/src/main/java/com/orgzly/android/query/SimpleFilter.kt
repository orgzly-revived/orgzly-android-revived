package com.orgzly.android.query

import androidx.compose.runtime.Immutable

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