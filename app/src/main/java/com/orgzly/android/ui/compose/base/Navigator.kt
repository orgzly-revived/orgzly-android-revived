package com.orgzly.android.ui.compose.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentManager
import com.orgzly.android.ui.DisplayManager
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.compose.providers.DirectFragmentManagerAccess
import com.orgzly.android.ui.compose.providers.currentFragmentManager

sealed interface NavigationDestination {
    data object SavedSearches: NavigationDestination

    data object Books: NavigationDestination

    data class Book(
        val bookId: Long,
        val noteId: Long? = null
    ): NavigationDestination

    data class NewNote(
        val target: NotePlace
    ): NavigationDestination

    data class Note(
        val bookId: Long,
        val noteId: Long
    ): NavigationDestination

    data class Query(
        val query: String,
        val searchName: String?
    ): NavigationDestination

    data class Editor(
        val book: com.orgzly.android.db.entity.Book
    ): NavigationDestination

}

// Acts as a more "compose-y" abstraction on top of DisplayManager & with a view towards a possible
// migration to a different navigation framework
interface Navigator {

    fun canPop(): Boolean
    fun pop()
    fun navigate(destination: NavigationDestination)

}

private class DefaultNavigator(
    private val fragmentManager: FragmentManager
): Navigator {

    override fun canPop(): Boolean {
        return fragmentManager.backStackEntryCount > 0
    }

    override fun pop() {
        fragmentManager.popBackStack()
    }

    override fun navigate(destination: NavigationDestination) {
        when (destination) {
            is NavigationDestination.Books -> DisplayManager.displayBooks(fragmentManager, true)
            is NavigationDestination.SavedSearches -> DisplayManager.displaySavedSearches(fragmentManager)
            is NavigationDestination.Book -> DisplayManager.displayBook(
                fragmentManager,
                destination.bookId,
                destination.noteId ?: 0
            )
            is NavigationDestination.NewNote -> DisplayManager.displayNewNote(
                fragmentManager,
                destination.target
            )
            is NavigationDestination.Note -> DisplayManager.displayExistingNote(
                fragmentManager,
                destination.bookId,
                destination.noteId
            )
            is NavigationDestination.Query -> DisplayManager.displayQuery(
                fragmentManager,
                destination.query,
                destination.searchName
            )
            is NavigationDestination.Editor -> DisplayManager.displayEditor(
                fragmentManager,
                destination.book
            )
        }
    }

}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator provided") }

@OptIn(DirectFragmentManagerAccess::class)
@Composable
fun createNavigator(): Navigator? {
    val fragmentManager = currentFragmentManager()
    return remember(fragmentManager) {
        DefaultNavigator(fragmentManager ?: return@remember null)
    }
}
