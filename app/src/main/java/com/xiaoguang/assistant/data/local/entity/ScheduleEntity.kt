package com.xiaoguang.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 日程实体（Room）
 */
@Entity(tableName = "schedule")
data class ScheduleEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String? = null,
    val dueTime: Long,
    val isCompleted: Boolean = false,
    val priority: String = "NORMAL",  // LOW, NORMAL, HIGH, URGENT
    val type: String = "TODO",        // TODO, REMINDER, EVENT
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
