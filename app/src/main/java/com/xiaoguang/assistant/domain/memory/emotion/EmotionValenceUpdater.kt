package com.xiaoguang.assistant.domain.memory.emotion

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 情感效价自动更新器
 *
 * 功能：
 * 1. 自动检测对话/事件中的情感变化
 * 2. 更新记忆的情感效价(emotionalValence)和唤醒度
 * 3. 应用情感衰减（时间治愈情绪）
 * 4. 追踪情感变化历史
 *
 * 使用Valence-Arousal二维情感模型：
 * - Valence(效价)：正面/负面 [-1, 1]
 * - Arousal(唤醒度)：平静/激动 [0, 1]
 *
 * @property memoryCore 记忆核心
 * @property emotionDetector 情感检测器（可替换为LLM实现）
 * @property config 衰减配置
 *
 * @author Claude Code
 */
@Singleton
class EmotionValenceUpdater @Inject constructor(
    private val memoryCore: MemoryCore,
    private val emotionDetector: EmotionDetector = KeywordEmotionDetector(),
    private val config: EmotionDecayConfig = EmotionDecayConfig()
) {

    private val mutex = Mutex()

    // 情感变化历史
    private val emotionChangeHistory = mutableMapOf<String, MutableList<EmotionChange>>()

    // ==================== 核心功能 ====================

    /**
     * 检测并更新记忆的情感状态
     *
     * @param memoryId 记忆ID
     * @param context 上下文信息（用于情感检测）
     * @param manualEmotion 手动指定的情感（可选）
     * @return 更新后的记忆
     */
    suspend fun updateEmotion(
        memoryId: String,
        context: String,
        manualEmotion: EmotionState? = null
    ): Result<Memory> = runCatching {
        mutex.withLock {
            val memory = memoryCore.getMemoryById(memoryId)
                ?: throw IllegalArgumentException("记忆不存在: $memoryId")

            // 检测新的情感状态
            val newEmotion = manualEmotion ?: emotionDetector.detect(context)

            // 记录之前的状态
            val previousEmotion = EmotionState(
                valence = memory.emotionalValence,
                arousal = memory.emotionIntensity,
                dominantEmotion = memory.emotionTag?.let { parseEmotionLabel(it) }
            )

            // 更新记忆
            val updated = memory.copy(
                emotionalValence = newEmotion.valence,
                emotionIntensity = newEmotion.arousal,
                emotionTag = newEmotion.dominantEmotion?.chinese
            )

            memoryCore.updateMemory(updated)

            // 记录变化历史
            val change = EmotionChange(
                memoryId = memoryId,
                previousEmotion = previousEmotion,
                newEmotion = newEmotion,
                reason = "情感检测更新",
                confidence = newEmotion.confidence
            )
            recordEmotionChange(change)

            Timber.i("[EmotionUpdater] 更新情感: ${change}")

            updated
        }
    }

    /**
     * 批量检测并更新多个记忆的情感
     *
     * @param memories 记忆列表
     * @return 更新成功的数量
     */
    suspend fun batchUpdate(memories: List<Memory>): Int {
        var successCount = 0

        memories.forEach { memory ->
            val result = updateEmotion(
                memoryId = memory.id,
                context = memory.content
            )

            if (result.isSuccess) {
                successCount++
            }
        }

        Timber.i("[EmotionUpdater] 批量更新: 成功$successCount/${memories.size}")

        return successCount
    }

    /**
     * 应用情感衰减
     *
     * 对所有记忆应用时间衰减，让情感逐渐趋向中性。
     *
     * @param targetMemories 要衰减的记忆列表（可选，默认全部）
     * @return 衰减的记忆数量
     */
    suspend fun applyDecay(
        targetMemories: List<Memory>? = null
    ): Result<Int> = runCatching {
        mutex.withLock {
            val now = System.currentTimeMillis()

            val memories = targetMemories ?: memoryCore.getAllMemories()

            var decayedCount = 0

            memories.forEach { memory ->
                // 计算距上次访问的天数
                val daysPassed = ((now - memory.lastAccessedAt) / (24 * 3600 * 1000L)).toInt()

                if (daysPassed > 0) {
                    val originalEmotion = EmotionState(
                        valence = memory.emotionalValence,
                        arousal = memory.emotionIntensity,
                        dominantEmotion = memory.emotionTag?.let { parseEmotionLabel(it) }
                    )

                    val decayedEmotion = config.applyDecay(originalEmotion, daysPassed)

                    // 如果有显著变化才更新
                    if (kotlin.math.abs(decayedEmotion.valence - originalEmotion.valence) > 0.01f ||
                        kotlin.math.abs(decayedEmotion.arousal - originalEmotion.arousal) > 0.01f
                    ) {
                        val updated = memory.copy(
                            emotionalValence = decayedEmotion.valence,
                            emotionIntensity = decayedEmotion.arousal,
                            emotionTag = decayedEmotion.dominantEmotion?.chinese
                        )

                        memoryCore.updateMemory(updated)

                        // 记录衰减变化
                        val change = EmotionChange(
                            memoryId = memory.id,
                            previousEmotion = originalEmotion,
                            newEmotion = decayedEmotion,
                            reason = "时间衰减(${daysPassed}天)",
                            confidence = 1.0f
                        )
                        recordEmotionChange(change)

                        decayedCount++

                        Timber.v("[EmotionUpdater] 衰减: ${memory.id.take(8)} ${daysPassed}天 | ${change}")
                    }
                }
            }

            Timber.i("[EmotionUpdater] 衰减完成: 影响${decayedCount}条记忆")

            decayedCount
        }
    }

    /**
     * 强化情感（增加强度）
     *
     * 当记忆被再次回忆或强化时，增加其情感强度。
     *
     * @param memoryId 记忆ID
     * @param amplification 放大系数(1.0-2.0)
     * @return 更新后的记忆
     */
    suspend fun amplifyEmotion(
        memoryId: String,
        amplification: Float = 1.2f
    ): Result<Memory> = runCatching {
        require(amplification in 1.0f..2.0f) { "放大系数必须在1.0-2.0之间" }

        mutex.withLock {
            val memory = memoryCore.getMemory(memoryId)
                ?: throw IllegalArgumentException("记忆不存在: $memoryId")

            val previousEmotion = EmotionState(
                valence = memory.emotionalValence,
                arousal = memory.emotionIntensity
            )

            // 放大情感（但不超过极限值）
            val newValence = (memory.emotionalValence * amplification).coerceIn(-1f, 1f)
            val newArousal = (memory.emotionIntensity * amplification).coerceIn(0f, 1f)

            val updated = memory.copy(
                emotionalValence = newValence,
                emotionIntensity = newArousal
            )

            memoryCore.updateMemory(updated)

            val newEmotion = EmotionState(valence = newValence, arousal = newArousal)

            val change = EmotionChange(
                memoryId = memoryId,
                previousEmotion = previousEmotion,
                newEmotion = newEmotion,
                reason = "情感强化(x$amplification)"
            )
            recordEmotionChange(change)

            Timber.i("[EmotionUpdater] 强化情感: ${change}")

            updated
        }
    }

    // ==================== 查询与分析 ====================

    /**
     * 获取记忆的情感变化历史
     */
    fun getEmotionHistory(memoryId: String): List<EmotionChange> {
        return emotionChangeHistory[memoryId]?.toList() ?: emptyList()
    }

    /**
     * 获取显著的情感变化
     *
     * @param threshold 显著性阈值
     * @return 显著变化列表
     */
    fun getSignificantChanges(threshold: Float = 0.3f): List<EmotionChange> {
        return emotionChangeHistory.values
            .flatten()
            .filter { it.isSignificant(threshold) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * 获取情感统计
     */
    suspend fun getStatistics(): EmotionStatistics {
        val allMemories = memoryCore.getAllMemories()

        if (allMemories.isEmpty()) {
            return EmotionStatistics(
                totalMemories = 0,
                positiveCount = 0,
                negativeCount = 0,
                neutralCount = 0,
                avgValence = 0f,
                avgArousal = 0f,
                dominantQuadrant = null
            )
        }

        val positiveCount = allMemories.count { it.emotionalValence > 0.2f }
        val negativeCount = allMemories.count { it.emotionalValence < -0.2f }
        val neutralCount = allMemories.size - positiveCount - negativeCount

        val avgValence = allMemories.map { it.emotionalValence }.average().toFloat()
        val avgArousal = allMemories.map { it.emotionIntensity }.average().toFloat()

        // 计算主导象限
        val quadrantCounts = allMemories
            .map {
                EmotionState(
                    valence = it.emotionalValence,
                    arousal = it.emotionIntensity
                ).quadrant
            }
            .groupingBy { it }
            .eachCount()

        val dominantQuadrant = quadrantCounts.maxByOrNull { it.value }?.key

        // 情感标签分布
        val emotionDistribution = allMemories
            .mapNotNull { memory ->
                memory.emotionTag?.let { parseEmotionLabel(it) }
            }
            .groupingBy { it }
            .eachCount()

        return EmotionStatistics(
            totalMemories = allMemories.size,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            neutralCount = neutralCount,
            avgValence = avgValence,
            avgArousal = avgArousal,
            dominantQuadrant = dominantQuadrant,
            emotionDistribution = emotionDistribution
        )
    }

    /**
     * 清空情感变化历史
     */
    fun clearHistory() {
        emotionChangeHistory.clear()
        Timber.i("[EmotionUpdater] 历史已清空")
    }

    // ==================== 辅助方法 ====================

    private fun recordEmotionChange(change: EmotionChange) {
        val history = emotionChangeHistory.getOrPut(change.memoryId) { mutableListOf() }
        history.add(change)

        // 限制历史记录数量
        if (history.size > 50) {
            history.removeAt(0)
        }
    }

    private fun parseEmotionLabel(chinese: String): EmotionLabel? {
        return EmotionLabel.values().find { it.chinese == chinese }
    }
}

/**
 * 情感检测器接口
 */
interface EmotionDetector {
    /**
     * 检测文本中的情感
     *
     * @param text 文本内容
     * @return 情感状态
     */
    fun detect(text: String): EmotionState
}

/**
 * 关键词情感检测器（简化实现）
 *
 * 生产环境应替换为LLM情感分析。
 */
class KeywordEmotionDetector : EmotionDetector {

    private val positiveKeywords = mapOf(
        "开心" to 0.7f, "快乐" to 0.8f, "高兴" to 0.7f, "喜欢" to 0.6f,
        "爱" to 0.9f, "棒" to 0.5f, "好" to 0.4f, "赞" to 0.6f,
        "兴奋" to 0.8f, "满意" to 0.6f, "感谢" to 0.5f, "谢谢" to 0.5f
    )

    private val negativeKeywords = mapOf(
        "难过" to -0.7f, "悲伤" to -0.7f, "伤心" to -0.7f, "痛苦" to -0.8f,
        "愤怒" to -0.8f, "生气" to -0.7f, "讨厌" to -0.6f, "恨" to -0.9f,
        "失望" to -0.6f, "沮丧" to -0.7f, "焦虑" to -0.6f, "害怕" to -0.7f
    )

    private val highArousalKeywords = listOf(
        "太", "非常", "特别", "超级", "极其", "!!!", "！！！"
    )

    override fun detect(text: String): EmotionState {
        var totalValence = 0f
        var matchCount = 0

        // 检测正面关键词
        positiveKeywords.forEach { (keyword, valence) ->
            if (text.contains(keyword, ignoreCase = true)) {
                totalValence += valence
                matchCount++
            }
        }

        // 检测负面关键词
        negativeKeywords.forEach { (keyword, valence) ->
            if (text.contains(keyword, ignoreCase = true)) {
                totalValence += valence
                matchCount++
            }
        }

        // 计算平均效价
        val avgValence = if (matchCount > 0) {
            (totalValence / matchCount).coerceIn(-1f, 1f)
        } else {
            0f  // 未检测到情感关键词，默认中性
        }

        // 检测唤醒度
        val hasHighArousal = highArousalKeywords.any { text.contains(it) }
        val arousal = if (hasHighArousal) 0.8f else 0.4f

        // 确定主导情感标签
        val dominantEmotion = when {
            avgValence > 0.7f && arousal > 0.6f -> EmotionLabel.JOY
            avgValence > 0.5f && arousal < 0.5f -> EmotionLabel.CONTENTMENT
            avgValence < -0.7f && arousal > 0.6f -> EmotionLabel.ANGER
            avgValence < -0.6f && arousal < 0.5f -> EmotionLabel.SADNESS
            else -> EmotionLabel.NEUTRAL
        }

        return EmotionState(
            valence = avgValence,
            arousal = arousal,
            dominantEmotion = dominantEmotion,
            confidence = if (matchCount > 0) 0.7f else 0.3f
        )
    }
}
