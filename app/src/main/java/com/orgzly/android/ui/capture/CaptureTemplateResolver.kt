package com.orgzly.android.ui.capture

import android.content.Context
import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place

/**
 * Resolves a [CaptureTemplate] to a [NotePlace] by looking up the target
 * book and optional headline path from the repository.
 *
 * @return a [Result] containing the [NotePlace] and an optional warning
 *         message when the configured headline was not found.
 */
object CaptureTemplateResolver {

    data class Result(
        val notePlace: NotePlace,
        val warning: String? = null
    )

    fun resolve(
        context: Context,
        dataRepository: DataRepository,
        template: CaptureTemplate
    ): Result {
        val bookName = template.targetBook
        val headline = template.targetHeadline.orEmpty().trim()

        val book = if (bookName.isNotBlank()) {
            dataRepository.getBook(bookName)
        } else {
            null
        }

        // Book configured but not found
        if (book == null && bookName.isNotBlank()) {
            return Result(
                notePlace = NotePlace(dataRepository.getTargetBook(context).book.id),
                warning = "notebook_not_found"
            )
        }

        val bookId = book?.id ?: dataRepository.getTargetBook(context).book.id
        val resolvedBookName = book?.name ?: dataRepository.getTargetBook(context).book.name

        // No headline configured — place at book root
        if (headline.isBlank()) {
            return Result(NotePlace(bookId))
        }

        // Resolve headline via full path: "bookName/Heading1/Heading2"
        val fullPath = "$resolvedBookName/$headline"
        val targetNote = dataRepository.getNoteAtPath(fullPath)

        return if (targetNote != null) {
            Result(NotePlace(bookId, targetNote.note.id, Place.UNDER))
        } else {
            Result(
                notePlace = NotePlace(bookId),
                warning = "headline_not_found"
            )
        }
    }
}
