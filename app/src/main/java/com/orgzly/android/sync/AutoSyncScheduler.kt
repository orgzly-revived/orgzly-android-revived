package com.orgzly.android.sync

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.orgzly.android.AppIntent
import com.orgzly.android.data.logs.AppLogsRepository
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.getAlarmManager
import com.orgzly.android.util.LogMajorEvents
import org.joda.time.DateTime
import javax.inject.Inject

class AutoSyncScheduler @Inject constructor(val context: Application, val logs: AppLogsRepository) {

    companion object {
        fun schedule(context: Context) {
            val intervalInMs = AppPreferences.scheduledSyncIntervalInMins(context).toLong() * 60 * 1000
            val alarmManager = context.getAlarmManager()
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + intervalInMs,
                buildPendingIntent(context))
        }

        fun cancelAll(context: Context) {
            context.getAlarmManager().cancel(buildPendingIntent(context))
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, ScheduledAutoSyncBroadcastReceiver::class.java)
            intent.setAction(AppIntent.ACTION_SYNC_START)
            return PendingIntent.getBroadcast(context, 0, intent, ActivityUtils.mutable(PendingIntent.FLAG_UPDATE_CURRENT))
        }
    }

    private fun logScheduled() {
        if (LogMajorEvents.isEnabled()) {
            val now = System.currentTimeMillis()
            val intervalInMs = AppPreferences.scheduledSyncIntervalInMins(context).toLong() * 60 * 1000
            logs.log(
                LogMajorEvents.SYNC,
                "Scheduled sync in $intervalInMs ms (~ ${DateTime(now + intervalInMs)}) on ${Build.DEVICE} (API ${Build.VERSION.SDK_INT})"
            )
        }
    }

    private fun logCancelled() {
        if (LogMajorEvents.isEnabled()) {
            logs.log(LogMajorEvents.SYNC, "Canceled all scheduled syncs")
        }
    }

    fun schedule() {
        Companion.schedule(context)
        logScheduled()
    }

    fun cancelAll() {
        Companion.cancelAll(context)
        logCancelled()
    }
}
