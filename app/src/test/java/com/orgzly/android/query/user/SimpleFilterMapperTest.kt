package com.orgzly.android.query.user

import com.orgzly.android.query.Condition
import com.orgzly.android.query.Query
import com.orgzly.android.query.QueryInterval
import com.orgzly.android.query.Relation
import com.orgzly.android.query.RelativeDateOption
import com.orgzly.android.query.SimpleFilter
import com.orgzly.android.query.SimpleSortOrder
import com.orgzly.android.query.SortOrder
import com.orgzly.android.query.StateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SimpleFilterMapperTest {

    private val mapper = SimpleFilterMapper(InternalQueryParser())

    @Test
    fun `fromQuery - empty query`() {
        val query = Query(null)
        val simpleQuery = mapper.fromQuery(query)

        assertEquals("", simpleQuery.search)
        assertTrue(simpleQuery.filter.books.isEmpty())
        assertFalse(simpleQuery.filter.excludeDone)
        assertTrue(simpleQuery.filter.state.isEmpty())
        assertNull(simpleQuery.filter.priority)
        assertTrue(simpleQuery.filter.tags.isEmpty())
        assertNull(simpleQuery.filter.event)
        assertEquals(SimpleSortOrder.DEFAULT, simpleQuery.filter.sortOrder)
    }

    @Test
    fun `fromQuery - search text`() {
        val query = Query(Condition.And(listOf(
            Condition.HasText("foo", false),
            Condition.HasText("bar baz", true)
        )))
        val simpleQuery = mapper.fromQuery(query)

        assertEquals("foo \"bar baz\"", simpleQuery.search)
    }

    @Test
    fun `fromQuery - nested And conditions`() {
        val query = Query(Condition.And(listOf(
            Condition.InBook("work"),
            Condition.And(listOf(
                Condition.HasState("TODO"),
                Condition.HasPriority("A")
            ))
        )))
        val simpleQuery = mapper.fromQuery(query)

        assertEquals(setOf("work"), simpleQuery.filter.books)
        assertEquals(setOf("TODO"), simpleQuery.filter.state)
        assertEquals("A", simpleQuery.filter.priority)
    }

    @Test
    fun `fromQuery - all conditions`() {
        val query = Query(Condition.And(listOf(
            Condition.InBook("work"),
            Condition.HasState("TODO"),
            Condition.HasPriority("A"),
            Condition.HasTag("urgent"),
            Condition.HasStateType(StateType.DONE, true),
            Condition.Scheduled(QueryInterval(QueryInterval.Unit.DAY, 0), Relation.LE),
            Condition.Deadline(QueryInterval(QueryInterval.Unit.DAY, 1), Relation.EQ),
            Condition.Event(QueryInterval(QueryInterval.Unit.DAY), Relation.GE),
            Condition.Closed(QueryInterval(QueryInterval.Unit.DAY), Relation.LT),
            Condition.Created(QueryInterval(QueryInterval.Unit.WEEK, 0), Relation.GE),
            Condition.Created(QueryInterval(QueryInterval.Unit.WEEK, 1), Relation.LT),
        )))

        val simpleQuery = mapper.fromQuery(query)

        assertEquals(setOf("work"), simpleQuery.filter.books)
        assertEquals(setOf("TODO"), simpleQuery.filter.state)
        assertEquals("A", simpleQuery.filter.priority)
        assertEquals(setOf("urgent"), simpleQuery.filter.tags)
        assertTrue(simpleQuery.filter.excludeDone)
        assertEquals(RelativeDateOption.TODAY, simpleQuery.filter.scheduled)
        assertEquals(RelativeDateOption.TOMORROW, simpleQuery.filter.deadline)
        assertEquals(RelativeDateOption.FUTURE, simpleQuery.filter.event)
        assertEquals(RelativeDateOption.PAST, simpleQuery.filter.closed)
        assertEquals(RelativeDateOption.THIS_WEEK, simpleQuery.filter.created)
    }

    @Test
    fun `fromQuery - dates - today tomorrow`() {
        val todayQuery = Query(Condition.And(listOf(
            Condition.Scheduled(QueryInterval(QueryInterval.Unit.DAY, 0), Relation.LE)
        )))
        assertEquals(RelativeDateOption.TODAY, mapper.fromQuery(todayQuery).filter.scheduled)

        val tomorrowQuery = Query(Condition.And(listOf(
            Condition.Deadline(QueryInterval(QueryInterval.Unit.DAY, 1), Relation.EQ)
        )))
        assertEquals(RelativeDateOption.TOMORROW, mapper.fromQuery(tomorrowQuery).filter.deadline)
    }

    @Test
    fun `fromQuery - dates - past future`() {
        val futureQuery = Query(Condition.And(listOf(
            Condition.Event(QueryInterval(QueryInterval.Unit.DAY), Relation.GE)
        )))
        assertEquals(RelativeDateOption.FUTURE, mapper.fromQuery(futureQuery).filter.event)

        val pastQuery = Query(Condition.And(listOf(
            Condition.Closed(QueryInterval(QueryInterval.Unit.DAY), Relation.LT)
        )))
        assertEquals(RelativeDateOption.PAST, mapper.fromQuery(pastQuery).filter.closed)
    }

    @Test
    fun `fromQuery - dates - next 7 and 30 days`() {
        val next7 = Query(Condition.And(listOf(
            Condition.Scheduled(QueryInterval(QueryInterval.Unit.WEEK, 0), Relation.GE),
            Condition.Scheduled(QueryInterval(QueryInterval.Unit.WEEK, 1), Relation.LT),
        )))
        assertEquals(RelativeDateOption.THIS_WEEK, mapper.fromQuery(next7).filter.scheduled)

        val next30 = Query(Condition.And(listOf(
            Condition.Deadline(QueryInterval(QueryInterval.Unit.MONTH, 0), Relation.GE),
            Condition.Deadline(QueryInterval(QueryInterval.Unit.MONTH, 1), Relation.LT),
        )))
        assertEquals(RelativeDateOption.THIS_MONTH, mapper.fromQuery(next30).filter.deadline)
    }

    @Test
    fun `fromQuery - sort orders`() {
        val orders = listOf(
            SortOrder.Book() to SimpleSortOrder.BOOK,
            SortOrder.Created() to SimpleSortOrder.CREATED,
            SortOrder.Closed() to SimpleSortOrder.CLOSED,
            SortOrder.Deadline() to SimpleSortOrder.DEADLINE,
            SortOrder.Event() to SimpleSortOrder.EVENT,
            SortOrder.Scheduled() to SimpleSortOrder.SCHEDULED,
            SortOrder.Priority() to SimpleSortOrder.PRIORITY,
            SortOrder.State() to SimpleSortOrder.STATE,
            SortOrder.Title() to SimpleSortOrder.TITLE
        )

        for ((sortOrder, simpleSortOrder) in orders) {
            val query = Query(null, listOf(sortOrder))
            val simpleQuery = mapper.fromQuery(query)
            assertEquals(simpleSortOrder, simpleQuery.filter.sortOrder)
            assertFalse(simpleQuery.filter.sortDescending)
        }
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - multiple sort orders throws`() {
        val query = Query(null, listOf(SortOrder.Priority(true), SortOrder.Created(false)))
        mapper.fromQuery(query)
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - OR condition throws`() {
        val query = Query(Condition.Or(listOf(Condition.InBook("a"), Condition.InBook("b"))))
        mapper.fromQuery(query)
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - NOT book condition throws`() {
        val query = Query(Condition.InBook("a", true))
        mapper.fromQuery(query)
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - multiple state conditions throws`() {
        val query = Query(Condition.And(listOf(Condition.HasState("TODO"), Condition.HasState("NEXT"))))
        mapper.fromQuery(query)
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - multiple priority conditions throws`() {
        val query = Query(Condition.And(listOf(Condition.HasPriority("A"), Condition.HasPriority("B"))))
        mapper.fromQuery(query)
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - unsupported condition throws`() {
        val query = Query(Condition.HasSetPriority("A"))
        mapper.fromQuery(query)
    }

    @Test
    fun `toQuery - complex filter`() {
        val filter = SimpleFilter(
            books = setOf("personal"),
            excludeDone = true,
            state = setOf("NEXT"),
            priority = "B",
            tags = setOf("home", "work"),
            scheduled = RelativeDateOption.TODAY,
            sortOrder = SimpleSortOrder.TITLE,
            sortDescending = false,
            agendaDays = 7
        )
        val query = mapper.toQuery("find me", filter)

        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.InBook("personal")))
        assertTrue(conditions.contains(Condition.HasStateType(StateType.DONE, true)))
        assertTrue(conditions.contains(Condition.HasState("NEXT", false)))
        assertTrue(conditions.contains(Condition.HasPriority("B")))
        assertTrue(conditions.contains(Condition.HasTag("home")))
        assertTrue(conditions.contains(Condition.HasTag("work")))
        
        // TODAY for Scheduled uses LE by default (getDefaultEQForType)
        assertTrue(conditions.any { it is Condition.Scheduled && it.interval.unit == QueryInterval.Unit.DAY && it.interval.value == 0 && it.relation == Relation.LE })
        
        assertTrue(conditions.contains(Condition.HasText("find", false)))
        assertTrue(conditions.contains(Condition.HasText("me", false)))

        assertEquals(1, query.sortOrders.size)
        assertTrue(query.sortOrders.first() is SortOrder.Title)
        assertFalse(query.sortOrders.first().desc)
        assertEquals(7, query.options.agendaDays)
    }

    @Test
    fun `toQuery - unquoting search`() {
        val query = mapper.toQuery("\"quoted text\" unquoted", SimpleFilter())
        val conditions = (query.condition as Condition.And).operands
        
        assertTrue(conditions.contains(Condition.HasText("quoted text", true)))
        assertTrue(conditions.contains(Condition.HasText("unquoted", false)))
    }

    @Test(expected = UnsupportedSimpleFilterException::class)
    fun `fromQuery - unsupported sort order throws`() {
        val query = Query(null, listOf(SortOrder.Position(false)))
        mapper.fromQuery(query)
    }

    @Test
    fun `toQuery - all date conditions`() {
        val filter = SimpleFilter(
            event = RelativeDateOption.TODAY,
            scheduled = RelativeDateOption.TOMORROW,
            deadline = RelativeDateOption.FUTURE,
            closed = RelativeDateOption.PAST,
            created = RelativeDateOption.THIS_WEEK
        )
        val query = mapper.toQuery("", filter)
        val conditions = (query.condition as Condition.And).operands

        assertTrue(conditions.any { it is Condition.Event && it.relation == Relation.EQ })
        assertTrue(conditions.any { it is Condition.Scheduled && it.relation == Relation.EQ })
        assertTrue(conditions.any { it is Condition.Deadline && it.relation == Relation.GE })
        assertTrue(conditions.any { it is Condition.Closed && it.relation == Relation.LT })
        assertTrue(conditions.any { it is Condition.Created && it.relation == Relation.GE })
        assertTrue(conditions.any { it is Condition.Created && it.relation == Relation.LT })
    }

    @Test
    fun `toQuery - sanitises search field`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery(".it.done", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText(".it.done", true)))
    }

    @Test
    fun `toQuery - sanitises search field agenda`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery("ad.3", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText("ad.3", true)))
    }

    @Test
    fun `toQuery - sanitises search field AND`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery("AND", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText("AND", true)))
    }

    @Test
    fun `toQuery - sanitises search field multiple unsafe`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery("ad.3 AND test", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText("AND", true)))
        assertTrue(conditions.contains(Condition.HasText("ad.3", true)))
        assertTrue(conditions.contains(Condition.HasText("test", false)))
    }

    @Test
    fun `toQuery - maintains unquoted when safe`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery("test", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText("test", false)))
    }

    @Test
    fun `toQuery - maintains quoted when safe`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery("\"test\"", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText("test", true)))
    }

    @Test
    fun `toQuery - maintains quoted when unsafe`() {
        val filter = SimpleFilter()

        val query = mapper.toQuery("\".it.done\"", filter)
        val conditions = (query.condition as Condition.And).operands
        assertTrue(conditions.contains(Condition.HasText(".it.done", true)))
    }
}

