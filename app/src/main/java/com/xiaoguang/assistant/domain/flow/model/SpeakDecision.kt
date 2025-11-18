package com.xiaoguang.assistant.domain.flow.model

/**
 * 发言决策
 */
data class SpeakDecision(
    val shouldSpeak: Boolean,
    val confidence: Float,  // 0-1
    val reason: String,
    val suggestedContent: String? = null,
    val timing: SpeakTiming,
    val priority: SpeakPriority = SpeakPriority.NORMAL
) {
    /**
     * 是否应该立即执行
     */
    fun shouldExecuteImmediately(): Boolean {
        return shouldSpeak && timing == SpeakTiming.IMMEDIATE
    }

    /**
     * 是否需要等待
     */
    fun needsWaiting(): Boolean {
        return shouldSpeak && timing != SpeakTiming.IMMEDIATE
    }
}

/**
 * 发言时机
 */
enum class SpeakTiming(
    val displayName: String,
    val description: String
) {
    IMMEDIATE(
        "立即",
        "立即说话（被叫到名字、紧急情况）"
    ),
    WAIT_FOR_GAP(
        "等待间隙",
        "等待对话间隙（当前在讨论中）"
    ),
    WAIT_FOR_OPPORTUNITY(
        "等待机会",
        "等待更好的机会（不急）"
    ),
    DONT_SPEAK(
        "不说话",
        "选择沉默"
    )
}

/**
 * 发言优先级
 */
enum class SpeakPriority {
    URGENT,    // 紧急（被叫、需要帮助）
    HIGH,      // 高（强烈情绪、重要话题）
    NORMAL,    // 正常（一般主动发言）
    LOW        // 低（随便说说）
}
