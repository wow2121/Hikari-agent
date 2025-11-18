package com.xiaoguang.assistant.domain.model

data class EventIntent(
    val hasEvent: Boolean,
    val title: String = "",
    val datetime: String = "", // ISO 8601 format or natural language
    val durationMinutes: Int = 60,
    val location: String = "",
    val description: String = "",
    val confidence: Float = 0.0f,
    val participants: List<String> = emptyList()
)
