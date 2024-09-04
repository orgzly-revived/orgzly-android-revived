package com.orgzly.android.ui.books

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.BookFormat
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.Book
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.Repo
import com.orgzly.android.ui.AppBar
import com.orgzly.android.ui.CommonViewModel
import com.orgzly.android.ui.SingleLiveEvent
import com.orgzly.android.usecase.BookCreate
import com.orgzly.android.usecase.BookDelete
import com.orgzly.android.usecase.BookExportToUri
import com.orgzly.android.usecase.BookForceLoad
import com.orgzly.android.usecase.BookForceSave
import com.orgzly.android.usecase.BookImportFromUri
import com.orgzly.android.usecase.BookLinkUpdate
import com.orgzly.android.usecase.BookRename
import com.orgzly.android.usecase.UseCaseResult
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.android.util.LogUtils


class BooksViewModel(private val dataRepository: DataRepository) : CommonViewModel() {
    private val booksParams = MutableLiveData<String>()

    // Book being operated on (deleted, renamed, etc.)
    private var lastBook = MutableLiveData<Pair<Book, BookFormat>>()

    val booksToDeleteEvent: SingleLiveEvent<Set<BookView>> = SingleLiveEvent()
    val bookDeletedEvent: SingleLiveEvent<UseCaseResult> = SingleLiveEvent()
    val bookToRenameEvent: SingleLiveEvent<BookView> = SingleLiveEvent()
    val bookToExportEvent: SingleLiveEvent<Pair<Book, BookFormat>> = SingleLiveEvent()
    val bookExportedEvent: SingleLiveEvent<String> = SingleLiveEvent()
    val setBookLinkRequestEvent: SingleLiveEvent<BookLinkOptions> = SingleLiveEvent()


    enum class ViewState {
        LOADING,
        LOADED,
        EMPTY
    }

    val viewState = MutableLiveData<ViewState>(ViewState.LOADING)

    val data = Transformations.switchMap(booksParams) {
        Transformations.map(dataRepository.getBooksLiveData()) { books ->
            viewState.value = if (books.isNotEmpty()) {
                ViewState.LOADED
            } else {
                ViewState.EMPTY
            }
            books
        }
    }

    val appBar: AppBar = AppBar(mapOf(
        APP_BAR_DEFAULT_MODE to null,
        APP_BAR_SELECTION_MODE to APP_BAR_DEFAULT_MODE))

    /* Triggers querying only if parameters changed. */
    fun refresh(sortOrder: String) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, sortOrder)

        if (booksParams.value != sortOrder) {
            booksParams.value = sortOrder
        }
    }

    fun deleteBooksRequest(bookIds: Set<Long>) {
        val bookViews = bookIds.map { requireNotNull(dataRepository.getBookView(it)) }.toSet()
        App.EXECUTORS.diskIO().execute {
            booksToDeleteEvent.postValue(bookViews)
        }
    }

    fun deleteBooks(bookIds: Set<Long>, deleteLinked: Boolean) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                val result = UseCaseRunner.run(BookDelete(bookIds, deleteLinked))
                bookDeletedEvent.postValue(result)
            }
        }
    }

    fun renameBookRequest(bookId: Long) {
        App.EXECUTORS.diskIO().execute {
            bookToRenameEvent.postValue(dataRepository.getBookView(bookId))
        }
    }

    fun renameBook(book: BookView, name: String) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                UseCaseRunner.run(BookRename(book, name))
            }
        }
    }

    data class BookLinkOptions(
        val bookIds: Set<Long>, val links: List<Repo>, val urls: List<String>, val selected: Int)

    fun setBookLinksRequest(bookIds: Set<Long>) {
        App.EXECUTORS.diskIO().execute {
            if (bookIds.isEmpty()) {
                errorEvent.postValue(Throwable("No books found"))
            } else {
                val repos = dataRepository.getRepos()

                val options = if (repos.isEmpty()) {
                    BookLinkOptions(bookIds, emptyList(), emptyList(), -1)
                } else {
                    if (bookIds.size == 1) {
                        val bookView = dataRepository.getBookView(bookIds.first())
                        val currentLink = bookView?.linkRepo
                        val selectedLink = repos.indexOfFirst {
                            it.url == currentLink?.url
                        }
                        BookLinkOptions(bookIds, repos, repos.map { it.url }, selectedLink)
                    } else {
                        BookLinkOptions(bookIds, repos, repos.map { it.url }, -1)
                    }
                }

                setBookLinkRequestEvent.postValue(options)
            }
        }
    }

    fun setBookLinks(bookIds: Set<Long>, repo: Repo? = null) {
        for (bookId in bookIds) {
            App.EXECUTORS.diskIO().execute {
                catchAndPostError {
                    UseCaseRunner.run(BookLinkUpdate(bookId, repo))
                }
            }
        }
    }

    fun forceSaveBookRequest(bookIds: Set<Long>) {
        for (bookId in bookIds) {
            App.EXECUTORS.diskIO().execute {
                catchAndPostError {
                    UseCaseRunner.run(BookForceSave(bookId))
                }
            }
        }
    }

    fun forceLoadBookRequest(bookIds: Set<Long>) {
        for (bookId in bookIds) {
            App.EXECUTORS.diskIO().execute {
                catchAndPostError {
                    UseCaseRunner.run(BookForceLoad(bookId))
                }
            }
        }
    }

    // User requested notebook export
    fun exportBookRequest(bookId: Long, format: BookFormat) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                val book = dataRepository.getBookOrThrow(bookId)
                lastBook.postValue(Pair(book, format))
                bookToExportEvent.postValue(Pair(book, format))
            }
        }
    }

    fun exportBook(uri: Uri) {
        val (book, format) = lastBook.value ?: return

        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                App.getAppContext().contentResolver.openOutputStream(uri).let { stream ->
                    if (stream != null) {
                        UseCaseRunner.run(BookExportToUri(book.id, stream, format))
                        bookExportedEvent.postValue(uri.toString())
                    } else {
                        errorEvent.postValue(Throwable("Failed to open output stream"))
                    }
                }
            }
        }
    }

    fun createBook(name: String) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                UseCaseRunner.run(BookCreate(name))
            }
        }
    }

    fun importBook(uri: Uri, bookName: String) {
        App.EXECUTORS.diskIO().execute {
            catchAndPostError {
                UseCaseRunner.run(BookImportFromUri(bookName, BookFormat.ORG, uri))
            }
        }
    }

    companion object {
        private val TAG = BooksViewModel::class.java.name

        const val APP_BAR_DEFAULT_MODE = 0
        const val APP_BAR_SELECTION_MODE = 1
    }
}