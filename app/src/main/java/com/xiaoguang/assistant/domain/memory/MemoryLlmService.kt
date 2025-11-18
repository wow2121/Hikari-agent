package com.xiaoguang.assistant.domain.memory

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.domain.memory.models.IntentType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆系统统一LLM服务
 *
 * 提供：
 * - 情感效价评估（替代硬编码关键词）
 * - 意图分类（替代if-else规则）
 * - 纪念日名称提取（替代简单匹配）
 * - 性格洞察生成（替代数值判断）
 */
@Singleton
class MemoryLlmService @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val gson: Gson
) {

    /**
     * 评估情感效价（-1.0 到 1.0）
     *
     * @param emotionTag 情绪标签（如："happy", "生气"）
     * @param context 上下文内容（可选，提供更准确评估）
     * @return 效价值：-1.0（极端消极）到 1.0（极端积极）
     */
    suspend fun evaluateEmotionValence(
        emotionTag: String,
        context: String? = null
    ): Result<Float> {
        return try {
            val prompt = if (context != null) {
                """
                分析情绪的效价（积极/消极程度）：

                情绪标签：$emotionTag
                上下文：$context

                请评估这个情绪在当前上下文中的效价，返回-1.0到1.0之间的数值：
                - -1.0：极端消极（如：愤怒、绝望）
                - -0.5：轻度消极（如：失望、担心）
                - 0.0：中性（如：平静、无感）
                - 0.5：轻度积极（如：满意、放松）
                - 1.0：极端积极（如：狂喜、深爱）

                返回JSON格式：{"valence": 数值, "reason": "简短解释"}
                """.trimIndent()
            } else {
                """
                分析情绪标签的通用效价：

                情绪：$emotionTag

                返回-1.0到1.0之间的数值，代表该情绪的一般效价。
                返回JSON格式：{"valence": 数值}
                """.trimIndent()
            }

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是情感分析专家，精确评估情绪的效价（积极/消极程度）。"
                        ),
                        ChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.3f,  // 低温度保证稳定性
                    maxTokens = 100,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val valence = json.get("valence")?.asFloat ?: 0f
                val clampedValence = valence.coerceIn(-1f, 1f)

                Timber.d("[MemoryLlm] 情感效价评估: $emotionTag -> $clampedValence")
                Result.success(clampedValence)
            } else {
                Timber.w("[MemoryLlm] 情感效价评估失败，使用fallback")
                Result.success(fallbackEmotionValence(emotionTag))
            }

        } catch (e: Exception) {
            Timber.e(e, "[MemoryLlm] 情感效价评估异常")
            Result.success(fallbackEmotionValence(emotionTag))
        }
    }

    /**
     * 分类意图类型
     *
     * @param content 内容文本
     * @param category 记忆分类（可选，辅助判断）
     * @return 意图类型
     */
    suspend fun classifyIntent(
        content: String,
        category: String? = null
    ): Result<IntentType> {
        return try {
            val categoryHint = if (category != null) "\n记忆分类：$category" else ""

            val prompt = """
            分析以下内容的意图类型：

            内容：$content$categoryHint

            可选类型：
            - QUESTION：提问、询问
            - STATEMENT：陈述事实、表达观点
            - DESIRE：表达愿望、需求
            - PREFERENCE：表达偏好、喜恶
            - INFORM：告知信息、通知
            - COMMAND：命令、请求行动

            返回JSON格式：{"intent": "类型名称"}
            """.trimIndent()

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是意图分类专家，准确识别文本的交流意图。"
                        ),
                        ChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.2f,
                    maxTokens = 50,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val intentStr = json.get("intent")?.asString?.uppercase() ?: "STATEMENT"
                val intent = try {
                    IntentType.valueOf(intentStr)
                } catch (e: Exception) {
                    IntentType.STATEMENT
                }

                Timber.d("[MemoryLlm] 意图分类: ${content.take(30)} -> $intent")
                Result.success(intent)
            } else {
                Timber.w("[MemoryLlm] 意图分类失败，使用fallback")
                Result.success(fallbackIntentType(content, category))
            }

        } catch (e: Exception) {
            Timber.e(e, "[MemoryLlm] 意图分类异常")
            Result.success(fallbackIntentType(content, category))
        }
    }

    /**
     * 提取纪念日名称
     *
     * @param content 纪念日记忆内容
     * @return 纪念日名称（如："小明的生日"）
     */
    suspend fun extractAnniversaryName(content: String): Result<String> {
        return try {
            val prompt = """
            从纪念日记忆中提取简短的名称：

            内容：$content

            提取规则：
            - 如果提到人名，包含人名（如："小明的生日"）
            - 如果是通用节日，返回节日名（如："春节"）
            - 保持简短（2-8个字）
            - 不要返回完整句子

            返回JSON格式：{"name": "名称"}
            """.trimIndent()

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是信息提取专家，从文本中精确提取关键名称。"
                        ),
                        ChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.3f,
                    maxTokens = 50,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val name = json.get("name")?.asString ?: fallbackAnniversaryName(content)

                Timber.d("[MemoryLlm] 纪念日名称提取: ${content.take(30)} -> $name")
                Result.success(name)
            } else {
                Timber.w("[MemoryLlm] 纪念日名称提取失败，使用fallback")
                Result.success(fallbackAnniversaryName(content))
            }

        } catch (e: Exception) {
            Timber.e(e, "[MemoryLlm] 纪念日名称提取异常")
            Result.success(fallbackAnniversaryName(content))
        }
    }

    /**
     * 生成性格洞察
     *
     * @param characterName 角色名称
     * @param memories 最近的记忆列表（50条左右）
     * @return 性格洞察
     */
    suspend fun generatePersonalityInsights(
        characterName: String,
        memories: List<String>
    ): Result<PersonalityInsights> {
        return try {
            val memoriesText = memories.take(50).joinToString("\n") { "- $it" }

            val prompt = """
            基于以下记忆分析 $characterName 的性格特征：

            $memoriesText

            分析维度：
            1. 情感倾向：整体情绪是积极、消极还是中性？
            2. 行为模式：有哪些典型的行为特点？
            3. 兴趣偏好：喜欢什么、不喜欢什么？
            4. 互动风格：如何与他人交流？

            返回JSON格式：
            {
              "emotionalTendency": "情感倾向描述（2-6字）",
              "behaviorPatterns": ["行为特点1", "行为特点2"],
              "interests": ["兴趣1", "兴趣2"],
              "interactionStyle": "互动风格描述（4-10字）"
            }
            """.trimIndent()

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是性格分析专家，根据记忆准确分析人物性格特征。"
                        ),
                        ChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.5f,
                    maxTokens = 300,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val insights = PersonalityInsights(
                    emotionalTendency = json.get("emotionalTendency")?.asString ?: "情绪平稳",
                    behaviorPatterns = json.getAsJsonArray("behaviorPatterns")?.map { it.asString } ?: emptyList(),
                    interests = json.getAsJsonArray("interests")?.map { it.asString } ?: emptyList(),
                    interactionStyle = json.get("interactionStyle")?.asString ?: "正常交流"
                )

                Timber.d("[MemoryLlm] 性格洞察生成: $characterName")
                Result.success(insights)
            } else {
                Timber.w("[MemoryLlm] 性格洞察生成失败")
                Result.failure(Exception("LLM生成失败"))
            }

        } catch (e: Exception) {
            Timber.e(e, "[MemoryLlm] 性格洞察生成异常")
            Result.failure(e)
        }
    }

    // ==================== Fallback 规则（LLM失败时使用） ====================

    /**
     * 情感效价fallback（简单关键词匹配）
     */
    private fun fallbackEmotionValence(emotionTag: String): Float {
        val positive = listOf("快乐", "开心", "高兴", "兴奋", "喜欢", "爱", "感动", "满意", "happy", "joy", "love", "excited", "proud")
        val negative = listOf("难过", "伤心", "生气", "愤怒", "讨厌", "失望", "焦虑", "害怕", "sad", "angry", "fear", "frustrated", "disgusted")

        return when {
            positive.any { it in emotionTag.lowercase() } -> 0.7f
            negative.any { it in emotionTag.lowercase() } -> -0.7f
            else -> 0f
        }
    }

    /**
     * 意图类型fallback（简单规则）
     */
    private fun fallbackIntentType(content: String, category: String?): IntentType {
        return when {
            content.contains("?") || content.contains("？") -> IntentType.QUESTION
            category == "preference" -> IntentType.PREFERENCE
            content.contains("想") || content.contains("希望") -> IntentType.DESIRE
            category == "knowledge" || category == "fact" -> IntentType.INFORM
            else -> IntentType.STATEMENT
        }
    }

    /**
     * 纪念日名称fallback（简单匹配）
     */
    private fun fallbackAnniversaryName(content: String): String {
        return when {
            content.contains("生日") -> "生日"
            content.contains("纪念日") -> "纪念日"
            content.contains("节日") -> "节日"
            content.contains("周年") -> "周年纪念"
            else -> "特殊的日子"
        }
    }

    /**
     * 分析人物关系类型
     *
     * @param personA 第一个人名
     * @param personB 第二个人名
     * @param context 上下文文本
     * @return 关系类型（如：朋友、同事、家人等）
     */
    suspend fun analyzeRelationshipType(
        personA: String,
        personB: String,
        context: String
    ): Result<String> {
        return try {
            val prompt = """
            分析以下文本中两个人的关系类型：

            人物A：$personA
            人物B：$personB
            上下文：$context

            可能的关系类型：
            - 朋友
            - 同事
            - 家人
            - 同学
            - 邻居
            - 上下级（上司/下属）
            - 师生
            - 恋人
            - 认识（一般认识）
            - 陌生人

            返回JSON格式：{"relationship": "关系类型"}
            """.trimIndent()

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是关系分析专家，准确判断人物间的关系类型。"
                        ),
                        ChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.2f,
                    maxTokens = 50,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val relationship = json.get("relationship")?.asString ?: "认识"

                Timber.d("[MemoryLlm] 关系分析: $personA-$personB -> $relationship")
                Result.success(relationship)
            } else {
                Timber.w("[MemoryLlm] 关系分析失败，使用fallback")
                Result.success(fallbackRelationshipType(context))
            }

        } catch (e: Exception) {
            Timber.e(e, "[MemoryLlm] 关系分析异常")
            Result.success(fallbackRelationshipType(context))
        }
    }

    /**
     * 从描述中提取性格特征
     *
     * @param description 人物描述
     * @return 性格特征列表
     */
    suspend fun extractPersonalityTraits(description: String): Result<List<String>> {
        return try {
            val prompt = """
            从以下人物描述中提取性格特征：

            描述：$description

            常见性格特征：
            - 开朗、活泼、乐观
            - 内向、安静、害羞
            - 友善、友好、温柔
            - 严肃、严格、认真
            - 幽默、搞笑、有趣
            - 聪明、智慧、机智
            - 勇敢、果断、坚强
            - 善良、热心、体贴
            - 冷静、理性、沉着
            - 热情、积极、主动

            返回JSON格式：{"traits": ["特征1", "特征2", ...]}
            如果没有明显特征，返回空数组
            """.trimIndent()

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = ChatRequest(
                    messages = listOf(
                        ChatMessage(
                            role = "system",
                            content = "你是性格分析专家，从文本中精确提取人物性格特征。"
                        ),
                        ChatMessage(role = "user", content = prompt)
                    ),
                    temperature = 0.3f,
                    maxTokens = 100,
                    responseFormat = mapOf("type" to "json_object")
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val json = JsonParser.parseString(
                    response.body()!!.choices.firstOrNull()?.message?.content
                ).asJsonObject

                val traits = json.getAsJsonArray("traits")?.map { it.asString } ?: emptyList()

                Timber.d("[MemoryLlm] 性格特征提取: ${description.take(30)} -> $traits")
                Result.success(traits.ifEmpty { listOf("普通") })
            } else {
                Timber.w("[MemoryLlm] 性格特征提取失败，使用fallback")
                Result.success(fallbackPersonalityTraits(description))
            }

        } catch (e: Exception) {
            Timber.e(e, "[MemoryLlm] 性格特征提取异常")
            Result.success(fallbackPersonalityTraits(description))
        }
    }

    // ==================== Fallback 方法 ====================

    /**
     * 关系类型fallback（关键词匹配）
     */
    private fun fallbackRelationshipType(context: String): String {
        return when {
            context.contains("朋友") -> "朋友"
            context.contains("同事") -> "同事"
            context.contains("家人") || context.contains("亲人") -> "家人"
            context.contains("上司") || context.contains("老板") -> "上下级"
            context.contains("邻居") -> "邻居"
            context.contains("同学") -> "同学"
            context.contains("师生") || context.contains("老师") || context.contains("学生") -> "师生"
            else -> "认识"
        }
    }

    /**
     * 性格特征fallback（关键词匹配）
     */
    private fun fallbackPersonalityTraits(description: String): List<String> {
        val traits = mutableListOf<String>()

        val traitKeywords = mapOf(
            "开朗" to listOf("开朗", "活泼", "乐观"),
            "内向" to listOf("内向", "安静", "害羞"),
            "友善" to listOf("友善", "友好", "和善", "温柔"),
            "严肃" to listOf("严肃", "严格", "认真"),
            "幽默" to listOf("幽默", "搞笑", "有趣"),
            "聪明" to listOf("聪明", "智慧", "机智"),
            "勇敢" to listOf("勇敢", "勇气", "果断"),
            "善良" to listOf("善良", "好心", "热心")
        )

        for ((trait, keywords) in traitKeywords) {
            if (keywords.any { description.contains(it) }) {
                traits.add(trait)
            }
        }

        return traits.ifEmpty { listOf("普通") }
    }
}

/**
 * 性格洞察数据模型
 */
data class PersonalityInsights(
    val emotionalTendency: String,
    val behaviorPatterns: List<String>,
    val interests: List<String>,
    val interactionStyle: String
)
