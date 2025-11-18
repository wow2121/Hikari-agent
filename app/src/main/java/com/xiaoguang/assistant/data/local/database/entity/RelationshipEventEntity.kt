package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 关系事件实体
 * 记录与每个人之间发生的重要事件
 */
@Entity(tableName = "relationship_events")
data class RelationshipEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val personName: String,
    val eventType: String,  // 事件类型
    val description: String,  // 事件描述
    val affectionImpact: Int = 0,  // 好感度影响
    val importance: Int = 3,  // 重要程度 1-5
    val timestamp: Long = System.currentTimeMillis(),
    val tags: String = "",  // JSON格式的标签列表
    val relatedMemoryId: Long? = null  // 关联的记忆ID
) {
    companion object {
        // 事件类型常量
        const val TYPE_FIRST_MEET = "first_meet"
        const val TYPE_HELP_GIVEN = "help_given"
        const val TYPE_HELP_RECEIVED = "help_received"
        const val TYPE_CONFLICT = "conflict"
        const val TYPE_RECONCILIATION = "reconciliation"
        const val TYPE_CELEBRATION = "celebration"
        const val TYPE_DEEP_CONVERSATION = "deep_conversation"
        const val TYPE_PRAISE = "praise"
        const val TYPE_CRITICISM = "criticism"
        const val TYPE_GIFT = "gift"
        const val TYPE_MILESTONE = "milestone"
        const val TYPE_LONG_ABSENCE = "long_absence"
        const val TYPE_RETURN = "return"
    }
}
