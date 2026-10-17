package com.orgzly.android.ui.notes.book

import android.os.Looper
import com.orgzly.android.AppDataRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookPrefaceViewModelTest {
    @get:Rule
    val data = AppDataRule()

    @Test
    fun testRepeatedSaveIsIgnored() {
        val bookId = data.loadBook("book", "Old preface\n* Note A\n")
        val viewModel = BookPrefaceViewModel()
        var updated = 0
        viewModel.prefaceUpdatedEvent.observeForever { updated++ }

        viewModel.updatePreface(bookId, "First")
        viewModel.updatePreface(bookId, "Second")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("First", data.dataRepository.getBook(bookId)?.preface)
        assertEquals(1, updated)
    }
}
