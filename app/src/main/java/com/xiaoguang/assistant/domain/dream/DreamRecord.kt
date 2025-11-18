package com.xiaoguang.assistant.domain.dream

/**
 * 梦境记录
 */
data class DreamRecord(
    /**
     * 梦境ID
     */
    val id: Long,

    /**
     * 梦境内容
     */
    val content: String,

    /**
     * 梦境类型
     */
    val type: DreamType,

    /**
     * 情感色彩
     */
    val emotionalTone: EmotionalTone,

    /**
     * 梦境素材（关联的记忆片段）
     */
    val memoryFragments: List<String>,

    /**
     * 创建时间（做梦时间）
     */
    val createdAt: Long,

    /**
     * 是否已分享
     */
    var shared: Boolean = false
)

/**
 * 梦境类型
 */
enum class DreamType(val displayName: String, val description: String) {
    HAPPY_DREAM("美梦", "开心的梦"),
    STRANGE_DREAM("奇怪的梦", "不合逻辑的奇怪梦"),
    NOSTALGIC_DREAM("怀旧梦", "梦到过去的事"),
    PROPHETIC_DREAM("预言梦", "像是预示什么的梦"),
    NIGHTMARE("噩梦", "可怕的梦"),
    RANDOM_DREAM("随机梦", "毫无头绪的梦")
}

/**
 * 情感色彩
 */
enum class EmotionalTone {
    POSITIVE,   // 正面
    NEUTRAL,    // 中性
    NEGATIVE    // 负面
}
