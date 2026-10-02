package com.orgzly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OrgFormatterLogbookTest {

    private val entry =
        "- State \"TODO\"       from \"DONE\"       [2026-10-02 Fri 18:00]"

    @Test
    fun preservesExistingLogbookListIndentation() {
        val content =
            ":LOGBOOK:\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-09-10 Thu 13:07]\n" +
                "   :END:"

        assertEquals(
            ":LOGBOOK:\n" +
                "   $entry\n" +
                "   - State \"DONE\"       from \"TODO\"       [2026-09-10 Thu 13:07]\n" +
                "   :END:",
            OrgFormatter.insertLogbookEntryLine(content, entry),
        )
    }

    @Test
    fun keepsUnindentedLogbookListUnindented() {
        val content =
            ":LOGBOOK:\n" +
                "- State \"DONE\"       from \"TODO\"       [2026-09-10 Thu 13:07]\n" +
                ":END:"

        assertEquals(
            ":LOGBOOK:\n" +
                "$entry\n" +
                "- State \"DONE\"       from \"TODO\"       [2026-09-10 Thu 13:07]\n" +
                ":END:",
            OrgFormatter.insertLogbookEntryLine(content, entry),
        )
    }

    @Test
    fun keepsLegacyFormattingWhenCreatingLogbook() {
        assertEquals(
            ":LOGBOOK:\n$entry\n:END:\n\nBody",
            OrgFormatter.insertLogbookEntryLine("Body", entry),
        )
    }
}
