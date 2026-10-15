package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrgFormatterLogbookTest {

    @Test
    fun testIssueFixtureKeepsThreeSpaceIndentOnNewStateChange() {
        val entry = OrgFormatter.stateChangeLine("TODO", "DONE", "[2026-09-10 Thu 13:07]")
        val content =
                ":LOGBOOK:\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-06-23 Tue 17:03]\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-05-07 Thu 23:31]\n" +
                "   :END:"
        val expected =
                ":LOGBOOK:\n" +
                "   $entry\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-06-23 Tue 17:03]\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-05-07 Thu 23:31]\n" +
                "   :END:"

        assertTrue(entry.startsWith("-"))
        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }

    @Test
    fun testSharedTwoSpaceIndent() {
        val entry = OrgFormatter.stateChangeLine("TODO", "DONE", "[2026-09-10 Thu 13:07]")
        val content =
                "  :LOGBOOK:\n" +
                "  - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "  :END:"
        val expected =
                "  :LOGBOOK:\n" +
                "  $entry\n" +
                "  - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "  :END:"

        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }

    @Test
    fun testUnindentedDrawerKeepsColumnZeroEntry() {
        val entry = OrgFormatter.stateChangeLine("NEXT", "DONE", "[2018-04-16 Mon 09:00]")
        val content =
                ":LOGBOOK:\n" +
                "- State \"DONE\"       from \"NEXT\"       [2018-04-08 Sun 09:00]\n" +
                ":END:\n" +
                "\n" +
                "Content"
        val expected =
                ":LOGBOOK:\n" +
                "$entry\n" +
                "- State \"DONE\"       from \"NEXT\"       [2018-04-08 Sun 09:00]\n" +
                ":END:\n" +
                "\n" +
                "Content"

        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }

    @Test
    fun testNoDrawer() {
        val entry = OrgFormatter.stateChangeLine("NEXT", "DONE", "[2018-04-16 Mon 09:00]")

        assertEquals(
                ":LOGBOOK:\n$entry\n:END:",
                OrgFormatter.insertLogbookEntryLine(null, entry))
        assertEquals(
                ":LOGBOOK:\n$entry\n:END:\n\nbody text",
                OrgFormatter.insertLogbookEntryLine("body text", entry))
    }

    @Test
    fun testEmptyIndentedDrawer() {
        val entry = OrgFormatter.stateChangeLine("TODO", "DONE", "[2026-09-10 Thu 13:07]")
        val content = "  :LOGBOOK:\n  :END:"
        val expected = "  :LOGBOOK:\n  $entry\n  :END:"

        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }

    @Test
    fun testBlankFirstBodyLineUsesIndentedEntry() {
        val entry = OrgFormatter.stateChangeLine("TODO", "DONE", "[2026-09-10 Thu 13:07]")
        val content =
                ":LOGBOOK:\n" +
                "\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "   :END:"
        val expected =
                ":LOGBOOK:\n" +
                "   $entry\n" +
                "\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "   :END:"

        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }

    @Test
    fun testTabIndentedBodyLine() {
        val entry = OrgFormatter.stateChangeLine("TODO", "DONE", "[2026-09-10 Thu 13:07]")
        val content =
                ":LOGBOOK:\n" +
                "\t- State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                ":END:"
        val expected =
                ":LOGBOOK:\n" +
                "\t$entry\n" +
                "\t- State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                ":END:"

        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }

    @Test
    fun testPropertiesDrawerAndParagraphAreUnchanged() {
        val entry = OrgFormatter.stateChangeLine("TODO", "DONE", "[2026-09-10 Thu 13:07]")
        val content =
                ":PROPERTIES:\n" +
                ":ID:       6BAC58E2-0596-459D-8724-FE158DA00DC0\n" +
                ":END:\n" +
                ":LOGBOOK:\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "   :END:\n" +
                "\n" +
                "Paragraph after."
        val expected =
                ":PROPERTIES:\n" +
                ":ID:       6BAC58E2-0596-459D-8724-FE158DA00DC0\n" +
                ":END:\n" +
                ":LOGBOOK:\n" +
                "   $entry\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-07-23 Thu 15:59]\n" +
                "   :END:\n" +
                "\n" +
                "Paragraph after."

        assertEquals(expected, OrgFormatter.insertLogbookEntryLine(content, entry))
    }
}
