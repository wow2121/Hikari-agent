package com.xiaoguang.assistant.domain.model

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    // 说话人信息（用于多人对话识别）
    val speakerId: String? = null,       // 说话人唯一标识（personIdentifier）
    val speakerName: String? = null      // 说话人显示名称（personName 或 identifier）
)

enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}
