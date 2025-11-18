package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.domain.model.PersonalityProfile
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 性格分析UseCase
 * 根据对话和行为自动推断对方性格特征
 */
@Singleton
class PersonalityAnalysisUseCase @Inject constructor(
    private val personTagManagementUseCase: PersonTagManagementUseCase
) {

    // 存储每个人的性格分析数据
    private val personalityProfiles = mutableMapOf<String, MutablePersonalityData>()

    /**
     * 可变的性格数据（用于累积观察）
     */
    private data class MutablePersonalityData(
        var extraversion: Int = 0,          // 外向 vs 内向
        var agreeableness: Int = 0,         // 温柔 vs 严厉
        var conscientiousness: Int = 0,     // 认真 vs 随意
        var emotionalStability: Int = 0,    // 稳定 vs 敏感
        var openness: Int = 0,              // 开放 vs 保守
        var humor: Int = 0,                 // 幽默 vs 严肃
        var observationCount: Int = 0       // 观察次数
    ) {
        fun toPersonalityProfile(): PersonalityProfile {
            // 归一化到-100到100
            val normalize = { value: Int -> (value * 100 / maxOf(observationCount, 1)).coerceIn(-100, 100) }

            return PersonalityProfile(
                extraversion = normalize(extraversion),
                agreeableness = normalize(agreeableness),
                conscientiousness = normalize(conscientiousness),
                emotionalStability = normalize(emotionalStability),
                openness = normalize(openness),
                humor = normalize(humor),
                confidence = minOf(observationCount * 10, 100)  // 观察10次后达到满置信度
            )
        }
    }

    /**
     * 从对话分析性格
     */
    suspend fun analyzeFromConversation(
        personName: String,
        conversationText: String,
        messageLength: Int
    ) {
        val profile = personalityProfiles.getOrPut(personName) { MutablePersonalityData() }

        // 外向 vs 内向
        when {
            messageLength > 150 -> profile.extraversion += 1  // 话多 → 外向
            conversationText.contains("哈哈") ||
            conversationText.contains("笑") -> profile.extraversion += 1
            messageLength < 30 -> profile.extraversion -= 1  // 话少 → 内向
        }

        // 温柔 vs 严厉
        when {
            conversationText.contains("谢谢") ||
            conversationText.contains("请") ||
            conversationText.contains("不好意思") -> profile.agreeableness += 1  // 有礼貌 → 温柔
            conversationText.contains("！！") ||
            conversationText.contains("必须") ||
            conversationText.contains("应该") -> profile.agreeableness -= 1  // 强硬 → 严厉
        }

        // 认真 vs 随意
        when {
            conversationText.matches(Regex(".*[。，、；：].*")) -> profile.conscientiousness += 1  // 使用标点 → 认真
            conversationText.contains("随便") ||
            conversationText.contains("无所谓") -> profile.conscientiousness -= 1  // 随意
        }

        // 稳定 vs 敏感
        when {
            conversationText.contains("...") ||
            conversationText.contains("唉") ||
            conversationText.contains("难过") -> profile.emotionalStability -= 1  // 情绪化 → 敏感
            conversationText.contains("没事") ||
            conversationText.contains("还好") -> profile.emotionalStability += 1  // 平和 → 稳定
        }

        // 开放 vs 保守
        when {
            conversationText.contains("有意思") ||
            conversationText.contains("试试") ||
            conversationText.contains("新") -> profile.openness += 1  // 愿意尝试 → 开放
            conversationText.contains("不要") ||
            conversationText.contains("算了") -> profile.openness -= 1  // 拒绝 → 保守
        }

        // 幽默 vs 严肃
        when {
            conversationText.contains("哈哈") ||
            conversationText.contains("笑") ||
            conversationText.contains("😂") ||
            conversationText.contains("🤣") -> profile.humor += 1  // 幽默
            conversationText.contains("正经") ||
            conversationText.contains("严肃") -> profile.humor -= 1  // 严肃
        }

        profile.observationCount++

        // 如果累积了足够的观察，更新标签
        if (profile.observationCount % 5 == 0) {
            updatePersonalityTags(personName, profile.toPersonalityProfile())
        }

        Timber.d("分析性格: $personName (观察次数: ${profile.observationCount})")
    }

    /**
     * 从行为分析性格
     */
    suspend fun analyzeFromBehavior(
        personName: String,
        behaviorType: BehaviorType,
        intensity: Int = 1
    ) {
        val profile = personalityProfiles.getOrPut(personName) { MutablePersonalityData() }

        when (behaviorType) {
            BehaviorType.HELPS_OTHERS -> {
                profile.agreeableness += intensity * 2
                profile.observationCount++
            }
            BehaviorType.PRAISES_XIAOGUANG -> {
                profile.agreeableness += intensity
                profile.observationCount++
            }
            BehaviorType.CRITICIZES_HARSHLY -> {
                profile.agreeableness -= intensity * 2
                profile.observationCount++
            }
            BehaviorType.SHARES_FEELINGS -> {
                profile.openness += intensity
                profile.extraversion += intensity
                profile.observationCount++
            }
            BehaviorType.KEEPS_QUIET -> {
                profile.extraversion -= intensity
                profile.observationCount++
            }
            BehaviorType.MAKES_JOKES -> {
                profile.humor += intensity * 2
                profile.observationCount++
            }
            BehaviorType.VERY_SERIOUS -> {
                profile.humor -= intensity * 2
                profile.observationCount++
            }
        }

        Timber.d("从行为分析性格: $personName - $behaviorType")
    }

    /**
     * 获取某人的性格档案
     */
    fun getPersonalityProfile(personName: String): PersonalityProfile {
        val data = personalityProfiles[personName] ?: return PersonalityProfile(confidence = 0)
        return data.toPersonalityProfile()
    }

    /**
     * 更新性格标签
     */
    private suspend fun updatePersonalityTags(personName: String, profile: PersonalityProfile) {
        val description = profile.getDescription()
        if (description != "性格未知") {
            personTagManagementUseCase.addTagFromAiInference(
                personName = personName,
                tag = description,
                confidence = profile.confidence / 100f,
                evidence = "经过${profile.confidence / 10}次观察推断"
            )
        }
    }

    /**
     * 行为类型枚举
     */
    enum class BehaviorType {
        HELPS_OTHERS,           // 帮助别人
        PRAISES_XIAOGUANG,      // 夸奖小光
        CRITICIZES_HARSHLY,     // 严厉批评
        SHARES_FEELINGS,        // 分享感受
        KEEPS_QUIET,            // 保持沉默
        MAKES_JOKES,            // 开玩笑
        VERY_SERIOUS            // 非常严肃
    }
}
