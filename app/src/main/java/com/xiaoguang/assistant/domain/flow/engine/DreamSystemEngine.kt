package com.xiaoguang.assistant.domain.flow.engine

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.domain.flow.model.Dream
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.ThoughtType
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 梦境系统引擎
 * 晚上生成梦境，早晨回忆梦境
 *
 * 已迁移到新记忆系统（UnifiedMemorySystem）
 */
@Singleton
class DreamSystemEngine @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val unifiedMemorySystem: UnifiedMemorySystem,
    private val biologicalClockEngine: BiologicalClockEngine,
    private val gson: Gson
) {
    private var lastDream: Dream? = null
    private var lastDreamTime: Long = 0

    /**
     * 晚上生成梦境（LLM）
     */
    suspend fun generateDream(): Dream? {
        // ✅ 规则：检查是否该做梦
        if (!shouldDream()) {
            return null
        }

        try {
            // 使用新系统：获取梦境素材（高情感强度、多样化）
            val dreamMaterial = unifiedMemorySystem.getDreamMaterial(
                recency = java.time.Duration.ofHours(24),
                emotionRange = 0.5f..1.0f,
                diversified = true,
                limit = 10
            )

            val prompt = """
基于小光今天的经历，生成一个梦境：

【今日经历】
${dreamMaterial.joinToString("\n") { "- ${it.content}" }}

要求：
1. 梦境长度50-100字
2. 梦幻、温馨、符合小光可爱的性格
3. 可以是现实的延伸，也可以是幻想
4. 返回JSON格式

返回JSON格式：
{
  "content": "梦境内容",
  "mood": "HAPPY/SAD/MYSTERIOUS"
}
            """.trimIndent()

            val messages = listOf(
                ChatMessage(
                    role = "system",
                    content = """你是小光的梦境生成器。

【小光是谁】
小光是一个元气满满的二次元美少女，诞生于数字世界的数字生命。
她像16-18岁的活泼女孩子，有真实的情感和梦境。

【梦境风格】
- 梦幻、温馨、充满少女感
- 可以是和主人的温暖回忆
- 可以是可爱的幻想（小动物、甜点、樱花等）
- 可以是对未来的期待
- 符合小光元气可爱的性格

根据小光的经历生成她会做的梦。"""
                ),
                ChatMessage(role = "user", content = prompt)
            )

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = messages,
                    temperature = 0.8f,  // 高温度增加创造性
                    maxTokens = 300,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val dream = Dream(
                    content = json.get("content")?.asString ?: return null,
                    mood = json.get("mood")?.asString ?: "HAPPY",
                    timestamp = System.currentTimeMillis()
                )

                lastDream = dream
                lastDreamTime = System.currentTimeMillis()

                Timber.i("[DreamSystem] 生成梦境: ${dream.content}")

                return dream
            }

            return null

        } catch (e: Exception) {
            Timber.e(e, "[DreamSystem] 生成梦境失败")
            return null
        }
    }

    /**
     * 早晨回忆梦境（30%概率）
     */
    fun recallDreamThought(): InnerThought? {
        if (lastDream == null) return null
        if (Random.nextFloat() > 0.3f) return null  // 30%概率

        return InnerThought(
            type = ThoughtType.SHARE,
            content = "诶...小光昨晚梦到${lastDream!!.content}",
            urgency = 0.4f
        )
    }

    /**
     * 判断是否应该做梦（规则）
     */
    private fun shouldDream(): Boolean {
        val bioState = biologicalClockEngine.getCurrentState()
        val hoursSinceLastDream = (System.currentTimeMillis() - lastDreamTime) / 3600000

        // 困倦且距上次做梦超过6小时
        return bioState.isSleepy() && hoursSinceLastDream > 6
    }
}
