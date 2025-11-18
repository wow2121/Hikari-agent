package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 好感度变化实体
 *
 * 记录角色好感度的变化历史，用于Flow社交系统分析
 *
 * @property id 记录ID
 * @property characterId 角色ID
 * @property oldValue 变化前的好感度
 * @property newValue 变化后的好感度
 * @property reason 变化原因
 * @property timestamp 变化时间戳
 * @property context 上下文信息（可选）
 */
@Entity(tableName = "affection_changes")
data class AffectionChange(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val characterId: String,

    val oldValue: Int,

    val newValue: Int,

    val reason: String,

    val timestamp: Long = System.currentTimeMillis(),

    val context: String? = null
)
