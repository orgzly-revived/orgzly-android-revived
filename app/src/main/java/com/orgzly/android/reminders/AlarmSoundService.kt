package com.orgzly.android.reminders

import android.app.Service
import android.content.Intent
import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.AudioAttributes
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.ui.util.getAudioManager

class AlarmSoundService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // ensure no other active alarm
        if (player != null) {
            return START_REDELIVER_INTENT
        }
        
        // ensure no silent/vibration mode
        var audioManager = applicationContext.getAudioManager()
        if (audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {

            // get alarm sound
            val uri = Settings.System.DEFAULT_ALARM_ALERT_URI ?: Settings.System.DEFAULT_RINGTONE_URI
            uri?.let {

                // create alarm volume media player
                player = MediaPlayer()
                player?.setAudioAttributes(
                        AudioAttributes.Builder()
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .setLegacyStreamType(AudioManager.STREAM_ALARM)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                player?.setDataSource(this@AlarmSoundService, it)
                player?.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                player?.setLooping(true)
                player?.prepare()
                player?.start()
            }
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {

        // stop audio
        player?.stop()
        player?.release()
        player = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
