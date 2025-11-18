package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 人物标签实体
 * 动态记录人物的特征和属性
 */
@Entity(tableName = "person_tags")
data class PersonTagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val tag: String,  // 标签内容
    val source: String,  // 标签来源
    val confidence: Float = 0.5f,  // 置信度 0.0-1.0
    val evidence: String = "",  // 证据/原因
    val timestamp: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        // 标签来源常量
        const val SOURCE_USER_EXPLICIT = "user_explicit"  // 用户明确提到（置信度0.9）
        const val SOURCE_AI_INFERRED = "ai_inferred"  // AI推断（置信度0.6）
        const val SOURCE_BEHAVIOR_OBSERVED = "behavior_observed"  // 行为观察（置信度0.7）
        const val SOURCE_TOPIC_PREFERENCE = "topic_preference"  // 话题偏好（置信度0.6）
    }
}
