package com.orgzly.android.ui.capture

import java.util.UUID

data class CaptureTemplate(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val content: String = "",
    val targetBook: String = "",
    val state: String = "",
    val priority: String = "",
    val tags: String = "",
    val isScheduled: Boolean = false
)

/** Returns the best available display name, using the numbered fallback if needed. */
fun CaptureTemplate.getDisplayName(fallback: String): String =
    description.ifBlank { fallback }
