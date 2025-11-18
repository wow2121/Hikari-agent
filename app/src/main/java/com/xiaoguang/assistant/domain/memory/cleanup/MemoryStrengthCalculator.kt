package com.xiaoguang.assistant.domain.memory.cleanup

import com.xiaoguang.assistant.domain.memory.models.Memory
import timber.log.Timber
import kotlin.math.exp
import kotlin.math.pow

/**
 * 记忆强度计算器
 *
 * 基于Ebbinghaus遗忘曲线和多因素模型计算记忆强度。
 *
 * 公式：
 * ```
 * Strength(t) = Base × Decay(t) × Reinforcement × Importance × Confidence × Emotional × Access
 * ```
 *
 * 其中：
 * - Base = 初始强度（1.0）
 * - Decay(t) = e^(-t/S)  [Ebbinghaus遗忘曲线]
 * - Reinforcement = 强化系数
 * - Importance = 重要性
 * - Confidence = 置信度
 * - Emotional = 情感强度加成
 * - Access = 访问频率加成
 *
 * @author Claude Code
 */
class MemoryStrengthCalculator {

    companion object {
        // Ebbinghaus遗忘曲线参数
        private const val DEFAULT_DECAY_RATE = 86400000L  // 1天（毫秒）
        private const val FAST_DECAY_RATE = 3600000L  // 1小时
        private const val SLOW_DECAY_RATE = 604800000L  // 7天

        // 权重参数
        private const val WEIGHT_IMPORTANCE = 0.3f
        private const val WEIGHT_CONFIDENCE = 0.2f
        private const val WEIGHT_EMOTIONAL = 0.2f
        private const val WEIGHT_ACCESS = 0.15f
        private const val WEIGHT_REINFORCEMENT = 0.15f
    }

    /**
     * 计算记忆强度
     *
     * @param memory 记忆对象
     * @param currentTime 当前时间戳（默认为系统时间）
     * @return 记忆强度（0.0-1.0）
     */
    fun calculateStrength(
        memory: Memory,
        currentTime: Long = System.currentTimeMillis()
    ): Float {
        // 1. 时间衰减（Ebbinghaus遗忘曲线）
        val decayFactor = calculateDecay(memory, currentTime)

        // 2. 重要性因素
        val importanceFactor = memory.importance * WEIGHT_IMPORTANCE

        // 3. 置信度因素
        val confidenceFactor = memory.confidence * WEIGHT_CONFIDENCE

        // 4. 情感因素（情感越强，记忆越深刻）
        val emotionalIntensity = kotlin.math.abs(memory.emotionalValence)
        val emotionalFactor = emotionalIntensity * WEIGHT_EMOTIONAL

        // 5. 访问频率因素（归一化）
        val accessFactor = calculateAccessFactor(memory.accessCount) * WEIGHT_ACCESS

        // 6. 强化因素
        val reinforcementFactor = calculateReinforcementFactor(memory.reinforcementCount) * WEIGHT_REINFORCEMENT

        // 综合计算
        val baseStrength = importanceFactor + confidenceFactor + emotionalFactor + accessFactor + reinforcementFactor
        val finalStrength = (baseStrength * decayFactor).coerceIn(0f, 1f)

        Timber.v(
            "[MemoryStrength] ${memory.id.take(8)}: " +
                    "decay=%.2f, importance=%.2f, confidence=%.2f, emotional=%.2f, access=%.2f, reinforcement=%.2f → %.2f"
                .format(decayFactor, importanceFactor, confidenceFactor, emotionalFactor, accessFactor, reinforcementFactor, finalStrength)
        )

        return finalStrength
    }

    /**
     * 计算时间衰减因子（Ebbinghaus遗忘曲线）
     *
     * R(t) = e^(-t/S)
     * - t: 经过的时间
     * - S: 记忆的稳定性常数（由重要性和强化次数决定）
     */
    private fun calculateDecay(memory: Memory, currentTime: Long): Float {
        val timeSinceLastAccess = currentTime - memory.lastAccessedAt
        if (timeSinceLastAccess < 0) return 1f  // 时间异常，返回满强度

        // 根据记忆特征调整衰减率
        val decayRate = when {
            // 高重要性记忆衰减慢
            memory.importance > 0.8f -> SLOW_DECAY_RATE

            // 低重要性记忆衰减快
            memory.importance < 0.3f -> FAST_DECAY_RATE

            // 普通记忆使用默认衰减率
            else -> DEFAULT_DECAY_RATE
        }

        // 强化次数越多，衰减越慢
        val reinforcementBonus = 1.0 + (memory.reinforcementCount * 0.1)
        val effectiveDecayRate = (decayRate * reinforcementBonus).toLong()

        // Ebbinghaus公式: R = e^(-t/S)
        val decay = exp(-timeSinceLastAccess.toDouble() / effectiveDecayRate).toFloat()

        return decay.coerceIn(0f, 1f)
    }

