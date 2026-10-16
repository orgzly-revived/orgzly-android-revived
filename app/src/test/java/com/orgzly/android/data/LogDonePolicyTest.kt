package com.orgzly.android.data

import com.orgzly.org.utils.LogDone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogDonePolicyTest {

    @Test
    fun `no preface defers to the caller`() {
        assertNull(LogDonePolicy.fromPreface(null))
        assertNull(LogDonePolicy.fromPreface(""))
    }

    @Test
    fun `preface without a startup keyword defers to the caller`() {
        assertNull(LogDonePolicy.fromPreface("#+TITLE: Notes\n"))
    }

    /** Tokens governing other logging say nothing about done, so they must not decide. */
    @Test
    fun `startup without a done token defers to the caller`() {
        assertNull(LogDonePolicy.fromPreface("#+STARTUP: overview logrepeat\n"))
    }

    @Test
    fun `done tokens are recognised`() {
        assertEquals(LogDone.NONE, LogDonePolicy.fromPreface("#+STARTUP: nologdone\n"))
        assertEquals(LogDone.TIME, LogDonePolicy.fromPreface("#+STARTUP: logdone\n"))
        assertEquals(LogDone.NOTE, LogDonePolicy.fromPreface("#+STARTUP: lognotedone\n"))
    }

    @Test
    fun `a done token is found among unrelated ones`() {
        assertEquals(
            LogDone.NONE,
            LogDonePolicy.fromPreface("#+STARTUP: overview nologdone logrepeat\n"))
    }

    @Test
    fun `the last token on a line wins`() {
        assertEquals(LogDone.NONE, LogDonePolicy.fromPreface("#+STARTUP: logdone nologdone\n"))
        assertEquals(LogDone.TIME, LogDonePolicy.fromPreface("#+STARTUP: nologdone logdone\n"))
    }

    @Test
    fun `the last of several startup lines wins`() {
        assertEquals(
            LogDone.TIME,
            LogDonePolicy.fromPreface("#+STARTUP: nologdone\n#+STARTUP: logdone\n"))
    }

    @Test
    fun `other keywords in the preface are ignored`() {
        assertEquals(
            LogDone.NONE,
            LogDonePolicy.fromPreface("#+TITLE: Notes\n#+STARTUP: nologdone\n#+FILETAGS: :work:\n"))
    }

    @Test
    fun `an absent logging property defers to the caller`() {
        assertNull(LogDonePolicy.fromLoggingProperty(null))
    }

    /** LOGGING replaces all logging settings, so a value without a done keyword means off. */
    @Test
    fun `a logging property silent on done means off`() {
        assertEquals(LogDone.NONE, LogDonePolicy.fromLoggingProperty("nil"))
        assertEquals(LogDone.NONE, LogDonePolicy.fromLoggingProperty("logrepeat"))
        assertEquals(LogDone.NONE, LogDonePolicy.fromLoggingProperty(""))
        assertEquals(LogDone.NONE, LogDonePolicy.fromLoggingProperty("   "))
    }

    @Test
    fun `a logging property can turn done-logging back on`() {
        assertEquals(LogDone.TIME, LogDonePolicy.fromLoggingProperty("logdone"))
        assertEquals(LogDone.NOTE, LogDonePolicy.fromLoggingProperty("lognotedone"))
        assertEquals(LogDone.NONE, LogDonePolicy.fromLoggingProperty("nologdone"))
    }

    @Test
    fun `the last done token in a logging property wins`() {
        assertEquals(LogDone.NONE, LogDonePolicy.fromLoggingProperty("logdone nologdone"))
        assertEquals(LogDone.TIME, LogDonePolicy.fromLoggingProperty("nologdone logdone"))
    }

    @Test
    fun `a done token is found among other logging tokens`() {
        assertEquals(
            LogDone.TIME, LogDonePolicy.fromLoggingProperty("logrepeat logdone logdrawer"))
    }

    // --- which scope decides ---

    @Test
    fun `a logging property beats the preface`() {
        assertEquals(
            LogDone.TIME,
            LogDonePolicy.resolve("logdone", "#+STARTUP: nologdone", LogDone.NONE))
    }

    @Test
    fun `the preface beats the app setting`() {
        assertEquals(LogDone.NONE, LogDonePolicy.resolve(null, "#+STARTUP: nologdone", LogDone.TIME))
    }

    @Test
    fun `the app setting applies when nothing else says`() {
        assertEquals(LogDone.TIME, LogDonePolicy.resolve(null, "#+TITLE: Notes", LogDone.TIME))
    }

    // --- writing the keyword back into a preface ---

    @Test
    fun `writing into an empty preface creates the line`() {
        assertEquals("#+STARTUP: nologdone", LogDonePolicy.withLogDoneInPreface(null, LogDone.NONE))
        assertEquals("#+STARTUP: logdone", LogDonePolicy.withLogDoneInPreface("", LogDone.TIME))
    }

    @Test
    fun `writing keeps other content and puts the line first`() {
        assertEquals(
            "#+STARTUP: nologdone\n#+TITLE: Notes",
            LogDonePolicy.withLogDoneInPreface("#+TITLE: Notes", LogDone.NONE))
    }

    @Test
    fun `writing replaces an existing done token`() {
        assertEquals(
            "#+STARTUP: nologdone",
            LogDonePolicy.withLogDoneInPreface("#+STARTUP: logdone", LogDone.NONE))
    }

    /** `#+STARTUP:` holds unrelated tokens, so only the done token may change. */
    @Test
    fun `writing preserves unrelated startup tokens`() {
        assertEquals(
            "#+STARTUP: overview logrepeat nologdone",
            LogDonePolicy.withLogDoneInPreface("#+STARTUP: overview logdone logrepeat", LogDone.NONE))
    }

    @Test
    fun `clearing removes only the done token`() {
        assertEquals(
            "#+STARTUP: overview",
            LogDonePolicy.withLogDoneInPreface("#+STARTUP: overview nologdone", null))
    }

    @Test
    fun `clearing drops a line left with nothing`() {
        assertEquals(
            "#+TITLE: Notes",
            LogDonePolicy.withLogDoneInPreface("#+STARTUP: nologdone\n#+TITLE: Notes", null))
    }

    @Test
    fun `writing collapses done tokens spread over several lines`() {
        assertEquals(
            "#+STARTUP: overview\n#+STARTUP: indent logdone",
            LogDonePolicy.withLogDoneInPreface(
                "#+STARTUP: overview logdone\n#+STARTUP: indent nologdone", LogDone.TIME))
    }

    @Test
    fun `writing matches the keyword case-insensitively`() {
        assertEquals(
            "#+startup: nologdone",
            LogDonePolicy.withLogDoneInPreface("#+startup: logdone", LogDone.NONE))
    }

    @Test
    fun `lognotedone survives being written back`() {
        assertEquals(
            "#+STARTUP: lognotedone",
            LogDonePolicy.withLogDoneInPreface("#+STARTUP: lognotedone", LogDone.NOTE))
    }

    @Test
    fun `what is written is what is read back`() {
        for (value in listOf(LogDone.NONE, LogDone.TIME, LogDone.NOTE)) {
            val preface = LogDonePolicy.withLogDoneInPreface("#+TITLE: Notes", value)
            assertEquals(value, LogDonePolicy.fromPreface(preface))
        }
        assertNull(LogDonePolicy.fromPreface(
            LogDonePolicy.withLogDoneInPreface("#+STARTUP: logdone", null)))
    }
}
