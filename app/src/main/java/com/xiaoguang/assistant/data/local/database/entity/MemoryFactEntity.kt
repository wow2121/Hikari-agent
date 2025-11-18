package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 记忆事实实体
 * 存储基础记忆数据，作为MemoryCore的核心存储
 *
 * v2.4架构：作为MemoryCore的基础数据存储
 * 主要存储：MemoryFactEntity (Room) + ChromaDB (向量) + Neo4j (关系图谱)
 */
@Entity(
    tableName = "memory_facts",
    indices = [
        Index("category"),
        Index("importance"),
        Index("createdAt"),
        Index("isActive")
    ]
)
data class MemoryFactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,                   // 记忆内容
    val category: String,                  // 记忆分类
    val importance: Float = 0.5f,          // 重要性 0.0-1.0
    val emotionalValence: Float = 0f,      // 情感效价 -1.0 ~ +1.0
    val tags: String = "",                 // 标签（逗号分隔）
    val relatedEntities: String = "",      // 相关实体（逗号分隔）
    val relatedCharacters: String = "",    // 相关角色（逗号分隔）
    val location: String? = null,          // 地点
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,              // 访问次数
    val reinforcementCount: Int = 0,       // 强化次数
    val confidence: Float = 1.0f,          // 可信度 0.0-1.0
    val isActive: Boolean = true,          // 是否活跃
    val isForgotten: Boolean = false,      // 是否已遗忘
    val forgottenAt: Long? = null,         // 遗忘时间
    val sourceType: String = "conversation", // 来源类型
    val metadata: String = ""              // 额外元数据（JSON字符串）
) {
    /**
     * 标记为已遗忘
     */
    fun markAsForgotten(forgottenAt: Long = System.currentTimeMillis()): MemoryFactEntity {
        return copy(
            isForgotten = true,
            forgottenAt = forgottenAt,
            isActive = false
        )
    }

    /**
     * 强化记忆
     */
    fun reinforce(reinforcedAt: Long = System.currentTimeMillis()): MemoryFactEntity {
        return copy(
            reinforcementCount = reinforcementCount + 1,
            importance = (importance + 0.05f).coerceAtMost(1.0f),
            lastAccessedAt = reinforcedAt
        )
    }

    /**
     * 记录访问
     */
    fun recordAccess(): MemoryFactEntity {
        return copy(
            accessCount = accessCount + 1,
            lastAccessedAt = System.currentTimeMillis()
        )
    }
}
