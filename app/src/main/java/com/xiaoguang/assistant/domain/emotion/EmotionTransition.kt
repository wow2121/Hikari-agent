package com.xiaoguang.assistant.domain.emotion

import com.xiaoguang.assistant.domain.model.EmotionalState

/**
 * 情绪转换记录
 */
data class EmotionTransition(
    /**
     * 当前情绪
     */
    val currentEmotion: EmotionalState,

    /**
     * 目标情绪
     */
    val targetEmotion: EmotionalState,

    /**
     * 转换进度 (0.0 - 1.0)
     * 0.0 = 完全是currentEmotion
     * 1.0 = 完全是targetEmotion
     */
    val progress: Float,

    /**
     * 转换开始时间
     */
    val startTime: Long,

    /**
     * 预计完成时间
     */
    val estimatedEndTime: Long,

    /**
     * 情绪强度
     */
    val intensity: Float
) {
    /**
     * 是否已完成转换
     */
    fun isComplete(): Boolean = progress >= 1.0f

    /**
     * 获取当前实际情绪（混合状态）
     */
    fun getCurrentBlendedEmotion(): EmotionalState {
        // 如果已完成，直接返回目标情绪
        if (isComplete()) return targetEmotion

        // 如果进度超过50%，返回目标情绪
        // 否则返回当前情绪
        return if (progress > 0.5f) targetEmotion else currentEmotion
    }

    /**
     * 更新转换进度
     */
    fun updateProgress(currentTime: Long): EmotionTransition {
        val elapsed = currentTime - startTime
        val totalDuration = estimatedEndTime - startTime

        val newProgress = if (totalDuration > 0) {
            (elapsed.toFloat() / totalDuration).coerceIn(0f, 1f)
        } else {
            1f
        }

        return copy(progress = newProgress)
    }
}

/**
 * 情绪转换代价矩阵
 * 定义从一个情绪转换到另一个情绪需要多长时间（秒）
 */
object EmotionTransitionCost {

    /**
     * 默认转换时间（秒）
     */
    private const val DEFAULT_TRANSITION_TIME = 120  // 2分钟

    /**
     * 快速转换时间（秒）
     */
    private const val FAST_TRANSITION_TIME = 30  // 30秒

    /**
     * 慢速转换时间（秒）
     */
    private const val SLOW_TRANSITION_TIME = 300  // 5分钟

    /**
     * 极慢转换时间（秒）
     */
    private const val VERY_SLOW_TRANSITION_TIME = 600  // 10分钟

    /**
     * 获取从一个情绪转换到另一个情绪的时间代价（秒）
     */
    fun getTransitionTime(from: EmotionalState, to: EmotionalState): Int {
        // 如果目标情绪和当前情绪相同，无需转换
        if (from == to) return 0

        // 转换到CALM是恢复过程，较慢
        if (to == EmotionalState.CALM) {
            return when (from) {
                EmotionalState.HAPPY, EmotionalState.CURIOUS -> FAST_TRANSITION_TIME
                EmotionalState.EXCITED, EmotionalState.SHY -> DEFAULT_TRANSITION_TIME
                EmotionalState.WORRIED, EmotionalState.TIRED -> SLOW_TRANSITION_TIME
                EmotionalState.SAD, EmotionalState.ANGRY -> VERY_SLOW_TRANSITION_TIME
                EmotionalState.TOUCHED -> DEFAULT_TRANSITION_TIME
                else -> DEFAULT_TRANSITION_TIME
            }
        }

        // 从CALM转换到其他情绪，较快
        if (from == EmotionalState.CALM) {
            return when (to) {
                EmotionalState.HAPPY, EmotionalState.CURIOUS -> FAST_TRANSITION_TIME
                EmotionalState.EXCITED -> DEFAULT_TRANSITION_TIME
                EmotionalState.WORRIED, EmotionalState.SAD, EmotionalState.ANGRY -> DEFAULT_TRANSITION_TIME
                EmotionalState.SHY, EmotionalState.TIRED -> FAST_TRANSITION_TIME
                EmotionalState.TOUCHED -> DEFAULT_TRANSITION_TIME
                else -> DEFAULT_TRANSITION_TIME
            }
        }

        // 正面到负面情绪转换，较难
        if (isPositive(from) && isNegative(to)) {
            return SLOW_TRANSITION_TIME
        }

        // 负面到正面情绪转换，也较难
        if (isNegative(from) && isPositive(to)) {
            return SLOW_TRANSITION_TIME
        }

        // 相似情绪转换，较快
        if (areSimilar(from, to)) {
            return FAST_TRANSITION_TIME
        }

        // 其他情况使用默认转换时间
        return DEFAULT_TRANSITION_TIME
    }

