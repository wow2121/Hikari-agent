package com.xiaoguang.assistant.domain.memory.emotion

/**
 * Valence-Arousal情感模型
 *
 * 基于心理学的二维情感空间：
 * - Valence(效价)：情感的正负性 [-1.0, 1.0]
 *   -1.0 = 非常负面（悲伤、愤怒）
 *    0.0 = 中性
 *   +1.0 = 非常正面（快乐、兴奋）
 *
 * - Arousal(唤醒度)：情感的激烈程度 [0.0, 1.0]
 *    0.0 = 平静（放松、无聊）
 *    1.0 = 激动（兴奋、愤怒）
 *
 * 例子：
 * - 愤怒：valence=-0.8, arousal=0.9 (负面+高激动)
 * - 快乐：valence=0.8, arousal=0.6 (正面+中等激动)
 * - 悲伤：valence=-0.7, arousal=0.3 (负面+低激动)
 * - 平静：valence=0.2, arousal=0.1 (轻微正面+低激动)
 *
 * @property valence 情感效价(-1.0到1.0)
 * @property arousal 唤醒度(0.0到1.0)
 * @property dominantEmotion 主导情感标签(可选)
 * @property confidence 检测置信度(0.0到1.0)
 *
 * @author Claude Code
 */
data class EmotionState(
    val valence: Float,
    val arousal: Float,
    val dominantEmotion: EmotionLabel? = null,
    val confidence: Float = 0.8f
) {
    init {
        require(valence in -1.0f..1.0f) { "valence必须在-1.0到1.0之间" }
        require(arousal in 0.0f..1.0f) { "arousal必须在0.0到1.0之间" }
        require(confidence in 0.0f..1.0f) { "confidence必须在0.0到1.0之间" }
    }

    /**
     * 计算情感强度（综合效价和唤醒度）
     */
    val intensity: Float
        get() = kotlin.math.abs(valence) * arousal

    /**
     * 判断是否为正面情感
     */
    val isPositive: Boolean
        get() = valence > 0.2f

    /**
     * 判断是否为负面情感
     */
    val isNegative: Boolean
        get() = valence < -0.2f

    /**
     * 判断是否为中性情感
     */
    val isNeutral: Boolean
        get() = !isPositive && !isNegative

    /**
     * 判断是否为高激动情感
     */
    val isHighArousal: Boolean
        get() = arousal > 0.6f

    /**
     * 获取象限（4象限分类）
     */
    val quadrant: EmotionQuadrant
        get() = when {
            valence > 0 && arousal > 0.5f -> EmotionQuadrant.HIGH_POSITIVE  // 高兴、兴奋
            valence > 0 && arousal <= 0.5f -> EmotionQuadrant.LOW_POSITIVE   // 平静、满足
            valence < 0 && arousal > 0.5f -> EmotionQuadrant.HIGH_NEGATIVE  // 愤怒、焦虑
            else -> EmotionQuadrant.LOW_NEGATIVE  // 悲伤、沮丧
        }

    override fun toString(): String {
        return buildString {
            append("情感状态: ")
            append("valence=%.2f, arousal=%.2f".format(valence, arousal))
            if (dominantEmotion != null) {
                append(", $dominantEmotion")
            }
            append(" ($quadrant)")
        }
    }
}

/**
 * 情感标签（常见情感分类）
 */
enum class EmotionLabel(
    val chinese: String,
    val typicalValence: Float,
    val typicalArousal: Float
) {
    // 正面高唤醒
    JOY("喜悦", 0.8f, 0.7f),
    EXCITEMENT("兴奋", 0.9f, 0.9f),
    ENTHUSIASM("热情", 0.7f, 0.8f),

    // 正面低唤醒
    CONTENTMENT("满足", 0.6f, 0.3f),
    CALM("平静", 0.3f, 0.1f),
    RELAXED("放松", 0.5f, 0.2f),

    // 负面高唤醒
    ANGER("愤怒", -0.8f, 0.9f),
    FEAR("恐惧", -0.7f, 0.8f),
    ANXIETY("焦虑", -0.6f, 0.7f),
    FRUSTRATION("沮丧", -0.7f, 0.6f),

    // 负面低唤醒
    SADNESS("悲伤", -0.7f, 0.3f),
    BOREDOM("无聊", -0.2f, 0.1f),
    DISAPPOINTMENT("失望", -0.6f, 0.4f),

    // 中性
    NEUTRAL("中性", 0.0f, 0.3f),
    SURPRISE("惊讶", 0.1f, 0.8f);

    /**
     * 创建对应的情感状态
     */
    fun toEmotionState(confidence: Float = 0.8f): EmotionState {
        return EmotionState(
            valence = typicalValence,
            arousal = typicalArousal,
            dominantEmotion = this,
            confidence = confidence
        )
    }
}

