package com.xiaoguang.assistant.domain.model

/**
 * 文本Embedding模型
 */
data class TextEmbedding(
    val text: String,
    val vector: List<Float>,
    val dimension: Int,
    val model: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 批量Embedding结果
 */
data class BatchEmbeddingResult(
    val embeddings: List<TextEmbedding>,
    val totalTokens: Int
)

/**
 * Embedding配置
 */
data class EmbeddingConfig(
    val model: String,
    val dimension: Int,
    val useLocalModel: Boolean = false
)
