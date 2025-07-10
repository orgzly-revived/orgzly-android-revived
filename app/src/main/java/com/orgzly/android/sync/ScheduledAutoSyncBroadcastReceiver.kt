package com.orgzly.android.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.AppIntent
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.util.LogUtils
import javax.inject.Inject

class ScheduledAutoSyncBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var autoSync: AutoSync

    override fun onReceive(context: Context, intent: Intent) {
        App.appComponent.inject(this)

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent)

        if (AppPreferences.scheduledSyncEnabled(context) && intent.action.equals(AppIntent.ACTION_SYNC_START)) {
            autoSync.trigger(AutoSync.Type.SCHEDULED)
        }
    }

    companion object {
        private val TAG: String = ScheduledAutoSyncBroadcastReceiver::class.java.name
    }
}
