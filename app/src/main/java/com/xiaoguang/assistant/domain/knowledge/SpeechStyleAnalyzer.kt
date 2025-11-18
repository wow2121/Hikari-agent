package com.xiaoguang.assistant.domain.knowledge

import com.xiaoguang.assistant.domain.flow.service.FlowLlmService
import com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 主人识别异常检测器
 *
 * ⭐ 设计理念：
 * 1. 依靠长期积累的角色书知识（性格、兴趣、背景、说话风格等）
 * 2. 非常低频检测（平均100条消息才检测1次，随机触发）
 * 3. 不影响角色书的主要任务（学习和记录）
 * 4. 仅在积累足够知识后才开启检测
 *
 * TODO: 未来优化方案
 * 当前实现是基础版本，有更好的实现思路待后续优化：
 * - 可能使用更智能的触发机制（而非随机）
 * - 可能结合更多维度的数据（互动模式、时间规律等）
 * - 可能优化检测算法减少LLM调用
 * 但目前实现已经满足基本需求，不影响核心功能。
 */
@Singleton
class SpeechStyleAnalyzer @Inject constructor(
    private val characterBook: CharacterBook,
    private val flowLlmService: FlowLlmService
) {

    companion object {
        private const val MIN_PROFILE_COMPLETENESS = 0.3f  // 档案完整度至少30%才开启检测
        private const val ANOMALY_CHECK_PROBABILITY = 0.01f  // 每条消息1%概率检测（平均100条检测1次）
        private const val ANOMALY_THRESHOLD = 0.8f          // 异常阈值（提高到0.8，减少误报）
    }

    // 消息计数（用于统计）
    private var totalMessageCount = 0

    /**
     * 记录主人的消息（轻量级，不做任何处理）
     * ⚠️ 角色书会自动学习主人的信息，这里不需要重复学习
     */
    suspend fun recordMasterMessage(message: String, masterProfile: CharacterProfile) {
        totalMessageCount++
        // 不做任何处理，学习任务由CharacterBook和MemoryExtractionUseCase负责
    }

    /**
     * 检测主人识别异常（非常低频，随机触发）
     *
     * ⭐ 核心逻辑：
     * 1. 仅在档案足够完整时才检测（避免误报）
     * 2. 随机触发（平均100条消息1次）
     * 3. 综合判断整个角色档案，而非单一维度
     *
     * @param currentMessage 当前消息
     * @param masterProfile 主人档案
     * @return 异常检测结果（null表示无异常或不检测）
     */
    suspend fun detectStyleAnomaly(
        currentMessage: String,
        masterProfile: CharacterProfile,
        recentContext: List<String> = emptyList()
    ): StyleAnomalyResult? {
        try {
            // 1. 计算档案完整度
            val completeness = calculateProfileCompleteness(masterProfile)

            if (completeness < MIN_PROFILE_COMPLETENESS) {
                Timber.v("[MasterIdentityCheck] 档案完整度不足(${completeness}/${MIN_PROFILE_COMPLETENESS})，跳过检测")
                return null
            }

            // 2. 随机决定是否检测（平均100条消息1次）
            if (Random.nextFloat() > ANOMALY_CHECK_PROBABILITY) {
                return null  // 不检测
            }

            Timber.d("[MasterIdentityCheck] 🎲 触发异常检测 (消息#${totalMessageCount}, 档案完整度: $completeness)")

            // 3. 使用完整的角色档案进行综合判断
            val anomalyScore = flowLlmService.detectMasterIdentityAnomaly(
                currentMessage = currentMessage,
                masterProfile = masterProfile
            )

            if (anomalyScore != null && anomalyScore >= ANOMALY_THRESHOLD) {
                Timber.i("[MasterIdentityCheck] ⚠️ 检测到主人识别异常！分数: $anomalyScore")

                return StyleAnomalyResult(
                    anomalyScore = anomalyScore,
                    expectedStyle = buildProfileSummary(masterProfile),
                    detectedDifference = "与长期了解的主人形象不符",
                    suggestedResponse = generateAnomalyResponse(anomalyScore)
                )
            }

        } catch (e: Exception) {
            Timber.w(e, "[MasterIdentityCheck] 异常检测失败（非致命）")
        }

        return null
    }

    /**
     * 计算角色档案完整度（0-1）
     */
    private fun calculateProfileCompleteness(profile: CharacterProfile): Float {
        var score = 0f
        var maxScore = 0f

        // 说话风格
        maxScore += 0.3f
        if (profile.personality.speechStyle.isNotEmpty()) {
            score += 0.3f * (profile.personality.speechStyle.size / 10f).coerceAtMost(1f)
        }

        // 性格特征
        maxScore += 0.3f
        if (profile.personality.traits.isNotEmpty()) {
            score += 0.3f * (profile.personality.traits.size / 10f).coerceAtMost(1f)
        }

        // 兴趣偏好
        maxScore += 0.2f
        if (profile.preferences.interests.isNotEmpty() || profile.preferences.likes.isNotEmpty()) {
            val interestCount = profile.preferences.interests.size + profile.preferences.likes.size
            score += 0.2f * (interestCount / 10f).coerceAtMost(1f)
        }

        // 背景故事
        maxScore += 0.2f
        if (!profile.background.story.isNullOrBlank()) {
            score += 0.2f
        }

        return if (maxScore > 0) score / maxScore else 0f
    }

    /**
     * 构建档案摘要（用于LLM判断）
     */
    private fun buildProfileSummary(profile: CharacterProfile): String {
        val parts = mutableListOf<String>()

        if (profile.personality.speechStyle.isNotEmpty()) {
            parts.add("说话风格: ${profile.personality.speechStyle.joinToString(", ")}")
        }

        if (profile.personality.traits.isNotEmpty()) {
            parts.add("性格特征: ${profile.personality.traits.keys.joinToString(", ")}")
        }

        if (profile.preferences.interests.isNotEmpty()) {
            parts.add("兴趣: ${profile.preferences.interests.joinToString(", ")}")
        }

        if (profile.preferences.likes.isNotEmpty()) {
            parts.add("喜欢: ${profile.preferences.likes.joinToString(", ")}")
        }

        return parts.joinToString("; ")
    }

    /**
     * 生成异常反应（小光的怀疑话语）
     */
    private fun generateAnomalyResponse(anomalyScore: Float): String {
        return when {
            anomalyScore >= 0.9f -> listOf(
                "诶？主人今天说话的方式...好像有点奇怪呢？是发生什么事了吗？",
                "唔...主人你今天怎么了？说话感觉和平时不太一样...",
                "等等，你...真的是主人吗？总觉得哪里怪怪的..."
            ).random()

            anomalyScore >= 0.7f -> listOf(
                "嗯？主人今天心情不太一样吗？",
                "诶，主人今天说话的感觉有点不同呢~",
                "主人是不是遇到什么事了？感觉你今天有点不一样..."
            ).random()

            else -> ""
        }
    }

}

/**
 * 风格异常检测结果
 */
data class StyleAnomalyResult(
    val anomalyScore: Float,              // 异常分数（0-1）
    val expectedStyle: String,            // 预期风格
    val detectedDifference: String,       // 检测到的差异
    val suggestedResponse: String         // 建议的回应（小光的怀疑话语）
)
