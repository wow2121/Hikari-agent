package com.xiaoguang.assistant.domain.emotion

import com.xiaoguang.assistant.domain.model.EmotionalState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 情绪渐变引擎
 *
 * 职责：
 * 1. 管理情绪的渐进式转换（不再瞬间切换）
 * 2. 处理情绪累积效果（多次小刺激→情绪爆发）
 * 3. 情绪衰减和恢复
 * 4. 情绪惯性（情绪有"动量"，不易改变）
 */
@Singleton
class EmotionTransitionEngine @Inject constructor() {

    // 当前情绪转换状态
    private val _currentTransition = MutableStateFlow<EmotionTransition?>(null)
    val currentTransition: StateFlow<EmotionTransition?> = _currentTransition.asStateFlow()

    // 情绪累积记录（用于检测情绪爆发）
    private val accumulationMap = mutableMapOf<EmotionalState, EmotionAccumulation>()

    /**
     * 请求情绪转换
     *
     * @param targetEmotion 目标情绪
     * @param reason 转换原因
     * @param intensity 情绪强度 (0.0-1.0)
     * @param force 是否强制立即切换（紧急情况）
     * @param customTransitionSeconds ✅ LLM 推荐的转换时间（秒），如果提供则使用，否则用规则
     * @return 实际的当前情绪（可能还在转换中）
     */
    fun requestEmotionChange(
        targetEmotion: EmotionalState,
        reason: String,
        intensity: Float = 0.5f,
        force: Boolean = false,
        customTransitionSeconds: Int? = null  // ✅ LLM 决定的转换速度
    ): EmotionalState {
        val currentTime = System.currentTimeMillis()

        // 如果没有转换在进行，使用CALM作为当前情绪
        val currentEmotion = _currentTransition.value?.getCurrentBlendedEmotion()
            ?: EmotionalState.CALM

        // 如果目标情绪和当前情绪相同，无需转换
        if (targetEmotion == currentEmotion && !force) {
            Timber.d("[EmotionTransition] 目标情绪与当前情绪相同，无需转换")
            return currentEmotion
        }

        // 检查是否应该累积而非立即转换
        if (!force && shouldAccumulate(currentEmotion, targetEmotion, intensity)) {
            accumulate(targetEmotion, intensity, currentTime)

            // 检查是否触发情绪爆发
            val accumulation = accumulationMap[targetEmotion]
            if (accumulation?.shouldTriggerOutburst() == true) {
                Timber.w("[EmotionTransition] 💥 情绪爆发！累积触发: $targetEmotion")
                // 情绪爆发时，强度加倍
                return startTransition(
                    currentEmotion,
                    targetEmotion,
                    reason,
                    (intensity * 1.5f).coerceAtMost(1f),
                    currentTime
                )
            } else {
                Timber.d("[EmotionTransition] 情绪累积中: $targetEmotion (${accumulation?.count}/3)")
                return currentEmotion  // 保持当前情绪
            }
        }

        // 强制切换或正常转换
        if (force) {
            Timber.w("[EmotionTransition] ⚡ 强制切换情绪: $currentEmotion -> $targetEmotion")
            // 强制切换时，直接完成转换
            _currentTransition.value = EmotionTransition(
                currentEmotion = targetEmotion,
                targetEmotion = targetEmotion,
                progress = 1f,
                startTime = currentTime,
                estimatedEndTime = currentTime,
                intensity = intensity
            )
            return targetEmotion
        }

        // 正常渐进式转换
        return startTransition(currentEmotion, targetEmotion, reason, intensity, currentTime, customTransitionSeconds)
    }

    /**
     * 开始情绪转换
     */
    private fun startTransition(
        currentEmotion: EmotionalState,
        targetEmotion: EmotionalState,
        reason: String,
        intensity: Float,
        currentTime: Long,
        customTransitionSeconds: Int? = null  // ✅ LLM 推荐的转换时间
    ): EmotionalState {
        // 计算转换时间
        val adjustedTime = if (customTransitionSeconds != null) {
            // ✅ 使用 LLM 推荐的转换时间
            Timber.d("[EmotionTransition] 使用 LLM 推荐的转换时间: ${customTransitionSeconds}秒")
            customTransitionSeconds
        } else {
            // ✅ Fallback: 使用规则计算（仅在 LLM 失败时）
            val baseTransitionTime = EmotionTransitionCost.getTransitionTime(currentEmotion, targetEmotion)
            val adjusted = EmotionTransitionCost.adjustByIntensity(baseTransitionTime, intensity)
            Timber.d("[EmotionTransition] 使用规则计算的转换时间: ${adjusted}秒（LLM未提供）")
            adjusted
        }
        val estimatedEndTime = currentTime + (adjustedTime * 1000)

        Timber.i("[EmotionTransition] 🔄 开始情绪转换: $currentEmotion -> $targetEmotion " +
                "(预计${adjustedTime}秒, 原因: $reason)")

        _currentTransition.value = EmotionTransition(
            currentEmotion = currentEmotion,
            targetEmotion = targetEmotion,
            progress = 0f,
            startTime = currentTime,
            estimatedEndTime = estimatedEndTime,
            intensity = intensity
        )

        return currentEmotion  // 返回当前情绪，因为转换刚开始
    }

