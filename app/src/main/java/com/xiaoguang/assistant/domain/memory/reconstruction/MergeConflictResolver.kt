package com.xiaoguang.assistant.domain.memory.reconstruction

import com.xiaoguang.assistant.domain.memory.models.Memory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 记忆冲突解决器
 *
 * 当记忆之间存在冲突时，提供解决方案
 */
interface MergeConflictResolver {
    /**
     * 解决记忆冲突
     *
     * @param memory1 记忆1
     * @param memory2 记忆2
     * @param conflictType 冲突类型
     * @return 解决方案
     */
    suspend fun resolveConflict(
        memory1: Memory,
        memory2: Memory,
        conflictType: ConflictType
    ): ConflictResolution
}

/**
 * 冲突类型
 */
enum class ConflictType(
    val description: String,
    val severity: Int  // 严重程度，1-5，5最严重
) {
    /**
     * 内容冲突 - 核心内容不一致
     */
    CONTENT_CONFLICT(
        description = "记忆核心内容冲突",
        severity = 5
    ),

    /**
     * 时间冲突 - 时间信息不一致
     */
    TIME_CONFLICT(
        description = "记忆时间信息冲突",
        severity = 3
    ),

    /**
     * 实体冲突 - 相关实体信息不一致
     */
    ENTITY_CONFLICT(
        description = "相关实体信息冲突",
        severity = 3
    ),

    /**
     * 重要性冲突 - 重要性评估不一致
     */
    IMPORTANCE_CONFLICT(
        description = "记忆重要性评估冲突",
        severity = 2
    ),

    /**
     * 标签冲突 - 标签信息不一致
     */
    TAG_CONFLICT(
        description = "记忆标签冲突",
        severity = 1
    ),

    /**
     * 情感冲突 - 情感效价不一致
     */
    EMOTION_CONFLICT(
        description = "情感效价冲突",
        severity = 4
    );
}

/**
 * 冲突解决方案
 */
data class ConflictResolution(
    val strategy: ResolveStrategy,
    val resolvedMemory: Memory,
    val explanation: String,
    val confidence: Float  // 解决方案的置信度 (0.0-1.0)
)

/**
 * 解决策略
 */
enum class ResolveStrategy(
    val description: String
) {
    /**
     * 保留主要记忆
     */
    KEEP_PRIMARY(
        description = "保留主要记忆，忽略冲突内容"
    ),

    /**
     * 保留次要记忆
     */
    KEEP_SECONDARY(
        description = "保留次要记忆"
    ),

    /**
     * 智能合并
     */
    MERGE_SMART(
        description = "智能合并冲突内容"
    ),

    /**
     * 创建联合记录
     */
    CREATE_COMBINED(
        description = "创建联合记录保存两个版本"
    ),

    /**
     * 需要人工干预
     */
    REQUIRES_HUMAN(
        description = "冲突严重，需要人工判断"
    ),

    /**
     * 保留最新的
     */
    KEEP_LATEST(
        description = "保留时间上最新的记忆"
    ),

    /**
     * 保留更重要的
     */
    KEEP_MORE_IMPORTANT(
        description = "保留重要性更高的记忆"
    )
}

/**
 * 智能冲突解决器
 *
 * 根据冲突类型和记忆特征自动选择最佳解决策略
 */
class SmartConflictResolver : MergeConflictResolver {
    private val resolutionCache = ConcurrentHashMap<String, ConflictResolution>()

    override suspend fun resolveConflict(
        memory1: Memory,
        memory2: Memory,
        conflictType: ConflictType
    ): ConflictResolution {
        val cacheKey = "${memory1.id}_${memory2.id}_${conflictType.name}"
        resolutionCache[cacheKey]?.let { return it }

        val resolution = when (conflictType) {
            ConflictType.CONTENT_CONFLICT -> resolveContentConflict(memory1, memory2)
            ConflictType.TIME_CONFLICT -> resolveTimeConflict(memory1, memory2)
            ConflictType.ENTITY_CONFLICT -> resolveEntityConflict(memory1, memory2)
            ConflictType.IMPORTANCE_CONFLICT -> resolveImportanceConflict(memory1, memory2)
            ConflictType.TAG_CONFLICT -> resolveTagConflict(memory1, memory2)
            ConflictType.EMOTION_CONFLICT -> resolveEmotionConflict(memory1, memory2)
        }

        resolutionCache[cacheKey] = resolution
        return resolution
    }

