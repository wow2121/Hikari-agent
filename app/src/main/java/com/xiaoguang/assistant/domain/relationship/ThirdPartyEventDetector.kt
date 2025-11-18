package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.domain.flow.service.FlowLlmService
import com.xiaoguang.assistant.domain.flow.service.RelationshipEvent
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.models.CharacterMemory
import com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory
import com.xiaoguang.assistant.domain.usecase.RelationshipNetworkManagementUseCase
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*

/**
 * Phase 4: 第三方事件检测器
 * 自动检测并记录第三方之间的关系事件
 *
 * 功能：
 * 1. 实时监听环境对话
 * 2. 检测第三方互动事件（帮助、争吵、合作等）
 * 3. 自动记录到RelationshipNetwork
 * 4. 根据事件影响更新关系强度
 * 5. 重要事件记录到CharacterBook
 */
@Singleton
class ThirdPartyEventDetector @Inject constructor(
    private val flowLlmService: FlowLlmService,
    private val characterBook: CharacterBook,
    private val relationshipNetworkUseCase: RelationshipNetworkManagementUseCase
) {

    // 对话缓冲区（用于批量处理）
    private val conversationBuffer = mutableListOf<ConversationSegment>()
    private val bufferLock = Any()

    // 检测阈值
    private var detectionThreshold = 0.5f
    private var isRunning = false

    /**
     * 启动事件检测
     */
    fun start() {
        if (!isRunning) {
            isRunning = true
            Timber.d("[ThirdPartyEventDetector] 启动事件检测")
        }
    }

    /**
     * 停止事件检测
     */
    fun stop() {
        isRunning = false
        Timber.d("[ThirdPartyEventDetector] 停止事件检测")
    }

    /**
     * 处理新的对话段
     * 由ThinkingLayer在环境感知时调用
     */
    suspend fun processConversationSegment(
        conversationText: String,
        speaker: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!isRunning) {
            return
        }

        // 添加到缓冲区
        synchronized(bufferLock) {
            conversationBuffer.add(ConversationSegment(
                text = conversationText,
                speaker = speaker,
                timestamp = timestamp
            ))

            // 如果缓冲区达到一定大小，触发批量检测
            if (conversationBuffer.size >= 5) {
                val segments = conversationBuffer.toList()
                conversationBuffer.clear()

                // 异步处理
                CoroutineScope(Dispatchers.IO).launch {
                    detectEventsFromSegments(segments)
                }
            }
        }
    }

    /**
     * 强制刷新缓冲区（处理所有待处理的对话）
     */
    suspend fun flush() {
        val segments = synchronized(bufferLock) {
            val result = conversationBuffer.toList()
            conversationBuffer.clear()
            result
        }

        if (segments.isNotEmpty()) {
            detectEventsFromSegments(segments)
        }
    }

    /**
     * 从对话段中检测事件
     */
    private suspend fun detectEventsFromSegments(segments: List<ConversationSegment>) {
        try {
            // 合并对话文本
            val combinedText = segments.joinToString("\n") { segment ->
                if (segment.speaker != null) {
                    "${segment.speaker}: ${segment.text}"
                } else {
                    segment.text
                }
            }

            // 获取已知人物列表
            val knownPeople = characterBook.getAllProfiles().map { it.basicInfo.name }

            // 使用LLM提取关系事件
            val events = flowLlmService.extractRelationshipEvents(
                conversationText = combinedText,
                knownPeople = knownPeople
            )

            Timber.d("[ThirdPartyEventDetector] 检测到 ${events.size} 个事件")

            // 处理每个事件
            for (event in events) {
                processEvent(event)
            }

        } catch (e: Exception) {
            Timber.e(e, "[ThirdPartyEventDetector] 事件检测失败")
        }
    }

    /**
     * 处理单个事件
     */
    private suspend fun processEvent(event: RelationshipEvent) {
        try {
            // 1. 记录到RelationshipNetwork
            relationshipNetworkUseCase.recordRelationEvent(
                personA = event.personA,
                personB = event.personB,
                eventType = event.eventType,
                description = event.description,
                emotionalImpact = event.emotionalImpact
            )

            // 2. 根据事件重要性，决定是否记录到CharacterBook
            val importance = calculateEventImportance(event)

            if (importance >= 0.6f) {
                recordEventToCharacterBook(event, importance)
            }

            // 3. 发送事件通知（用于UI更新或其他模块）
            notifyEventDetected(event)

            Timber.d("[ThirdPartyEventDetector] 处理事件: ${event.personA} ${event.eventType} ${event.personB}")

        } catch (e: Exception) {
            Timber.e(e, "[ThirdPartyEventDetector] 处理事件失败: ${event.eventType}")
        }
    }

    /**
     * 计算事件重要性
     */
    private fun calculateEventImportance(event: RelationshipEvent): Float {
        var importance = event.confidenceLevel

        // 根据事件类型调整重要性
        val eventTypeWeight = when (event.eventType) {
            "帮助", "救助" -> 0.3f
            "争吵", "冲突" -> 0.3f
            "合作", "共同完成" -> 0.2f
            "赞美", "表扬" -> 0.1f
            "批评", "指责" -> 0.1f
            "告白", "表白" -> 0.4f
            "分手", "断绝关系" -> 0.4f
            else -> 0.0f
        }

        importance += eventTypeWeight

        // 根据情感影响调整重要性
        importance += kotlin.math.abs(event.emotionalImpact) * 0.2f

        return importance.coerceIn(0.0f, 1.0f)
    }

    /**
     * 将事件记录到CharacterBook
     */
    private suspend fun recordEventToCharacterBook(event: RelationshipEvent, importance: Float) {
        try {
            // 为两个人物都记录这个事件

            // 记录到personA的记忆
            val profileA = characterBook.getProfileByName(event.personA)
            if (profileA != null) {
                val memoryA = CharacterMemory(
                    memoryId = "mem_event_${System.currentTimeMillis()}_${UUID.randomUUID()}",
                    characterId = profileA.basicInfo.characterId,
                    category = MemoryCategory.EPISODIC,  // 情节记忆：具体事件
                    content = "${event.personA}与${event.personB}发生了${event.eventType}：${event.description}",
                    importance = importance,
                    emotionalValence = event.emotionalImpact,
                    tags = listOf("第三方事件", event.eventType, event.personB),
                    createdAt = event.timestamp
                )
                characterBook.addMemory(memoryA)
            }

            // 记录到personB的记忆
            val profileB = characterBook.getProfileByName(event.personB)
            if (profileB != null) {
                val memoryB = CharacterMemory(
                    memoryId = "mem_event_${System.currentTimeMillis()}_${UUID.randomUUID()}",
                    characterId = profileB.basicInfo.characterId,
                    category = MemoryCategory.EPISODIC,
                    content = "${event.personB}与${event.personA}发生了${event.eventType}：${event.description}",
                    importance = importance,
                    emotionalValence = event.emotionalImpact,
                    tags = listOf("第三方事件", event.eventType, event.personA),
                    createdAt = event.timestamp
                )
                characterBook.addMemory(memoryB)
            }

            Timber.d("[ThirdPartyEventDetector] 事件已记录到CharacterBook: ${event.eventType}")

        } catch (e: Exception) {
            Timber.e(e, "[ThirdPartyEventDetector] 记录到CharacterBook失败")
        }
    }

    /**
     * 发送事件通知
     */
    private fun notifyEventDetected(event: RelationshipEvent) {
        // 这里可以通过LiveData、Flow或EventBus发送通知
        // 用于UI更新或其他模块响应
        Timber.d("[ThirdPartyEventDetector] 事件通知: ${event.personA} - ${event.personB}: ${event.eventType}")
    }

    /**
     * 获取事件历史
     * @param personName 人物名称（可选，null则返回所有事件）
     * @param limit 最多返回数量
     */
    suspend fun getEventHistory(
        personName: String? = null,
        limit: Int = 50
    ): List<EventRecord> {
        return try {
            if (personName != null) {
                // 从CharacterBook获取该人物的事件记忆
                val profile = characterBook.getProfileByName(personName)
                if (profile != null) {
                    characterBook.getMemoriesByCategory(
                        profile.basicInfo.characterId,
                        MemoryCategory.EPISODIC
                    )
                        .filter { it.tags.contains("第三方事件") }
                        .take(limit)
                        .map { memory ->
                            EventRecord(
                                personA = personName,
                                personB = "",  // 从content解析
                                eventType = memory.tags.firstOrNull { it != "第三方事件" && it != personName } ?: "未知",
                                description = memory.content,
                                timestamp = memory.createdAt,
                                importance = memory.importance,
                                emotionalImpact = memory.emotionalValence
                            )
                        }
                } else {
                    emptyList()
                }
            } else {
                // 获取所有人物的事件记忆
                val allProfiles = characterBook.getAllProfiles()
                allProfiles.flatMap { profile ->
                    characterBook.getMemoriesByCategory(
                        profile.basicInfo.characterId,
                        MemoryCategory.EPISODIC
                    )
                        .filter { it.tags.contains("第三方事件") }
                        .map { memory ->
                            EventRecord(
                                personA = profile.basicInfo.name,
                                personB = "",
                                eventType = memory.tags.firstOrNull { it != "第三方事件" && it != profile.basicInfo.name } ?: "未知",
                                description = memory.content,
                                timestamp = memory.createdAt,
                                importance = memory.importance,
                                emotionalImpact = memory.emotionalValence
                            )
                        }
                }
                    .sortedByDescending { it.timestamp }
                    .take(limit)
            }
        } catch (e: Exception) {
            Timber.e(e, "[ThirdPartyEventDetector] 获取事件历史失败")
            emptyList()
        }
    }

    /**
     * 获取事件统计
     */
    suspend fun getEventStatistics(personName: String? = null): EventStatistics {
        return try {
            val eventHistory = getEventHistory(personName, limit = Int.MAX_VALUE)

            val totalEvents = eventHistory.size
            val positiveEvents = eventHistory.count { it.emotionalImpact > 0 }
            val negativeEvents = eventHistory.count { it.emotionalImpact < 0 }
            val neutralEvents = eventHistory.count { it.emotionalImpact == 0f }

            val eventTypeDistribution = eventHistory
                .groupBy { it.eventType }
                .mapValues { it.value.size }

            EventStatistics(
                totalEvents = totalEvents,
                positiveEvents = positiveEvents,
                negativeEvents = negativeEvents,
                neutralEvents = neutralEvents,
                eventTypeDistribution = eventTypeDistribution
            )
        } catch (e: Exception) {
            Timber.e(e, "[ThirdPartyEventDetector] 获取事件统计失败")
            EventStatistics()
        }
    }

    /**
     * 设置检测阈值
     */
    fun setDetectionThreshold(threshold: Float) {
        detectionThreshold = threshold.coerceIn(0.0f, 1.0f)
        Timber.d("[ThirdPartyEventDetector] 设置检测阈值: $detectionThreshold")
    }
}

// ==================== 数据模型 ====================

/**
 * 对话段
 */
private data class ConversationSegment(
    val text: String,
    val speaker: String?,
    val timestamp: Long
)

/**
 * 事件记录
 */
data class EventRecord(
    val personA: String,
    val personB: String,
    val eventType: String,
    val description: String,
    val timestamp: Long,
    val importance: Float,
    val emotionalImpact: Float
)

/**
 * 事件统计
 */
data class EventStatistics(
    val totalEvents: Int = 0,
    val positiveEvents: Int = 0,
    val negativeEvents: Int = 0,
    val neutralEvents: Int = 0,
    val eventTypeDistribution: Map<String, Int> = emptyMap()
)