    /**
     * 更新转换进度（应定期调用）
     *
     * @return 当前实际情绪
     */
    fun updateTransition(): EmotionalState {
        val transition = _currentTransition.value ?: return EmotionalState.CALM

        val currentTime = System.currentTimeMillis()
        val updated = transition.updateProgress(currentTime)

        if (updated.isComplete()) {
            Timber.i("[EmotionTransition] ✅ 情绪转换完成: ${transition.currentEmotion} -> ${transition.targetEmotion}")

            // 转换完成，设置为新的稳定状态
            _currentTransition.value = EmotionTransition(
                currentEmotion = transition.targetEmotion,
                targetEmotion = transition.targetEmotion,
                progress = 1f,
                startTime = currentTime,
                estimatedEndTime = currentTime,
                intensity = transition.intensity
            )

            return transition.targetEmotion
        } else {
            _currentTransition.value = updated
            return updated.getCurrentBlendedEmotion()
        }
    }

    /**
     * 获取当前实际情绪
     */
    fun getCurrentEmotion(): EmotionalState {
        return _currentTransition.value?.getCurrentBlendedEmotion()
            ?: EmotionalState.CALM
    }

    /**
     * 获取转换进度（0.0-1.0）
     */
    fun getTransitionProgress(): Float {
        return _currentTransition.value?.progress ?: 1f
    }

    /**
     * 判断是否应该累积而非立即转换
     *
     * 累积条件：
     * 1. 强度较低（< 0.7）
     * 2. 当前情绪比较强烈
     * 3. 情绪类型差异不大
     */
    private fun shouldAccumulate(
        currentEmotion: EmotionalState,
        targetEmotion: EmotionalState,
        intensity: Float
    ): Boolean {
        // 强度很高时，直接转换
        if (intensity >= 0.7f) return false

        // 当前在转换中，且目标不同，累积
        val ongoingTransition = _currentTransition.value
        if (ongoingTransition != null &&
            !ongoingTransition.isComplete() &&
            ongoingTransition.targetEmotion != targetEmotion) {
            return true
        }

        // 当前情绪强度较高，不易改变
        if (ongoingTransition != null && ongoingTransition.intensity > 0.7f) {
            return true
        }

        return false
    }

    /**
     * 累积情绪事件
     */
    private fun accumulate(emotion: EmotionalState, intensity: Float, currentTime: Long) {
        val existing = accumulationMap[emotion]

        if (existing == null) {
            // 创建新的累积记录
            accumulationMap[emotion] = EmotionAccumulation(
                emotionType = emotion,
                count = 1,
                firstTriggerTime = currentTime,
                lastTriggerTime = currentTime,
                accumulatedIntensity = intensity
            )
        } else if (!existing.isExpired(currentTime)) {
            // 更新现有累积
            accumulationMap[emotion] = existing.addTrigger(intensity, currentTime)
        } else {
            // 过期了，重新开始累积
            accumulationMap[emotion] = EmotionAccumulation(
                emotionType = emotion,
                count = 1,
                firstTriggerTime = currentTime,
                lastTriggerTime = currentTime,
                accumulatedIntensity = intensity
            )
        }
    }

    /**
     * 清理过期的累积记录
     */
    fun cleanupExpiredAccumulations() {
        val currentTime = System.currentTimeMillis()
        accumulationMap.entries.removeIf { (_, accumulation) ->
            accumulation.isExpired(currentTime)
        }
    }

    /**
     * 重置到平静状态
     */
    fun reset() {
        _currentTransition.value = null
        accumulationMap.clear()
        Timber.d("[EmotionTransition] 重置到平静状态")
    }

    /**
     * 获取情绪累积状态（用于调试）
     */
    fun getAccumulationStatus(): Map<EmotionalState, EmotionAccumulation> {
        return accumulationMap.toMap()
    }
}