    /**
     * 计算访问频率因素
     *
     * 使用对数缩放避免过度权重
     */
    private fun calculateAccessFactor(accessCount: Int): Float {
        if (accessCount <= 0) return 0f

        // 对数缩放: log(1 + count) / log(100)
        // 1次访问 → 0.30
        // 10次访问 → 0.50
        // 100次访问 → 1.00
        val normalized = (kotlin.math.ln(1.0 + accessCount) / kotlin.math.ln(100.0)).toFloat()

        return normalized.coerceIn(0f, 1f)
    }

    /**
     * 计算强化因素
     *
     * 强化次数越多，记忆越牢固
     */
    private fun calculateReinforcementFactor(reinforcementCount: Int): Float {
        if (reinforcementCount <= 0) return 0.2f  // 基础值

        // 使用幂函数: (1 - e^(-count/5))
        // 1次强化 → 0.18
        // 5次强化 → 0.63
        // 10次强化 → 0.86
        val factor = (1.0 - exp(-reinforcementCount / 5.0)).toFloat()

        return factor.coerceIn(0f, 1f)
    }

    /**
     * 批量计算记忆强度
     *
     * @param memories 记忆列表
     * @return 记忆ID到强度的映射
     */
    fun calculateBatch(
        memories: List<Memory>,
        currentTime: Long = System.currentTimeMillis()
    ): Map<String, Float> {
        return memories.associate { memory ->
            memory.id to calculateStrength(memory, currentTime)
        }
    }

    /**
     * 预测未来时间点的记忆强度
     *
     * @param memory 记忆对象
     * @param futureTime 未来时间戳
     * @return 预测的强度
     */
    fun predictStrength(memory: Memory, futureTime: Long): Float {
        require(futureTime >= System.currentTimeMillis()) { "未来时间必须>=当前时间" }
        return calculateStrength(memory, futureTime)
    }

    /**
     * 计算记忆的半衰期（强度降到50%所需时间）
     *
     * @param memory 记忆对象
     * @return 半衰期（毫秒）
     */
    fun calculateHalfLife(memory: Memory): Long {
        val currentStrength = calculateStrength(memory)
        if (currentStrength <= 0) return 0

        val targetStrength = currentStrength * 0.5f

        // 二分查找半衰期
        var low = 0L
        var high = 365L * 24 * 3600 * 1000  // 1年
        var halfLife = 0L

        while (low <= high) {
            val mid = (low + high) / 2
            val futureTime = System.currentTimeMillis() + mid
            val predictedStrength = calculateStrength(memory, futureTime)

            if (predictedStrength > targetStrength) {
                low = mid + 1
                halfLife = mid
            } else {
                high = mid - 1
            }
        }

        return halfLife
    }
}

/**
 * 记忆强度详细信息（用于调试和分析）
 */
data class MemoryStrengthDetails(
    val memoryId: String,
    val totalStrength: Float,
    val decayFactor: Float,
    val importanceFactor: Float,
    val confidenceFactor: Float,
    val emotionalFactor: Float,
    val accessFactor: Float,
    val reinforcementFactor: Float,
    val halfLifeDays: Float
) {
    override fun toString(): String = buildString {
        appendLine("【记忆强度详情】")
        appendLine("记忆ID: ${memoryId.take(12)}...")
        appendLine("总强度: %.2f".format(totalStrength))
        appendLine("- 时间衰减: %.2f".format(decayFactor))
        appendLine("- 重要性: %.2f".format(importanceFactor))
        appendLine("- 置信度: %.2f".format(confidenceFactor))
        appendLine("- 情感强度: %.2f".format(emotionalFactor))
        appendLine("- 访问频率: %.2f".format(accessFactor))
        appendLine("- 强化次数: %.2f".format(reinforcementFactor))
        appendLine("半衰期: %.1f天".format(halfLifeDays))
    }
}
