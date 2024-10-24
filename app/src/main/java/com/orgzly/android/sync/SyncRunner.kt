package com.orgzly.android.sync

import android.content.Intent
import androidx.core.content.ContextCompat.startActivity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.ui.repos.ReposActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.util.LogUtils
import java.util.concurrent.TimeUnit

object SyncRunner {
    const val IS_AUTO_SYNC = "auto-sync"

    private val TAG: String = SyncRunner::class.java.name

    private const val UNIQUE_WORK_NAME = "sync"

    @JvmStatic
    fun startAuto() {
        startSync(true)
    }

    @JvmStatic
    @JvmOverloads
    fun startSync(autoSync: Boolean = false) {
        val workManager = WorkManager.getInstance(App.getAppContext())


        // There is a bug in WorkManager, documented here https://issuetracker.google.com/issues/115575872,
        // due to which an ACTION_UPDATE intent is sent to AppWidgetProviders when the last worker finishes.
        // This leads to an ugly flickering and resetting of the scroll position when using the Orgzly widget,
        // especially in th case of marking a task done, while having auto update activated.
        // The provided solution is to schedule a worker in the "infinite" future, thus preventing the case
        // of the last worker finishing.
        val infiniteScheduledWorkerToPreventWidgetUpdate = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(10 * 365, TimeUnit.DAYS)
            .build()

        workManager.beginUniqueWork(
            "infiniteScheduledWorkerToPreventWidgetUpdate",
            ExistingWorkPolicy.REPLACE,
            infiniteScheduledWorkerToPreventWidgetUpdate
        ).enqueue()

        // Enqueue the sync worker
        val syncWorker = OneTimeWorkRequestBuilder<SyncWorker>()
            // On Android >= 12 notification from overridden getForegroundInfo might not be shown
            // Sync-in-progress notification cannot be canceled if app is killed by the system,
            // when handling notification manually from the worker.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(IS_AUTO_SYNC to autoSync))
            .build()

        workManager
            .beginUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, syncWorker)
            .enqueue()
    }

    @JvmStatic
    fun showSyncFailedSnackBar(activity: FragmentActivity, state: SyncState) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, activity, state)

        val msg = state.getDescription(activity)

        activity.showSnackbar(msg, R.string.repositories) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClass(activity, ReposActivity::class.java)
            }
            startActivity(activity, intent, null)
        }
    }

    @JvmStatic
    fun stopSync() {
        val workManager = WorkManager.getInstance(App.getAppContext())
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    @JvmStatic
    fun onStateChange(tag: String): LiveData<SyncState?> {
        return onAllWorkInfo().map { workInfoList ->
            syncStateFromWorkInfoList(workInfoList).also { state ->
                logStateChange(tag, state, workInfoList)
            }
        }

//        return MediatorLiveData<SyncState>().apply {
//            addSource(onInitWorkInfo()) {
//                value = state
//            }
//
//            addSource(onMainWorkInfo()) {
//                value = state
//            }
//        }
    }

    private fun logStateChange(tag: String, state: SyncState?, workInfoList: List<WorkInfo>?) {
        if (BuildConfig.LOG_DEBUG) {
            // LogUtils.d(TAG, "-> ($tag) Workers changed state to $state <- $workInfoList")
            LogUtils.d(
                TAG,
                "-> ($tag) Workers changed state to $state <- ${workInfoList?.map { it.state }}"
            )
        }
    }

    private fun onAllWorkInfo(): LiveData<List<WorkInfo>> {
        return WorkManager.getInstance(App.getAppContext())
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_WORK_NAME)
    }

    private fun syncStateFromWorkInfoList(workInfoList: List<WorkInfo>): SyncState? {
        if (workInfoList.isEmpty()) {
            return null
        }

        val oneAndOnlyWorker = workInfoList.first()

        oneAndOnlyWorker.run {
            if (state == WorkInfo.State.CANCELLED) {
                return SyncState.getInstance(SyncState.Type.CANCELED)

            } else if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                return SyncState.fromData(progress)
                    ?: SyncState.getInstance(SyncState.Type.STARTING)

            } else if (state.isFinished) {
                return SyncState.fromData(outputData)
            }
        }

        return null
    }
}