package com.xiaoguang.assistant.domain.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统事件总线
 *
 * 作用：让各个系统能互相感知和通信
 * 例如：
 * - 小光记了日程 → 心流系统知道 → 主动告诉用户"我刚记下了哦~"
 * - 用户完成待办 → 情绪系统知道 → 小光感到开心
 * - 提取到重要记忆 → 关系系统知道 → 更新亲密度
 */
@Singleton
class SystemEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<SystemEvent>(
        replay = 0,  // 不重放历史事件
        extraBufferCapacity = 10  // 缓冲10个事件
    )
    val events: SharedFlow<SystemEvent> = _events.asSharedFlow()

    /**
     * 发布事件
     */
    suspend fun publish(event: SystemEvent) {
        Timber.d("[EventBus] 📢 发布事件: ${event.javaClass.simpleName}")
        _events.emit(event)
    }

    /**
     * 同步发布（不推荐，除非必要）
     */
    fun publishSync(event: SystemEvent) {
        Timber.d("[EventBus] 📢 发布事件(同步): ${event.javaClass.simpleName}")
        _events.tryEmit(event)
    }
}

/**
 * 系统事件基类
 */
sealed class SystemEvent(
    open val timestamp: Long = System.currentTimeMillis(),
    open val description: String
) {

    // ==================== 任务/日程事件 ====================

    /**
     * 自动创建了待办
     */
    data class TodoAutoCreated(
        val todoId: String,
        val title: String,
        val dueDate: Long,
        val confidence: Float,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "自动创建待办: $title"
    ) : SystemEvent(timestamp, description)

    /**
     * 用户完成了待办
     */
    data class TodoCompleted(
        val todoId: String,
        val title: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "完成待办: $title"
    ) : SystemEvent(timestamp, description)

    /**
     * 创建了日程
     */
    data class EventCreated(
        val eventId: Long,
        val title: String,
        val startTime: Long,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "创建日程: $title"
    ) : SystemEvent(timestamp, description)

    // ==================== 记忆事件 ====================

    /**
     * 提取到重要记忆
     */
    data class ImportantMemoryExtracted(
        val memoryId: Long,
        val content: String,
        val importance: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "提取到重要记忆(重要性:$importance): ${content.take(30)}"
    ) : SystemEvent(timestamp, description)

    /**
     * 记住了承诺
     */
    data class PromiseRecorded(
        val promiseDescription: String,
        val deadline: Long,
        val personName: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "$personName 的承诺: $promiseDescription"
    ) : SystemEvent(timestamp, description)

    // ==================== 情绪事件 ====================

    /**
     * 情绪变化
     */
    data class EmotionChanged(
        val fromEmotion: com.xiaoguang.assistant.domain.model.EmotionalState,
        val toEmotion: com.xiaoguang.assistant.domain.model.EmotionalState,
        val reason: String,
        val intensity: Float,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "情绪变化: $fromEmotion -> $toEmotion ($reason)"
    ) : SystemEvent(timestamp, description)

    // ==================== 关系事件 ====================

    /**
     * 关系升级
     */
    data class RelationshipLevelUp(
        val personName: String,
        val oldLevel: com.xiaoguang.assistant.domain.model.RelationshipLevel,
        val newLevel: com.xiaoguang.assistant.domain.model.RelationshipLevel,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "和 $personName 的关系升级: $oldLevel -> $newLevel"
    ) : SystemEvent(timestamp, description)

    /**
     * 认识新朋友
     */
    data class NewPersonMet(
        val personName: String,
        val firstImpression: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "认识了新朋友: $personName"
    ) : SystemEvent(timestamp, description)

    // ==================== 环境事件 ====================

    /**
     * 检测到有趣的对话
     */
    data class InterestingTopicDetected(
        val topic: String,
        val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "检测到有趣话题: $topic"
    ) : SystemEvent(timestamp, description)

    /**
     * 用户很久没来了
     */
    data class UserInactiveForLong(
        val hours: Int,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "用户已经 $hours 小时没来了"
    ) : SystemEvent(timestamp, description)

    // ==================== 学习事件 ====================

    /**
     * 学到新知识
     */
    data class NewKnowledgeLearned(
        val topic: String,
        val content: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String = "学到了关于 $topic 的新知识"
    ) : SystemEvent(timestamp, description)

    // ==================== 自定义事件 ====================

    /**
     * 自定义事件（万能类型）
     */
    data class Custom(
        val eventType: String,
        val data: Map<String, Any>,
        override val timestamp: Long = System.currentTimeMillis(),
        override val description: String
    ) : SystemEvent(timestamp, description)
}
