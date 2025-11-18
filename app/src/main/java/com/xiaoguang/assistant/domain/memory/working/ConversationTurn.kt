package com.xiaoguang.assistant.domain.memory.working

import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory

/**
 * 对话轮次数据模型
 *
 * 代表一轮完整的用户-AI交互，是工作记忆的基本单位。
 *
 * @property turnId 唯一标识符
 * @property userInput 用户输入内容
 * @property aiResponse AI回复内容
 * @property timestamp 对话时间戳
 * @property speakerName 说话人姓名（如果识别到）
 * @property emotionTag 情感标签
 * @property emotionIntensity 情感强度 (0.0-1.0)
 * @property emotionalValence 情感效价 (-1.0到1.0，负面到正面)
 * @property importance 重要性评分 (0.0-1.0)
 * @property relatedEntities 相关实体列表
 * @property shouldPromote 是否应晋升为长期记忆
 * @property promotionReason 晋升原因说明
 */
data class ConversationTurn(
    val turnId: String = "turn_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}",
    val userInput: String,
    val aiResponse: String,
    val timestamp: Long = System.currentTimeMillis(),
    val speakerName: String? = null,
    val emotionTag: String? = null,
    val emotionIntensity: Float = 0f,
    val emotionalValence: Float = 0f,
    val importance: Float = 0.5f,
    val relatedEntities: List<String> = emptyList(),
    val shouldPromote: Boolean = false,
    val promotionReason: String? = null
) {

    /**
     * 将对话轮次转换为长期记忆
     */
    fun toMemory(): Memory {
        val content = buildString {
            if (speakerName != null) {
                appendLine("【${speakerName}】: $userInput")
            } else {
                appendLine("【用户】: $userInput")
            }
            appendLine("【小光】: $aiResponse")
        }

        return Memory(
            id = turnId,
            content = content,
            category = MemoryCategory.EPISODIC,  // 对话属于情景记忆
            importance = importance,
            emotionalValence = emotionalValence,
            relatedEntities = relatedEntities,
            relatedCharacters = listOfNotNull(speakerName),
            timestamp = timestamp,
            createdAt = timestamp,
            emotionTag = emotionTag,
            emotionIntensity = emotionIntensity
        )
    }

    /**
     * 获取对话年龄（秒）
     */
    fun getAgeSeconds(): Long {
        return (System.currentTimeMillis() - timestamp) / 1000
    }

    /**
     * 获取对话摘要（用于日志）
     */
    fun getSummary(): String {
        val userPreview = userInput.take(30) + if (userInput.length > 30) "..." else ""
        val aiPreview = aiResponse.take(30) + if (aiResponse.length > 30) "..." else ""
        return "[$speakerName] $userPreview → $aiPreview (重要性: $importance)"
    }
}
