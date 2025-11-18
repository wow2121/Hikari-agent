package com.xiaoguang.assistant.domain.event

import com.xiaoguang.assistant.domain.flow.layer.ActionLayer
import com.xiaoguang.assistant.domain.flow.model.SpeakPriority
import com.xiaoguang.assistant.domain.flow.model.SpeakTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统事件观察者
 *
 * 职责：
 * 1. 订阅系统事件总线
 * 2. 让小光对各种系统事件做出反应
 * 3. 实现"小光知道自己做了什么并主动告诉用户"
 */
@Singleton
class SystemEventObserver @Inject constructor(
    private val systemEventBus: SystemEventBus,
    private val actionLayer: ActionLayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        startObserving()
    }

    /**
     * 开始观察系统事件
     */
    private fun startObserving() {
        scope.launch {
            systemEventBus.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    /**
     * 处理事件
     */
    private suspend fun handleEvent(event: SystemEvent) {
        Timber.d("[EventObserver] 📥 收到事件: ${event.description}")

        when (event) {
            is SystemEvent.TodoAutoCreated -> handleTodoAutoCreated(event)
            is SystemEvent.EventCreated -> handleEventCreated(event)
            is SystemEvent.ImportantMemoryExtracted -> handleImportantMemoryExtracted(event)
            is SystemEvent.PromiseRecorded -> handlePromiseRecorded(event)
            is SystemEvent.RelationshipLevelUp -> handleRelationshipLevelUp(event)
            is SystemEvent.NewPersonMet -> handleNewPersonMet(event)
            is SystemEvent.InterestingTopicDetected -> handleInterestingTopic(event)
            else -> {
                // 其他事件暂时不处理
                Timber.d("[EventObserver] 事件类型暂未处理: ${event.javaClass.simpleName}")
            }
        }
    }

    /**
     * 处理：自动创建了待办
     * 让小光主动告诉用户"我帮你记下来了哦~"
     */
    private suspend fun handleTodoAutoCreated(event: SystemEvent.TodoAutoCreated) {
        val dueTimeStr = formatDueTime(event.dueDate)

        val responses = listOf(
            "诶！小光帮你记下来了哦~ ${event.title}，$dueTimeStr 要完成哦~",
            "嗯嗯！我记住啦！${event.title}，$dueTimeStr 记得做~",
            "好的好的！小光已经帮你记下了：${event.title} ($dueTimeStr)",
            "收到！小光帮你加到待办里了~ ${event.title}"
        )

        // 根据置信度选择语气
        val message = if (event.confidence > 0.8f) {
            responses.random()
        } else {
            "嗯...好像是要记一个待办？小光记下了：${event.title}，如果不对的话记得告诉我哦~"
        }

        // 通过ActionLayer的事件流发出（不直接说，而是加入心流的自然表达中）
        // 注意：这里我们暂时用日志，实际可以发到特定的"系统反馈"流
        Timber.i("[EventObserver] 小光想说: $message")
        // TODO: 实际实现时可以通过一个专门的"系统反馈流"来发送这些消息
    }

    /**
     * 处理：创建了日程
     */
    private suspend fun handleEventCreated(event: SystemEvent.EventCreated) {
        val timeStr = formatEventTime(event.startTime)
        val message = "好的！小光帮你安排上了：${event.title}，$timeStr 记得参加哦~"

        Timber.i("[EventObserver] 小光想说: $message")
    }

    /**
     * 处理：提取到重要记忆
     */
    private suspend fun handleImportantMemoryExtracted(event: SystemEvent.ImportantMemoryExtracted) {
        if (event.importance >= 8) {
            val message = "嗯嗯！小光会好好记住的：${event.content.take(50)}"
            Timber.i("[EventObserver] 小光想说: $message")
        }
    }

    /**
     * 处理：记住了承诺
     */
    private suspend fun handlePromiseRecorded(event: SystemEvent.PromiseRecorded) {
        val timeStr = formatDueTime(event.deadline)
        val message = "好的！${event.personName}说的话小光都记着呢~ $timeStr 的时候我会提醒的！"

        Timber.i("[EventObserver] 小光想说: $message")
    }

    /**
     * 处理：关系升级
     */
    private suspend fun handleRelationshipLevelUp(event: SystemEvent.RelationshipLevelUp) {
        val message = when (event.newLevel) {
            com.xiaoguang.assistant.domain.model.RelationshipLevel.BEST_FRIEND ->
                "和${event.personName}的关系变得更好了呢~ 小光很开心！"
            com.xiaoguang.assistant.domain.model.RelationshipLevel.MASTER ->
                "${event.personName}...是小光最重要的人了！"
            else ->
                "和${event.personName}的关系变好了~"
        }

        Timber.i("[EventObserver] 小光想说: $message")
    }

    /**
     * 处理：认识新朋友
     */
    private suspend fun handleNewPersonMet(event: SystemEvent.NewPersonMet) {
        val message = "认识了新朋友：${event.personName}~ ${event.firstImpression}"
        Timber.i("[EventObserver] 小光想说: $message")
    }

    /**
     * 处理：检测到有趣话题
     */
    private suspend fun handleInterestingTopic(event: SystemEvent.InterestingTopicDetected) {
        val message = "诶！${event.topic}好有趣！小光也想了解~"
        Timber.i("[EventObserver] 小光想说: $message")
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化截止时间
     */
    private fun formatDueTime(timestamp: Long): String {
        if (timestamp == 0L) return "有空的时候"

        val now = LocalDateTime.now()
        val dueTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )

        val days = java.time.Duration.between(now, dueTime).toDays()

        return when {
            days == 0L -> "今天"
            days == 1L -> "明天"
            days == 2L -> "后天"
            days < 7 -> "${days}天后"
            else -> dueTime.format(DateTimeFormatter.ofPattern("MM月dd日"))
        }
    }

    /**
     * 格式化事件时间
     */
    private fun formatEventTime(timestamp: Long): String {
        val eventTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp),
            ZoneId.systemDefault()
        )

        return eventTime.format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
    }
}
