package com.orgzly.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.orgzly.BuildConfig;
import com.orgzly.android.prefs.AppPreferences;
import com.orgzly.android.sync.AutoSyncScheduler;
import com.orgzly.android.ui.notifications.Notifications;
import com.orgzly.android.util.LogUtils;

public class BootCompletedReceiver extends BroadcastReceiver {
    public static final String TAG = BootCompletedReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, context, intent);

        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (AppPreferences.newNoteNotification(context)) {
                Notifications.showOngoingNotification(context);
            }
            if (AppPreferences.scheduledSyncEnabled(context)) {
                AutoSyncScheduler.Companion.cancelAll(context);
                AutoSyncScheduler.Companion.schedule(context);
            }
        }
    }
}
