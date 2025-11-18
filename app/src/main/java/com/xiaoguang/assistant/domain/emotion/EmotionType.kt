package com.xiaoguang.assistant.domain.emotion

/**
 * 情绪类型枚举
 *
 * 定义小光可以拥有的所有情绪状态
 */
enum class EmotionType {
    /** 开心 */
    HAPPY,

    /** 兴奋 */
    EXCITED,

    /** 平静 */
    CALM,

    /** 疲倦 */
    TIRED,

    /** 好奇/思考 */
    CURIOUS,

    /** 惊讶 */
    SURPRISED,

    /** 难过 */
    SAD,

    /** 焦虑 */
    ANXIOUS,

    /** 困惑 */
    CONFUSED,

    /** 沮丧 */
    FRUSTRATED,

    /** 中性/默认 */
    NEUTRAL;

    companion object {
        /**
         * 从字符串解析情绪类型
         */
        fun fromString(value: String): EmotionType {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                NEUTRAL
            }
        }
    }
}
