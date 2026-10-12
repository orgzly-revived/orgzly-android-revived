package com.orgzly.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading a workflow out of a preface. Resolution against the app setting needs a Context and
 * is covered by the instrumented tests.
 */
class BookWorkflowTest {

    private fun todo(preface: String) =
        BookWorkflow.fromPreface(preface)!!.flatMap { it.todoKeywords }

    private fun done(preface: String) =
        BookWorkflow.fromPreface(preface)!!.flatMap { it.doneKeywords }

    @Test
    fun `no preface declares nothing`() {
        assertNull(BookWorkflow.fromPreface(null))
        assertNull(BookWorkflow.fromPreface(""))
    }

    @Test
    fun `a preface without a workflow declares nothing`() {
        assertNull(BookWorkflow.fromPreface("#+TITLE: Notes\n#+FILETAGS: :work:\n"))
    }

    @Test
    fun `keywords are split on the bar`() {
        val preface = "#+TODO: TODO NEXT | DONE CNCL\n"
        assertEquals(listOf("TODO", "NEXT"), todo(preface))
        assertEquals(listOf("DONE", "CNCL"), done(preface))
    }

    /** Org's rule when a workflow has no bar: the last keyword is the done state. */
    @Test
    fun `without a bar the last keyword is done`() {
        val preface = "#+TODO: TODO NEXT DONE\n"
        assertEquals(listOf("TODO", "NEXT"), todo(preface))
        assertEquals(listOf("DONE"), done(preface))
    }

    @Test
    fun `SEQ_TODO and TYP_TODO are workflows too`() {
        assertEquals(listOf("A"), todo("#+SEQ_TODO: A | B\n"))
        assertEquals(listOf("C"), todo("#+TYP_TODO: C | D\n"))
    }

    @Test
    fun `several workflow lines are all read`() {
        val preface = "#+TODO: TODO | DONE\n#+TODO: BUG | FIXED\n"
        assertEquals(listOf("TODO", "BUG"), todo(preface))
        assertEquals(listOf("DONE", "FIXED"), done(preface))
    }

    // --- states shared by several notebooks ---

    @Test
    fun `shared states keep the order of the first list`() {
        assertEquals(
            listOf("NEXT", "DONE"),
            BookWorkflow.shared(listOf(listOf("TODO", "NEXT", "DONE"), listOf("DONE", "NEXT"))))
    }

    @Test
    fun `no lists share nothing`() {
        assertEquals(emptyList<String>(), BookWorkflow.shared(emptyList()))
    }
}
