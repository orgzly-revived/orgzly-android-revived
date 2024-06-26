package com.orgzly.android.sync

import com.orgzly.R
import com.orgzly.android.App

// TODO: Write tests for *all* cases.
enum class BookSyncStatus {
    NO_CHANGE,

    BOOK_WITHOUT_LINK_AND_ONE_OR_MORE_ROOKS_EXIST,
    DUMMY_WITHOUT_LINK_AND_MULTIPLE_ROOKS,
    NO_BOOK_MULTIPLE_ROOKS, // TODO: This should never be the case, as we already add dummy (dummy = there was no book)
    ONLY_BOOK_WITHOUT_LINK_AND_MULTIPLE_REPOS,
    BOOK_WITH_LINK_AND_ROOK_EXISTS_BUT_LINK_POINTING_TO_DIFFERENT_ROOK,
    ONLY_DUMMY,
    ROOK_AND_VROOK_HAVE_DIFFERENT_REPOS,
    BOOK_WITH_PREVIOUS_ERROR_AND_NO_LINK,

    /* Conflict. */
    CONFLICT_BOTH_BOOK_AND_ROOK_MODIFIED,
    CONFLICT_BOOK_WITH_LINK_AND_ROOK_BUT_NEVER_SYNCED_BEFORE,
    CONFLICT_LAST_SYNCED_ROOK_AND_LATEST_ROOK_ARE_DIFFERENT,

    /* Book can be loaded. */
    NO_BOOK_ONE_ROOK, // TODO: Can this happen? We always load dummy.
    DUMMY_WITHOUT_LINK_AND_ONE_ROOK,
    BOOK_WITH_LINK_AND_ROOK_MODIFIED,
    DUMMY_WITH_LINK,

    /* Book can be saved. */
    ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO,
    BOOK_WITH_LINK_LOCAL_MODIFIED,
    ONLY_BOOK_WITH_LINK,

    /* Previously synced remote book no longer exists. */
    ROOK_NO_LONGER_EXISTS;

    // TODO: Extract string resources
    @JvmOverloads
    fun msg(arg: Any = ""): String {
        val context = App.getAppContext()
        when (this) {
            NO_CHANGE ->
                return context.getString(R.string.sync_status_no_change)

            BOOK_WITHOUT_LINK_AND_ONE_OR_MORE_ROOKS_EXIST ->
                return context.getString(R.string.sync_status_book_without_link_and_one_or_more_rooks_exist)

            DUMMY_WITHOUT_LINK_AND_MULTIPLE_ROOKS ->
                return context.getString(R.string.sync_status_dummy_without_link_and_multiple_rooks)

            NO_BOOK_MULTIPLE_ROOKS ->
                return context.getString(R.string.sync_status_no_book_multiple_rooks)

            ONLY_BOOK_WITHOUT_LINK_AND_MULTIPLE_REPOS ->
                return context.getString(R.string.sync_status_no_link_and_multiple_repos)

            BOOK_WITH_LINK_AND_ROOK_EXISTS_BUT_LINK_POINTING_TO_DIFFERENT_ROOK ->
                return "Notebook has link and remote notebook with the same name exists, but link is pointing to a different remote notebook which does not exist"

            ONLY_DUMMY ->
                return "Only local dummy exists"

            ROOK_AND_VROOK_HAVE_DIFFERENT_REPOS ->
                return "Linked and synced notebooks have different repositories"

            BOOK_WITH_PREVIOUS_ERROR_AND_NO_LINK ->
                return context.getString(R.string.sync_status_no_link_and_previous_error)

            CONFLICT_BOTH_BOOK_AND_ROOK_MODIFIED ->
                return "Both local and remote notebook have been modified"

            CONFLICT_BOOK_WITH_LINK_AND_ROOK_BUT_NEVER_SYNCED_BEFORE ->
                return "Link and remote notebook exist but notebook hasn't been synced before"

            CONFLICT_LAST_SYNCED_ROOK_AND_LATEST_ROOK_ARE_DIFFERENT ->
                return "Last synced notebook and latest remote notebook differ"

            NO_BOOK_ONE_ROOK, DUMMY_WITHOUT_LINK_AND_ONE_ROOK, BOOK_WITH_LINK_AND_ROOK_MODIFIED, DUMMY_WITH_LINK ->
                return context.getString(R.string.sync_status_loaded, "$arg")

            ONLY_BOOK_WITHOUT_LINK_AND_ONE_REPO, BOOK_WITH_LINK_LOCAL_MODIFIED, ONLY_BOOK_WITH_LINK ->
                return context.getString(R.string.sync_status_saved, "$arg")

            ROOK_NO_LONGER_EXISTS ->
                return context.getString(R.string.sync_status_rook_no_longer_exists)

            else ->
                throw IllegalArgumentException("Unknown sync status " + this)
        }
    }
}
