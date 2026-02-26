package com.orgzly.android.ui.util

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.orgzly.android.db.entity.Note
import com.orgzly.android.db.entity.NoteView
import com.orgzly.android.db.entity.isNotEmpty
import com.orgzly.android.db.entity.toList
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.util.OrgFormatter

class TitleGenerator(
    private val context: Context,
    private val inBook: Boolean,
    private val attributes: TitleAttributes
) {
    fun generateTitle(noteView: NoteView): CharSequence {
        val note = noteView.note

        val builder = SpannableStringBuilder()

        /* State. */
        if (note.state != null) {
            builder.append(generateState(note))
        }

        /* Priority. */
        if (note.priority != null) {
            if (builder.isNotEmpty()) {
                builder.append(TITLE_SEPARATOR)
            }
            builder.append(generatePriority(note))
        }

        /* Bold everything up until now. */
        if (builder.isNotEmpty()) {
            builder.setSpan(StyleSpan(Typeface.BOLD), 0, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        /* Space before title, unless there's nothing added. */
        if (builder.isNotEmpty()) {
            builder.append(TITLE_SEPARATOR)
        }

        /* Title. */
        builder.append(OrgFormatter.parse(note.title, context, true, false))

        /* Append note ID. */
        // builder.append(TITLE_SEPARATOR).append("#").append(note.id.toString())

        val mark = builder.length

        var hasPostTitleText = false

        /* Tags. */
        if (note.tags.isNotEmpty()) {
            builder.append(TITLE_SEPARATOR).append(generateTags(note.tags.toList()))
            hasPostTitleText = true
        }

        /* Inherited tags in search results. */
        if (!inBook && noteView.hasInheritedTags() && AppPreferences.inheritedTagsInSearchResults(context)) {
            if (note.tags.isNotEmpty()) {
                builder.append(INHERITED_TAGS_SEPARATOR)
            } else {
                builder.append(TITLE_SEPARATOR)
            }
            builder.append(generateTags(noteView.getInheritedTagsList()))
            hasPostTitleText = true
        }

        /* Content line number. */
        if (note.hasContent() && AppPreferences.contentLineCountDisplayed(context)) {
            if (!shouldDisplayContent(note)) {
                builder.append(TITLE_SEPARATOR).append(note.contentLineCount.toString())
                hasPostTitleText = true
            }
        }

        /* Change font style of text after title. */
        if (hasPostTitleText) {
            builder.setSpan(attributes.postTitleTextSize, mark, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(attributes.postTitleTextColor, mark, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return builder
    }

    /**
     * Should note's content be displayed if it exists.
     */
    fun shouldDisplayContent(note: Note): Boolean {
        var display = true

        if (AppPreferences.isNotesContentDisplayedInList(context)) { // Content could be displayed in list
            if (inBook) { // In book, folded
                if (AppPreferences.isNotesContentFoldable(context) && note.position.isFolded) {
                    display = false
                }
            } else { // In search results, not displaying content
                if (!AppPreferences.isNotesContentDisplayedInSearch(context)) {
                    display = false
                }

                if (AppPreferences.isSearchFoldable(context) && note.position.isFolded) {
                    display = false
                }
            }

        } else { // Never displaying content in list
            display = false
        }

        return display
    }

    private fun generateTags(tags: List<String>): CharSequence {
        return SpannableString(TextUtils.join(TAGS_SEPARATOR, tags))
    }

    private fun generateState(note: Note): CharSequence {
        val str = SpannableString(note.state)

        val color = if (AppPreferences.doneKeywordsSet(context).contains(note.state)) {
            attributes.colorDone
        } else {
            attributes.colorTodo
        }

        str.setSpan(color, 0, str.length, 0)

        return str
    }

    private fun generatePriority(note: Note): CharSequence {
        return "#${note.priority}"
    }

    class TitleAttributes(
        colorTodo: Int,
        colorDone: Int,
        postTitleTextSize: Int,
        postTitleTextColor: Int
    ) {
        val colorTodo: ForegroundColorSpan = ForegroundColorSpan(colorTodo)
        val colorDone: ForegroundColorSpan = ForegroundColorSpan(colorDone)
        val postTitleTextSize: AbsoluteSizeSpan = AbsoluteSizeSpan(postTitleTextSize)
        val postTitleTextColor: ForegroundColorSpan = ForegroundColorSpan(postTitleTextColor)
    }

    companion object {
        /* Separator for heading parts (state, priority, title, tags). */
        private const val TITLE_SEPARATOR = "  "

        /*
         * Separator between note's tags and inherited tags.
         * Not used if note doesn't have its own tags.
         */
        private const val INHERITED_TAGS_SEPARATOR = " • "

        /* Separator for individual tags. */
        private const val TAGS_SEPARATOR = " "
    }
}
