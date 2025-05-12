package com.orgzly.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.orgzly.BuildConfig
import com.orgzly.android.App
import com.orgzly.android.ui.notifications.Notifications
import com.orgzly.android.usecase.NoteCreateFromNotification
import com.orgzly.android.usecase.UseCaseRunner
import com.orgzly.android.util.LogUtils
import com.orgzly.android.sync.AutoSync
import javax.inject.Inject


class NewNoteBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var autoSync: AutoSync

    companion object {
        val TAG: String = NewNoteBroadcastReceiver::class.java.name
        const val NOTE_TITLE = "NOTE_TITLE"
    }

    init {
        App.appComponent.inject(this)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, intent)

        val title = getNoteTitle(intent)

        title?.let {
            autoSync.trigger(AutoSync.Type.QUICK_NOTE)

            App.EXECUTORS.diskIO().execute {
                UseCaseRunner.run(NoteCreateFromNotification(title))
            }

            Notifications.showOngoingNotification(context)
        }
    }

    private fun getNoteTitle(intent: Intent): String? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getString(NOTE_TITLE)
    }
}
