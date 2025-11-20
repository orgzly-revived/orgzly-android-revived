package com.orgzly.android.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.util.LogUtils
import javax.inject.Inject

/**
 * WorkManager worker that triggers scheduled auto-sync at regular intervals.
 */
class ScheduledSyncWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    @Inject
    lateinit var autoSync: AutoSync

    override suspend fun doWork(): Result {
        App.appComponent.inject(this)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Scheduled sync triggered")

        // Check if scheduled sync is still enabled
        if (AppPreferences.scheduledSyncEnabled(context)) {
            autoSync.trigger(AutoSync.Type.SCHEDULED)
            return Result.success()
        } else {
            // If disabled, don't reschedule (WorkManager will handle this)
            if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, "Scheduled sync disabled, skipping")
            return Result.success()
        }
    }

    companion object {
        private val TAG: String = ScheduledSyncWorker::class.java.name
    }
}