    /**
     * 判断是否为正面情绪
     */
    private fun isPositive(emotion: EmotionalState): Boolean {
        return when (emotion) {
            EmotionalState.HAPPY,
            EmotionalState.EXCITED,
            EmotionalState.TOUCHED,
            EmotionalState.CURIOUS -> true
            else -> false
        }
    }

    /**
     * 判断是否为负面情绪
     */
    private fun isNegative(emotion: EmotionalState): Boolean {
        return when (emotion) {
            EmotionalState.SAD,
            EmotionalState.ANGRY,
            EmotionalState.WORRIED -> true
            else -> false
        }
    }

    /**
     * 判断两个情绪是否相似（容易互相转换）
     */
    private fun areSimilar(emotion1: EmotionalState, emotion2: EmotionalState): Boolean {
        val similarGroups = listOf(
            listOf(EmotionalState.HAPPY, EmotionalState.EXCITED, EmotionalState.TOUCHED),
            listOf(EmotionalState.SAD, EmotionalState.WORRIED),
            listOf(EmotionalState.CURIOUS, EmotionalState.HAPPY),
            listOf(EmotionalState.TIRED, EmotionalState.CALM),
            listOf(EmotionalState.SHY, EmotionalState.WORRIED)
        )

        return similarGroups.any { group ->
            group.contains(emotion1) && group.contains(emotion2)
        }
    }

    /**
     * 根据强度调整转换时间
     * 强度越高，转换越慢（情绪更"顽固"）
     */
    fun adjustByIntensity(baseTime: Int, intensity: Float): Int {
        val intensityMultiplier = 1f + (intensity - 0.5f)  // 0.5 intensity = 1x, 1.0 intensity = 1.5x
        return (baseTime * intensityMultiplier).toInt().coerceAtLeast(10)
    }
}

/**
 * 情绪累积效果
 * 记录同一类情绪事件的累积
 */
data class EmotionAccumulation(
    /**
     * 累积的情绪类型
     */
    val emotionType: EmotionalState,

    /**
     * 累积次数
     */
    val count: Int,

    /**
     * 第一次触发时间
     */
    val firstTriggerTime: Long,

    /**
     * 最后一次触发时间
     */
    val lastTriggerTime: Long,

    /**
     * 累积强度
     */
    val accumulatedIntensity: Float
) {
    /**
     * 是否应该触发情绪爆发
     *
     * 触发条件：
     * 1. 累积次数 >= 3
     * 2. 时间间隔不超过30分钟
     * 3. 累积强度 >= 1.5
     */
    fun shouldTriggerOutburst(): Boolean {
        val timeWindow = 30 * 60 * 1000  // 30分钟
        val timeSinceFirst = lastTriggerTime - firstTriggerTime

        return count >= 3 &&
               timeSinceFirst <= timeWindow &&
               accumulatedIntensity >= 1.5f
    }

    /**
     * 是否已过期（超过1小时未触发）
     */
    fun isExpired(currentTime: Long): Boolean {
        val expiryTime = 60 * 60 * 1000  // 1小时
        return currentTime - lastTriggerTime > expiryTime
    }

    /**
     * 添加新的触发
     */
    fun addTrigger(intensity: Float, currentTime: Long): EmotionAccumulation {
        return copy(
            count = count + 1,
            lastTriggerTime = currentTime,
            accumulatedIntensity = accumulatedIntensity + intensity
        )
    }
}
