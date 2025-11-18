package com.xiaoguang.assistant.domain.flow.model

import com.xiaoguang.assistant.domain.model.EmotionalState
import com.xiaoguang.assistant.domain.model.Message
import java.time.LocalDateTime
import kotlin.time.Duration

/**
 * 感知数据
 * 小光对当前环境的感知
 */
data class Perception(
    // 时间感知
    val currentTime: LocalDateTime,
    val timeSinceLastInteraction: Duration,
    val silenceDuration: Duration,
    val lastSpeakTime: Long,

    // 情感感知
    val currentEmotion: EmotionalState,
    val emotionIntensity: Float,

    // 环境感知
    val recentMessages: List<Message>,
    val hasRecentMessages: Boolean,
    val environmentNoise: Float,  // 对话活跃度 0-1

    // 关系感知
    val masterPresent: Boolean,
    val friendsPresent: List<String>,
    val strangerPresent: Boolean,

    // ✅ Phase 1: 多说话人检测
    val hasMultipleSpeakers: Boolean = false,       // 是否检测到多人同时说话
    val estimatedSpeakerCount: Int = 0,             // 估计的总说话人数量
    val recentMultiSpeakerUtterances: Int = 0,      // 最近10秒内多人语句数量

    // 【新增】性格和关系数据（老系统集成）
    val masterPersonality: String? = null,  // 主人性格描述（如"外向、温柔、幽默"）
    val relationshipIntimacy: Float = 0.5f,  // 主人关系亲密度 0-1

    // 【新增】当前说话人信息（用于个性化回复）
    val currentSpeakerName: String? = null,  // 当前说话人名字
    val currentSpeakerPersonality: String? = null,  // 当前说话人性格
    val currentSpeakerIntimacy: Float = 0.5f,  // 与当前说话人的亲密度 0-1

    // 特殊标记
    val mentionsXiaoguang: Boolean,  // 有人叫小光
    val isPrivateConversation: Boolean,  // 私密对话
    val isInClass: Boolean,  // 在上课

    // 时段信息
    val timeOfDay: TimeOfDay,
    val isWorkingHours: Boolean,

    // 生物状态（时间感和疲劳）
    val biologicalState: BiologicalState? = null
) {
    /**
     * 计算环境适宜度（0-1）
     */
    fun calculateEnvironmentFitness(): Float {
        var fitness = 0.5f

        // 主人在场，提高适宜度
        if (masterPresent) fitness += 0.3f

        // 有人叫小光，最高适宜度
        if (mentionsXiaoguang) return 1.0f

        // 私密对话，降低适宜度
        if (isPrivateConversation) fitness -= 0.4f

        // 在上课，最低适宜度
        if (isInClass) return 0.0f

        // 环境噪音适中（0.3-0.7），提高适宜度
        if (environmentNoise in 0.3f..0.7f) {
            fitness += 0.2f
        } else if (environmentNoise > 0.8f) {
            fitness -= 0.3f  // 太吵，降低
        }

        return fitness.coerceIn(0f, 1f)
    }

    /**
     * 是否是合适的发言时机
     */
    fun isSuitableTime(): Boolean {
        return !isPrivateConversation && !isInClass
    }
}

/**
 * 一天中的时段
 */
enum class TimeOfDay(val displayName: String) {
    LATE_NIGHT("深夜"),       // 0-6点
    EARLY_MORNING("清晨"),    // 6-9点
    MORNING("上午"),          // 9-12点
    NOON("中午"),             // 12-14点
    AFTERNOON("下午"),        // 14-18点
    EVENING("傍晚"),          // 18-22点
    NIGHT("晚上")             // 22-24点
}

/**
 * 根据当前时间判断时段
 */
fun LocalDateTime.toTimeOfDay(): TimeOfDay {
    return when (hour) {
        in 0..5 -> TimeOfDay.LATE_NIGHT
        in 6..8 -> TimeOfDay.EARLY_MORNING
        in 9..11 -> TimeOfDay.MORNING
        in 12..13 -> TimeOfDay.NOON
        in 14..17 -> TimeOfDay.AFTERNOON
        in 18..21 -> TimeOfDay.EVENING
        in 22..23 -> TimeOfDay.NIGHT
        else -> TimeOfDay.LATE_NIGHT
    }
}
