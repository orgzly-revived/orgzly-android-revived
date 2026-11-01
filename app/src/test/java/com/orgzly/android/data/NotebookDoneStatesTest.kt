package com.orgzly.android.data

import com.orgzly.android.db.dao.BookDao.BookPreface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookDoneStatesTest {

    private val app = setOf("DONE", "CANCELLED")

    private val states = NotebookDoneStates.declaredBy(listOf(
        BookPreface(1, "#+TODO: TODO NEXT | SEEN\n"),
        BookPreface(2, "#+TITLE: No workflow here\n")))

    @Test
    fun `a notebook's own done state is done there`() {
        assertTrue(states.isDone(1, "SEEN", app))
    }

    /** The notebook's workflow replaces the app's, as it does in org. */
    @Test
    fun `an app done state the notebook omits is not done there`() {
        assertFalse(states.isDone(1, "DONE", app))
    }

    @Test
    fun `a notebook declaring nothing uses the app's done states`() {
        assertTrue(states.isDone(2, "DONE", app))
        assertFalse(states.isDone(2, "SEEN", app))
        assertTrue(states.isDone(3, "CANCELLED", app))
    }

    @Test
    fun `todo states and no state are not done`() {
        assertFalse(states.isDone(1, "NEXT", app))
        assertFalse(states.isDone(1, null, app))
        assertFalse(states.isDone(2, null, app))
    }

    /** Lists redraw only when this changes, so equal workflows must compare equal. */
    @Test
    fun `equal declarations compare equal`() {
        val again = NotebookDoneStates.declaredBy(listOf(BookPreface(1, "#+TODO: A | SEEN\n")))
        assertEquals(NotebookDoneStates.declaredBy(listOf(BookPreface(1, "#+TODO: B | SEEN\n"))), again)
        assertEquals(NotebookDoneStates.NONE, NotebookDoneStates.declaredBy(emptyList()))
    }
}
