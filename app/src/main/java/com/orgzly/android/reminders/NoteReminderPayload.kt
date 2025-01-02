package com.orgzly.android.reminders

import com.orgzly.org.datetime.OrgDateTime

data class NoteReminderPayload(
    var noteId: Long,
    var bookId: Long,
    var bookName: String,
    var title: String,
    var tags: String?,
    var timeType: Int,
    var orgDateTime: OrgDateTime
)