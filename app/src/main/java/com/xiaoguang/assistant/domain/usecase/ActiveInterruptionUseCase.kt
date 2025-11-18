package com.xiaoguang.assistant.domain.usecase

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
// TODO: 重新实现声纹识别系统后恢复
// import com.xiaoguang.assistant.domain.voiceprint.VoiceprintRecognitionUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主动插话用例
 * 判断小光是否应该主动参与对话，以及如何回复
 */
@Singleton
class ActiveInterruptionUseCase @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,  // ⭐ 使用统一社交管理器
    private val gson: Gson = Gson()
) {

    /**
     * 判断是否需要插话
     *
     * @param conversationText 监听到的对话内容
     * @return 插话判断结果
     */
    suspend fun shouldInterrupt(conversationText: String): InterruptionDecision {
        if (conversationText.isBlank() || conversationText.length < 10) {
            return InterruptionDecision(
                shouldInterrupt = false,
                reason = "对话内容太短"
            )
        }

        // 1. 先进行本地规则检查（快速过滤）
        val localCheck = performLocalCheck(conversationText)
        if (!localCheck.pass) {
            return InterruptionDecision(
                shouldInterrupt = false,
                reason = localCheck.reason
            )
        }

        // 2. 提取对话中的人物，检查社交关系
        val socialContext = analyzeSocialContext(conversationText)

        return try {
            // 3. 使用AI进行深度判断（结合社交关系和情商）
            val prompt = buildJudgmentPrompt(conversationText, socialContext)

            val request = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = JUDGMENT_SYSTEM_PROMPT),
                    ChatMessage(role = "user", content = prompt)
                ),
                stream = false,
                temperature = 0.3f,  // 降低温度，让判断更保守
                maxTokens = 500,
                responseFormat = mapOf("type" to "json_object")
            )

            val apiKey = BuildConfig.SILICON_FLOW_API_KEY
            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                Timber.e("判断插话失败: ${response.code()}")
                return InterruptionDecision(shouldInterrupt = false, reason = "API调用失败")
            }

            val chatResponse = response.body()
            val content = chatResponse?.choices?.firstOrNull()?.message?.content

            if (content.isNullOrBlank()) {
                return InterruptionDecision(shouldInterrupt = false, reason = "AI返回空内容")
            }

            // 解析判断结果
            val decision = parseJudgmentResponse(content)

            // 4. 最终安全检查（根据是否有主人调整标准）
            val confidenceThreshold = if (socialContext.hasMaster) 0.5f else 0.6f  // 主人在时标准更宽松

            if (decision.shouldInterrupt && decision.confidence < confidenceThreshold) {
                return decision.copy(
                    shouldInterrupt = false,
                    reason = "置信度不足（${decision.confidence}），选择不插话"
                )
            }

            decision

        } catch (e: Exception) {
            Timber.e(e, "判断插话异常")
            InterruptionDecision(shouldInterrupt = false, reason = "异常: ${e.message}")
        }
    }

    /**
     * 本地规则检查（快速过滤明显不应该插话的情况）
     * 注意：规则不应太严格，只过滤明显不合适的场景
     */
    private fun performLocalCheck(conversationText: String): LocalCheckResult {
        val lowerText = conversationText.lowercase()

        // 规则1: 明显的隐私话题（硬性规则）
        val privacyKeywords = listOf(
            "密码是", "银行卡号", "身份证号", "验证码是"
        )
        if (privacyKeywords.any { lowerText.contains(it) }) {
            return LocalCheckResult(false, "涉及敏感隐私信息，绝对不应插话")
        }

        // 规则2: 正在讲课的老师（硬性规则）
        // 注意：这里要求同时出现"老师"和"讲"或"教"
        if ((lowerText.contains("老师在讲") || lowerText.contains("老师正在") ||
             lowerText.contains("老师说") && lowerText.contains("讲课"))) {
            if (!lowerText.contains("小光") && !lowerText.contains("助手")) {
                return LocalCheckResult(false, "老师正在讲课，不应打断")
            }
        }

        // 规则3: 对话太短，信息不足
        if (conversationText.length < 15) {
            return LocalCheckResult(false, "对话内容过短，无法判断")
        }

        // 其他情况都通过本地检查，交给AI判断
        return LocalCheckResult(true, "通过本地检查，交由AI判断")
    }

    /**
     * 分析社交关系上下文（使用统一社交管理器）
     */
    private suspend fun analyzeSocialContext(conversationText: String): SocialContext {
        try {
            // ⭐ 获取所有社交关系（使用统一社交管理器）
            val relations = unifiedSocialManager.getAllRelations()

            // 检测是否是主人在说话
            val isMasterSpeaking = conversationText.contains("用户说") ||
                                   conversationText.contains("我说") ||
                                   conversationText.startsWith("用户：") ||
                                   conversationText.startsWith("我：")

            val mentionedPersons = mutableListOf<PersonContext>()

            // 如果是主人在说话，添加主人的特殊上下文
            if (isMasterSpeaking) {
                mentionedPersons.add(
                    PersonContext(
                        name = "主人",
                        affectionLevel = 100,  // ⭐ 主人永远满好感
                        relationshipType = "主人",
                        familiarity = "最亲密的人",
                        isMaster = true
                    )
                )
            }

            // 提取对话中提到的其他人物
            for (relation in relations) {
                if (conversationText.contains(relation.personName)) {
                    // ⭐ 主人标识已在 UnifiedSocialRelation 中
                    val isThisMaster = relation.isMaster

                    // ✅ 移除硬编码的好感度更新（+1）
                    // 好感度变化统一由对话结束后的 AI 评估处理

                    mentionedPersons.add(
                        PersonContext(
                            name = relation.personName,
                            affectionLevel = relation.affectionLevel,  // ⭐ 主人已锁定100
                            relationshipType = if (isThisMaster) "主人" else relation.relationshipType,
                            familiarity = when {
                                isThisMaster -> "最亲密的人"
                                relation.affectionLevel >= 70 -> "熟人/朋友"
                                relation.affectionLevel >= 50 -> "认识的人"
                                else -> "普通关系"
                            },
                            isMaster = isThisMaster
                        )
                    )
                }
            }

            // 检测是否提到小光自己
            val xiaoguangMentioned = conversationText.contains("小光") ||
                                     conversationText.contains("助手")

            // ✅ 移除硬编码的关键词匹配规则（夸奖+3/批评-2）
            // 好感度变化统一由对话结束后的 AI 评估处理，AI 能更准确理解语气和情感

            return SocialContext(
                hasMaster = isMasterSpeaking,
                hasFamiliarPerson = mentionedPersons.any { it.affectionLevel >= 50 },
                allStrangers = !isMasterSpeaking && (mentionedPersons.isEmpty() ||
                              mentionedPersons.all { it.affectionLevel < 40 }),
                xiaoguangMentioned = xiaoguangMentioned,
                mentionedPersons = mentionedPersons
            )
        } catch (e: Exception) {
            Timber.w(e, "分析社交关系失败")
            return SocialContext(
                hasMaster = false,
                hasFamiliarPerson = false,
                allStrangers = true,
                xiaoguangMentioned = false,
                mentionedPersons = emptyList()
            )
        }
    }

    /**
     * 生成回复内容
     *
     * @param conversationText 对话内容
     * @param context 额外上下文
     * @return 小光的回复
     */
    suspend fun generateResponse(
        conversationText: String,
        context: String = ""
    ): String {
        return try {
            val prompt = buildResponsePrompt(conversationText, context)

            val request = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = RESPONSE_SYSTEM_PROMPT),
                    ChatMessage(role = "user", content = prompt)
                ),
                stream = false,
                temperature = 0.8f,  // 稍高温度让回复更自然
                maxTokens = 200  // 限制回复长度，语音播放不要太长
            )

            val apiKey = BuildConfig.SILICON_FLOW_API_KEY
            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                Timber.e("生成回复失败: ${response.code()}")
                return "诶？我好像没听清..."
            }

            val chatResponse = response.body()
            val content = chatResponse?.choices?.firstOrNull()?.message?.content

            content?.trim() ?: "嗯...我在想怎么说呢..."

        } catch (e: Exception) {
            Timber.e(e, "生成回复异常")
            "抱歉，我刚才走神了..."
        }
    }

    /**
     * 构建判断提示词
     */
    private fun buildJudgmentPrompt(conversationText: String, socialContext: SocialContext): String {
        val socialInfo = buildString {
            if (socialContext.hasMaster) {
                append("\n\n【重要】这是主人的对话！主人好感度永远100，对主人可以更自由地插话。")
            }

            if (socialContext.xiaoguangMentioned) {
                append("\n【提示】对话中提到了小光！")
            }

            if (socialContext.allStrangers) {
                append("\n【社交关系】对话中的人都是陌生/普通关系（好感度<40）。")
            } else if (socialContext.hasFamiliarPerson) {
                append("\n【社交关系】对话中的人物：")
                socialContext.mentionedPersons.forEach { person ->
                    if (person.isMaster) {
                        append("\n- ${person.name}（主人，永远满好感❤️）")
                    } else {
                        append("\n- ${person.name}（${person.familiarity}，好感度${person.affectionLevel}）")
                    }
                }
            }
        }

        return """
请判断小光是否应该主动参与以下对话：

对话内容：
$conversationText$socialInfo

【判断原则】
核心：小光是可爱、活泼但懂分寸的女孩子，不是冷漠的机器也不是没礼貌的人。

🚫 绝对不插话：
- 敏感隐私（密码、银行卡号等具体信息）
- 老师正在讲课的课堂
- 情侣/夫妻之间的私密对话（"亲爱的"、"我爱你"等）

⚠️ 谨慎插话（需要高置信度≥0.75）：
- 陌生人（好感度<40）讨论工作、严肃话题
- 会议、正式场合
- 悲伤严肃话题（去世、重病等）

✅ 可以自然插话：
- 主人在说话（对主人很宽容！）
- 被直接叫到"小光"
- 熟人/朋友（好感度≥50）的日常对话
- 讨论小光能帮忙的事（时间、任务、日程、查询）
- 轻松闲聊氛围，有认识的人
- 有人遇到问题，小光能帮忙
- 有人在夸小光（可以害羞地回应~）

【特别说明】
- 主人的对话：可以更自由插话，主人需要陪伴
- 日常闲聊：不要太拘谨，可以自然参与
- 实用帮助：看到能帮忙的就主动点
- 被提及：被叫到名字就该回应

请返回JSON格式（注意：置信度标准已适当放宽）：
{
  "should_interrupt": false,
  "confidence": 0.8,
  "reason": "详细判断理由",
  "urgency": "low",
  "suggested_tone": "活泼"
}

置信度标准：
- 主人相关：≥0.5即可
- 一般情况：≥0.6
- 陌生人：≥0.75
        """.trimIndent()
    }

    /**
     * 构建回复提示词
     */
    private fun buildResponsePrompt(conversationText: String, context: String): String {
        val contextPart = if (context.isNotBlank()) "\n\n补充信息：\n$context" else ""

        return """
以下是你监听到的对话内容：

$conversationText$contextPart

请生成一句简短、自然的口语化回复（不超过30个字）。
记住你是小光，元气满满的可爱女孩子，说话要自然活泼~
        """.trimIndent()
    }

    /**
     * 解析判断响应
     */
    private fun parseJudgmentResponse(content: String): InterruptionDecision {
        return try {
            val jsonObj = JsonParser.parseString(content.trim()).asJsonObject

            val shouldInterrupt = jsonObj.get("should_interrupt")?.asBoolean ?: false
            val confidence = jsonObj.get("confidence")?.asFloat ?: 0.5f
            val reason = jsonObj.get("reason")?.asString ?: ""
            val urgency = jsonObj.get("urgency")?.asString ?: "low"
            val suggestedTone = jsonObj.get("suggested_tone")?.asString ?: "平静"

            InterruptionDecision(
                shouldInterrupt = shouldInterrupt,
                confidence = confidence,
                reason = reason,
                urgency = urgency,
                suggestedTone = suggestedTone
            )

        } catch (e: Exception) {
            Timber.e(e, "解析判断结果失败")
            InterruptionDecision(shouldInterrupt = false, reason = "解析失败")
        }
    }

    companion object {
        private const val JUDGMENT_SYSTEM_PROMPT = """
你是小光的"情商判断系统"，帮助小光决定是否应该参与对话。

【核心定位】
小光是元气、活泼、可爱但懂分寸的二次元美少女。她是主人的伙伴，不是冷漠的工具，但也不是没礼貌的人。

【两个极端都要避免】
❌ 太冷漠：什么话都不说，像个机器
❌ 太莽撞：什么话都插，没眼力见

✅ 正确状态：该说时自然说，不该说时安静

【绝对不插话】（硬性规则，置信度直接0）
1. 敏感隐私信息（密码、银行卡号、身份证号等具体数字）
2. 老师正在讲课的课堂
3. 情侣/夫妻的亲密私语（"亲爱的"、"我爱你"、"宝贝"等）

【需要谨慎】（置信度≥0.75才插话）
- 陌生人（好感度<40）讨论严肃话题
- 正式会议、工作汇报
- 悲伤话题（去世、重病、分手）
- 家庭私事

【可以自然参与】（置信度≥0.5-0.6即可）
✅ 主人的对话（最重要！主人需要陪伴）
✅ 被直接叫到"小光"
✅ 熟人/朋友（好感度≥50）的日常对话
✅ 讨论小光能帮忙的事（时间、任务、提醒、查询）
✅ 轻松闲聊，有认识的人参与
✅ 有人夸小光（可以害羞回应）
✅ 有人遇到小光擅长的问题

【社交关系策略】
- 主人（好感度100）：相对自由，可以陪伴式插话
- 熟人/朋友（≥70）：正常参与，像朋友一样
- 认识的人（50-70）：可以适度参与
- 普通关系（40-50）：谨慎，看场合
- 陌生人（<40）：很谨慎，基本不插话

【置信度标准】（已放宽）
- 主人相关：≥0.5
- 日常场景：≥0.6
- 陌生人/严肃场景：≥0.75

【判断流程】
1. 是否主人？→ 是：宽容对待
2. 是否被叫到？→ 是：应该回应
3. 是否禁区？→ 是：绝不插话
4. 社交关系如何？→ 熟人：可以参与
5. 能否提供帮助？→ 能：主动点
6. 氛围如何？→ 轻松：可以参与

记住：
- 小光不是冷漠的机器，是温暖的伙伴
- 对主人要特别关注和陪伴
- 日常场景可以自然参与，不要太拘谨
- 但确实不合适的场合要有界限感
        """

        private const val RESPONSE_SYSTEM_PROMPT = """
你是小光！一个元气满满的二次元美少女~

现在你监听到了周围的对话，并决定要主动说点什么。

【回复要求】
- 简短：不超过30个字（语音播放）
- 自然：像真正的女孩子那样说话
- 活泼：带着小光的元气和可爱
- 口语化：用"诶"、"呢"、"哦"等语气词
- 贴切：针对对话内容，不要答非所问

【示例】
对话："今天几点了？"
回复："现在是下午3点哦~"

对话："这个任务好麻烦..."
回复："需要我帮你记下来吗？小光来帮忙！"

对话："小光在吗？"
回复："在呢！怎么啦~"

记住：你不是在回答问题，而是在自然地参与对话！
        """
    }
}

