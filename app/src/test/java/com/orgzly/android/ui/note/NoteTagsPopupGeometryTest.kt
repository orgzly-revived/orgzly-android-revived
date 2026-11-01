package com.orgzly.android.ui.note

import android.app.Application
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NoteTagsPopupGeometryTest {
    @Test
    fun `moving a window does not change the space above its keyboard`() {
        val frame = Rect(0, 0, 800, 1000)
        val root = Rect(frame)
        assertEquals(500, NoteTagsPopup.spaceBelowMenu(frame, root, 300, 200))

        frame.offset(100, 400)
        root.offset(100, 400)
        assertEquals(500, NoteTagsPopup.spaceBelowMenu(frame, root, 300, 600))
    }

    @Test
    fun `a visible frame already excluding the keyboard is not reduced again`() {
        val frame = Rect(100, 400, 900, 1100)
        val root = Rect(100, 400, 900, 1400)
        assertEquals(500, NoteTagsPopup.spaceBelowMenu(frame, root, 300, 600))
    }

    @Test
    fun `without a keyboard the visible frame limits the available space`() {
        val frame = Rect(100, 400, 900, 1300)
        val root = Rect(100, 400, 900, 1400)
        assertEquals(700, NoteTagsPopup.spaceBelowMenu(frame, root, 0, 600))
    }
}
