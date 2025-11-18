package com.xiaoguang.assistant.domain.memory

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆重要性分级引擎
 *
 * 职责：
 * 1. 评估记忆的重要程度（0-10分）
 * 2. 重要记忆永久保存，普通记忆遵循遗忘曲线
 * 3. 频繁提及的记忆自动升级
 * 4. 提供记忆衰减和强化机制
 */
@Singleton
class MemoryImportanceEngine @Inject constructor() {

    /**
     * 评估记忆重要性
     *
     * @param content 记忆内容
     * @param context 记忆上下文
     * @param isMasterRelated 是否与主人相关
     * @param emotionalIntensity 情感强度
     * @return 重要性评分 (0-10)
     */
    fun evaluateImportance(
        content: String,
        context: MemoryContext,
        isMasterRelated: Boolean,
        emotionalIntensity: Float = 0.5f
    ): Int {
        var score = 5  // 基础分5分

        // 1. 上下文加分
        score += when (context) {
            MemoryContext.FIRST_MEET -> 3        // 第一次见面 +3
            MemoryContext.IMPORTANT_EVENT -> 3   // 重要事件 +3
            MemoryContext.PROMISE -> 2           // 承诺 +2
            MemoryContext.CONFLICT -> 2          // 冲突 +2
            MemoryContext.CELEBRATION -> 1       // 庆祝 +1
            MemoryContext.DAILY_CHAT -> 0        // 日常对话 +0
        }

        // 2. 主人相关加分
        if (isMasterRelated) {
            score += 2
        }

        // 3. 情感强度加分
        if (emotionalIntensity > 0.7f) {
            score += 2
        } else if (emotionalIntensity > 0.5f) {
            score += 1
        }

        // 4. 内容关键词加分
        val keywordBonus = calculateKeywordBonus(content)
        score += keywordBonus

        // 限制在0-10范围内
        return score.coerceIn(0, 10)
    }

    /**
     * 根据关键词计算加分
     */
    private fun calculateKeywordBonus(content: String): Int {
        var bonus = 0

        // 重要关键词
        val veryImportantKeywords = listOf(
            "第一次", "永远", "答应", "保证", "承诺",
            "生日", "纪念", "重要", "特别"
        )

        // 情感关键词
        val emotionalKeywords = listOf(
            "爱", "喜欢", "讨厌", "生气", "开心", "难过",
            "感动", "害怕", "担心"
        )

        veryImportantKeywords.forEach { keyword ->
            if (content.contains(keyword, ignoreCase = true)) {
                bonus += 2
            }
        }

        emotionalKeywords.forEach { keyword ->
            if (content.contains(keyword, ignoreCase = true)) {
                bonus += 1
            }
        }

        return bonus.coerceAtMost(3)  // 最多+3分
    }

    /**
     * 计算记忆衰减
     *
     * @param importance 重要性评分
     * @param daysSinceCreated 创建后的天数
     * @param accessCount 访问次数
     * @return 当前保留强度 (0.0-1.0)
     */
    fun calculateRetentionStrength(
        importance: Int,
        daysSinceCreated: Int,
        accessCount: Int
    ): Float {
        // 重要记忆不衰减
        if (importance >= 8) {
            return 1.0f
        }

        // 基于艾宾浩斯遗忘曲线
        // R(t) = e^(-t/S)
        // R: 记忆保留度，t: 时间，S: 记忆强度

        val memoryStrength = importance * 5f + accessCount * 2f
        val decayFactor = Math.exp(-daysSinceCreated.toDouble() / memoryStrength).toFloat()

        return decayFactor.coerceIn(0f, 1f)
    }

    /**
     * 是否应该保留记忆
     *
     * @param importance 重要性
     * @param retentionStrength 保留强度
     * @return 是否保留
     */
    fun shouldRetainMemory(importance: Int, retentionStrength: Float): Boolean {
        // 重要记忆永久保留
        if (importance >= 8) return true

        // 根据保留强度决定
        return when (importance) {
            in 6..7 -> retentionStrength > 0.3f   // 较重要：保留强度>30%
            in 4..5 -> retentionStrength > 0.5f   // 一般重要：保留强度>50%
            else -> retentionStrength > 0.7f      // 不太重要：保留强度>70%
        }
    }

    /**
     * 强化记忆（被再次提及）
     *
     * @param currentImportance 当前重要性
     * @param mentionContext 提及上下文
     * @return 新的重要性评分
     */
    fun reinforceMemory(
        currentImportance: Int,
        mentionContext: String
    ): Int {
        var newImportance = currentImportance

        // 频繁被提及，升级重要性
        newImportance += 1

        // 如果在重要对话中被提及，额外加分
        if (mentionContext.contains("记得") || mentionContext.contains("还记得吗")) {
            newImportance += 1
        }

        return newImportance.coerceAtMost(10)
    }

    /**
     * 获取记忆分类描述
     */
    fun getImportanceDescription(importance: Int): String {
        return when (importance) {
            in 9..10 -> "永久记忆"
            in 7..8 -> "重要记忆"
            in 5..6 -> "一般记忆"
            in 3..4 -> "普通记忆"
            else -> "临时记忆"
        }
    }

    /**
     * 批量评估记忆
     */
    fun batchEvaluate(memories: List<MemoryItem>): List<MemoryEvaluation> {
        return memories.map { memory ->
            val importance = evaluateImportance(
                content = memory.content,
                context = memory.context,
                isMasterRelated = memory.isMasterRelated,
                emotionalIntensity = memory.emotionalIntensity
            )

            val retentionStrength = calculateRetentionStrength(
                importance = importance,
                daysSinceCreated = memory.daysSinceCreated,
                accessCount = memory.accessCount
            )

            val shouldRetain = shouldRetainMemory(importance, retentionStrength)

            MemoryEvaluation(
                memoryId = memory.id,
                importance = importance,
                retentionStrength = retentionStrength,
                shouldRetain = shouldRetain,
                description = getImportanceDescription(importance)
            )
        }
    }
}

/**
 * 记忆上下文
 */
enum class MemoryContext {
    FIRST_MEET,         // 第一次见面
    IMPORTANT_EVENT,    // 重要事件
    PROMISE,            // 承诺
    CONFLICT,           // 冲突
    CELEBRATION,        // 庆祝
    DAILY_CHAT          // 日常对话
}

/**
 * 记忆项目（简化）
 */
data class MemoryItem(
    val id: Long,
    val content: String,
    val context: MemoryContext,
    val isMasterRelated: Boolean,
    val emotionalIntensity: Float,
    val daysSinceCreated: Int,
    val accessCount: Int
)

/**
 * 记忆评估结果
 */
data class MemoryEvaluation(
    val memoryId: Long,
    val importance: Int,
    val retentionStrength: Float,
    val shouldRetain: Boolean,
    val description: String
)
