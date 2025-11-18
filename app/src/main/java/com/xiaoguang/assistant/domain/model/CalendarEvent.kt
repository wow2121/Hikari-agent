package com.xiaoguang.assistant.domain.model

data class CalendarEvent(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val location: String = "",
    val isAutoCreated: Boolean = false,
    val confidence: Float = 1.0f, // 0.0 to 1.0
    val sourceConversationId: String? = null
)
