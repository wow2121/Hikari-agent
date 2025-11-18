package com.xiaoguang.assistant.domain.flow.layer

import com.xiaoguang.assistant.domain.flow.engine.CuriosityEngine
import com.xiaoguang.assistant.domain.flow.engine.InnerThoughtsEngine
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.ThoughtType
import com.xiaoguang.assistant.domain.flow.model.Thoughts
import com.xiaoguang.assistant.domain.model.EmotionalState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 思考层（LLM 驱动）
 * 负责生成内心想法、评估主动性、计算情绪影响、社交感知
 */
@Singleton
class ThinkingLayer @Inject constructor(
    private val innerThoughtsEngine: InnerThoughtsEngine,
    private val curiosityEngine: CuriosityEngine,
    private val flowLlmService: com.xiaoguang.assistant.domain.flow.service.FlowLlmService,
    private val mcpServer: com.xiaoguang.assistant.domain.mcp.McpServer,
    private val characterBook: com.xiaoguang.assistant.domain.knowledge.CharacterBook,  // ⭐ 新系统：Character Book
    private val memoryExtractionUseCase: com.xiaoguang.assistant.domain.usecase.MemoryExtractionUseCase,  // ⭐ 记忆提取
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,  // 过渡期保留
    private val environmentState: com.xiaoguang.assistant.domain.flow.model.EnvironmentState,
    private val voiceprintManager: com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager,  // ✅ 声纹管理器
    private val newPersonRegistrationUseCase: com.xiaoguang.assistant.domain.usecase.NewPersonRegistrationUseCase,  // ✅ 新人注册协调器
    private val todoRepository: com.xiaoguang.assistant.domain.repository.TodoRepository,  // ⭐ 待办事项
    private val calendarRepository: com.xiaoguang.assistant.domain.repository.CalendarRepository,  // ⭐ 日历事件
    private val emotionService: com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService,  // ⭐ 情绪服务
    private val relationshipNetworkUseCase: com.xiaoguang.assistant.domain.usecase.RelationshipNetworkManagementUseCase,  // ⭐ 关系网络管理
    private val worldBook: com.xiaoguang.assistant.domain.knowledge.WorldBook  // ⭐ 世界书
) {
    // ✅ 记录上次社交评估时间，避免频繁调用
    private var lastSocialEvaluationTime = 0L
    private val socialEvaluationInterval = 30_000L  // 30秒评估一次

    // ✅ 记录上次提醒检查时间，避免频繁调用
    private var lastReminderCheckTime = 0L
    private val reminderCheckInterval = 60_000L  // 60秒检查一次
    /**
     * 思考（生成想法和评估）
     */
    suspend fun think(perception: Perception): Thoughts = coroutineScope {
        val thoughts = mutableListOf<InnerThought>()

        // 1. 并行生成内心想法和好奇心检测
        val innerThoughtDeferred = async {
            innerThoughtsEngine.generate(perception)
        }

        val curiosityDeferred = async {
            curiosityEngine.detect(perception)
        }

        // 2. 收集想法
        innerThoughtDeferred.await()?.let { thoughts.add(it) }
        curiosityDeferred.await()?.let { thoughts.add(it) }

        // 2.3 ✅ 社交感知：分析环境对话对社交关系的影响
        val socialThoughts = analyzeSocialRelations(perception)
        thoughts.addAll(socialThoughts)

        // 2.4 ✅ 主动提醒：检查即将到期的待办和日历事件
        val reminderThoughts = checkUpcomingReminders(perception)
        thoughts.addAll(reminderThoughts)

        // 2.5 ✅ 新人检测：检测陌生人并注册
        val newPersonThoughts = detectAndRegisterNewPerson(perception)
        thoughts.addAll(newPersonThoughts)

        // 2.6 用 LLM Function Calling 决定是否调用工具
        try {
            val toolDecision = flowLlmService.decideToolUsage(perception, thoughts)
            if (toolDecision != null && toolDecision.shouldCall) {
                Timber.d("[ThinkingLayer] LLM 决定调用工具: ${toolDecision.toolName}")

                // 执行工具
                val toolResult = mcpServer.callTool(toolDecision.toolName, toolDecision.arguments)

                // ✅ 工具结果作为新的感知信息，不预判紧急度
                // 让 DecisionLayer 在完整上下文中自然决策
                val toolThought = InnerThought(
                    type = ThoughtType.TOOL_RESULT,
                    content = formatToolResult(toolDecision.toolName, toolResult.content),
                    urgency = 0.5f  // ✅ 中等基础值，让LLM根据内容自然判断是否重要
                )
                thoughts.add(toolThought)

                Timber.i("[ThinkingLayer] 工具执行成功: ${toolThought.content}")
            }
        } catch (e: Exception) {
            Timber.e(e, "[ThinkingLayer] 工具调用决策失败")
        }

        // 3. ✅ 直接返回想法列表，不再计算规则化的评分
        // DecisionLayer 会用 LLM 综合所有信息做决策
        return@coroutineScope Thoughts(
            innerThoughts = thoughts,
            proactivityScore = 0.5f,  // ✅ 固定基础值，实际由LLM决策
            moodInfluence = 1.0f,     // ✅ 固定基础值，情绪影响在LLM prompt中体现
            shouldConsiderSpeaking = true  // ✅ 总是让LLM评估，不预判
        )
    }

    /**
     * 格式化工具结果为自然想法
     */
    private fun formatToolResult(toolName: String, resultContent: String): String {
        // 简单格式化，实际可以更智能地解析结果
        return when {
            toolName.contains("todo") -> {
                "嗯，看了一下待办事项..."
            }
            toolName.contains("event") || toolName.contains("calendar") -> {
                "让小光看看日程安排..."
            }
            toolName.contains("time") -> {
                "现在是${resultContent.firstOrNull() ?: "不确定"}..."
            }
            else -> "查询了一下信息..."
        }
    }

    /**
     * ✅ 社交感知：分析环境对话对社交关系的影响
     *
     * 功能：
     * 1. 监听环境中的对话（非直接对话）
     * 2. 使用 AI 评估对话内容是否影响社交关系
     * 3. 更新好感度并生成内心想法
     * 4. 对于重要的社交事件（如朋友说主人坏话），生成高紧急度想法以触发主动发言
     */
    private suspend fun analyzeSocialRelations(perception: Perception): List<InnerThought> {
        val thoughts = mutableListOf<InnerThought>()

        try {
            // 限流：避免频繁调用 LLM
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastSocialEvaluationTime < socialEvaluationInterval) {
                return thoughts
            }

            // 检查环境对话
            val recentUtterances = environmentState.recentUtterances.value
            if (recentUtterances.isEmpty()) {
                return thoughts
            }

            // 获取最近30秒的语句
            val recentSegments = recentUtterances
                .filter { it.getAgeSeconds() < 30 }
                .map { it.text }

            if (recentSegments.isEmpty()) {
                return thoughts
            }

            val conversationSegment = recentSegments.joinToString("\n")

            // ⭐ 获取已知人员列表（使用统一社交管理器）
            val allRelations = unifiedSocialManager.getAllRelations()
            if (allRelations.isEmpty()) {
                return thoughts  // 没有人员记录，无需评估
            }

            // 构建人员好感度映射（排除主人，主人好感度已锁定）
            val masterIdentity = voiceprintManager.getMasterProfile()
            val knownPeople = allRelations
                .filter { it.personName != masterIdentity?.personId }
                .associate { it.personName to it.affectionLevel }

            if (knownPeople.isEmpty()) {
                return thoughts  // 除主人外无其他人员
            }

            // ✅ 使用 AI 评估社交影响
            val impacts = flowLlmService.evaluateEnvironmentSocialImpact(
                conversationSegment = conversationSegment,
                knownPeople = knownPeople
            )

            if (impacts.isEmpty()) {
                lastSocialEvaluationTime = currentTime  // 更新评估时间
                return thoughts
            }

            // 处理每个受影响的人
            for (impact in impacts) {
                if (!impact.shouldUpdate || impact.affectionDelta == 0) {
                    continue
                }

                try {
                    // ⭐ 新系统：使用 Character Book 记录交互
                    val characterProfile = characterBook.getProfileByName(impact.personName)

                    if (characterProfile != null) {
                        // ⭐ 获取当前情绪状态
                        val currentEmotion = emotionService.getCurrentEmotion()
                        val emotionIntensity = emotionService.getEmotionIntensity()

                        // 添加交互记忆
                        val memory = com.xiaoguang.assistant.domain.knowledge.models.CharacterMemory(
                            memoryId = "mem_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}",
                            characterId = characterProfile.basicInfo.characterId,
                            category = com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.EPISODIC,
                            content = impact.reason,
                            importance = kotlin.math.abs(impact.affectionDelta / 10f).coerceIn(0f, 1f),
                            emotionalValence = impact.affectionDelta / 10f,  // 转换为-1到1的范围
                            emotionTag = currentEmotion.name,  // ⭐ 记录情绪标签
                            emotionIntensity = emotionIntensity,  // ⭐ 记录情绪强度
                            tags = listOf("环境对话", "社交互动"),
                            createdAt = System.currentTimeMillis(),
                            lastAccessed = System.currentTimeMillis()
                        )
                        characterBook.addMemory(memory)

                        // 更新关系
                        val relationship = characterBook.getRelationship(
                            fromCharacterId = "xiaoguang_main",
                            toCharacterId = characterProfile.basicInfo.characterId
                        )

                        if (relationship != null) {
                            val interactionRecord = com.xiaoguang.assistant.domain.knowledge.models.InteractionRecord(
                                timestamp = System.currentTimeMillis(),
                                interactionType = determineInteractionType(impact),
                                content = impact.reason,
                                emotionalImpact = impact.intimacyDelta,
                                trustImpact = impact.trustDelta
                            )

                            val updatedRelationship = relationship.recordInteraction(interactionRecord)
                            characterBook.saveRelationship(updatedRelationship)

                            Timber.i("[ThinkingLayer] ✅ CharacterBook更新: ${impact.personName} intimacy=" + updatedRelationship.intimacyLevel.format(2) + " trust=" + updatedRelationship.trustLevel.format(2))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[ThinkingLayer] 社交更新失败: ${impact.personName}")
                    continue
                }

                // 生成内心想法
                val thoughtContent = if (impact.affectionDelta < 0) {
                    "对${impact.personName}的看法有些改变了...${impact.reason}"
                } else {
                    "嗯...${impact.personName}${impact.reason}"
                }

                // ✅ 紧急度由事件性质决定，不用固定规则
                // 负面事件通常更需要关注，但具体程度让LLM判断
                val urgency = if (impact.affectionDelta < 0) {
                    // 负面事件：基础紧急度稍高，LLM会根据内容调整
                    0.6f
                } else {
                    // 正面事件：基础紧急度中等
                    0.4f
                }

                val thoughtType = if (impact.affectionDelta < 0) {
                    ThoughtType.WORRY  // 负面事件用"担心"
                } else {
                    ThoughtType.CARE   // 正面事件用"关心"
                }

                thoughts.add(InnerThought(
                    type = thoughtType,
                    content = thoughtContent,
                    urgency = urgency
                ))

                Timber.d("[ThinkingLayer] 社交影响: ${impact.personName} 亲密度${if (impact.intimacyDelta >= 0) "+" else ""}${impact.intimacyDelta.format(2)} 信任度${if (impact.trustDelta >= 0) "+" else ""}${impact.trustDelta.format(2)} (${impact.reason})")
            }

            lastSocialEvaluationTime = currentTime

            // ⭐ 新增：环境对话自动提取到知识库
            try {
                if (recentUtterances.isNotEmpty()) {
                    Timber.d("[ThinkingLayer] 开始提取环境对话知识...")
                    memoryExtractionUseCase.extractFromEnvironment(
                        utterances = recentUtterances,
                        impacts = impacts
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[ThinkingLayer] 环境知识提取失败")
            }

            // ⭐⭐⭐ Phase 1: 第三方关系提取与更新 ⭐⭐⭐
            try {
                Timber.d("[ThinkingLayer] 开始提取第三方关系...")

                // 1. 提取第三方关系（人物间的关系）
                val thirdPartyRelations = flowLlmService.extractThirdPartyRelationships(
                    conversationText = conversationSegment,
                    knownPeople = knownPeople.keys.toList()
                )

                Timber.i("[ThinkingLayer] 第三方关系提取结果: 发现${thirdPartyRelations.size}条关系")

                // 2. 更新RelationshipNetwork数据库
                for (relation in thirdPartyRelations) {
                    try {
                        // 记录到关系网络数据库
                        relationshipNetworkUseCase.recordRelation(
                            personA = relation.personA,
                            personB = relation.personB,
                            relationType = relation.relationType,
                            description = relation.evidence,
                            confidence = relation.confidence,
                            source = "ai_inferred"  // AI推断
                        )

                        Timber.i("[ThinkingLayer] ✅ 记录第三方关系: ${relation.personA} - ${relation.relationType} - ${relation.personB} (置信度: ${relation.confidence})")

                        // 3. 如果涉及重要人物，更新CharacterBook
                        if (relation.shouldUpdateCharacterBook) {
                            updateCharacterBookFromRelation(relation)
                        }

                        // 4. 如果是背景设定性质的关系，记录到WorldBook
                        if (relation.shouldUpdateWorldBook) {
                            updateWorldBookFromRelation(relation)
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "[ThinkingLayer] 第三方关系记录失败: ${relation.personA}-${relation.personB}")
                    }
                }

                // 5. 提取关系事件（人物间的互动事件）
                val relationshipEvents = flowLlmService.extractRelationshipEvents(
                    conversationText = conversationSegment,
                    knownPeople = knownPeople.keys.toList()
                )

                Timber.i("[ThinkingLayer] 关系事件提取结果: 发现${relationshipEvents.size}个事件")

                // 6. 处理关系事件（更新关系强度）
                for (event in relationshipEvents) {
                    try {
                        // 根据事件调整关系强度
                        relationshipNetworkUseCase.recordRelationEvent(
                            personA = event.personA,
                            personB = event.personB,
                            eventType = event.eventType,
                            description = event.description,
                            emotionalImpact = event.emotionalImpact
                        )

                        Timber.i("[ThinkingLayer] ✅ 记录关系事件: ${event.personA} ${event.eventType} ${event.personB} (影响: ${event.emotionalImpact})")

                    } catch (e: Exception) {
                        Timber.e(e, "[ThinkingLayer] 关系事件记录失败: ${event.personA}-${event.personB}")
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "[ThinkingLayer] 第三方关系提取失败")
            }

        } catch (e: Exception) {
            Timber.e(e, "[ThinkingLayer] 社交感知分析失败")
        }

        return thoughts
    }

    /**
     * ⭐ 从第三方关系更新CharacterBook
     * 为涉及的人物创建或更新档案
     */
    private suspend fun updateCharacterBookFromRelation(relation: com.xiaoguang.assistant.domain.flow.service.ThirdPartyRelation) {
        try {
            // 确保两个人物都有档案
            for (personName in listOf(relation.personA, relation.personB)) {
                var profile = characterBook.getProfileByName(personName)
                if (profile == null) {
                    // 创建新档案
                    val characterId = "char_${System.currentTimeMillis()}_${personName.hashCode()}"
                    profile = com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile(
                        basicInfo = com.xiaoguang.assistant.domain.knowledge.models.BasicInfo(
                            characterId = characterId,
                            name = personName,
                            bio = "通过环境对话了解到的人物",
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    characterBook.saveProfile(profile)
                    Timber.i("[ThinkingLayer] 创建新档案: $personName")
                }
            }

            // 记录关系信息到角色记忆
            val profileA = characterBook.getProfileByName(relation.personA)
            if (profileA != null) {
                val memory = com.xiaoguang.assistant.domain.knowledge.models.CharacterMemory(
                    memoryId = "mem_rel_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}",
                    characterId = profileA.basicInfo.characterId,
                    category = com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.SEMANTIC,  // 语义记忆：关于他人的知识
                    content = "${relation.personA}和${relation.personB}是${relation.relationType}关系",
                    importance = relation.confidence,
                    emotionalValence = 0f,  // 中性
                    tags = listOf("第三方关系", relation.relationType),
                    createdAt = System.currentTimeMillis()
                )
                characterBook.addMemory(memory)
            }

        } catch (e: Exception) {
            Timber.e(e, "[ThinkingLayer] CharacterBook更新失败")
        }
    }

    /**
     * ⭐ 从第三方关系更新WorldBook
     * 记录背景设定性质的关系（如"市长的女儿"）
     */
    private suspend fun updateWorldBookFromRelation(relation: com.xiaoguang.assistant.domain.flow.service.ThirdPartyRelation) {
        try {
            val entryId = "world_rel_${relation.personA.hashCode()}_${relation.personB.hashCode()}"
            val entry = com.xiaoguang.assistant.domain.knowledge.models.WorldEntry(
                entryId = entryId,
                keys = listOf(relation.personA, relation.personB, relation.relationType),
                content = "${relation.personA}和${relation.personB}是${relation.relationType}。证据：${relation.evidence}",
                category = com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory.KNOWLEDGE,
                priority = (relation.confidence * 100).toInt(),
                enabled = true,
                metadata = mapOf(
                    "source" to "environment_relation",
                    "confidence" to relation.confidence.toString(),
                    "person_a" to relation.personA,
                    "person_b" to relation.personB,
                    "relation_type" to relation.relationType
                ),
                createdAt = System.currentTimeMillis()
            )

            worldBook.addEntry(entry)
            Timber.i("[ThinkingLayer] ✅ WorldBook记录关系: ${relation.personA}-${relation.relationType}-${relation.personB}")

        } catch (e: Exception) {
            Timber.e(e, "[ThinkingLayer] WorldBook更新失败")
        }
    }

    /**
     * ✅ 主动提醒：检查即将到期的待办和日历事件
     *
     * 功能：
     * 1. 检查即将到期（24小时内）的待办事项
     * 2. 检查即将开始（2小时内）的日历事件
     * 3. 生成提醒类型的内心想法
     */
    private suspend fun checkUpcomingReminders(perception: Perception): List<InnerThought> {
        val thoughts = mutableListOf<InnerThought>()

        try {
            // 限流：避免频繁调用
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastReminderCheckTime < reminderCheckInterval) {
                return thoughts
            }

            // 1. 检查待办事项
            val todos = todoRepository.getAllTodos()
            val now = System.currentTimeMillis()
            val oneDayLater = now + 24 * 60 * 60 * 1000  // 24小时后
            val oneHourLater = now + 60 * 60 * 1000  // 1小时后

            // 筛选即将到期的待办
            val upcomingTodos = todos.filter { todo ->
                !todo.isCompleted && todo.dueDate != null && todo.dueDate!! in now..oneDayLater
            }

            for (todo in upcomingTodos) {
                val hoursLeft = ((todo.dueDate!! - now) / (60 * 60 * 1000)).toInt()

                val urgency = when {
                    todo.dueDate!! < oneHourLater -> 0.9f  // 1小时内，非常紧急
                    hoursLeft < 6 -> 0.7f  // 6小时内，紧急
                    hoursLeft < 12 -> 0.6f  // 12小时内，较紧急
                    else -> 0.5f  // 24小时内，中等
                }

                val timeDesc = when {
                    hoursLeft < 1 -> "不到1小时"
                    hoursLeft < 2 -> "1小时"
                    hoursLeft < 6 -> "${hoursLeft}小时"
                    hoursLeft < 24 -> "大约${hoursLeft}小时"
                    else -> "今天"
                }

                thoughts.add(InnerThought(
                    type = ThoughtType.REMINDER,
                    content = "「${todo.title}」还有${timeDesc}就要截止了...",
                    urgency = urgency
                ))

                Timber.i("[ThinkingLayer] 生成待办提醒: ${todo.title} (剩余${hoursLeft}小时)")
            }

            // 2. 检查日历事件
            val twoHoursLater = now + 2 * 60 * 60 * 1000  // 2小时后
            val events = calendarRepository.getEventsBetween(
                startTime = now,
                endTime = twoHoursLater
            )

            for (event in events) {
                val minutesLeft = ((event.startTime - now) / (60 * 1000)).toInt()

                if (minutesLeft < 0) {
                    // 已经开始的事件跳过
                    continue
                }

                val urgency = when {
                    minutesLeft < 15 -> 0.95f  // 15分钟内，极其紧急
                    minutesLeft < 30 -> 0.85f  // 30分钟内，很紧急
                    minutesLeft < 60 -> 0.75f  // 1小时内，紧急
                    else -> 0.6f  // 2小时内，较紧急
                }

                val timeDesc = when {
                    minutesLeft < 15 -> "马上"
                    minutesLeft < 30 -> "不到半小时"
                    minutesLeft < 60 -> "大约${minutesLeft}分钟"
                    else -> "大约${minutesLeft / 60}小时"
                }

                thoughts.add(InnerThought(
                    type = ThoughtType.REMINDER,
                    content = "「${event.title}」还有${timeDesc}就要开始了...",
                    urgency = urgency
                ))

                Timber.i("[ThinkingLayer] 生成日历提醒: ${event.title} (剩余${minutesLeft}分钟)")
            }

            lastReminderCheckTime = currentTime

        } catch (e: Exception) {
            Timber.e(e, "[ThinkingLayer] 提醒检查失败")
        }

        return thoughts
    }

    // ✅ 记录上次新人检测时间，避免频繁调用
    private var lastNewPersonCheckTime = 0L
    private val newPersonCheckInterval = 15_000L  // 15秒检查一次

    /**
     * ✅ 新人检测：检测陌生人并注册
     *
     * 功能：
     * 1. 检测环境中的陌生人（未匹配到声纹的说话人）
     * 2. 触发新人注册流程（协调9个系统）
     * 3. 尝试从对话推断名称
     * 4. 生成小光的内心想法（好奇、关心等）
     */
    private suspend fun detectAndRegisterNewPerson(perception: Perception): List<InnerThought> {
        val thoughts = mutableListOf<InnerThought>()

        try {
            // 限流：避免频繁调用
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastNewPersonCheckTime < newPersonCheckInterval) {
                return thoughts
            }

            // 检查最近的环境语句
            val recentUtterances = environmentState.recentUtterances.value
            if (recentUtterances.isEmpty()) {
                return thoughts
            }

            // 查找陌生人（speakerId以"stranger_"开头的语句）
            val strangerUtterances = recentUtterances.filter {
                it.speakerId?.startsWith("stranger_") == true
            }

            if (strangerUtterances.isEmpty()) {
                return thoughts
            }

            // 获取陌生人ID
            val strangerIds = strangerUtterances.mapNotNull { it.speakerId }.distinct()

            for (strangerId in strangerIds) {
                // 检查是否已经注册过
                val allProfiles = voiceprintManager.getAllProfiles()
                val alreadyRegistered = allProfiles.any { it.personId == strangerId }

                if (alreadyRegistered) {
                    continue  // 已经注册过，跳过
                }

                Timber.i("[ThinkingLayer] 检测到陌生人: $strangerId，开始注册流程...")

                // 创建临时声纹档案（需要后续UI收集样本）
                val tempProfile = com.xiaoguang.assistant.domain.voiceprint.VoiceprintProfile(
                    voiceprintId = java.util.UUID.randomUUID().toString(),
                    personId = strangerId,
                    personName = null,
                    displayName = "陌生人_${System.currentTimeMillis() % 10000}",
                    isStranger = true,
                    featureVector = FloatArray(128) { 0f },  // 临时空向量
                    sampleCount = 0,
                    confidence = 0f
                )

                // 获取相关对话消息（用于名称推断）
                val relevantMessages = strangerUtterances
                    .filter { it.speakerId == strangerId }
                    .take(10)
                    .mapNotNull { utterance ->
                        // 转换为Message对象
                        com.xiaoguang.assistant.domain.model.Message(
                            role = com.xiaoguang.assistant.domain.model.MessageRole.USER,
                            content = utterance.text,
                            speakerId = utterance.speakerId,
                            speakerName = utterance.speakerName,
                            timestamp = utterance.timestamp
                        )
                    }

                // 触发注册流程
                val registrationResult = newPersonRegistrationUseCase.registerNewPerson(
                    voiceprintProfile = tempProfile,
                    recentMessages = relevantMessages,
                    context = "环境监听中检测到新人"
                )

                if (registrationResult.success) {
                    // 注册成功，生成小光的内心想法
                    val thoughtContent = if (registrationResult.isStranger) {
                        "诶？好像有新朋友出现了呢...不过还不知道叫什么名字..."
                    } else {
                        "诶！刚才推断出来了，这位朋友可能叫「${registrationResult.displayName}」~"
                    }

                    thoughts.add(InnerThought(
                        type = ThoughtType.CARE,
                        content = thoughtContent,
                        urgency = 0.4f  // 中低紧急度，小光会好奇但不会太紧张
                    ))

                    Timber.i("[ThinkingLayer] ✅ 新人注册成功: ${registrationResult.displayName} " +
                            "(${registrationResult.checklist.getCompletionRate()}% 完成)")
                } else {
                    Timber.w("[ThinkingLayer] ❌ 新人注册失败: ${registrationResult.message}")
                }
            }

            lastNewPersonCheckTime = currentTime

        } catch (e: Exception) {
            Timber.e(e, "[ThinkingLayer] 新人检测失败")
        }

        return thoughts
    }
}


    /**
     * 根据多维度影响确定交互类型
     */
    private fun determineInteractionType(impact: com.xiaoguang.assistant.domain.flow.service.EnvironmentSocialImpact): String {
        val totalImpact = impact.intimacyDelta + impact.trustDelta
        
        return when {
            impact.trustDelta < -0.05f -> "betrayal"        // 背叛/失信
            totalImpact < -0.05f -> "negative"              // 负面
            totalImpact > 0.05f -> "positive"               // 正面
            impact.intimacyDelta > 0.05f -> "bonding"       // 亲近
            impact.trustDelta > 0.05f -> "reliable"         // 可靠
            else -> "neutral"                                // 中性
        }
    }

    /**
     * Float 格式化扩展（保留N位小数）
     */
    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }

