package com.xiaoguang.assistant.domain.flow.model

import com.xiaoguang.assistant.domain.model.EmotionalState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 小光的内在状态
 * 记录小光的内心状态、冲动值、决策历史等
 */
data class InternalState(
    // 情感状态
    val currentEmotion: EmotionalState = EmotionalState.CALM,
    val emotionIntensity: Float = 0.5f,
    val emotionDuration: Duration = Duration.ZERO,

    // 冲动值（想说话的冲动）
    val impulseValue: Float = 0f,  // 0-1，累积到阈值会触发发言倾向

    // 时间状态
    val lastSpeakTime: Long = 0L,  // 上次发言时间（包括主动和被动）
    val lastProactiveSpeakTime: Long = 0L,  // ✅ 上次**主动**发言时间
    val lastPassiveReplyTime: Long = 0L,  // ✅ 上次**被动回复**时间
    val lastInteractionTime: Long = 0L,  // 上次用户互动时间
    val timeSinceLastSpeak: Duration = Duration.ZERO,
    val timeSinceLastProactiveSpeak: Duration = Duration.ZERO,  // ✅ 距上次主动发言
    val timeSinceLastPassiveReply: Duration = Duration.ZERO,  // ✅ 距上次被动回复
    val timeSinceLastInteraction: Duration = Duration.ZERO,

    // 累积状态
    val ignoredCount: Int = 0,  // 被忽视次数
    val recentSpeakRatio: Float = 0f,  // 最近发言占比（频率控制）

    // 决策历史
    val recentDecisions: List<DecisionRecord> = emptyList(),

    // 内心想法队列
    val pendingThoughts: List<InnerThought> = emptyList(),

    // ⭐ 短期记忆：最近说过的话（用于避免重复）
    val recentUtterances: List<UtteranceMemory> = emptyList()
) {
    /**
     * 计算当前状态的描述
     */
    fun getStateDescription(): String {
        return when {
            impulseValue > 0.8f -> "非常想说话"
            impulseValue > 0.6f -> "比较想说话"
            impulseValue > 0.4f -> "有点想说话"
            impulseValue > 0.2f -> "在观察中"
            else -> "安静状态"
        }
    }

    /**
     * 更新冲动值（基于时间和情感）
     */
    fun updateImpulse(deltaTime: Duration, emotionInfluence: Float): InternalState {
        // 基于时间的累积（每分钟增加0.01）
        val timeIncrement = (deltaTime.inWholeMilliseconds / 60000.0 * 0.01).toFloat()

        // 情感调节
        val emotionMultiplier = when (currentEmotion) {
            EmotionalState.EXCITED -> 1.5f
            EmotionalState.HAPPY -> 1.2f
            EmotionalState.CURIOUS -> 1.3f
            EmotionalState.WORRIED -> 1.1f
            EmotionalState.SAD -> 0.7f
            EmotionalState.CALM -> 1.0f
            else -> 1.0f
        }

        val newImpulse = (impulseValue + timeIncrement * emotionMultiplier * emotionInfluence)
            .coerceIn(0f, 1f)

        return copy(impulseValue = newImpulse)
    }

    /**
     * 重置冲动值（说话后）
     */
    fun resetImpulse(): InternalState {
        return copy(
            impulseValue = 0f,
            ignoredCount = 0  // 说话成功，清除被忽视计数
        )
    }

    /**
     * 增加被忽视次数
     */
    fun incrementIgnored(): InternalState {
        return copy(ignoredCount = ignoredCount + 1)
    }

    /**
     * 添加决策记录
     */
    fun addDecisionRecord(record: DecisionRecord): InternalState {
        val newRecords = (recentDecisions + record).takeLast(20)
        return copy(recentDecisions = newRecords)
    }

    /**
     * 添加内心想法
     */
    fun addThought(thought: InnerThought): InternalState {
        val newThoughts = (pendingThoughts + thought).takeLast(10)
        return copy(pendingThoughts = newThoughts)
    }

    /**
     * 清除已处理的想法
     */
    fun clearThought(thought: InnerThought): InternalState {
        return copy(pendingThoughts = pendingThoughts - thought)
    }

    /**
     * 添加发言记录（短期记忆）
     * ⭐ 用于避免重复相同话题
     */
    fun addUtterance(content: String, topic: String? = null): InternalState {
        val now = System.currentTimeMillis()
        val extractedTopic = topic ?: extractSimpleTopic(content)

        val newMemory = UtteranceMemory(
            content = content,
            topic = extractedTopic,
            timestamp = now
        )

        // 保留最近10条，超过5分钟的移除
        val cutoffTime = now - 5 * 60 * 1000
        val recentList = (recentUtterances + newMemory)
            .filter { it.timestamp > cutoffTime }
            .takeLast(10)

        return copy(recentUtterances = recentList)
    }

    /**
     * 检查是否重复话题
     * @param keywords 话题关键词列表
     * @param withinMinutes 时间窗口（分钟）
     */
    fun isDuplicateTopic(keywords: List<String>, withinMinutes: Int = 5): Boolean {
        if (keywords.isEmpty()) return false

        val cutoff = System.currentTimeMillis() - withinMinutes * 60 * 1000
        return recentUtterances.any { utterance ->
            utterance.timestamp > cutoff && keywords.any { keyword ->
                utterance.topic.contains(keyword, ignoreCase = true) ||
                utterance.content.contains(keyword, ignoreCase = true)
            }
        }
    }

    /**
     * 简单提取话题关键词
     * （基础实现，提取较长的词作为关键词）
     */
    private fun extractSimpleTopic(content: String): String {
        // 移除标点符号，按空格分词，取最长的2-3个词
        val words = content
            .replace(Regex("""[！？。，、；：""''（）《》【】…—~·]"""), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }  // 至少2个字
            .sortedByDescending { it.length }
            .take(3)

        return words.joinToString(" ")
    }
}

/**
 * 决策记录
 */
data class DecisionRecord(
    val timestamp: Long,
    val shouldSpeak: Boolean,
    val confidence: Float,
    val reason: String,
    val actuallySpoke: Boolean,  // 是否真的说了话
    val result: SpeakResult? = null
)

/**
 * 发言结果
 */
data class SpeakResult(
    val success: Boolean,
    val userResponse: Boolean,  // 用户是否有回应
    val affectionChange: Float = 0f  // 好感度变化
)

/**
 * 发言记忆（短期）
 * ⭐ 用于避免重复相同话题
 */
data class UtteranceMemory(
    val content: String,         // 发言内容
    val topic: String,           // 提取的话题关键词
    val timestamp: Long          // 时间戳
)
