package com.orgzly.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.SharingShortcutsManager
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.logs.AppLogsRepository
import com.orgzly.android.db.entity.BookAction
import com.orgzly.android.db.entity.SyncResult
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.reminders.RemindersScheduler
import com.orgzly.android.repos.DirectoryRepo
import com.orgzly.android.repos.RepoType
import com.orgzly.android.repos.RepoUtils
import com.orgzly.android.repos.SyncRepo
import com.orgzly.android.repos.TwoWaySyncRepo
import com.orgzly.android.ui.notifications.SyncNotifications
import com.orgzly.android.ui.util.haveNetworkConnection
import com.orgzly.android.util.AppPermissions
import com.orgzly.android.util.LogMajorEvents
import com.orgzly.android.util.LogUtils
import com.orgzly.android.widgets.ListWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import javax.inject.Inject

class SyncWorker(val context: Context, val params: WorkerParameters) :
    CoroutineWorker(context, params) {

    @Inject
    lateinit var dataRepository: DataRepository

    @Inject
    lateinit var appLogs: AppLogsRepository

    override suspend fun doWork(): Result {
        App.appComponent.inject(this)

        val state = try {
            tryDoWork()

        } catch (e: CancellationException) {
            updateBooksStatusToCanceled()
            SyncState.getInstance(SyncState.Type.CANCELED)

        } catch (e: Exception) {
            SyncState.getInstance(SyncState.Type.FAILED_EXCEPTION, e.localizedMessage)
        }

        val result = if (state.isFailure()) {
            Result.failure(state.toData())
        } else {
            Result.success(state.toData())
        }

        SyncNotifications.cancelSyncInProgressNotification(context)
        showNotificationOnFailures(state)

        if (BuildConfig.LOG_DEBUG)
            LogUtils.d(TAG, "Worker ${javaClass.simpleName} finished: $result")

        return result
    }

    private fun updateBooksStatusToCanceled() {
        dataRepository.updateBooksStatusToCanceled()
    }

    private fun showNotificationOnFailures(state: SyncState) {
        if (AppPreferences.showSyncNotifications(context)) {
            val msg = if (state.isFailure()) {
                // Whole thing failed
                state.getDescription(context)
            } else {
                // Perhaps some books failed?
                messageIfBooksFailed(dataRepository)
            }

            if (msg != null) {
                SyncNotifications.showSyncFailedNotification(context, msg)
            }
        }
    }

    private suspend fun tryDoWork(): SyncState {
        SyncNotifications.cancelSyncFailedNotification(context)
        SyncNotifications.showSyncInProgressNotification(context)

        sendProgress(SyncState.getInstance(SyncState.Type.STARTING))

        checkConditions()?.let { return it }

        val syncStartTime = System.currentTimeMillis()

        syncRepos()?.let { return it }

        RemindersScheduler.notifyDataSetChanged(App.getAppContext())
        ListWidgetProvider.notifyDataSetChanged(App.getAppContext())
        SharingShortcutsManager().replaceDynamicShortcuts(App.getAppContext())

        val syncEndTime = System.currentTimeMillis()

        // Save last successful sync time to preferences
        AppPreferences.lastSuccessfulSyncTime(context, syncEndTime)

        if (LogMajorEvents.isEnabled()) {
            val syncDuration = (syncEndTime - syncStartTime)
            val numberOfRepos = dataRepository.getRepos().size
            val numberOfBooks = dataRepository.getBooks().size
            appLogs.log(
                LogMajorEvents.SYNC,
                "Sync took $syncDuration milliseconds. Synced $numberOfBooks books in $numberOfRepos repos."
            )
        }

        return SyncState.getInstance(SyncState.Type.FINISHED)
    }

    private fun messageIfBooksFailed(dataRepository: DataRepository): String? {
        val books = dataRepository.getBooksWithError()

        val sb = StringBuilder().apply {
            for (book in books) {
                book.lastAction?.let { action ->
                    append(book.name).append(": ").append(action.message).append("\n")
                }
            }
        }

        return sb.toString().trim().ifEmpty { null }
    }

    private fun checkConditions(): SyncState? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        val autoSync = params.inputData.getBoolean(SyncRunner.IS_AUTO_SYNC, false)

        val repos = dataRepository.getSyncRepos()

        /* Do nothing if it's auto-sync and there are no repos or they require connection. */
        if (autoSync) {
            if (repos.isEmpty() || !RepoUtils.isAutoSyncSupported(repos)) {
                return SyncState.getInstance(SyncState.Type.AUTO_SYNC_NOT_STARTED)
            }
        }

        /* There are no repositories configured. */
        if (repos.isEmpty()) {
            return SyncState.getInstance(SyncState.Type.FAILED_NO_REPOS)
        }

        /* If one of the repositories requires internet connection, check for it. */
        if (RepoUtils.isConnectionRequired(repos) && !context.haveNetworkConnection()) {
            return SyncState.getInstance(SyncState.Type.FAILED_NO_CONNECTION)
        }

        /* Make sure we have permission to access local storage,
         * if there are repositories that would use it.
         */
        if (reposRequireStoragePermission(repos)) {
            if (!AppPermissions.isGranted(context, AppPermissions.Usage.SYNC_START)) {
                return SyncState.getInstance(SyncState.Type.FAILED_NO_STORAGE_PERMISSION)
            }
        }

        return null
    }

    private suspend fun syncRepos(): SyncState? {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)

        sendProgress(SyncState.getInstance(SyncState.Type.COLLECTING_BOOKS))

        /* Get the list of local and remote books from all repositories.
         * Group them by name.
         * Inserts dummy books if they don't exist in database.
         */
        val namesakes = withContext(Dispatchers.IO) {
            SyncUtils.groupAllNotebooksByName(dataRepository)
        }

        if (isStopped) {
            return SyncState.getInstance(SyncState.Type.CANCELED)
        }

        if (namesakes.isEmpty()) {
            return SyncState.getInstance(SyncState.Type.FAILED_NO_BOOKS_FOUND)
        }

        sendProgress(SyncState.getInstance(SyncState.Type.BOOKS_COLLECTED, total = namesakes.size))

        /* Because android sometimes drops milliseconds on reported file lastModified,
         * wait until the next full second
         */
        //            if (isTriggeredAutomatically) {
        //                long now = System.currentTimeMillis();
        //                long nowMsPart = now % 1000;
        //                SystemClock.sleep(1000 - nowMsPart);
        //            }

        /* If there are namesakes in Git repos with conflict status, make
         * sure to sync them first, so that any conflict branches are
         * created as early as possible. Otherwise, we risk committing
         * changes on master which we cannot see on the conflict branch.
         */
        val orderedNamesakes = LinkedHashMap<String, BookNamesake>()
        val lowPriorityNamesakes = LinkedHashMap<String, BookNamesake>()
        for (namesake in namesakes.values) {
            if (namesake.rooks.isNotEmpty() &&
                namesake.rooks[0].repoType == RepoType.GIT &&
                namesake.status == BookSyncStatus.CONFLICT_BOTH_BOOK_AND_ROOK_MODIFIED
            ) {
                orderedNamesakes[namesake.name] = namesake
            } else {
                lowPriorityNamesakes[namesake.name] = namesake
            }
        }
        orderedNamesakes.putAll(lowPriorityNamesakes)

        /*
         * Update books' statuses, before starting to sync them.
         */
        for (namesake in orderedNamesakes.values) {
            dataRepository.setBookLastActionAndSyncStatus(namesake.book.book.id, BookAction.forNow(
                BookAction.Type.PROGRESS, context.getString(R.string.syncing_in_progress)))
        }

        /*
         * Start syncing name by name.
         */
        for ((curr, namesake) in orderedNamesakes.values.withIndex()) {
            /* If task has been canceled, just mark the remaining books as such. */
            if (isStopped) {
                dataRepository.setBookLastActionAndSyncStatus(
                    namesake.book.book.id,
                    BookAction.forNow(BookAction.Type.INFO, context.getString(R.string.canceled)))

            } else {
                sendProgress(SyncState.getInstance(
                    SyncState.Type.BOOK_STARTED, namesake.name, curr, namesakes.size))

                val originalStatus = namesake.status
                try {
                    val resultingAction = SyncUtils.syncNamesake(dataRepository, namesake)

                    dataRepository.setBookLastActionAndSyncStatus(
                        namesake.book.book.id,
                        resultingAction,
                        namesake.status.toString())

                    // Determine and update specific SyncResult
                    val syncResult = when (resultingAction.type) {
                        BookAction.Type.INFO -> SyncResult.SUCCESS // Includes NO_CHANGE implicitly
                        BookAction.Type.ERROR -> {
                            // Check original status to differentiate CONFLICT from other ERRORs
                            when (originalStatus) {
                                BookSyncStatus.CONFLICT_BOTH_BOOK_AND_ROOK_MODIFIED,
                                BookSyncStatus.CONFLICT_BOOK_WITH_LINK_AND_ROOK_BUT_NEVER_SYNCED_BEFORE,
                                BookSyncStatus.CONFLICT_LAST_SYNCED_ROOK_AND_LATEST_ROOK_ARE_DIFFERENT -> SyncResult.CONFLICT
                                else -> SyncResult.ERROR
                            }
                        }
                        else -> null // Ignore PROGRESS type for final result
                    }
                    if (syncResult != null) {
                        dataRepository.updateLastSyncActionResult(namesake.book.book.id, syncResult, resultingAction.timestamp)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    dataRepository.setBookLastActionAndSyncStatus(
                        namesake.book.book.id,
                        BookAction.forNow(BookAction.Type.ERROR, e.message.orEmpty()))
                    // Also record the specific sync error result.
                    // OK using "Merge conflict" is a little hacky, but this might just be a small edge case from github merge
                    val syncResult = if (e.message?.contains("Merge conflict") == true) SyncResult.CONFLICT else SyncResult.ERROR
                    dataRepository.updateLastSyncActionResult(namesake.book.book.id, syncResult, System.currentTimeMillis())
                }

                sendProgress(SyncState.getInstance(
                    SyncState.Type.BOOK_ENDED, namesake.name, curr + 1, namesakes.size))
            }
        }

        if (isStopped) {
            return SyncState.getInstance(SyncState.Type.CANCELED)
        }

        val repos = dataRepository.getSyncRepos()

        for (repo in repos) {
            if (repo is TwoWaySyncRepo) {
                repo.tryPushIfHeadDiffersFromRemote()
            }
        }

        return null
    }

    // TODO: Remove or repo.requiresStoragePermission
    private fun reposRequireStoragePermission(repos: Collection<SyncRepo>): Boolean {
        for (repo in repos) {
            if (DirectoryRepo.SCHEME == repo.uri.scheme) {
                return true
            }
        }
        return false
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG)
        return SyncNotifications.syncInProgressForegroundInfo(context)
    }

    private suspend fun sendProgress(state: SyncState) {
        setProgress(state.toData())
    }

    companion object {
        private val TAG: String = SyncWorker::class.java.name
    }
}
