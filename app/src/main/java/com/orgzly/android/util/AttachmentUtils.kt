package com.orgzly.android.util

object AttachmentUtils {
    /** Returns the attachment directory based on ID property. */
    fun getAttachDir(idStr: String) : String {
        return if (idStr.length <= 2) {
            idStr.substring(0, 2)
        } else {
            idStr.substring(0, 2) + "/" + idStr.substring(2)
        }
    }
}