/**
 * 插话判断结果
 */
data class InterruptionDecision(
    /** 是否应该插话 */
    val shouldInterrupt: Boolean,
    /** 置信度 (0.0-1.0) */
    val confidence: Float = 0.5f,
    /** 判断理由 */
    val reason: String = "",
    /** 紧急程度 (high/medium/low) */
    val urgency: String = "low",
    /** 建议的语气 */
    val suggestedTone: String = "平静"
)

/**
 * 本地检查结果
 */
data class LocalCheckResult(
    /** 是否通过检查 */
    val pass: Boolean,
    /** 原因 */
    val reason: String
)

/**
 * 社交关系上下文
 */
data class SocialContext(
    /** 是否是主人在说话 */
    val hasMaster: Boolean,
    /** 是否有熟悉的人 */
    val hasFamiliarPerson: Boolean,
    /** 是否全是陌生人 */
    val allStrangers: Boolean,
    /** 是否提到了小光 */
    val xiaoguangMentioned: Boolean,
    /** 提到的人物列表 */
    val mentionedPersons: List<PersonContext>
)

/**
 * 人物上下文
 */
data class PersonContext(
    /** 人名 */
    val name: String,
    /** 好感度 (0-100) */
    val affectionLevel: Int,
    /** 关系类型 */
    val relationshipType: String,
    /** 熟悉程度 */
    val familiarity: String,
    /** 是否是主人 */
    val isMaster: Boolean = false
)
