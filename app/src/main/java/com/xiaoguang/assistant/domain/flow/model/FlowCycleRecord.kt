package com.xiaoguang.assistant.domain.flow.model

import com.xiaoguang.assistant.domain.flow.layer.ActionResult

/**
 * 心流循环记录
 * 用于追溯和调试心流系统的决策过程
 */
data class FlowCycleRecord(
    val cycleId: Long,
    val timestamp: Long = System.currentTimeMillis(),

    // ✅ 完整的心流链路
    val perception: PerceptionSnapshot,
    val thoughts: List<InnerThought>,
    val decisionReasoning: String,  // LLM 的决策推理过程
    val decision: SpeakDecision,
    val action: ActionResult,

    // ✅ 性能数据
    val timings: Map<String, Long> = emptyMap(),  // 各阶段耗时（毫秒）
    val totalDuration: Long = 0L  // 总耗时（毫秒）
) {
    /**
     * 是否成功执行
     */
    fun isSuccessful(): Boolean = action.success

    /**
     * 获取决策类型摘要
     */
    fun getDecisionSummary(): String {
        return if (decision.shouldSpeak) {
            "发言: ${decision.reason}"
        } else {
            "沉默: ${decision.reason}"
        }
    }

    /**
     * 获取性能摘要
     */
    fun getPerformanceSummary(): String {
        val stages = timings.entries.sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { "${it.key}: ${it.value}ms" }
        return "总计${totalDuration}ms ($stages)"
    }
}

/**
 * 感知快照（简化版，只保存关键信息）
 */
data class PerceptionSnapshot(
    val masterPresent: Boolean,
    val friendsCount: Int,
    val hasRecentMessages: Boolean,
    val timeSinceLastInteraction: Long,  // 毫秒
    val currentEmotion: com.xiaoguang.assistant.domain.model.EmotionalState,
    val emotionIntensity: Float,
    val noiseLevel: Float,
    val conversationIntensity: Float
) {
    companion object {
        /**
         * 从完整 Perception 创建快照
         */
        fun fromPerception(perception: Perception): PerceptionSnapshot {
            return PerceptionSnapshot(
                masterPresent = perception.masterPresent,
                friendsCount = perception.friendsPresent.size,
                hasRecentMessages = perception.hasRecentMessages,
                timeSinceLastInteraction = perception.timeSinceLastInteraction.inWholeMilliseconds,
                currentEmotion = perception.currentEmotion,
                emotionIntensity = perception.emotionIntensity,
                noiseLevel = perception.environmentNoise,  // 使用environmentNoise
                conversationIntensity = (perception.recentMessages.size.toFloat() / 10f).coerceIn(0f, 1f)  // 基于最近消息数量计算
            )
        }
    }
}
