package com.orgzly.android.sync

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.orgzly.android.data.logs.AppLogsRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.util.LogMajorEvents
import org.joda.time.DateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AutoSyncScheduler @Inject constructor(val context: Application, val logs: AppLogsRepository) {

    companion object {
        private const val WORK_NAME = "scheduled-auto-sync"

        fun schedule(context: Context) {
            val intervalInMins = AppPreferences.scheduledSyncIntervalInMins(context).toLong()

            val workRequest = PeriodicWorkRequestBuilder<ScheduledSyncWorker>(
                intervalInMins, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    private fun logScheduled() {
        if (LogMajorEvents.isEnabled()) {
            val now = System.currentTimeMillis()
            val intervalInMins = AppPreferences.scheduledSyncIntervalInMins(context).toLong()
            val intervalInMs = intervalInMins * 60 * 1000

            logs.log(
                LogMajorEvents.SYNC,
                "Scheduled sync every $intervalInMins minutes (next ~ ${DateTime(now + intervalInMs)}) on ${Build.DEVICE} (API ${Build.VERSION.SDK_INT})"
            )
        }
    }

    fun schedule() {
        schedule(context)
        logScheduled()
    }
}
