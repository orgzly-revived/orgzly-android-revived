package com.orgzly.android.ui.note

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class NoteAttachmentData(
    val uri: Uri,
    val filename: String,
    var isNew: Boolean,
    var isDeleted: Boolean = false,
    val type: Type? = null
) : Parcelable {
    enum class Type {
        LINK,
        COPY_TO_DIR,
        COPY_TO_ID
    }
}