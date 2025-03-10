package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StateChangeParentTitleUpdaterTest {
    private val defaultTodoKeywords get() = listOf("TODO")
    private val defaultDoneKeywords get() = listOf("DONE")

    private fun getTarget(
        todoKeywords: Collection<String> = defaultTodoKeywords,
        doneKeywords: Collection<String> = defaultDoneKeywords
    ): StateChangeParentTitleUpdater {
        return StateChangeParentTitleUpdater(todoKeywords, doneKeywords)
    }

    @Test
    fun testTitleIsNotChangedWhenCookiesAreNotPresent() {
        val target = getTarget()
        val title = "This title will not be changed"

        val result = target.updateTitleForStates(
            title = title,
            childStates = defaultTodoKeywords + defaultDoneKeywords
        )

        assertEquals(title, result)
    }

    @Test
    fun testTitleIsNotChangedWhenTotalsHaveNotChanged() {
        val target = getTarget()
        val title = "This title will not be changed [50%] [1/2]"

        val result = target.updateTitleForStates(
            title = title,
            childStates = defaultTodoKeywords + defaultDoneKeywords
        )

        assertEquals(title, result)
    }

    @Test
    fun testTitleWithNewPercentageCookieIsUpdated() {
        val target = getTarget()

        val result = target.updateTitleForStates(
            title = "This title will be changed [%]",
            childStates = defaultTodoKeywords + defaultDoneKeywords
        )

        assertEquals("This title will be changed [50%]", result)
    }

    @Test
    fun testTitleWithNewFractionCookieIsUpdated() {
        val target = getTarget()

        val result = target.updateTitleForStates(
            title = "This title will be changed [/]",
            childStates = defaultTodoKeywords + defaultDoneKeywords
        )

        assertEquals("This title will be changed [1/2]", result)
    }

    @Test
    fun testTitleWithIncorrectPercentageCookieIsUpdated() {
        val target = getTarget()
        val testValues = listOf("0%", "1%", "100%", "1000%")

        for (value in testValues) {
            val result = target.updateTitleForStates(
                title = "This title will be changed [$value]",
                childStates = defaultTodoKeywords + defaultDoneKeywords
            )

            assertEquals("This title will be changed [50%]", result)
        }
    }

    @Test
    fun testTitleWithIncorrectFractionCookieIsUpdated() {
        val target = getTarget()
        val testValues = listOf("0/2", "2/2", "2/1", "200/100", "2/", "/1")

        for (value in testValues) {
            val result = target.updateTitleForStates(
                title = "This title will be changed [$value]",
                childStates = defaultTodoKeywords + defaultDoneKeywords
            )

            assertEquals("This title will be changed [1/2]", result)
        }
    }
}