package com.xiaoguang.assistant.domain.model

/**
 * AI提取的任务信息
 */
data class ExtractedTask(
    val title: String,
    val description: String = "",
    val dueDate: Long = 0L,  // Unix timestamp
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val relatedPersonId: String = "",
    val sourceText: String,  // 原始对话文本
    val confidence: Float,  // AI置信度 0.0-1.0
    val extractedAt: Long = System.currentTimeMillis()
)

/**
 * 信息提取结果
 */
data class ExtractionResult(
    val tasks: List<ExtractedTask>,
    val events: List<ExtractedEvent>,
    val processingTime: Long,  // 处理耗时(ms)
    val rawResponse: String  // AI原始响应
)

/**
 * 提取的事件信息
 */
data class ExtractedEvent(
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long = 0L,
    val location: String = "",
    val participants: List<String> = emptyList(),
    val sourceText: String,
    val confidence: Float
)
