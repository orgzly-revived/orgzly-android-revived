package com.orgzly.android.ui.note

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NoteAttachmentData(
        val uri: Uri,
        val filename: String,
        val isNew: Boolean = false,
        var isDeleted: Boolean = false): Parcelable