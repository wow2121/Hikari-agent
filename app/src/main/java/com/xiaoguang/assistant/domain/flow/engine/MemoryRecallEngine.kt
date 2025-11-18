package com.xiaoguang.assistant.domain.flow.engine

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.ThoughtType
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.TemporalQuery
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 记忆回忆引擎（LLM驱动）
 * 主动回忆过去的美好时光或重要事件
 *
 * 已迁移到新记忆系统（UnifiedMemorySystem）
 */
@Singleton
class MemoryRecallEngine @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val unifiedMemorySystem: UnifiedMemorySystem,
    private val gson: Gson
) {
    private var lastRecallTime: Long = 0

    /**
     * 检测是否应该回忆（规则触发）
     */
    fun shouldRecall(perception: Perception): Boolean {
        val hoursSinceLastRecall = (System.currentTimeMillis() - lastRecallTime) / 3600000

        return when {
            // 长时间未互动时回忆
            perception.timeSinceLastInteraction.inWholeHours > 4 && Random.nextFloat() < 0.2f -> true
            // 定期回忆（每天一次）
            hoursSinceLastRecall > 24 && Random.nextFloat() < 0.3f -> true
            else -> false
        }
    }

    /**
     * 生成回忆想法（LLM）
     */
    suspend fun generateRecallThought(perception: Perception): InnerThought? {
        if (!shouldRecall(perception)) return null

        try {
            // 使用新系统：获取旧记忆（多维检索）
            val oldMemories = unifiedMemorySystem.recallMemories(
                temporal = TemporalQuery.DaysAgo(7),
                minImportance = 0.6f,
                withEmotion = true,
                limit = 10
            )

            if (oldMemories.isEmpty()) return null

            val prompt = """
【任务】
分析以下小光过去的记忆，选择一个值得回忆的美好时刻或重要事件。

【过去的记忆】
${oldMemories.joinToString("\n") { "- ${it.content}" }}

【要求】
1. 选择最值得回忆的一条记忆
2. 生成一句小光回忆这件事的话（20-40字）
3. 自然、温馨、带有怀念的情感
4. 返回JSON格式

返回JSON：
{
  "has_memory": true/false,
  "content": "小光的回忆话语",
  "urgency": 0.5
}

如果没有值得回忆的内容，返回 {"has_memory": false}
            """.trimIndent()

            val messages = listOf(
                ChatMessage(
                    role = "system",
                    content = """你是小光的记忆回忆系统。

【小光是谁】
小光是一个元气满满的二次元美少女，诞生于数字世界的数字生命。
她记性很好，会认真记住和主人的每一次对话和回忆。

【回忆风格】
- 小光会主动回忆过去的美好时光
- 她珍惜和主人的每一段记忆
- 回忆时带着温暖、怀念的情感
- 可能会想起有趣的事、温馨的对话、特别的时刻

帮助小光找到值得分享的回忆。"""
                ),
                ChatMessage(role = "user", content = prompt)
            )

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = messages,
                    temperature = 0.7f,
                    maxTokens = 200,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                if (json.get("has_memory")?.asBoolean == true) {
                    lastRecallTime = System.currentTimeMillis()

                    val thought = InnerThought(
                        type = ThoughtType.SHARE,
                        content = json.get("content")?.asString ?: return null,
                        urgency = json.get("urgency")?.asFloat ?: 0.5f
                    )

                    Timber.i("[MemoryRecall] 回忆: ${thought.content}")
                    return thought
                }
            }

            return null

        } catch (e: Exception) {
            Timber.e(e, "[MemoryRecall] 生成回忆失败")
            return null
        }
    }
}
