package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StateChangeParentTitleUpdaterTest {

    @Test
    fun divisionByZero_whenChildrenAreNotTodos() {
        val updater = StateChangeParentTitleUpdater(listOf("TODO"), listOf("DONE"))

        assertEquals("Test [0%]", updater.updateTitleForChildStates("Test [0%]", listOf(null, null)))
        assertEquals("Test [0/0]", updater.updateTitleForChildStates("Test [/]", listOf(null, null)))
    }
}
