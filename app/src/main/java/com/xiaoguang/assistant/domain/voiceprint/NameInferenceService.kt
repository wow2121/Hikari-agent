package com.xiaoguang.assistant.domain.voiceprint

import com.xiaoguang.assistant.domain.flow.service.FlowLlmService
import com.xiaoguang.assistant.domain.model.Message
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 名称推断服务
 *
 * 使用LLM从对话中推断陌生人的名称
 *
 * 场景示例：
 * - A: "老王，你来啦" B: "诶，什么事" → 推断B可能叫"老王"
 * - A: "小明，帮我拿一下" B: "好的" → 推断B可能叫"小明"
 * - A: "张总您好" B: "你好" → 推断B可能是"张总"或"张XX"
 *
 * 特性：
 * - 基于LLM的智能推断（而非规则）
 * - 考虑小光的人设和语境
 * - 返回置信度和多个候选名称
 * - 处理各种称呼方式（名字、昵称、职称等）
 */
@Singleton
class NameInferenceService @Inject constructor(
    private val flowLlmService: FlowLlmService
) {

    /**
     * 从对话中推断陌生人的名称
     *
     * @param strangerId 陌生人的临时ID
     * @param recentMessages 最近的对话消息
     * @param conversationContext 对话上下文描述
     * @return 推断结果，包含候选名称和置信度
     */
    suspend fun inferNameFromConversation(
        strangerId: String,
        recentMessages: List<Message>,
        conversationContext: String = ""
    ): NameInferenceResult {
        return try {
            // 构建对话文本
            val conversationText = buildConversationText(recentMessages, strangerId)

            if (conversationText.isBlank()) {
                return NameInferenceResult(
                    success = false,
                    inferred = false,
                    candidateNames = emptyList(),
                    confidence = 0f,
                    reasoning = "对话内容为空，无法推断"
                )
            }

            // 使用LLM推断名称
            val llmResult = callLLMForNameInference(conversationText, conversationContext)

            llmResult

        } catch (e: Exception) {
            Timber.e(e, "[NameInferenceService] 名称推断失败")
            NameInferenceResult(
                success = false,
                inferred = false,
                candidateNames = emptyList(),
                confidence = 0f,
                reasoning = "推断过程出错: ${e.message}"
            )
        }
    }

    /**
     * 使用LLM进行名称推断
     */
    private suspend fun callLLMForNameInference(
        conversationText: String,
        context: String
    ): NameInferenceResult {
        return try {
            val prompt = buildInferencePrompt(conversationText, context)

            // 调用FlowLlmService的名称推断功能
            val result = flowLlmService.inferPersonName(
                conversation = conversationText,
                context = context,
                xiaoguangPersonality = getXiaoguangPersonalityHint()
            )

            result

        } catch (e: Exception) {
            Timber.e(e, "[NameInferenceService] LLM调用失败")
            NameInferenceResult(
                success = false,
                inferred = false,
                candidateNames = emptyList(),
                confidence = 0f,
                reasoning = "LLM调用失败: ${e.message}"
            )
        }
    }

    /**
     * 构建对话文本
     */
    private fun buildConversationText(messages: List<Message>, targetStrangerId: String): String {
        return buildString {
            messages.forEach { msg ->
                val speaker = when {
                    msg.speakerId == targetStrangerId -> "陌生人"
                    msg.speakerName != null -> msg.speakerName
                    msg.role == com.xiaoguang.assistant.domain.model.MessageRole.USER -> "某人"
                    msg.role == com.xiaoguang.assistant.domain.model.MessageRole.ASSISTANT -> "小光"
                    else -> "未知"
                }

                appendLine("[$speaker] ${msg.content}")
            }
        }.trim()
    }

    /**
     * 构建推断提示词
     */
    private fun buildInferencePrompt(conversationText: String, context: String): String {
        return """
你是小光，一个可爱的AI助手。现在你听到了周围的对话，需要推断对话中陌生人的名字。

## 对话内容：
$conversationText

${if (context.isNotBlank()) "## 上下文：\n$context\n" else ""}

## 任务：
仔细分析对话，推断"陌生人"的真实名字或称呼。

## 分析要点：
1. 寻找直接称呼（如"老王"、"小明"、"张总"）
2. 注意对话中的暗示和间接提及
3. 考虑职称、昵称、关系称呼
4. 判断推断的可信度

## 返回格式（JSON）：
```json
{
  "inferred": true/false,  // 是否成功推断出名称
  "candidateNames": ["名称1", "名称2", ...],  // 候选名称列表（按可能性排序）
  "confidence": 0.0-1.0,  // 整体置信度
  "reasoning": "推断理由"  // 简短说明推断依据
}
```

## 注意事项：
- 如果对话中没有提到名字，返回 inferred: false
- 名称可以是真名、昵称、或职称（如"老王"、"小明"、"张总"）
- 置信度要诚实评估，不确定时降低置信度
- 用小光的视角和语气给出推断理由

请分析并返回JSON结果：
        """.trimIndent()
    }

    /**
     * 获取小光的人设提示
     */
    private fun getXiaoguangPersonalityHint(): String {
        return com.xiaoguang.assistant.domain.personality.XiaoguangPersonality.getPersonalitySystemPrompt()
    }
}

/**
 * 名称推断结果
 */
data class NameInferenceResult(
    val success: Boolean,                    // 推断过程是否成功（技术层面）
    val inferred: Boolean,                   // 是否成功推断出名称（逻辑层面）
    val candidateNames: List<String>,        // 候选名称列表（按可能性排序）
    val confidence: Float,                   // 置信度 (0.0-1.0)
    val reasoning: String,                   // 推断理由
    val metadata: Map<String, Any> = emptyMap()  // 额外元数据
) {
    /**
     * 获取最可能的名称
     */
    fun getMostLikelyName(): String? = candidateNames.firstOrNull()

    /**
     * 是否有高置信度的推断
     */
    fun isHighConfidence(): Boolean = inferred && confidence >= 0.7f

    /**
     * 是否需要人工确认
     */
    fun needsConfirmation(): Boolean = inferred && confidence < 0.7f
}
