package com.orgzly.android.ui.capture

import android.content.Context
import com.orgzly.android.data.DataRepository
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.note.NoteBuilder

/**
 * Resolves a [CaptureTemplate] to a [NotePlace] by looking up the target
 * book and optional headline path from the repository. Creates any
 * missing headings along the path automatically.
 *
 * @return a [Result] containing the [NotePlace] and an optional warning
 *         message when the configured notebook was not found.
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
    ): Result = resolve(context, dataRepository, template, null)

    fun resolve(
        context: Context,
        dataRepository: DataRepository,
        template: CaptureTemplate,
        fallbackBookName: String?
    ): Result {
        val bookName = template.targetBook.ifBlank { fallbackBookName.orEmpty() }
        val headline = normalizeHeadlinePath(template.targetHeadline).orEmpty()

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

        // Try to find existing headline via full path
        val fullPath = "$resolvedBookName/$headline"
        val existingNote = dataRepository.getNoteAtPath(fullPath)

        if (existingNote != null) {
            return Result(NotePlace(bookId, existingNote.note.id, Place.UNDER))
        }

        // Headline not found — create missing headings along the path
        val targetNoteId = ensureHeadlinePath(context, dataRepository, bookId, resolvedBookName, headline)
        return Result(NotePlace(bookId, targetNoteId, Place.UNDER))
    }

    /**
     * Walks each component of [headlinePath] (e.g. "Projects/Active"),
     * creating any headings that don't already exist. Returns the note ID
     * of the final (deepest) heading.
     */
    private fun ensureHeadlinePath(
        context: Context,
        dataRepository: DataRepository,
        bookId: Long,
        bookName: String,
        headlinePath: String
    ): Long {
        val components = normalizeHeadlinePath(headlinePath)
            ?.split("/")
            .orEmpty()
        require(components.isNotEmpty()) {
            "Headline path must contain at least one non-blank component"
        }
        var parentNoteId: Long? = null

        for (i in components.indices) {
            val partialPath = components.subList(0, i + 1).joinToString("/")
            val fullPath = "$bookName/$partialPath"
            val existing = dataRepository.getNoteAtPath(fullPath)

            if (existing != null) {
                parentNoteId = existing.note.id
            } else {
                // Create this heading
                val payload = NoteBuilder.newPayload(context, components[i], null)
                val place = if (parentNoteId != null) {
                    NotePlace(bookId, parentNoteId, Place.UNDER)
                } else {
                    NotePlace(bookId)
                }
                val created = dataRepository.createNote(payload, place)
                parentNoteId = created.id
            }
        }

        return requireNotNull(parentNoteId) {
            "Headline path resolution did not produce a target note"
        }
    }
}
