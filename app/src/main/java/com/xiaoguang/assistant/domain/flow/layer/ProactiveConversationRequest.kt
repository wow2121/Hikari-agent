package com.xiaoguang.assistant.domain.flow.layer

import com.xiaoguang.assistant.domain.flow.model.SpeakPriority
import com.xiaoguang.assistant.domain.flow.model.SpeakTiming

/**
 * 心流系统的主动对话请求
 *
 * 这不是一个完整的消息，而是一个"想要说话"的意图
 * 实际的对话内容会由LLM根据这个意图和当前上下文生成
 */
data class ProactiveConversationRequest(
    /**
     * 对话意图/主题
     * 例如："想分享一个想法"、"关心主人"、"询问矛盾"
     */
    val intent: String,

    /**
     * 意图类型
     */
    val intentType: ConversationIntentType,

    /**
     * 优先级
     */
    val priority: SpeakPriority,

    /**
     * 时机
     */
    val timing: SpeakTiming,

    /**
     * 触发原因
     */
    val reason: String,

    /**
     * 相关上下文（可选）
     * 例如：最近的对话、检测到的矛盾、关心的事件等
     */
    val contextHint: String? = null,

    /**
     * 情绪提示（可选）
     */
    val emotionHint: String? = null,

    /**
     * 时间戳
     */
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 对话意图类型
 */
enum class ConversationIntentType(
    val displayName: String,
    val description: String
) {
    // 内心想法相关
    CURIOSITY("好奇", "对某事感到好奇，想要询问"),
    CARE("关心", "关心主人的状态，想要问候"),
    SHARE("分享", "有想法想要分享"),
    QUESTION("疑问", "有疑问想要询问"),

    // 情绪驱动
    EXCITEMENT("兴奋", "因兴奋想要表达"),
    WORRY("担心", "因担心想要确认"),
    BOREDOM("无聊", "因无聊想要聊天"),

    // 主动关心
    PROACTIVE_GREETING("主动问候", "主动打招呼"),
    PROACTIVE_CHECK("主动关心", "主动关心主人"),

    // 矛盾检测
    DETECT_CONTRADICTION("发现矛盾", "发现对话中的矛盾"),

    // 随机
    RANDOM_CHAT("随机聊天", "随机想要聊天");

    /**
     * 转换为系统提示
     */
    fun toSystemPrompt(): String {
        return when (this) {
            CURIOSITY -> "你现在对某件事感到好奇，想要询问主人。请用自然、好奇的语气提问。"
            CARE -> "你现在关心主人的状态，想要问候一下。请用温暖、关心的语气说话。"
            SHARE -> "你现在有一个想法想要分享给主人。请用兴奋、分享的语气说话。"
            QUESTION -> "你现在有一个疑问想要询问主人。请用疑惑、求知的语气提问。"
            EXCITEMENT -> "你现在感到兴奋，想要表达这种情绪。请用兴奋、活跃的语气说话。"
            WORRY -> "你现在感到担心，想要确认一下情况。请用担心、关切的语气说话。"
            BOREDOM -> "你现在感到有点无聊，想要找主人聊聊天。请用轻松、闲聊的语气说话。"
            PROACTIVE_GREETING -> "你现在想要主动和主人打招呼。请用友好、问候的语气说话。"
            PROACTIVE_CHECK -> "你现在想要主动关心主人。请用温暖、体贴的语气说话。"
            DETECT_CONTRADICTION -> "你发现了对话中的一些矛盾或不一致之处，想要询问清楚。请用好奇、求证的语气提问。"
            RANDOM_CHAT -> "你现在想要随便聊聊天。请用轻松、随意的语气说话。"
        }
    }
}
