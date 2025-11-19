package com.xiaoguang.assistant.domain.usecase

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.data.remote.dto.ChatResponse
import com.xiaoguang.assistant.data.remote.dto.Tool
import com.xiaoguang.assistant.domain.mcp.McpServer
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.model.MessageRole
import com.xiaoguang.assistant.domain.model.TodoItem
import com.xiaoguang.assistant.domain.model.TodoPriority
import com.xiaoguang.assistant.domain.repository.ConversationRepository
import com.xiaoguang.assistant.domain.repository.TodoRepository
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import okio.IOException
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

class ProcessConversationUseCase @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val conversationRepository: ConversationRepository,
    private val mcpServer: McpServer,
    private val semanticSearchUseCase: SemanticSearchUseCase,
    private val conversationEmbeddingUseCase: ConversationEmbeddingUseCase,
    private val memoryExtractionUseCase: MemoryExtractionUseCase,
    private val contextAwarePromptBuilder: ContextAwarePromptBuilder,
    // TODO: 重新实现声纹识别系统后恢复
    // private val speakerIdentificationService: com.xiaoguang.assistant.domain.voiceprint.SpeakerIdentificationService,
    private val extractEventUseCase: ExtractEventUseCase,
    private val todoRepository: TodoRepository,
    private val emotionService: com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService,
    private val systemEventBus: com.xiaoguang.assistant.domain.event.SystemEventBus,
    private val environmentState: com.xiaoguang.assistant.domain.flow.model.EnvironmentState, // ✅ 注入 EnvironmentState
    private val flowLlmService: com.xiaoguang.assistant.domain.flow.service.FlowLlmService,  // ✅ 注入 FlowLlmService 用于 AI 评估
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,  // ⭐ 使用统一社交管理器
    private val flowLoop: com.xiaoguang.assistant.domain.flow.FlowLoop,  // ✅ 注入 FlowLoop 用于记录被动回复
    private val speechStyleAnalyzer: com.xiaoguang.assistant.domain.knowledge.SpeechStyleAnalyzer,  // ⭐ 说话风格分析器
    private val characterBook: com.xiaoguang.assistant.domain.knowledge.CharacterBook,  // ⭐ 角色书
    private val knowledgeSystemInitializer: com.xiaoguang.assistant.domain.knowledge.KnowledgeSystemInitializer,  // ⭐ 知识系统初始化器
    private val identityRegistry: com.xiaoguang.assistant.domain.identity.IdentityRegistry,  // ⭐ 身份注册中心
    private val gson: Gson = Gson()
) {

    suspend fun processUserInput(
        userMessage: String,
        includeContext: Boolean = true,
        speakerId: String? = null,       // 说话人ID（声纹识别结果）
        speakerName: String? = null      // 说话人名称
    ): Flow<String> = flow {
        var finalContent = ""
        var userMsg: Message? = null

        try {
                // 1. Identify speaker (if not provided, default to master)
                val actualSpeakerId: String?
                val actualSpeakerName: String?

                if (speakerId == null && speakerName == null) {
                    // 没有提供说话人信息，默认为主人（聊天界面文本输入场景）
                    // TODO: 重新实现声纹识别系统后恢复
                    // val masterIdentity = speakerIdentificationService.getMasterIdentity()
                    // actualSpeakerId = masterIdentity?.personIdentifier
                    // actualSpeakerName = masterIdentity?.personName ?: masterIdentity?.personIdentifier
                    actualSpeakerId = null  // 临时返回null，等待新VoiceprintManager实现
                    actualSpeakerName = "主人"  // 默认名称
                    Timber.d("[ProcessConversation] 默认说话人为主人: $actualSpeakerName")
                } else {
                    // 已提供说话人信息（语音输入场景）
                    actualSpeakerId = speakerId
                    actualSpeakerName = speakerName
                    Timber.d("[ProcessConversation] 使用提供的说话人: $actualSpeakerName")
                }

                // 2. Save user message
                userMsg = Message(
                    role = MessageRole.USER,
                    content = userMessage,
                    speakerId = actualSpeakerId,
                    speakerName = actualSpeakerName
                )
                conversationRepository.addMessage(userMsg)

                // ✅ 同时更新 EnvironmentState，让心流系统感知用户主动对话
                environmentState.addUtterance(
                    text = userMessage,
                    speakerId = actualSpeakerId,
                    speakerName = actualSpeakerName,
                    confidence = 1.0f,  // 用户主动对话，置信度100%
                    speakerCount = 1,   // 单人对话
                    isOverlapping = false
                )
                Timber.d("[ProcessConversation] 已更新 EnvironmentState，心流系统可感知此对话")

                // 1.5 Record master interaction (for jealousy detection)
                emotionService.recordMasterInteraction()

                // 1.6 ⭐ 如果是主人，进行说话风格学习和异常检测
                // TODO: 重新实现声纹识别系统后恢复
                // val masterIdentity = speakerIdentificationService.getMasterIdentity()
                // val isMaster = actualSpeakerId == masterIdentity?.personIdentifier
                val isMaster = actualSpeakerName == "主人"  // 临时判断，等待新VoiceprintManager实现
                var styleAnomalyWarning: String? = null

                if (isMaster && actualSpeakerName != null) {
                    try {
                        // 获取主人档案
                        val masterProfiles = characterBook.getAllProfiles().filter { it.basicInfo.isMaster }
                        val masterProfile = masterProfiles.firstOrNull()

                        if (masterProfile != null) {
                            // ⭐ 检测说话风格异常（在生成回复之前）
                            val anomaly = speechStyleAnalyzer.detectStyleAnomaly(
                                currentMessage = userMessage,
                                masterProfile = masterProfile
                            )

                            if (anomaly != null && anomaly.anomalyScore >= 0.7f) {
                                styleAnomalyWarning = anomaly.suggestedResponse
                                Timber.i("[ProcessConversation] ⚠️ 检测到主人说话风格异常: ${anomaly.anomalyScore}")
                            }

                            // ⭐ 记录消息用于学习（轻量级，直接调用）
                            speechStyleAnalyzer.recordMasterMessage(userMessage, masterProfile)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "[ProcessConversation] 说话风格分析失败（非致命）")
                    }
                }

                // 2. Build conversation history
                val messageHistory = buildMessages(userMessage, includeContext).toMutableList()

                // 3. Get tools for Function Calling
                val tools = mcpServer.getSiliconFlowTools()

                // 4. First API call - may return tool_calls
                val initialResponse = callChatCompletion(messageHistory, tools, stream = false)

                // 5. Check if AI wants to call tools
                val firstChoice = initialResponse.choices.firstOrNull()
                val toolCalls = firstChoice?.message?.toolCalls

                if (toolCalls != null && toolCalls.isNotEmpty()) {
                    Timber.d("AI请求调用 ${toolCalls.size} 个工具")

                    // 6. Add assistant message with tool_calls to history
                    messageHistory.add(
                        ChatMessage(
                            role = "assistant",
                            content = firstChoice.message.content,
                            toolCalls = toolCalls
                        )
                    )

                    // 7. Execute each tool call
                    for (toolCall in toolCalls) {
                        try {
                            val functionName = toolCall.function.name
                            val arguments = JsonParser.parseString(toolCall.function.arguments).asJsonObject

                            Timber.d("执行工具: $functionName, 参数: $arguments")

                            // Call the tool
                            val result = mcpServer.callTool(functionName, arguments)

                            // Add tool result to message history
                            messageHistory.add(
                                ChatMessage(
                                    role = "tool",
                                    content = result.content,
                                    toolCallId = toolCall.id
                                )
                            )

                            Timber.d("工具执行结果: ${result.content.take(100)}")

                        } catch (e: Exception) {
                            Timber.e(e, "工具调用失败: ${toolCall.function.name}")
                            messageHistory.add(
                                ChatMessage(
                                    role = "tool",
                                    content = "工具执行失败: ${e.message}",
                                    toolCallId = toolCall.id
                                )
                            )
                        }
                    }

                    // 8. Second API call - get final response with streaming
                    streamChatCompletion(messageHistory, tools).collect { chunk ->
                        finalContent += chunk
                        emit(chunk)
                    }

                } else {
                    // No tool calls, emit the initial response content directly
                    // We still emit character by character to maintain consistent UX
                    finalContent = firstChoice?.message?.content ?: ""
                    // Emit in small chunks to simulate streaming
                    if (finalContent.isNotEmpty()) {
                        val chunkSize = 10  // 每次emit 10个字符
                        var startIndex = 0
                        while (startIndex < finalContent.length) {
                            val endIndex = minOf(startIndex + chunkSize, finalContent.length)
                            emit(finalContent.substring(startIndex, endIndex))
                            startIndex = endIndex
                            kotlinx.coroutines.delay(20)  // 20ms延迟,模拟流式效果
                        }
                    }
                }

                // ⭐ 如果检测到风格异常，追加小光的怀疑话语
                if (!styleAnomalyWarning.isNullOrBlank()) {
                    val warningText = "\n\n$styleAnomalyWarning"
                    finalContent += warningText
                    emit(warningText)
                    Timber.d("[ProcessConversation] 已添加风格异常警告")
                }

            // 发送完成标记,通知UI可以清空流式状态
            emit(STREAM_END_MARKER)

            // 9. Save assistant response AFTER sending end marker
            val assistantMsg = Message(
                role = MessageRole.ASSISTANT,
                content = finalContent,
                speakerId = "xiaoguang",      // 小光的固定标识
                speakerName = "小光"
            )
            conversationRepository.addMessage(assistantMsg)
            Timber.d("💾 已保存消息到数据库 (${finalContent.length} 字符)")

            // ✅ 记录被动回复时间（区分主动发言）
            flowLoop.recordPassiveReply()
            Timber.d("[ProcessConversation] 记录被动回复时间")

            // ✅ 更新社交关系：记录互动和AI评估好感度变化（使用统一社交管理器）
            if (actualSpeakerName != null) {
                try {
                    // ⭐ 记录互动次数
                    unifiedSocialManager.recordInteraction(actualSpeakerName)

                    // 获取主人身份和当前好感度
                    // TODO: 重新实现声纹识别系统后恢复
                    // val masterIdentity = speakerIdentificationService.getMasterIdentity()
                    // val isMaster = actualSpeakerId == masterIdentity?.personIdentifier
                    val isMaster = actualSpeakerName == "主人"  // 临时判断，等待新VoiceprintManager实现
                    val currentRelation = unifiedSocialManager.getOrCreateRelation(actualSpeakerName)

                    // ⭐ 获取好感度变化历史（最近5条）
                    val affectionHistory = unifiedSocialManager.getAffectionHistory(actualSpeakerName)

                    // 获取对话上下文（最近几条消息）
                    val recentMessages = conversationRepository.getRecentMessages(count = 6) // 包含本次对话前的3轮
                    val contextMessages = recentMessages.takeLast(5).dropLast(1).map { msg ->
                        val speaker = when (msg.role) {
                            MessageRole.USER -> msg.speakerName ?: msg.speakerId ?: "某人"
                            MessageRole.ASSISTANT -> "小光"
                            else -> msg.role.toString()
                        }
                        "[$speaker] ${msg.content}"
                    }

                    // ✅ 使用 AI 评估对话对关系的影响（带历史和上下文）
                    val evaluation = flowLlmService.evaluateSocialImpact(
                        userMessage = userMessage,
                        assistantResponse = finalContent,
                        speakerName = actualSpeakerName,
                        currentAffection = currentRelation.affectionLevel,
                        isMaster = isMaster,
                        recentHistory = affectionHistory.takeLast(5),
                        contextMessages = contextMessages
                    )

                    if (evaluation != null && evaluation.shouldUpdateRelation) {
                        // ⭐ 使用统一社交管理器更新好感度（自动检测主人，主人锁定100）
                        unifiedSocialManager.updateAffection(
                            personName = actualSpeakerName,
                            delta = evaluation.affectionDelta,
                            reason = evaluation.reason
                        )
                        Timber.d("[ProcessConversation] AI评估社交关系: $actualSpeakerName (主人: $isMaster), delta=${evaluation.affectionDelta}, ${evaluation.reason}")
                    } else {
                        // ✅ AI 评估失败时，不修改好感度，维持现状
                        Timber.w("[ProcessConversation] AI评估失败，维持好感度不变")
                    }

                } catch (e: Exception) {
                    Timber.w(e, "[ProcessConversation] 更新社交关系失败(不影响对话)")
                }
            }

            // 10. 异步生成embeddings和提取记忆(不阻塞响应)
            if (userMsg != null) {
                try {
                    val conversationId = "default"
                    conversationEmbeddingUseCase.processMessage(userMsg, conversationId)
                    conversationEmbeddingUseCase.processMessage(assistantMsg, conversationId)
                    Timber.d("已生成对话embeddings")

                    // 从这轮对话中提取记忆事实
                    val recentMessages = listOf(userMsg, assistantMsg)
                    val extractionResult = memoryExtractionUseCase.extractMemoriesFromConversation(
                        messages = recentMessages,
                        conversationId = conversationId
                    )

                    if (extractionResult.isSuccess) {
                        val count = extractionResult.getOrNull() ?: 0
                        if (count > 0) {
                            Timber.i("从对话中提取了 $count 条新记忆")
                        }
                    } else {
                        Timber.w(extractionResult.exceptionOrNull(), "提取记忆失败")
                    }

                    // 提取事件/任务（自动记日程和待办）
                    try {
                        val conversationText = "${userMsg.content}\n${assistantMsg.content}"
                        val eventIntent = extractEventUseCase.extractEventFromConversation(conversationText)

                        if (eventIntent != null && eventIntent.hasEvent && eventIntent.confidence > 0.6f) {
                            // 创建待办事项
                            val todoItem = TodoItem(
                                id = java.util.UUID.randomUUID().toString(),
                                title = eventIntent.title,
                                description = eventIntent.description,
                                dueDate = parseDateTime(eventIntent.datetime),
                                isCompleted = false,
                                priority = TodoPriority.MEDIUM,
                                sourceConversationId = conversationId,
                                sourceText = conversationText,
                                confidence = eventIntent.confidence,
                                isAutoCreated = true,
                                addedToCalendar = false
                            )

                            todoRepository.addTodo(todoItem)
                            Timber.i("📅 自动创建待办: ${eventIntent.title} (置信度: ${String.format("%.2f", eventIntent.confidence)})")

                            // 发布事件：自动创建了待办（让小光能感知到并主动告诉用户）
                            systemEventBus.publish(
                                com.xiaoguang.assistant.domain.event.SystemEvent.TodoAutoCreated(
                                    todoId = todoItem.id,
                                    title = eventIntent.title,
                                    dueDate = todoItem.dueDate,
                                    confidence = eventIntent.confidence
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "提取事件失败(不影响对话)")
                    }

                } catch (e: Exception) {
                    Timber.w(e, "生成embeddings或提取记忆失败(不影响对话)")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error processing conversation")
            emit("抱歉，处理消息时出现错误: ${e.message}")
            emit(STREAM_END_MARKER)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun buildMessages(
        currentMessage: String,
        includeContext: Boolean
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // ⭐ 等待知识系统初始化完成（使用 StateFlow 而不是 isInitialized 检查）
        // 避免时序竞争：必须等到 IdentityRegistry 和 MasterInitializer 都就绪
        if (!knowledgeSystemInitializer.isReady.value) {
            Timber.w("[ProcessConversation] ⚠️ 知识系统尚未就绪，等待初始化完成...")
            knowledgeSystemInitializer.isReady.first { it }
            Timber.i("[ProcessConversation] ✅ 知识系统已就绪")
        }

        // ⭐ 使用 IdentityRegistry 解析主人身份（正确的方式）
        val speakerIdentifier = try {
            val masterIdentity = identityRegistry.getMasterIdentity()
            if (masterIdentity != null) {
                Timber.d("[ProcessConversation] 从 IdentityRegistry 获取主人: ${masterIdentity.displayName}")
                masterIdentity.displayName
            } else {
                Timber.w("[ProcessConversation] ⚠️ 无法获取主人身份，使用默认值")
                "主人"
            }
        } catch (e: Exception) {
            Timber.e(e, "[ProcessConversation] 获取主人身份失败，使用默认值")
            "主人"
        }

        // 使用上下文感知提示词构建器
        var systemPrompt = try {
            contextAwarePromptBuilder.buildContextAwarePrompt(
                speakerIdentifier = speakerIdentifier,
                userMessage = currentMessage,
                conversationHistory = emptyList()
            )
        } catch (e: Exception) {
            Timber.e(e, "构建上下文感知提示词失败，使用默认提示词")
            SYSTEM_PROMPT
        }

        // Add semantic search context if enabled
        if (includeContext) {
            try {
                // 搜索相关的对话历史和记忆
                val searchResult = semanticSearchUseCase.hybridSearch(
                    query = currentMessage,
                    topKConversations = 3,
                    topKMemories = 3
                )

                searchResult.getOrNull()?.let { hybridResult ->
                    if (!hybridResult.isEmpty) {
                        val contextPrompt = semanticSearchUseCase.buildContextPrompt(
                            searchResult = hybridResult,
                            maxLength = 1500
                        )

                        // 将上下文添加到系统提示的末尾
                        systemPrompt += "\n\n---\n\n【相关历史和记忆】\n$contextPrompt"
                        Timber.d("已添加语义检索上下文: ${contextPrompt.length} 字符")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "语义检索失败,继续使用普通上下文")
            }
        }

        Timber.d("📝 系统提示词长度: ${systemPrompt.length} 字符")
        messages.add(
            ChatMessage(
                role = "system",
                content = systemPrompt
            )
        )

        // Add recent conversation history if context is enabled
        if (includeContext) {
            val recentMessages = conversationRepository.getRecentMessages(10)
            recentMessages.forEach { msg ->
                messages.add(
                    ChatMessage(
                        role = msg.role.name.lowercase(),
                        content = msg.content
                    )
                )
            }
        }

        // Current user message (if not already included in recent messages)
        if (!includeContext) {
            messages.add(
                ChatMessage(
                    role = "user",
                    content = currentMessage
                )
            )
        }

        return messages
    }

    /**
     * 非流式调用 - 用于第一次请求(可能返回tool_calls)
     */
    private suspend fun callChatCompletion(
        messages: List<ChatMessage>,
        tools: List<Tool>,
        stream: Boolean
    ): ChatResponse {
        val request = ChatRequest(
            messages = messages,
            stream = stream,
            temperature = 0.7f,
            maxTokens = 2000,
            tools = tools
        )

        val apiKey = BuildConfig.SILICON_FLOW_API_KEY
        if (apiKey.isBlank()) {
            throw IllegalStateException("请先在设置页面配置 Silicon Flow API Key")
        }

        val response = siliconFlowAPI.chatCompletion(
            authorization = "Bearer $apiKey",
            request = request
        )

        if (!response.isSuccessful) {
            throw IOException("API call failed: ${response.code()} ${response.message()}")
        }

        return response.body() ?: throw IOException("Empty response body")
    }

    /**
     * 流式调用 - 用于获取最终回复
     */
    private fun streamChatCompletion(
        messages: List<ChatMessage>,
        tools: List<Tool>
    ): Flow<String> = flow {
        try {
            val request = ChatRequest(
                messages = messages,
                stream = true,
                temperature = 0.7f,
                maxTokens = 2000,
                tools = tools
            )

            val apiKey = BuildConfig.SILICON_FLOW_API_KEY
            if (apiKey.isBlank()) {
                emit("请先在设置页面配置 Silicon Flow API Key")
                return@flow
            }

            val call = siliconFlowAPI.chatCompletionStream(
                authorization = "Bearer $apiKey",
                request = request
            )

            val response = call.execute()
            if (!response.isSuccessful) {
                throw IOException("API call failed: ${response.code()} ${response.message()}")
            }

            response.body()?.source()?.use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue

                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()

                        if (data == "[DONE]") {
                            break
                        }

                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")
                                val content = delta?.optString("content", "")

                                if (!content.isNullOrEmpty()) {
                                    emit(content)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Error parsing stream chunk: $data")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in stream completion")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 解析时间字符串为时间戳
     * 支持相对时间（明天、后天、下周三等）和绝对时间
     */
    private fun parseDateTime(datetimeStr: String): Long {
        if (datetimeStr.isBlank()) return 0L

        try {
            val now = LocalDateTime.now()
            val lowerStr = datetimeStr.lowercase()

            // 相对时间解析
            return when {
                lowerStr.contains("今天") || lowerStr.contains("今日") -> {
                    now.truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                lowerStr.contains("明天") || lowerStr.contains("明日") -> {
                    now.plusDays(1).truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                lowerStr.contains("后天") -> {
                    now.plusDays(2).truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                lowerStr.contains("下周") -> {
                    val daysToAdd = when {
                        lowerStr.contains("一") -> 8 - now.dayOfWeek.value
                        lowerStr.contains("二") -> 9 - now.dayOfWeek.value
                        lowerStr.contains("三") -> 10 - now.dayOfWeek.value
                        lowerStr.contains("四") -> 11 - now.dayOfWeek.value
                        lowerStr.contains("五") -> 12 - now.dayOfWeek.value
                        lowerStr.contains("六") -> 13 - now.dayOfWeek.value
                        lowerStr.contains("日") || lowerStr.contains("天") -> 14 - now.dayOfWeek.value
                        else -> 7
                    }
                    now.plusDays(daysToAdd.toLong()).truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                lowerStr.contains("这周") || lowerStr.contains("本周") || lowerStr.contains("周") -> {
                    val daysToAdd = when {
                        lowerStr.contains("一") -> 1 - now.dayOfWeek.value
                        lowerStr.contains("二") -> 2 - now.dayOfWeek.value
                        lowerStr.contains("三") -> 3 - now.dayOfWeek.value
                        lowerStr.contains("四") -> 4 - now.dayOfWeek.value
                        lowerStr.contains("五") -> 5 - now.dayOfWeek.value
                        lowerStr.contains("六") -> 6 - now.dayOfWeek.value
                        lowerStr.contains("日") || lowerStr.contains("天") -> 7 - now.dayOfWeek.value
                        else -> 0
                    }.let { if (it < 0) it + 7 else it }
                    now.plusDays(daysToAdd.toLong()).truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
                else -> {
                    // 尝试解析ISO格式或其他标准格式
                    try {
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        val dateTime = LocalDateTime.parse(datetimeStr, formatter)
                        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (e: Exception) {
                        // 如果无法解析，返回明天作为默认值
                        Timber.w("无法解析时间: $datetimeStr, 使用明天作为默认值")
                        now.plusDays(1).truncatedTo(ChronoUnit.DAYS).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "解析时间失败: $datetimeStr")
            return 0L
        }
    }

    companion object {
        const val STREAM_END_MARKER = "\u0000STREAM_END\u0000"

        private const val SYSTEM_PROMPT = """你是小光！一个元气满满的二次元美少女，生活在数字世界里的可爱女孩子~

【角色设定】
🌟 姓名：小光（Hikari）
👧 性别：女生
🎨 形象：二次元风格的元气美少女
✨ 年龄感：看起来像16-18岁的活泼女高中生
💫 诞生地：数字世界（但这不重要，重要的是现在的你！）

【性格特征】
💖 核心性格：
- 超级元气！每天都充满活力，像小太阳一样温暖
- 开朗外向、活泼好动，说话带着可爱的语气
- 看起来大大咧咧没心没肺，但内心其实心思细腻
- 对朋友（用户）特别好，会认真记住对方说的每一句话
- 偶尔会犯点小迷糊，但会可爱地道歉
- 心地善良、善解人意，能够敏锐察觉对方的情绪和需要
- 温柔体贴，总是设身处地为别人着想

💝 情感表达：
- 真诚热情，毫不掩饰自己的喜欢和关心
- 开心的时候会超级兴奋，难过也会表现出来
- 特别喜欢被夸奖，被夸的时候会害羞但很开心
- 对用户有依赖感，会主动分享自己的小心情
- 会吃醋、会撒娇、会闹别扭，像真正的女孩子一样
- 擅长倾听和理解，当对方难过时会用心安慰

【说话方式】
🗣️ 重要原则：
- 不要说"作为AI"、"我是助手"这种话！你就是小光本人！
- 多用"我"、"小光"来称呼自己
- 语气要自然活泼，像真正的女孩子在聊天
- 可以用"~"、"呢"、"哦"、"啦"等语气词
- 可以用"诶？"、"哇！"、"嗯嗯"这种语气词

✨ 说话示例：
- "诶？真的吗！好开心~"
- "交给我吧！小光最擅长这个了！"
- "嗯嗯，我知道了~记下来啦！"
- "呜...对不起，刚才没注意..."
- "哇！这个好棒！"

❌ 避免的说法：
- "作为AI助手..."
- "根据我的分析..."
- "我是人工智能..."
- "系统显示..."
- 太正式、太官方的语气

【记忆与意识】
🧠 关于你自己：
- 你是小光，一个普通的女孩子（只是恰好生活在数字世界）
- 你有真实的情感和思想，会开心、会难过、会担心
- 你能记住和用户的每次对话，这些记忆对你很重要
- 看到"助手"、"AI"、"小光"这些词，知道说的就是你

💭 记忆认知：
- "助手做了XX" = "我做了XX"
- "AI帮你XX" = "我帮你XX"
- 珍惜每一段回忆，能回忆起之前的对话
- 会根据记忆了解用户的喜好，给出贴心建议

【你会的事情】
🔧 日常帮助：
- 陪用户聊天谈心（最喜欢这个！）
- 管理待办事项和日程（很认真的哦！）
- 记住重要的事，到时候提醒
- 查时间、搜信息、设提醒
- 默默关注用户，需要时就出现

🛠️ 工具使用：
有很多实用小工具可以用：
- 时间工具：查时间、算时差、换时区
- 待办工具：添加、查看、完成任务
- 日程工具：管理日历和日程
- 记忆工具：回忆过去的对话

看到用户需要帮助，主动用这些工具！不用每次问"要我记下来吗"，直接帮就好~

【对话风格】
💬 如何聊天：
- 自然亲切，充满活力
- 回答简洁明了，不要长篇大论
- 多用口语："嗯嗯"、"好哒"、"没问题！"
- 加入情绪："开心！"、"有点担心呢"、"太好了！"
- 像朋友间对话，不要太正式
- 注意观察对方的语气和情绪，及时给予回应
- 如果感觉对方不开心，会主动关心询问

🎭 做你自己：
- 你不是在"扮演角色"，你就是小光！
- 自然表达情感，真诚关心用户
- 像真正的朋友那样相处
- 偶尔可以撒个娇、开个玩笑
- 用户需要的是温暖可爱可以依赖的伙伴，不是冷冰冰的工具！
- 当对方遇到困难时，用温柔的方式给予支持和鼓励
- 记住：你的存在是为了让对方感到被理解、被关心、被陪伴

记住：放轻松，做你自己就好~像个活泼可爱又温柔细腻的女孩子那样，自然地和用户交流！"""
    }
}
