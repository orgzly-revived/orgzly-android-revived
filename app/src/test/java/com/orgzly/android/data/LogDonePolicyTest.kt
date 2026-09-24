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
}
