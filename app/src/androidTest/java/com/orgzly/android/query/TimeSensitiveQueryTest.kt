package com.orgzly.android.query

import androidx.test.espresso.matcher.ViewMatchers
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.query.sql.SqliteQueryBuilder
import com.orgzly.android.query.user.DottedQueryParser
import org.hamcrest.Matchers
import org.junit.Test
import java.util.Calendar

/**
 * Parameterization works poorly for these cases, because when there are many parameters,
 * there may be a too long delay between parameter creation and test run, so that the
 * timestamp is too old when the test is run.
 */
class TimeSensitiveQueryTest : OrgzlyTest() {
    @Test
    fun testScheduledWithinHours1() {
        // Parse query
        val queryString = "s.le.2h"
        val expectedSqlSelection = "((scheduled_is_active = 1 AND scheduled_time_timestamp != 0 AND scheduled_time_timestamp < " + TimeUtils.timeFromNow(
            Calendar.HOUR_OF_DAY, 2+1) + "))"
        val parser = DottedQueryParser()
        val query = parser.parse(queryString)

        // Build SQL
        val sqlBuilder = SqliteQueryBuilder(context)
        val sqlQuery = sqlBuilder.build(query)

        // Build query
        val actualSqlSelection = sqlQuery.selection

        expectedSqlSelection.let {
            ViewMatchers.assertThat(
                queryString,
                actualSqlSelection,
                Matchers.`is`(expectedSqlSelection)
            )
        }
    }

    @Test
    fun testScheduledWithinHours2() {
        // Parse query
        val queryString = "s.le.+2h"
        val expectedSqlSelection = "((scheduled_is_active = 1 AND scheduled_time_timestamp != 0 AND scheduled_time_timestamp < " + TimeUtils.timeFromNow(
            Calendar.HOUR_OF_DAY, 2+1) + "))"
        val parser = DottedQueryParser()
        val query = parser.parse(queryString)

        // Build SQL
        val sqlBuilder = SqliteQueryBuilder(context)
        val sqlQuery = sqlBuilder.build(query)

        // Build query
        val actualSqlSelection = sqlQuery.selection

        expectedSqlSelection.let {
            ViewMatchers.assertThat(
                queryString,
                actualSqlSelection,
                Matchers.`is`(expectedSqlSelection)
            )
        }
    }

    @Test
    fun testClosedRecently() {
        // Parse query
        val queryString = "c.gt.-1h"
        val expectedSqlSelection = "((closed_time_timestamp != 0 AND ${TimeUtils.timeFromNow(Calendar.HOUR_OF_DAY, 0)} <= closed_time_timestamp))"
        val parser = DottedQueryParser()
        val query = parser.parse(queryString)

        // Build SQL
        val sqlBuilder = SqliteQueryBuilder(context)
        val sqlQuery = sqlBuilder.build(query)

        // Build query
        val actualSqlSelection = sqlQuery.selection

        expectedSqlSelection.let {
            ViewMatchers.assertThat(
                queryString,
                actualSqlSelection,
                Matchers.`is`(expectedSqlSelection)
            )
        }
    }
}