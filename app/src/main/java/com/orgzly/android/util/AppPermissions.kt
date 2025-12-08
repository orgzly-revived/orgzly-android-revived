package com.orgzly.android.util


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.orgzly.BuildConfig
import com.orgzly.R
import com.orgzly.android.App
import com.orgzly.android.ui.CommonActivity
import com.orgzly.android.ui.showSnackbar
import com.orgzly.android.ui.util.ActivityUtils
import com.orgzly.android.ui.util.getAlarmManager

object AppPermissions {
    private val TAG = AppPermissions::class.java.name

    @JvmStatic
    fun isGrantedOrRequest(activity: CommonActivity, requestCode: Usage): Boolean {
        val permissions = permissionsForRequest(requestCode)
        val rationale = rationaleForRequest(requestCode)

        val grantedOrRequested = if (!isGranted(activity, requestCode)) {
            /* Should we show an explanation? */
            // Check if any of the permissions need rationale
            val shouldShowRationale = permissions.any { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }

            if (shouldShowRationale) {
                activity.showSnackbar(rationale, R.string.settings) {
                    ActivityUtils.openAppInfoSettings(activity)
                }

            } else {
                /* No explanation needed -- request the permission. */
                if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, requestCode, permissions.contentToString(), "Requesting...")
                ActivityCompat.requestPermissions(activity, permissions, requestCode.ordinal)
            }

            false

        } else {
            true
        }

        if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, requestCode, permissions.contentToString(), grantedOrRequested)

        return grantedOrRequested
    }

    @JvmStatic
    fun isGranted(context: Context, requestCode: Usage): Boolean {
        val permissions = permissionsForRequest(requestCode)

        // Check if all permissions are granted
        val allGranted = permissions.all { permission ->
            // WRITE_EXTERNAL_STORAGE is unused in API 30 and later
            if (permission == Manifest.permission.WRITE_EXTERNAL_STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (BuildConfig.LOG_DEBUG)
                    LogUtils.d(TAG, requestCode, permission, "API " + Build.VERSION.SDK_INT + ", returning true")
                true
            } else {
                val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                if (BuildConfig.LOG_DEBUG) LogUtils.d(TAG, requestCode, permission, granted)
                granted
            }
        }

        return allGranted
    }

    /** Map request code to permission. */
    private fun permissionsForRequest(requestCode: Usage): Array<String> {
        return when (requestCode) {
            Usage.LOCAL_REPO -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            Usage.BOOK_EXPORT -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            Usage.SYNC_START -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            Usage.SAVED_SEARCHES_EXPORT_IMPORT -> arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            Usage.EXTERNAL_FILES_ACCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            Usage.POST_NOTIFICATIONS -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            Usage.SCHEDULE_EXACT_ALARM -> arrayOf(Manifest.permission.SCHEDULE_EXACT_ALARM)
            Usage.CALENDAR_SYNC -> arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        }
    }

    /** Map request code to explanation. */
    private fun rationaleForRequest(requestCode: Usage): Int {
        return when (requestCode) {
            Usage.LOCAL_REPO -> R.string.permissions_rationale_for_local_repo
            Usage.BOOK_EXPORT -> R.string.permissions_rationale_for_book_export
            Usage.SYNC_START -> R.string.permissions_rationale_for_sync_start
            Usage.SAVED_SEARCHES_EXPORT_IMPORT -> R.string.storage_permissions_missing
            Usage.EXTERNAL_FILES_ACCESS -> R.string.permissions_rationale_for_external_files_access
            Usage.POST_NOTIFICATIONS -> R.string.permissions_rationale_for_post_notifications
            Usage.SCHEDULE_EXACT_ALARM -> R.string.permissions_rationale_for_schedule_exact_alarms
            Usage.CALENDAR_SYNC -> R.string.permissions_rationale_for_calendar_sync
        }
    }

    @JvmStatic
    fun canScheduleExactAlarms(context: Context): Boolean {
        var isGranted = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !context.getAlarmManager().canScheduleExactAlarms()) {
            isGranted = false
            val activity = App.getCurrentActivity()
            activity?.showSnackbar(rationaleForRequest(Usage.SCHEDULE_EXACT_ALARM), R.string.settings) {
                ActivityUtils.openAppScheduleExactAlarmsPermissionSetting(activity)
            }
        }
        return isGranted
    }

    enum class Usage {
        LOCAL_REPO,
        BOOK_EXPORT,
        SYNC_START,
        SAVED_SEARCHES_EXPORT_IMPORT,
        EXTERNAL_FILES_ACCESS,
        POST_NOTIFICATIONS,
        SCHEDULE_EXACT_ALARM,
        CALENDAR_SYNC,
    }
}
