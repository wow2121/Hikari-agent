package com.xiaoguang.assistant.domain.memory.reconstruction

import java.util.concurrent.atomic.AtomicLong

/**
 * 记忆重构记录
 *
 * 记录每次记忆重构的详细信息，支持版本追溯
 *
 * @property id 重构记录ID
 * @property originalMemoryId 原始记忆ID
 * @property updatedMemoryId 更新后记忆ID
 * @property type 重构类型
 * @property reason 重构原因
 * @property oldContent 旧内容摘要
 * @property newContent 新内容摘要
 * @property similarityScore 相似度分数 (0.0-1.0)
 * @property confidence 重构置信度 (0.0-1.0)
 * @property metadata 重构元数据 (如：算法名称、处理时间等)
 * @property timestamp 重构时间戳
 */
data class ReconstructionRecord(
    val id: Long = nextId(),
    val originalMemoryId: String,
    val updatedMemoryId: String,
    val type: ReconstructionType,
    val reason: String,
    val oldContent: String,
    val newContent: String,
    val similarityScore: Float,
    val confidence: Float,
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val idCounter = AtomicLong(0)

        private fun nextId(): Long {
            return idCounter.incrementAndGet()
        }

        /**
         * 创建重构记录
         */
        fun create(
            originalMemoryId: String,
            updatedMemoryId: String,
            type: ReconstructionType,
            reason: String,
            oldContent: String,
            newContent: String,
            similarityScore: Float,
            confidence: Float,
            metadata: Map<String, Any> = emptyMap()
        ): ReconstructionRecord {
            return ReconstructionRecord(
                originalMemoryId = originalMemoryId,
                updatedMemoryId = updatedMemoryId,
                type = type,
                reason = reason,
                oldContent = oldContent,
                newContent = newContent,
                similarityScore = similarityScore,
                confidence = confidence,
                metadata = metadata
            )
        }
    }

    /**
     * 获取重构的简短描述
     */
    fun getSummary(): String {
        return "${type.name}: $reason (置信度: ${"%.2f".format(confidence)})"
    }

    /**
     * 检查是否为高置信度重构
     */
    fun isHighConfidence(): Boolean {
        return confidence >= 0.8f
    }

    /**
     * 检查是否为高相似度重构
     */
    fun isHighSimilarity(): Boolean {
        return similarityScore >= 0.7f
    }

    /**
     * 获取重构影响级别
     */
    fun getImpactLevel(): String {
        return when {
            isHighConfidence() && isHighSimilarity() -> "高"
            confidence >= 0.6f || similarityScore >= 0.5f -> "中"
            else -> "低"
        }
    }

    override fun toString(): String {
        return "ReconstructionRecord(id=$id, type=$type, reason='$reason', " +
                "confidence=${"%.2f".format(confidence)}, " +
                "similarity=${"%.2f".format(similarityScore)}, " +
                "timestamp=$timestamp)"
    }
}
