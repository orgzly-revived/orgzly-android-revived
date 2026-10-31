package com.orgzly.android.query

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orgzly.android.data.BookWorkflow
import com.orgzly.android.db.dao.BookDao
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.query.sql.SqliteQueryBuilder
import com.orgzly.android.query.user.DottedQueryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A state-type condition asks the note's own notebook, so the SQL has to carry one branch per
 * notebook that declares a workflow, and bind their keywords in the order the branches appear.
 */
@RunWith(AndroidJUnit4::class)
class BookWorkflowQueryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppPreferences.states(context, "TODO NEXT | DONE")
    }

    private fun sql(query: String, vararg prefaces: Pair<Long, String>): Pair<String, List<String>> {
        val declared = BookWorkflow.declaredBy(
            prefaces.map { BookDao.BookPreface(it.first, it.second) })

        val built = SqliteQueryBuilder(context, declared).build(DottedQueryParser().parse(query))

        return built.selection to built.selectionArgs
    }

    @Test
    fun `without any declared workflow the condition stays a flat list`() {
        val (selection, args) = sql("it.done")

        assertFalse(selection.contains("CASE"))
        assertEquals(listOf("DONE"), args)
    }

    @Test
    fun `a declared workflow adds a branch for its notebook`() {
        val (selection, args) = sql("it.done", 7L to "#+TODO: NEXT | CNCL\n")

        assertTrue(selection.contains("CASE book_id"))
        assertTrue(selection.contains("WHEN 7 THEN"))
        // The notebook's own done keyword binds first, then the fallback for every other one.
        assertEquals(listOf("CNCL", "DONE"), args)
    }

    @Test
    fun `the todo side is resolved the same way`() {
        val (_, args) = sql("it.todo", 7L to "#+TODO: NEXT | CNCL\n")

        assertEquals(listOf("NEXT", "TODO", "NEXT"), args)
    }

    /** Replacement, not addition: the app's DONE must not match inside that notebook. */
    @Test
    fun `a declared workflow does not keep the configured states for its notebook`() {
        val (selection, _) = sql("it.done", 7L to "#+TODO: NEXT | CNCL\n")

        val branch = selection.substringAfter("WHEN 7 THEN").substringBefore("ELSE")
        assertEquals(1, branch.count { it == '?' })
    }

    @Test
    fun `notebooks declaring nothing get no branch`() {
        val (selection, args) = sql(
            "it.done",
            7L to "#+TODO: NEXT | CNCL\n",
            9L to "#+TITLE: Plain\n")

        assertTrue(selection.contains("WHEN 7 THEN"))
        assertFalse(selection.contains("WHEN 9 THEN"))
        assertEquals(listOf("CNCL", "DONE"), args)
    }

    @Test
    fun `each declaring notebook gets its own branch`() {
        val (selection, args) = sql(
            "it.done",
            7L to "#+TODO: NEXT | CNCL\n",
            9L to "#+TODO: BUG | FIXED\n")

        assertTrue(selection.contains("WHEN 7 THEN"))
        assertTrue(selection.contains("WHEN 9 THEN"))
        assertEquals(listOf("CNCL", "FIXED", "DONE"), args)
    }
}
