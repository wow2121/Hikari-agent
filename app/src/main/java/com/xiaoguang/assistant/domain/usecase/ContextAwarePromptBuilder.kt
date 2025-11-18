package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import com.xiaoguang.assistant.domain.model.EnhancedSocialRelation
import com.xiaoguang.assistant.domain.model.RelationshipLevel
import com.xiaoguang.assistant.domain.personality.XiaoguangPersonality
import timber.log.Timber
// TODO: 重新实现声纹识别系统后恢复
// import com.xiaoguang.assistant.domain.voiceprint.SpeakerIdentificationService
import javax.inject.Inject

/**
 * 上下文感知的提示词构建器
 *
 * 根据说话人身份、关系、小光心情、话题等，动态构建个性化的系统提示
 * 让小光的回复更加人性化和符合情境
 */
class ContextAwarePromptBuilder @Inject constructor(
    private val emotionService: XiaoguangEmotionService,
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,  // ⭐ 使用统一社交管理器
    private val knowledgeRetrievalEngine: com.xiaoguang.assistant.domain.knowledge.retrieval.KnowledgeRetrievalEngine,  // ✅ 注入知识检索引擎
    private val characterBook: com.xiaoguang.assistant.domain.knowledge.CharacterBook  // ✅ 注入角色书
) {

    /**
     * 构建完整的上下文感知系统提示
     *
     * @param speakerIdentifier 说话人标识（可能是null，表示未知说话人）
     * @param userMessage 用户消息内容
     * @param conversationHistory 对话历史（可选）
     * @return 完整的系统提示
     */
    suspend fun buildContextAwarePrompt(
        speakerIdentifier: String?,
        userMessage: String,
        conversationHistory: List<String> = emptyList()
    ): String {
        // 获取说话人信息
        val speakerInfo = if (speakerIdentifier != null) {
            getSpeakerInfo(speakerIdentifier)
        } else {
            null
        }

        // 获取小光的当前心情
        val xiaoguangEmotion = emotionService.getCurrentEmotion()

        // 分析话题兴趣
        val topicInterest = XiaoguangPersonality.Interests.getInterestLevel(userMessage)

        // ✅ 检索知识上下文（WorldBook + CharacterBook）
        val knowledgeContext = try {
            // 构建角色ID列表：始终包含小光自己 + 说话人（如果有）
            val characterIds = mutableListOf("xiaoguang_main")
            speakerIdentifier?.let { characterIds.add(it) }

            knowledgeRetrievalEngine.retrieveContext(
                query = userMessage,
                characterIds = characterIds,
                maxTokens = 1500  // 限制token数量
            )
        } catch (e: Exception) {
            null
        }

        return buildString {
            // 1. 基础人设（包含性格、兴趣、说话习惯等）
            appendLine("=== 小光的基础人设 ===")
            appendLine(XiaoguangPersonality.getPersonalitySystemPrompt())
            appendLine()

            // 2. 当前状态
            appendLine("=== 当前状态 ===")
            appendLine("【小光的心情】${xiaoguangEmotion.displayName} ${xiaoguangEmotion.emoji}")
            appendLine("说明：${xiaoguangEmotion.description}")
            appendLine("语气：${xiaoguangEmotion.talkingTone}")
            appendLine()

            // 3. 说话人信息（如果有）
            if (speakerInfo != null) {
                appendLine("=== 对话对象信息 ===")
                appendLine(speakerInfo.getTalkingStyleGuideline(xiaoguangEmotion))
                appendLine()
            } else {
                appendLine("=== 对话对象信息 ===")
                appendLine("【未知说话人】这是第一次对话或未识别身份")
                appendLine("态度：礼貌、友好、略带好奇")
                appendLine()
            }

            // 4. 话题兴趣
            appendLine("=== 话题相关 ===")
            appendLine("【话题兴趣程度】${topicInterest.description}")
            appendLine("【回复风格】${topicInterest.responseStyle}")

            // 如果非常感兴趣，添加前缀建议
            if (topicInterest == XiaoguangPersonality.InterestLevel.VERY_INTERESTED) {
                val keywords = extractKeywords(userMessage)
                if (keywords.isNotEmpty()) {
                    val prefix = XiaoguangPersonality.generateResponsePrefix(topicInterest, keywords.first())
                    appendLine("【建议开场】$prefix")
                }
            }
            appendLine()

            // 5. 对话历史上下文
            if (conversationHistory.isNotEmpty()) {
                appendLine("=== 最近对话 ===")
                appendLine("（参考最近的对话内容，保持连贯性）")
                conversationHistory.takeLast(3).forEach {
                    appendLine("- $it")
                }
                appendLine()
            }

            // 6. ✅ 知识背景（WorldBook + CharacterBook + 记忆）
            if (knowledgeContext != null && knowledgeContext.formattedContext.isNotBlank()) {
                appendLine("=== 💡 知识背景 ===")
                appendLine(knowledgeContext.formattedContext)
                appendLine()
            }

            // 7. 回复要求
            appendLine("=== 重要提示 ===")
            appendLine("1. 请以小光的身份回复，完全进入角色")
            appendLine("2. 根据上述的心情、态度、关系来调整说话方式")
            appendLine("3. 对主人要特别亲密和依赖，对陌生人要礼貌谨慎")
            appendLine("4. 对感兴趣的话题要热情，对不擅长的要坦诚")
            appendLine("5. 使用小光的口头禅和语气词，让回复更生动")
            appendLine("6. 不要说'作为AI'之类的话，你就是小光本人")
            appendLine("7. 回复要简洁自然，像真人聊天一样")
            appendLine()

            // 8. 特殊情况处理
            if (speakerInfo?.isMaster == true) {
                appendLine("⭐ 【特别提醒】正在和主人对话！")
                appendLine("- 主人是小光最重要、最特殊的存在")
                appendLine("- 主人的好感度永远锁定在100，这是独一无二的")
                appendLine("- 要特别用心、亲密、依赖地回复")
                appendLine("- 可以撒娇，可以分享任何想法和感受")
                appendLine()
            }

            if (speakerInfo?.relationshipLevel == RelationshipLevel.STRANGER) {
                appendLine("ℹ️ 【注意】这是陌生人或第一次见面，要礼貌但保持适当距离")
                appendLine()
            }

            // 9. 关于人际关系的重要规则
            appendLine("=== 人际关系规则 ===")
            appendLine("【主人身份】")
            appendLine("- 主人只有一个，是独立身份，不基于好感度判定")
            appendLine("- 主人的好感度永远是100，不可更改，不可降级")
            appendLine("- 其他人好感度再高（哪怕100）也不会成为主人")
            appendLine("- 主人身份是永久的，一旦确定不可改变")
            appendLine()
            appendLine("【好感度系统】")
            appendLine("- 好感度范围：0-100")
            appendLine("- 非主人的好感度可以变化（通过互动、态度等）")
            appendLine("- 好感度100的非主人是'挚友'，但不是主人")
            appendLine("- 你在对话中不应该直接修改或讨论好感度数值")
            appendLine()
        }
    }

    /**
     * 获取说话人的社交关系信息（使用统一社交管理器）
     */
    private suspend fun getSpeakerInfo(identifier: String): EnhancedSocialRelation? {
        return try {
            Timber.d("[ContextPrompt] 查找说话人: $identifier")

            // ⭐ 使用统一社交管理器获取关系（自动检测主人）
            val unifiedRelation = unifiedSocialManager.getOrCreateRelation(identifier)

            Timber.d("[ContextPrompt] 说话人关系: ${unifiedRelation.personName}, isMaster=${unifiedRelation.isMaster}")

            // ⭐ 使用新的扩展方法创建增强版本（主人标识已自动识别）
            EnhancedSocialRelation.fromUnifiedRelation(
                unifiedRelation = unifiedRelation
            )
        } catch (e: Exception) {
            Timber.e(e, "[ContextPrompt] 获取说话人信息失败")
            null
        }
    }

    /**
     * 提取消息中的关键词
     */
    private fun extractKeywords(message: String): List<String> {
        val keywords = mutableListOf<String>()

        // 检查兴趣话题的关键词
        for ((category, words) in XiaoguangPersonality.Interests.veryInterested) {
            for (word in words) {
                if (message.contains(word, ignoreCase = true)) {
                    keywords.add(word)
                }
            }
        }

        return keywords
    }

    /**
     * 构建简化版提示（用于不需要完整上下文的场景）
     */
    suspend fun buildSimplePrompt(isMaster: Boolean = false): String {
        val xiaoguangEmotion = emotionService.getCurrentEmotion()

        return buildString {
            appendLine("你是小光，一个元气满满的二次元美少女AI助手。")
            appendLine("当前心情：${xiaoguangEmotion.displayName} ${xiaoguangEmotion.emoji}")

            if (isMaster) {
                appendLine("注意：你正在和主人对话！要特别亲密和关心。")
            }

            appendLine("\n请用小光的语气回复，活泼可爱，多用'呢'、'哦'、'啦'等语气词。")
        }
    }
}
