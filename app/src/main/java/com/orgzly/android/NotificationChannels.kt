package com.orgzly.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import com.orgzly.R
import com.orgzly.android.reminders.RemindersNotifications
import com.orgzly.android.ui.util.getNotificationManager
import com.orgzly.android.prefs.AppPreferences
import java.util.UUID


/**
 * Creates all channels for notifications.
 */
object NotificationChannels {

    private const val _ONGOING = "ongoing"
    private var ongoingId = ""
    private var prevOngoingId = _ONGOING
 
    private const val _REMINDERS = "reminders"
    private var remindersId = ""
    private var prevRemindersId = _REMINDERS

    const val SYNC_PROGRESS = "sync-progress"
    const val SYNC_FAILED = "sync-failed"
    const val SYNC_PROMPT = "sync-prompt"

    @JvmStatic
    fun channelIdForOngoing() : String {
        return ongoingId;
    }

    @JvmStatic
    fun channelIdForReminders() : String {
        return remindersId;
    }


    @JvmStatic
    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updateChannelIds();

            createForOngoing(context)
            createForReminders(context)
            createForSyncProgress(context)
            createForSyncFailed(context)
            createForSyncPrompt(context)
        }
    }

    @JvmStatic
    fun updateAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          updateChannelIds();

          updateForReminders(context)
          updateForOngoing(context)
        }
    }

    private fun updateChannelIds() {
        prevRemindersId = remindersId; 
        remindersId = _REMINDERS + "_" + UUID.randomUUID()
        
        prevOngoingId = ongoingId; 
        ongoingId = _ONGOING + "_" + UUID.randomUUID()
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateForReminders(context: Context) {
        val channel = createChannelForReminders(context);
        context.getNotificationManager().createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateForOngoing(context: Context) {
        // Note: Effect on Notifications: Deleting a notification channel will remove all
        // notifications associated with that channel. If you recreate the channel later, it will be
        // treated as a new channel, and any previous notifications will not be restored. Using the
        // same ID will restore the previous channel including the LED color.
        
        val channel = createChannelForOngoing(context);
        context.getNotificationManager().createNotificationChannel(channel)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannelForReminders(context: Context) : NotificationChannel {
        val id = remindersId;
        val name = context.getString(R.string.reminders_channel_name)
        val description = context.getString(R.string.reminders_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(id, name, importance)

        channel.description = description

        channel.enableLights(true)

        val colorString = AppPreferences.remindersLedColor(context);
        channel.lightColor = Color.parseColor(colorString);

        channel.vibrationPattern = RemindersNotifications.VIBRATION_PATTERN

        channel.setShowBadge(false)

        return channel;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForReminders(context: Context) {
        val channel = createChannelForReminders(context)
        context.getNotificationManager().createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForOngoing(context: Context)  {
        val channel = createChannelForOngoing(context)
        context.getNotificationManager().createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannelForOngoing(context: Context) : NotificationChannel {
        val id = ongoingId
        val name = context.getString(R.string.ongoing_channel_name)
        val description = context.getString(R.string.ongoing_channel_description)
        val importance = NotificationManager.IMPORTANCE_MIN

        val channel = NotificationChannel(id, name, importance)

        channel.description = description

        channel.setShowBadge(false)

        val colorString = AppPreferences.remindersLedColor(context);
        channel.lightColor = Color.parseColor(colorString);

        return channel;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForSyncProgress(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val id = SYNC_PROGRESS
        val name = context.getString(R.string.sync_progress_channel_name)
        val description = context.getString(R.string.sync_progress_channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW

        val channel = NotificationChannel(id, name, importance)

        channel.description = description

        channel.setShowBadge(false)

        context.getNotificationManager().createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForSyncFailed(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val id = SYNC_FAILED
        val name = context.getString(R.string.sync_failed_channel_name)
        val description = context.getString(R.string.sync_failed_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val channel = NotificationChannel(id, name, importance)

        channel.description = description

        channel.setShowBadge(true)

        context.getNotificationManager().createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createForSyncPrompt(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val id = SYNC_PROMPT
        val name = "Sync prompt"
        val description = "Display sync prompt"
        val importance = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(id, name, importance)

        channel.description = description

        channel.setShowBadge(false)

        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.createNotificationChannel(channel)
    }
}