/**
 * 情感象限
 */
enum class EmotionQuadrant(val chinese: String) {
    HIGH_POSITIVE("高激动正面"),   // 兴奋、喜悦
    LOW_POSITIVE("低激动正面"),    // 平静、满足
    HIGH_NEGATIVE("高激动负面"),   // 愤怒、焦虑
    LOW_NEGATIVE("低激动负面")     // 悲伤、无聊
}

/**
 * 情感变化记录
 */
data class EmotionChange(
    val memoryId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val previousEmotion: EmotionState,
    val newEmotion: EmotionState,
    val reason: String,
    val confidence: Float = 0.8f
) {
    /**
     * 计算变化量
     */
    val valenceDelta: Float
        get() = newEmotion.valence - previousEmotion.valence

    val arousalDelta: Float
        get() = newEmotion.arousal - previousEmotion.arousal

    /**
     * 判断是否为显著变化
     */
    fun isSignificant(threshold: Float = 0.3f): Boolean {
        return kotlin.math.abs(valenceDelta) > threshold ||
                kotlin.math.abs(arousalDelta) > threshold
    }

    override fun toString(): String {
        return buildString {
            append("情感变化: ")
            append("valence: %.2f→%.2f (Δ%.2f)".format(
                previousEmotion.valence,
                newEmotion.valence,
                valenceDelta
            ))
            append(", arousal: %.2f→%.2f (Δ%.2f)".format(
                previousEmotion.arousal,
                newEmotion.arousal,
                arousalDelta
            ))
            append(" | $reason")
        }
    }
}

/**
 * 情感衰减配置
 */
data class EmotionDecayConfig(
    val enabled: Boolean = true,
    val decayRate: Float = 0.01f,  // 每天衰减1%
    val minValence: Float = -0.1f,  // 衰减下限（负面情感不会完全消失，保留轻微负面）
    val maxValence: Float = 0.1f,   // 衰减上限（正面情感不会完全消失，保留轻微正面）
    val arousalDecayRate: Float = 0.02f  // 唤醒度衰减更快（情绪冷静下来）
) {
    /**
     * 计算衰减后的情感状态
     *
     * @param original 原始情感状态
     * @param daysPassed 经过的天数
     * @return 衰减后的情感状态
     */
    fun applyDecay(original: EmotionState, daysPassed: Int): EmotionState {
        if (!enabled || daysPassed <= 0) return original

        // Valence向中性衰减
        val decayedValence = when {
            original.valence > 0 -> {
                // 正面情感向maxValence衰减
                val target = maxValence
                val delta = (original.valence - target) * decayRate * daysPassed
                (original.valence - delta).coerceAtLeast(target)
            }
            original.valence < 0 -> {
                // 负面情感向minValence衰减
                val target = minValence
                val delta = (target - original.valence) * decayRate * daysPassed
                (original.valence + delta).coerceAtMost(target)
            }
            else -> original.valence
        }

        // Arousal向0衰减
        val decayedArousal = (original.arousal * (1 - arousalDecayRate * daysPassed))
            .coerceIn(0f, 1f)

        return EmotionState(
            valence = decayedValence,
            arousal = decayedArousal,
            dominantEmotion = if (decayedArousal < 0.3f) EmotionLabel.NEUTRAL else original.dominantEmotion,
            confidence = original.confidence * 0.9f  // 时间越久，置信度越低
        )
    }
}

/**
 * 情感统计
 */
data class EmotionStatistics(
    val totalMemories: Int,
    val positiveCount: Int,
    val negativeCount: Int,
    val neutralCount: Int,
    val avgValence: Float,
    val avgArousal: Float,
    val dominantQuadrant: EmotionQuadrant?,
    val emotionDistribution: Map<EmotionLabel, Int> = emptyMap()
) {
    val positiveRate: Float
        get() = if (totalMemories > 0) positiveCount.toFloat() / totalMemories else 0f

    val negativeRate: Float
        get() = if (totalMemories > 0) negativeCount.toFloat() / totalMemories else 0f

    override fun toString(): String = buildString {
        appendLine("【情感统计】")
        appendLine("总记忆数: $totalMemories")
        appendLine("- 正面: $positiveCount (%.1f%%)".format(positiveRate * 100))
        appendLine("- 负面: $negativeCount (%.1f%%)".format(negativeRate * 100))
        appendLine("- 中性: $neutralCount")
        appendLine("平均效价: %.2f".format(avgValence))
        appendLine("平均唤醒度: %.2f".format(avgArousal))
        if (dominantQuadrant != null) {
            appendLine("主导象限: ${dominantQuadrant.chinese}")
        }
    }
}