    private fun resolveContentConflict(memory1: Memory, memory2: Memory): ConflictResolution {
        // 内容冲突比较严重，需要更谨慎的处理
        return when {
            // 如果重要性相差很大，保留更重要的
            kotlin.math.abs(memory1.importance - memory2.importance) > 0.3f -> {
                val primary = if (memory1.importance > memory2.importance) memory1 else memory2
                ConflictResolution(
                    strategy = ResolveStrategy.KEEP_MORE_IMPORTANT,
                    resolvedMemory = primary,
                    explanation = "内容冲突，保留更重要的记忆",
                    confidence = 0.8f
                )
            }
            // 如果时间相差很大，保留最新的
            kotlin.math.abs(memory1.createdAt - memory2.createdAt) > 7 * 24 * 60 * 60 * 1000L -> {
                val primary = if (memory1.createdAt > memory2.createdAt) memory1 else memory2
                ConflictResolution(
                    strategy = ResolveStrategy.KEEP_LATEST,
                    resolvedMemory = primary,
                    explanation = "内容冲突且时间相差较大，保留最新的记忆",
                    confidence = 0.7f
                )
            }
            // 否则创建联合记录
            else -> {
                val combinedContent = "版本1: ${memory1.content} | 版本2: ${memory2.content}"
                val primary = memory1.copy(
                    content = combinedContent,
                    importance = max(memory1.importance, memory2.importance),
                    lastAccessedAt = System.currentTimeMillis()
                )
                ConflictResolution(
                    strategy = ResolveStrategy.CREATE_COMBINED,
                    resolvedMemory = primary,
                    explanation = "内容冲突严重，创建联合记录",
                    confidence = 0.6f
                )
            }
        }
    }

    private fun resolveTimeConflict(memory1: Memory, memory2: Memory): ConflictResolution {
        // 时间冲突通常不严重，选择较新的时间或合并
        val primary = memory1.copy(
            createdAt = max(memory1.createdAt, memory2.createdAt),
            timestamp = max(memory1.timestamp, memory2.timestamp)
        )
        return ConflictResolution(
            strategy = ResolveStrategy.MERGE_SMART,
            resolvedMemory = primary,
            explanation = "时间冲突，选择较新的时间戳",
            confidence = 0.9f
        )
    }

    private fun resolveEntityConflict(memory1: Memory, memory2: Memory): ConflictResolution {
        // 实体冲突：合并所有实体
        val combinedEntities = (memory1.relatedEntities + memory2.relatedEntities).distinct()
        val primary = memory1.copy(
            relatedEntities = combinedEntities,
            lastAccessedAt = System.currentTimeMillis()
        )
        return ConflictResolution(
            strategy = ResolveStrategy.MERGE_SMART,
            resolvedMemory = primary,
            explanation = "实体冲突，合并所有相关实体",
            confidence = 0.85f
        )
    }

    private fun resolveImportanceConflict(memory1: Memory, memory2: Memory): ConflictResolution {
        // 重要性冲突：选择较高的
        val primary = if (memory1.importance >= memory2.importance) memory1 else memory2
        return ConflictResolution(
            strategy = ResolveStrategy.KEEP_MORE_IMPORTANT,
            resolvedMemory = primary,
            explanation = "重要性冲突，保留更重要的记忆",
            confidence = 0.9f
        )
    }

    private fun resolveTagConflict(memory1: Memory, memory2: Memory): ConflictResolution {
        // 标签冲突：合并所有标签
        val combinedTags = (memory1.tags + memory2.tags).distinct()
        val primary = memory1.copy(
            tags = combinedTags,
            lastAccessedAt = System.currentTimeMillis()
        )
        return ConflictResolution(
            strategy = ResolveStrategy.MERGE_SMART,
            resolvedMemory = primary,
            explanation = "标签冲突，合并所有标签",
            confidence = 0.9f
        )
    }

    private fun resolveEmotionConflict(memory1: Memory, memory2: Memory): ConflictResolution {
        // 情感冲突：检查情感强度
        val avgEmotion = (memory1.emotionalValence + memory2.emotionalValence) / 2f
        val primary = memory1.copy(
            emotionalValence = avgEmotion,
            lastAccessedAt = System.currentTimeMillis()
        )
        return ConflictResolution(
            strategy = ResolveStrategy.MERGE_SMART,
            resolvedMemory = primary,
            explanation = "情感冲突，取平均值",
            confidence = 0.75f
        )
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        resolutionCache.clear()
    }

    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Int {
        return resolutionCache.size
    }
}

/**
 * 简单冲突解决器
 *
 * 采用简单的规则解决冲突
 */
class SimpleConflictResolver : MergeConflictResolver {
    override suspend fun resolveConflict(
        memory1: Memory,
        memory2: Memory,
        conflictType: ConflictType
    ): ConflictResolution {
        // 总是选择较新的记忆
        val primary = if (memory1.createdAt > memory2.createdAt) memory1 else memory2
        return ConflictResolution(
            strategy = ResolveStrategy.KEEP_LATEST,
            resolvedMemory = primary,
            explanation = "简单策略：保留最新的记忆",
            confidence = 0.5f
        )
    }
}
