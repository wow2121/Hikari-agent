package com.xiaoguang.assistant.domain.model

data class TodoItem(
    val id: String,
    val title: String,
    val description: String = "",
    val dueDate: Long = 0L, // Timestamp, 0 means no due date
    val isCompleted: Boolean = false,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val relatedPersonId: String = "",
    val sourceConversationId: String = "",
    val sourceText: String = "", // Original text that AI extracted from
    val confidence: Float = 0.0f, // 0.0 to 1.0
    val isAutoCreated: Boolean = false,
    val addedToCalendar: Boolean = false,
    val calendarEventId: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class TodoPriority(val value: Int) {
    LOW(0),
    MEDIUM(1),
    HIGH(2);

    companion object {
        fun fromValue(value: Int): TodoPriority {
            return values().find { it.value == value } ?: MEDIUM
        }
    }
}

// 扩展函数：从Realm实体转换为Domain模型
fun com.xiaoguang.assistant.data.local.realm.entities.TaskEntity.toDomainModel() = TodoItem(
    id = taskId,
    title = title,
    description = description,
    dueDate = dueDate,
    isCompleted = isCompleted,
    priority = TodoPriority.fromValue(priority),
    relatedPersonId = relatedPersonId,
    sourceConversationId = sourceConversationId,
    sourceText = sourceText,
    confidence = confidence,
    isAutoCreated = isAutoCreated,
    addedToCalendar = addedToCalendar,
    calendarEventId = calendarEventId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
