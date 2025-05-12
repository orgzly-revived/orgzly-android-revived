package com.orgzly.android.reminders

import android.content.Context
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.dao.ReminderTimeDao
import com.orgzly.android.db.dao.ReminderTimeDao.NoteTime
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.org.datetime.OrgDateTime
import com.orgzly.org.datetime.OrgDateTimeUtils
import com.orgzly.org.datetime.OrgInterval
import org.joda.time.DateTime
import org.joda.time.ReadableInstant
import java.util.*
import kotlin.time.Duration


object NoteReminders {
    // private val TAG: String = NoteReminders::class.java.name

    const val INTERVAL_FROM_LAST_TO_NOW = 1
    const val INTERVAL_FROM_NOW = 2

    @JvmStatic
    fun getNoteReminders(
        context: Context,
        dataRepository: DataRepository,
        now: ReadableInstant,
        lastRun: LastRun,
        intervalType: Int): List<NoteReminder> {

        val result: MutableList<NoteReminder> = ArrayList()

        for (noteTime in dataRepository.times()) {
            if (isRelevantNoteTime(context, noteTime)) {
                val orgDateTime = OrgDateTime.parse(noteTime.orgTimestampString)

                val interval = intervalToConsider(intervalType, now, lastRun, noteTime.timeType)

                // Deadline warning period

                val warningPeriod = if (isWarningPeriodSupported(noteTime)) {
                    if (orgDateTime.hasDelay()) {
                        orgDateTime.delay as OrgInterval
                    } else {
                        // TODO: Use default from user preference
                        // OrgInterval(1, OrgInterval.Unit.DAY)
                        null
                    }
                } else {
                    null
                }

                val preNotification = noteTime.orgPreAlerts

                var periods = mutableSetOf(0)
                if (preNotification.length > 0) {
                    periods = (preNotification.split(",")
                        .map {
                            try {
                                Duration.parse(it.trim()).inWholeMilliseconds.toInt()
                            } catch (_: Exception) {
                                0 // safely default to 0
                            }
                        }).toMutableSet()
                }

                // always notify at the actual reminder time
                periods.add(0)

                for (period in periods.sortedDescending()) {
                    var time = getFirstTime(
                        orgDateTime,
                        interval,
                        AppPreferences.reminderDailyTime(context),
                        warningPeriod,
                        period
                    )

                    if (time != null) {
                        val payload = NoteReminderPayload(
                            noteTime.noteId,
                            noteTime.bookId,
                            noteTime.bookName,
                            noteTime.title,
                            noteTime.tags,
                            noteTime.timeType,
                            orgDateTime
                        )

                        result.add(NoteReminder(time, payload))
                    }
                }
            }
        }

        // Sort by time, older first
        result.sortWith { o1, o2 -> o1.runTime.compareTo(o2.runTime) }

        return result
    }

    fun isRelevantNoteTime(context: Context, noteTime: NoteTime): Boolean {
        val doneStateKeywords = AppPreferences.doneKeywordsSet(context)
        val isDone = doneStateKeywords.contains(noteTime.state)

        val isEnabled = AppPreferences.remindersForScheduledEnabled(context)
                && noteTime.timeType == ReminderTimeDao.SCHEDULED_TIME
                || AppPreferences.remindersForDeadlineEnabled(context)
                && noteTime.timeType == ReminderTimeDao.DEADLINE_TIME
                || AppPreferences.remindersForEventsEnabled(context)
                && noteTime.timeType == ReminderTimeDao.EVENT_TIME

        return isEnabled && !isDone
    }

    private fun isWarningPeriodSupported(noteTime: NoteTime): Boolean {
        return noteTime.timeType == ReminderTimeDao.DEADLINE_TIME
                || noteTime.timeType == ReminderTimeDao.EVENT_TIME
    }

    private fun intervalToConsider(
        intervalType: Int, now: ReadableInstant, lastRun: LastRun, timeType: Int
    ): Pair<ReadableInstant, ReadableInstant?> {

        when (intervalType) {
            INTERVAL_FROM_LAST_TO_NOW -> {
                val from = when (timeType) {
                    ReminderTimeDao.SCHEDULED_TIME -> {
                        lastRun.scheduled
                    }
                    ReminderTimeDao.DEADLINE_TIME -> {
                        lastRun.deadline
                    }
                    else -> {
                        lastRun.event
                    }
                }

                return Pair(from ?: now, now)
            }

            INTERVAL_FROM_NOW -> {
                return Pair(now, null)
            }

            else -> throw IllegalArgumentException("Before or after now?")
        }
    }

    private fun getFirstTime(
        orgDateTime: OrgDateTime,
        interval: Pair<ReadableInstant, ReadableInstant?>,
        defaultTimeOfDay: Int,
        warningPeriod: OrgInterval?,
        preNotificationPeriod: Int): DateTime? {

        val times = OrgDateTimeUtils.getTimesInInterval(
            orgDateTime,
            interval.first,
            interval.second,
            defaultTimeOfDay,
            false, // Do not use repeater for reminders
            warningPeriod,
            1,
            preNotificationPeriod)

        return times.firstOrNull()
    }
}