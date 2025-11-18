package com.xiaoguang.assistant.domain.flow.layer

import com.xiaoguang.assistant.domain.flow.engine.TimingOptimizer
import com.xiaoguang.assistant.domain.flow.model.InternalState
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.SpeakDecision
import com.xiaoguang.assistant.domain.flow.model.SpeakPriority
import com.xiaoguang.assistant.domain.flow.model.SpeakTiming
import com.xiaoguang.assistant.domain.flow.model.Thoughts
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 决策层（LLM 驱动）
 * 负责综合判断是否发言、何时发言、发言优先级
 */
@Singleton
class DecisionLayer @Inject constructor(
    private val flowLlmService: com.xiaoguang.assistant.domain.flow.service.FlowLlmService,
    private val timingOptimizer: TimingOptimizer,
    private val biologicalClockEngine: com.xiaoguang.assistant.domain.flow.engine.BiologicalClockEngine
) {
    /**
     * 做出决策（纯 LLM 驱动）
     */
    suspend fun decide(
        perception: Perception,
        thoughts: Thoughts,
        internalState: InternalState
    ): SpeakDecision {
        // 如果思考层认为不需要考虑发言，直接返回
        if (!thoughts.shouldConsiderSpeaking) {
            return SpeakDecision(
                shouldSpeak = false,
                confidence = 0f,
                reason = "无发言动机",
                suggestedContent = null,
                timing = SpeakTiming.DONT_SPEAK
            )
        }

        // 纯 LLM 决策（不使用规则评分）
        val llmDecision = try {
            flowLlmService.decideShouldSpeak(
                perception = perception,
                thoughts = thoughts.innerThoughts,
                internalState = internalState,
                biologicalState = biologicalClockEngine.getCurrentState()
            )
        } catch (e: Exception) {
            Timber.e(e, "[DecisionLayer] LLM 决策失败，保守选择沉默")
            // Fallback: 直接返回不发言（保守策略）
            return SpeakDecision(
                shouldSpeak = false,
                confidence = 0f,
                reason = "LLM决策失败，保守沉默",
                suggestedContent = null,
                timing = SpeakTiming.DONT_SPEAK
            )
        }

        // 3. 如果 LLM 决定不发言，直接返回
        if (!llmDecision.shouldSpeak) {
            Timber.d("[DecisionLayer] LLM 决定不发言: ${llmDecision.reason}")
            return SpeakDecision(
                shouldSpeak = false,
                confidence = llmDecision.confidence,
                reason = llmDecision.reason,
                suggestedContent = null,
                timing = SpeakTiming.DONT_SPEAK
            )
        }

        // 4. LLM 决定发言！
        // 使用 LLM 建议的消息（如果有），否则在 ActionLayer 生成
        val contentHint = llmDecision.suggestedMessage ?: generateContentHint(perception, thoughts)

        // 5. 优化时机
        val timing = timingOptimizer.optimize(perception, thoughts, true)

        // 6. 判断优先级
        val priority = calculatePriority(perception, thoughts, timing)

        val decision = SpeakDecision(
            shouldSpeak = true,
            confidence = llmDecision.confidence,
            reason = llmDecision.reason,
            suggestedContent = contentHint,
            timing = timing,
            priority = priority
        )

        Timber.i(
            "[DecisionLayer] LLM决定发言! 置信度: %.2f, 原因: %s, 时机: %s",
            decision.confidence,
            decision.reason,
            timing.displayName
        )

        return decision
    }

    /**
     * 生成发言内容提示
     */
    private fun generateContentHint(perception: Perception, thoughts: Thoughts): String {
        // 如果有紧急想法，直接使用
        val urgentThought = thoughts.getMostUrgent()
        if (urgentThought != null && urgentThought.isUrgent()) {
            return urgentThought.content
        }

        // 如果被叫到名字
        if (perception.mentionsXiaoguang) {
            return "回应主人的呼唤"
        }

        // 根据主导想法类型生成提示
        val dominantThought = thoughts.innerThoughts.firstOrNull()
        return when (dominantThought?.type) {
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.CURIOSITY -> "提出好奇的问题"
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.CARE -> "表达关心"
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.EXCITEMENT -> "分享兴奋的心情"
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.WORRY -> "表达担心"
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.BOREDOM -> "打破沉默"
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.SHARE -> "分享想法"
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.QUESTION -> "询问问题"
            else -> "主动问候或关心"
        }
    }

    /**
     * 计算发言优先级
     */
    private fun calculatePriority(
        perception: Perception,
        thoughts: Thoughts,
        timing: SpeakTiming
    ): SpeakPriority {
        // 被叫到名字 - 紧急
        if (perception.mentionsXiaoguang) {
            return SpeakPriority.URGENT
        }

        // 有紧急想法 - 紧急
        if (thoughts.getMostUrgent()?.urgency ?: 0f > 0.9f) {
            return SpeakPriority.URGENT
        }

        // 立即发言且有强烈情绪 - 高
        if (timing == SpeakTiming.IMMEDIATE && perception.emotionIntensity > 0.7f) {
            return SpeakPriority.HIGH
        }

        // 等待机会 - 低
        if (timing == SpeakTiming.WAIT_FOR_OPPORTUNITY) {
            return SpeakPriority.LOW
        }

        // 默认 - 正常
        return SpeakPriority.NORMAL
    }
}
