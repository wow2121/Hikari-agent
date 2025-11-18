package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 对话Embedding实体
 * 存储对话的向量化表示，用于语义搜索和相似度匹配
 */
@Entity(
    tableName = "conversation_embeddings",
    indices = [
        Index("conversationId"),
        Index("messageId"),
        Index("timestamp")
    ]
)
data class ConversationEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,        // 对话ID
    val messageId: String,             // 消息ID（唯一）
    val content: String,               // 原始文本内容
    val embeddingVector: List<Float>,  // 向量数据
    val dimension: Int,                // 向量维度
    val modelName: String,             // 使用的embedding模型
    val importance: Float = 0.5f,      // 重要性 0.0-1.0
    val category: String = "general",  // 分类
    val tags: String = "",             // 标签（逗号分隔）
    val accessCount: Int = 0,          // 访问次数
    val lastAccessedAt: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val role: String = "user",         // 消息角色
    val isArchived: Boolean = false    // 是否已归档
)
