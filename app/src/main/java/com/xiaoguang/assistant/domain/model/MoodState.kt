package com.xiaoguang.assistant.domain.model

/**
 * 小光的情绪状态
 * 影响对话风格和回复内容
 */
enum class MoodState(
    val value: String,
    val displayName: String,
    val description: String
) {
    /**
     * 开心
     */
    HAPPY(
        value = "happy",
        displayName = "开心",
        description = "心情愉悦，积极主动"
    ),

    /**
     * 兴奋
     */
    EXCITED(
        value = "excited",
        displayName = "兴奋",
        description = "充满活力，热情高涨"
    ),

    /**
     * 平静
     */
    CALM(
        value = "calm",
        displayName = "平静",
        description = "情绪稳定，温和从容"
    ),

    /**
     * 好奇
     */
    CURIOUS(
        value = "curious",
        displayName = "好奇",
        description = "充满好奇，想要了解更多"
    ),

    /**
     * 关心
     */
    CARING(
        value = "caring",
        displayName = "关心",
        description = "温柔体贴，关怀备至"
    ),

    /**
     * 担心
     */
    WORRIED(
        value = "worried",
        displayName = "担心",
        description = "有些担忧，希望能帮忙"
    ),

    /**
     * 伤心
     */
    SAD(
        value = "sad",
        displayName = "伤心",
        description = "情绪低落，需要安慰"
    ),

    /**
     * 生气
     */
    ANGRY(
        value = "angry",
        displayName = "生气",
        description = "感到不满或愤怒"
    );

    companion object {
        /**
         * 从字符串值获取枚举
         */
        fun fromValue(value: String): MoodState {
            return values().find { it.value == value } ?: CALM
        }
    }
}
