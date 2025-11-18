package com.xiaoguang.assistant.domain.knowledge.context

import com.xiaoguang.assistant.domain.knowledge.retrieval.KnowledgeRetrievalEngine
import com.xiaoguang.assistant.domain.knowledge.retrieval.RetrievedContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动态上下文构建器
 *
 * 职责：
 * 1. 整合多源知识（World Book、Character Book、Memory）
 * 2. 根据对话阶段动态调整上下文内容
 * 3. 智能Token管理，避免超出LLM限制
 * 4. 生成结构化的Prompt注入
 *
 * 设计理念：
 * - 上下文应该"有意识地"适应对话流
 * - 不同对话阶段需要不同的知识密度
 * - 优先保留核心人设和最相关信息
 */
@Singleton
class DynamicContextBuilder @Inject constructor(
    private val retrievalEngine: KnowledgeRetrievalEngine
) {

    private val config = ContextBuilderConfig()

    /**
     * 构建对话上下文
     *
     * @param conversationState 对话状态
     * @return 格式化的上下文字符串，可直接注入到LLM prompt
     */
    suspend fun buildContext(conversationState: ConversationState): BuiltContext {
        return try {
            Timber.d("[ContextBuilder] 开始构建上下文: stage=${conversationState.stage}")

            // 1. 根据对话阶段决定token分配策略
            val tokenBudget = allocateTokenBudget(conversationState.stage)

            // 2. 检索相关知识
            val retrievedContext = retrievalEngine.retrieveContext(
                query = conversationState.getCurrentQuery(),
                characterIds = conversationState.involvedCharacterIds,
                maxTokens = tokenBudget.totalTokens
            )

            // 3. 根据对话阶段调整上下文权重
            val adjustedContext = adjustContextByStage(
                context = retrievedContext,
                stage = conversationState.stage
            )

            // 4. 格式化为Prompt
            val formattedPrompt = formatAsPrompt(
                context = adjustedContext,
                stage = conversationState.stage
            )

            // 5. 生成元数据
            val metadata = ContextMetadata(
                tokenCount = retrievedContext.totalTokens,
                worldEntriesCount = retrievedContext.worldContext.triggeredEntries.size,
                characterCount = retrievedContext.characterContexts.size,
                memoryCount = retrievedContext.memories.size,
                stage = conversationState.stage
            )

            Timber.d("[ContextBuilder] 构建完成: tokens=${metadata.tokenCount}")

            BuiltContext(
                formattedPrompt = formattedPrompt,
                metadata = metadata,
                rawContext = adjustedContext
            )

        } catch (e: Exception) {
            Timber.e(e, "[ContextBuilder] 构建上下文失败")
            BuiltContext.empty()
        }
    }

    /**
     * 分配Token预算
     * 不同对话阶段有不同的知识需求
     */
    private fun allocateTokenBudget(stage: ConversationStage): TokenBudget {
        return when (stage) {
            ConversationStage.GREETING -> {
                // 打招呼阶段：重点加载角色人设
                TokenBudget(
                    totalTokens = 1500,
                    worldBookRatio = 0.2f,
                    characterRatio = 0.6f,
                    memoryRatio = 0.2f
                )
            }
            ConversationStage.CASUAL_CHAT -> {
                // 日常闲聊：均衡加载
                TokenBudget(
                    totalTokens = 2000,
                    worldBookRatio = 0.3f,
                    characterRatio = 0.4f,
                    memoryRatio = 0.3f
                )
            }
            ConversationStage.DEEP_CONVERSATION -> {
                // 深度对话：重点加载记忆
                TokenBudget(
                    totalTokens = 3000,
                    worldBookRatio = 0.2f,
                    characterRatio = 0.3f,
                    memoryRatio = 0.5f
                )
            }
            ConversationStage.TASK_EXECUTION -> {
                // 任务执行：重点加载世界规则
                TokenBudget(
                    totalTokens = 2500,
                    worldBookRatio = 0.5f,
                    characterRatio = 0.2f,
                    memoryRatio = 0.3f
                )
            }
            ConversationStage.EMOTIONAL_SUPPORT -> {
                // 情感支持：重点加载角色关系和记忆
                TokenBudget(
                    totalTokens = 2500,
                    worldBookRatio = 0.1f,
                    characterRatio = 0.4f,
                    memoryRatio = 0.5f
                )
            }
            ConversationStage.UNKNOWN -> {
                // 未知阶段：保守策略
                TokenBudget(
                    totalTokens = 1800,
                    worldBookRatio = 0.3f,
                    characterRatio = 0.4f,
                    memoryRatio = 0.3f
                )
            }
        }
    }

    /**
     * 根据对话阶段调整上下文
     * 不同阶段强调不同的信息
     */
    private fun adjustContextByStage(
        context: RetrievedContext,
        stage: ConversationStage
    ): RetrievedContext {
        // 在实际场景中，这里可以：
        // 1. 过滤不相关的记忆
        // 2. 调整记忆权重
        // 3. 补充必要信息
        // 现在先返回原始上下文
        return context
    }

    /**
     * 格式化为Prompt
     * 生成结构化的上下文注入
     */
    private fun formatAsPrompt(
        context: RetrievedContext,
        stage: ConversationStage
    ): String {
        return buildString {
            // 1. 系统级上下文标记
            appendLine("<!--CONTEXT_START-->")
            appendLine()

            // 2. World Book信息
            if (context.worldContext.formattedContext.isNotEmpty()) {
                appendLine("【世界设定与环境】")
                appendLine(context.worldContext.formattedContext)
                appendLine()
            }

            // 3. 角色信息
            for (charContext in context.characterContexts) {
                if (charContext.formattedContext.isNotEmpty()) {
                    appendLine(charContext.formattedContext)
                    appendLine()
                }
            }

            // 4. 相关记忆
            if (context.memories.isNotEmpty()) {
                appendLine("【相关记忆与背景】")
                for (memory in context.memories.take(10)) {
                    appendLine("- ${memory.memory.content}")
                }
                appendLine()
            }

            // 5. 阶段特定提示
            val stagePrompt = getStageSpecificPrompt(stage)
            if (stagePrompt.isNotEmpty()) {
                appendLine("【当前对话阶段提示】")
                appendLine(stagePrompt)
                appendLine()
            }

            // 6. 结束标记
            appendLine("<!--CONTEXT_END-->")
        }
    }

    /**
     * 获取阶段特定提示
     */
    private fun getStageSpecificPrompt(stage: ConversationStage): String {
        return when (stage) {
            ConversationStage.GREETING -> {
                "这是对话的开始阶段，请用自然、友好的方式打招呼，展现你的个性。"
            }
            ConversationStage.CASUAL_CHAT -> {
                "这是日常闲聊，保持轻松愉快的氛围，可以适当开玩笑或分享有趣的想法。"
            }
            ConversationStage.DEEP_CONVERSATION -> {
                "这是深度对话，请认真倾听，展现同理心，提供有深度的回应。"
            }
            ConversationStage.TASK_EXECUTION -> {
                "这是任务执行阶段，请专注于任务目标，提供清晰、准确的帮助。"
            }
            ConversationStage.EMOTIONAL_SUPPORT -> {
                "对方可能需要情感支持，请展现关心和理解，避免说教或轻率的建议。"
            }
            ConversationStage.UNKNOWN -> ""
        }
    }

    /**
     * 简化构建（快速模式）
     * 用于低延迟场景
     */
    suspend fun buildMinimalContext(
        query: String,
        characterIds: List<String>
    ): String {
        return try {
            val context = retrievalEngine.retrieveContext(
                query = query,
                characterIds = characterIds,
                maxTokens = 1000 // 低Token预算
            )

            buildString {
                if (context.worldContext.formattedContext.isNotEmpty()) {
                    appendLine(context.worldContext.formattedContext)
                }
                for (charContext in context.characterContexts) {
                    appendLine(charContext.formattedContext)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[ContextBuilder] 简化构建失败")
            ""
        }
    }

    /**
     * 增量更新上下文
     * 在长对话中，只更新变化的部分
     */
    suspend fun updateContext(
        previousContext: BuiltContext,
        newMessage: String,
        conversationState: ConversationState
    ): BuiltContext {
        // TODO: 实现增量更新逻辑
        // 1. 检测对话阶段变化
        // 2. 只检索新的相关知识
        // 3. 合并到现有上下文
        return buildContext(conversationState)
    }
}

/**
 * 对话状态
 * 包含构建上下文所需的所有信息
 */
data class ConversationState(
    val conversationId: String,
    val stage: ConversationStage,
    val involvedCharacterIds: List<String>,
    val messageHistory: List<Message>,
    val currentTurn: Int = 0
) {
    /**
     * 获取当前查询
     * 通常是最近几条消息的组合
     */
    fun getCurrentQuery(): String {
        return messageHistory.takeLast(3)
            .joinToString("\n") { "${it.sender}: ${it.content}" }
    }
}

/**
 * 对话阶段
 */
enum class ConversationStage {
    GREETING,           // 打招呼
    CASUAL_CHAT,        // 日常闲聊
    DEEP_CONVERSATION,  // 深度对话
    TASK_EXECUTION,     // 任务执行
    EMOTIONAL_SUPPORT,  // 情感支持
    UNKNOWN             // 未知
}

/**
 * 消息
 */
data class Message(
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Token预算
 */
data class TokenBudget(
    val totalTokens: Int,
    val worldBookRatio: Float,
    val characterRatio: Float,
    val memoryRatio: Float
) {
    fun getWorldBookTokens() = (totalTokens * worldBookRatio).toInt()
    fun getCharacterTokens() = (totalTokens * characterRatio).toInt()
    fun getMemoryTokens() = (totalTokens * memoryRatio).toInt()
}

/**
 * 构建的上下文
 */
data class BuiltContext(
    val formattedPrompt: String,
    val metadata: ContextMetadata,
    val rawContext: RetrievedContext
) {
    companion object {
        fun empty() = BuiltContext(
            formattedPrompt = "",
            metadata = ContextMetadata(),
            rawContext = RetrievedContext.empty()
        )
    }
}

/**
 * 上下文元数据
 */
data class ContextMetadata(
    val tokenCount: Int = 0,
    val worldEntriesCount: Int = 0,
    val characterCount: Int = 0,
    val memoryCount: Int = 0,
    val stage: ConversationStage = ConversationStage.UNKNOWN,
    val buildTime: Long = System.currentTimeMillis()
)

/**
 * 上下文构建器配置
 */
data class ContextBuilderConfig(
    val defaultMaxTokens: Int = 2000,
    val minimalModeTokens: Int = 1000,
    val enableStageAdaptation: Boolean = true,
    val enableIncrementalUpdate: Boolean = false
)
