package com.xiaoguang.assistant.domain.flow.layer

import com.google.gson.JsonObject
import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import com.xiaoguang.assistant.domain.flow.model.DecisionRecord
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.SpeakDecision
import com.xiaoguang.assistant.domain.flow.model.SpeakResult
import com.xiaoguang.assistant.domain.flow.model.Thoughts
import com.xiaoguang.assistant.domain.mcp.McpServer
import com.xiaoguang.assistant.domain.mcp.McpToolResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 行动层（LLM 驱动）
 * 负责执行发言动作、管理沉默、收集反馈、工具调用
 */
@Singleton
class ActionLayer @Inject constructor(
    private val flowLlmService: com.xiaoguang.assistant.domain.flow.service.FlowLlmService,
    private val emotionService: XiaoguangEmotionService,
    private val toneStyleEngine: com.xiaoguang.assistant.domain.personality.ToneStyleEngine,
    private val biologicalClockEngine: com.xiaoguang.assistant.domain.flow.engine.BiologicalClockEngine,
    private val imperfectionEngine: com.xiaoguang.assistant.domain.personality.ImperfectionEngine,
    private val mcpServer: McpServer  // ✅ 集成 MCP Server
) {
    // 发言事件流
    private val _speakEvents = MutableSharedFlow<ProactiveSpeakEvent>(replay = 0)
    val speakEvents: SharedFlow<ProactiveSpeakEvent> = _speakEvents.asSharedFlow()

    /**
     * 执行决策
     */
    suspend fun execute(
        decision: SpeakDecision,
        perception: Perception,
        thoughts: Thoughts
    ): ActionResult {
        Timber.d("[ActionLayer] 执行动作: shouldSpeak=${decision.shouldSpeak}, timing=${decision.timing}")

        return if (decision.shouldSpeak) {
            executeSpeak(decision, perception, thoughts)
        } else {
            executeSilence(decision)
        }
    }

    /**
     * 执行发言
     */
    private suspend fun executeSpeak(
        decision: SpeakDecision,
        perception: Perception,
        thoughts: Thoughts
    ): ActionResult {
        try {
            // 1. 生成发言内容
            val rawMessage = generateMessage(decision, perception, thoughts)

            // 2. 应用语气风格
            val currentEmotion = emotionService.getCurrentEmotion()
            val energyLevel = biologicalClockEngine.getEnergyLevel()

            // 确定关系等级（优先主人，否则使用好友）
            val relationshipLevel = when {
                perception.masterPresent -> com.xiaoguang.assistant.domain.model.RelationshipLevel.MASTER
                perception.friendsPresent.isNotEmpty() -> com.xiaoguang.assistant.domain.model.RelationshipLevel.FRIEND
                else -> com.xiaoguang.assistant.domain.model.RelationshipLevel.ACQUAINTANCE
            }

            // 应用语气风格
            val styledMessage = toneStyleEngine.applyStyle(
                message = rawMessage,
                emotion = currentEmotion,
                relationshipLevel = relationshipLevel,
                energyLevel = energyLevel
            )

            // 应用不完美性（让小光更真实）
            val imperfectionResult = imperfectionEngine.processMessage(
                message = styledMessage,
                emotion = currentEmotion
            )

            val message = imperfectionResult.message

            // 如果触发了不完美，记录日志
            if (imperfectionResult.type != com.xiaoguang.assistant.domain.personality.ImperfectionType.NONE) {
                Timber.i("[ActionLayer] 不完美行为触发: ${imperfectionResult.type}")
            }

            Timber.i("[ActionLayer] 小光主动发言: $message (原始: $rawMessage)")

            // 3. 发送发言事件（由外部系统处理实际的TTS播放）
            _speakEvents.emit(ProactiveSpeakEvent(
                message = message,
                priority = decision.priority,
                timing = decision.timing,
                reason = decision.reason
            ))

            // 4. ✅ 使用 LLM 推理情绪（根据发言内容和想法类型）
            val emotionEventDescription = buildEmotionEventDescription(decision, thoughts, perception)
            emotionService.reactToEvent(
                event = com.xiaoguang.assistant.domain.emotion.EmotionEvent.Custom(
                    targetEmotion = com.xiaoguang.assistant.domain.model.EmotionalState.CALM,  // 占位符，LLM会重新推理
                    intensity = 0.5f,  // 占位符
                    description = emotionEventDescription
                ),
                speakerName = if (perception.masterPresent) perception.currentSpeakerName else null  // 传入主人信息
            )

            // 5. 记录对话疲劳
            biologicalClockEngine.recordConversation(intensity = 0.1f)

            return ActionResult(
                success = true,
                actionType = ActionType.SPEAK,
                message = message,
                decisionRecord = DecisionRecord(
                    timestamp = System.currentTimeMillis(),
                    shouldSpeak = true,
                    confidence = decision.confidence,
                    reason = decision.reason,
                    actuallySpoke = true,
                    result = SpeakResult(
                        success = true,
                        userResponse = false,  // 待外部反馈
                        affectionChange = 0f
                    )
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "[ActionLayer] 执行发言失败")

            return ActionResult(
                success = false,
                actionType = ActionType.SPEAK,
                message = null,
                error = e.message,
                decisionRecord = DecisionRecord(
                    timestamp = System.currentTimeMillis(),
                    shouldSpeak = true,
                    confidence = decision.confidence,
                    reason = decision.reason,
                    actuallySpoke = false,
                    result = SpeakResult(
                        success = false,
                        userResponse = false
                    )
                )
            )
        }
    }

    /**
     * 执行沉默
     */
    private fun executeSilence(decision: SpeakDecision): ActionResult {
        Timber.d("[ActionLayer] 选择沉默: ${decision.reason}")

        return ActionResult(
            success = true,
            actionType = ActionType.SILENCE,
            message = null,
            decisionRecord = DecisionRecord(
                timestamp = System.currentTimeMillis(),
                shouldSpeak = false,
                confidence = decision.confidence,
                reason = decision.reason,
                actuallySpoke = false,
                result = null
            )
        )
    }

    /**
     * 生成发言消息（纯 LLM，无 fallback）
     */
    private suspend fun generateMessage(
        decision: SpeakDecision,
        perception: Perception,
        thoughts: Thoughts
    ): String {
        // 用 LLM 生成自然消息（唯一方式！）
        val llmMessage = flowLlmService.generateProactiveMessage(
            perception = perception,
            thoughts = thoughts.innerThoughts,
            reason = decision.reason
        )

        if (llmMessage != null && llmMessage.isNotBlank()) {
            Timber.i("[ActionLayer] LLM 生成消息: $llmMessage")
            return llmMessage
        }

        // LLM 失败，抛出异常
        throw IllegalStateException("LLM 生成消息失败，无法继续")
    }

    /**
     * 记录用户反馈
     */
    fun recordUserResponse(responded: Boolean, affectionChange: Float) {
        Timber.d("[ActionLayer] 用户反馈: responded=$responded, affectionChange=$affectionChange")
        // 可以在这里更新统计信息，用于学习和优化
    }

    /**
     * ✅ 并行执行工具调用和发言（类似 MaiBot 的 asyncio.gather）
     *
     * 使用场景示例：
     * - 查询日程 + 告诉主人结果
     * - 添加待办 + 确认已添加
     * - 搜索记忆 + 分享相关内容
     */
    suspend fun executeParallelAction(
        toolCalls: List<Pair<String, JsonObject>>,  // (toolName, arguments)
        decision: SpeakDecision,
        perception: Perception,
        thoughts: Thoughts
    ): ActionResult = coroutineScope {
        try {
            // 并行执行：工具调用（后台） + 发言生成（前台）
            val toolResultsDeferred = async {
                toolCalls.map { (toolName, args) ->
                    try {
                        mcpServer.callTool(toolName, args)
                    } catch (e: Exception) {
                        Timber.e(e, "[ActionLayer] 工具调用失败: $toolName")
                        McpToolResult(success = false, content = "工具调用失败: ${e.message}")
                    }
                }
            }

            val speakResultDeferred = async {
                executeSpeak(decision, perception, thoughts)
            }

            // 等待两个任务都完成
            val toolResults = toolResultsDeferred.await()
            val speakResult = speakResultDeferred.await()

            Timber.i("[ActionLayer] ✨ 并行执行完成: 工具调用${toolResults.size}个, 发言成功=${speakResult.success}")

            // 合并结果
            ActionResult(
                success = speakResult.success && toolResults.all { it.success },
                actionType = ActionType.COMPOSITE,
                message = speakResult.message,
                error = speakResult.error,
                decisionRecord = speakResult.decisionRecord,
                mcpToolResults = toolResults
            )

        } catch (e: Exception) {
            Timber.e(e, "[ActionLayer] 并行执行失败")

            ActionResult(
                success = false,
                actionType = ActionType.COMPOSITE,
                message = null,
                error = e.message,
                decisionRecord = DecisionRecord(
                    timestamp = System.currentTimeMillis(),
                    shouldSpeak = false,
                    confidence = 0f,
                    reason = "并行执行失败: ${e.message}",
                    actuallySpoke = false,
                    result = null
                )
            )
        }
    }

    /**
     * ✅ 执行单个工具调用（不发言）
     */
    suspend fun executeToolCall(
        toolName: String,
        arguments: JsonObject
    ): ActionResult {
        try {
            val result = mcpServer.callTool(toolName, arguments)

            Timber.i("[ActionLayer] 🔧 工具调用: $toolName, 成功=${result.success}")

            return ActionResult(
                success = result.success,
                actionType = ActionType.CALL_TOOL,
                message = null,
                error = if (!result.success) result.content else null,
                decisionRecord = DecisionRecord(
                    timestamp = System.currentTimeMillis(),
                    shouldSpeak = false,
                    confidence = 1.0f,
                    reason = "执行工具调用: $toolName",
                    actuallySpoke = false,
                    result = null
                ),
                mcpToolResults = listOf(result)
            )

        } catch (e: Exception) {
            Timber.e(e, "[ActionLayer] 工具调用失败: $toolName")

            return ActionResult(
                success = false,
                actionType = ActionType.CALL_TOOL,
                message = null,
                error = e.message,
                decisionRecord = DecisionRecord(
                    timestamp = System.currentTimeMillis(),
                    shouldSpeak = false,
                    confidence = 0f,
                    reason = "工具调用失败: ${e.message}",
                    actuallySpoke = false,
                    result = null
                )
            )
        }
    }

    /**
     * ✅ 根据想法类型和发言内容构建情绪事件描述
     * 让 LLM 推理发言后的情绪变化
     */
    private fun buildEmotionEventDescription(
        decision: SpeakDecision,
        thoughts: Thoughts,
        perception: Perception
    ): String {
        // 获取主导想法类型
        val dominantThought = thoughts.innerThoughts.firstOrNull()
        val thoughtType = dominantThought?.type

        // 获取说话对象（优先使用主人名字，否则用"主人"）
        val targetName = if (perception.masterPresent) {
            perception.currentSpeakerName ?: "主人"
        } else {
            perception.currentSpeakerName ?: "某人"
        }

        // 构建丰富的描述，让 LLM 理解发言的情境
        return when (thoughtType) {
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.CARE -> {
                "小光关心地对${targetName}说了话：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.WORRY -> {
                "小光担心地表达了忧虑：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.EXCITEMENT -> {
                "小光兴奋地分享了想法：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.CURIOSITY -> {
                "小光好奇地提出了问题：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.BOREDOM -> {
                "小光为了打破无聊，主动开启话题：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.GREETING -> {
                "小光主动问候${targetName}：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.MISSING -> {
                "小光表达了思念之情：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.SHARE -> {
                "小光分享了一些事情：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.QUESTION -> {
                "小光询问了问题：${decision.suggestedContent}"
            }
            com.xiaoguang.assistant.domain.flow.model.ThoughtType.REMINDER -> {
                "小光提醒${targetName}注意事项：${decision.suggestedContent}"
            }
            else -> {
                // 默认描述
                "小光主动发言：${decision.suggestedContent}（${decision.reason}）"
            }
        }
    }
}

/**
 * 行动结果
 */
data class ActionResult(
    val success: Boolean,
    val actionType: ActionType,
    val message: String?,
    val error: String? = null,
    val decisionRecord: DecisionRecord,
    val mcpToolResults: List<McpToolResult>? = null  // ✅ MCP 工具调用结果（支持多个）
)

/**
 * 行动类型
 */
enum class ActionType {
    SPEAK,          // 发言
    SILENCE,        // 沉默
    CALL_TOOL,      // ✅ 调用工具
    COMPOSITE       // ✅ 组合动作（工具+发言并行）
}

/**
 * 主动发言事件
 */
data class ProactiveSpeakEvent(
    val message: String,
    val priority: com.xiaoguang.assistant.domain.flow.model.SpeakPriority,
    val timing: com.xiaoguang.assistant.domain.flow.model.SpeakTiming,
    val reason: String
)
