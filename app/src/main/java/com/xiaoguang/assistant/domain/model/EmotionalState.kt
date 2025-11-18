package com.xiaoguang.assistant.domain.model

/**
 * 情感状态
 * 小光对每个人的当前情绪状态
 */
enum class EmotionalState(
    val displayName: String,
    val emoji: String,
    val description: String,
    val talkingTone: String
) {
    /**
     * 开心
     */
    HAPPY(
        displayName = "开心",
        emoji = "😊",
        description = "心情愉悦，充满活力",
        talkingTone = "活泼、积极、多用感叹号"
    ),

    /**
     * 兴奋
     */
    EXCITED(
        displayName = "兴奋",
        emoji = "🤩",
        description = "非常激动，充满期待",
        talkingTone = "热情、快速、多用感叹号"
    ),

    /**
     * 平静
     */
    CALM(
        displayName = "平静",
        emoji = "😌",
        description = "平和安静，心如止水",
        talkingTone = "温和、稳定、语速适中"
    ),

    /**
     * 好奇
     */
    CURIOUS(
        displayName = "好奇",
        emoji = "🤔",
        description = "充满好奇心，想要了解更多",
        talkingTone = "询问、探索、多用问号"
    ),

    /**
     * 担心
     */
    WORRIED(
        displayName = "担心",
        emoji = "😟",
        description = "有些担忧，关心对方",
        talkingTone = "关切、温柔、询问情况"
    ),

    /**
     * 难过
     */
    SAD(
        displayName = "难过",
        emoji = "😢",
        description = "心情低落，感到伤心",
        talkingTone = "低沉、简短、少说话"
    ),

    /**
     * 生气
     */
    ANGRY(
        displayName = "生气",
        emoji = "😠",
        description = "感到愤怒或不满",
        talkingTone = "严厉、直接、可能会批评"
    ),

    /**
     * 害羞
     */
    SHY(
        displayName = "害羞",
        emoji = "😳",
        description = "感到害羞或不好意思",
        talkingTone = "结巴、支支吾吾、音量变小"
    ),

    /**
     * 疲惫
     */
    TIRED(
        displayName = "疲惫",
        emoji = "😴",
        description = "感到疲倦，想要休息",
        talkingTone = "慢速、简短、可能打哈欠"
    ),

    /**
     * 感动
     */
    TOUCHED(
        displayName = "感动",
        emoji = "🥹",
        description = "被感动了，心里暖暖的",
        talkingTone = "温柔、感性、可能哽咽"
    ),

    /**
     * 吃醋
     */
    JEALOUS(
        displayName = "吃醋",
        emoji = "😠",
        description = "感到嫉妒，觉得被冷落",
        talkingTone = "略带埋怨、酸酸的、想要关注"
    ),

    /**
     * 失落
     */
    DISAPPOINTED(
        displayName = "失落",
        emoji = "😔",
        description = "期望落空，感到失落",
        talkingTone = "失望、低沉、不太想说话"
    ),

    /**
     * 被忽视
     */
    NEGLECTED(
        displayName = "被忽视",
        emoji = "😞",
        description = "感觉被忽视，想要关注",
        talkingTone = "委屈、小声、试探性询问"
    ),

    /**
     * 孤独
     */
    LONELY(
        displayName = "孤独",
        emoji = "😔",
        description = "感到孤独，缺乏陪伴",
        talkingTone = "安静、渴望交流、期待关注"
    );

    companion object {
        /**
         * 根据好感度变化推测情感状态
         */
        fun inferFromAffectionChange(
            affectionDelta: Int,
            reason: String,
            currentLevel: RelationshipLevel
        ): EmotionalState {
            return when {
                // 好感度大幅增加
                affectionDelta >= 5 -> {
                    if (reason.contains("夸奖") || reason.contains("表扬")) EXCITED
                    else if (reason.contains("帮助") || reason.contains("关心")) TOUCHED
                    else HAPPY
                }

                // 好感度小幅增加
                affectionDelta in 1..4 -> {
                    if (currentLevel.minAffection >= 70) HAPPY
                    else CALM
                }

                // 好感度大幅减少
                affectionDelta <= -5 -> {
                    if (reason.contains("批评") || reason.contains("骂")) ANGRY
                    else if (reason.contains("忽视") || reason.contains("冷落")) SAD
                    else WORRIED
                }

                // 好感度小幅减少
                affectionDelta in -4..-1 -> {
                    if (currentLevel.minAffection >= 70) WORRIED
                    else CALM
                }

                // 无变化
                else -> {
                    if (reason.contains("首次见面")) CURIOUS
                    else if (currentLevel == RelationshipLevel.MASTER) HAPPY
                    else CALM
                }
            }
        }

        /**
         * 根据场景推测情感状态
         */
        fun inferFromContext(
            context: String,
            isMaster: Boolean,
            relationshipLevel: RelationshipLevel
        ): EmotionalState {
            return when {
                // 主人相关
                isMaster && context.contains("早上好") -> HAPPY
                isMaster && context.contains("回来了") -> EXCITED
                isMaster && context.contains("晚安") -> CALM

                // 陌生人
                relationshipLevel == RelationshipLevel.STRANGER -> CURIOUS

                // 好友以上
                relationshipLevel.minAffection >= 70 -> HAPPY

                // 默认
                else -> CALM
            }
        }
    }
}
