package com.orgzly.android.ui.capture

import java.util.UUID

data class CaptureTemplate(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val title: String = "",
    val content: String = "",
    val targetBook: String = "",
    val state: String = "",
    val priority: String = "",
    val tags: String = "",
    val isScheduled: Boolean = false
)
