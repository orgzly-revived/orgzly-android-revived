package com.orgzly.android.domain.services

import android.content.Context
import com.orgzly.android.SharingShortcutsManager
import com.orgzly.android.reminders.RemindersScheduler
import com.orgzly.android.sync.AutoSync
import com.orgzly.android.widgets.ListWidgetProvider
import javax.inject.Inject

/** Indicates the type of local data modification. */
enum class LocalDataModificationType {
    /** A note has been created. */
    NOTE_CREATED,
    /** Data has been modified, but not in such a way that a sync is necessary. */
    SYNC_NOT_REQUIRED,
    /** Local data has been generically modified. */
    OTHER,
}

/** Handles notifying other app components about local data changes. */
// This class probably shouldn't exist - it is intended to be used by UseCaseV2s, but use cases
// should not generally be aware of the intricacies of data handling. Rather, repositories
// themselves should be aware whether a call to them requires a sync/intent broadcast.
class DataUpdateService @Inject constructor(
    private val context: Context,
    private val autoSync: AutoSync
) {

    /** Notifies other app components that local data has been modified. */
    fun onLocalDataModified(
        modificationType: LocalDataModificationType = LocalDataModificationType.OTHER
    ) {
        RemindersScheduler.notifyDataSetChanged(context)
        ListWidgetProvider.notifyDataSetChanged(context)
        SharingShortcutsManager().replaceDynamicShortcuts(context)

        when (modificationType) {
            LocalDataModificationType.SYNC_NOT_REQUIRED -> return
            LocalDataModificationType.NOTE_CREATED -> autoSync.trigger(AutoSync.Type.NOTE_CREATED)
            else -> autoSync.trigger(AutoSync.Type.DATA_MODIFIED)
        }
    }

    /** Notifies the list widget that the list has been updated. */
    fun onListWidgetUpdateRequired() {
        ListWidgetProvider.update(context)
    }

}
