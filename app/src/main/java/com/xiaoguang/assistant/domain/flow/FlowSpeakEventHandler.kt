package com.xiaoguang.assistant.domain.flow

import com.xiaoguang.assistant.domain.flow.layer.ProactiveSpeakEvent
import com.xiaoguang.assistant.domain.flow.model.SpeakPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 心流发言事件处理器
 *
 * 负责：
 * 1. 监听心流系统的主动发言事件
 * 2. 判断当前是否适合播放（用户状态、TTS状态）
 * 3. 管理消息队列（优先级排序、过期清理）
 * 4. 发送TTS播放事件
 *
 * 架构：
 * FlowLoop → ActionLayer → ProactiveSpeakEvent
 *     → FlowSpeakEventHandler（判断+队列）
 *     → TtsPlayEvent → TTS服务
 */
@Singleton
class FlowSpeakEventHandler @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // === 状态管理 ===
    @Volatile
    private var isUserBusy = false

    @Volatile
    private var isInCall = false

    @Volatile
    private var isTtsPlaying = false

    // === 消息队列 ===
    private val messageQueue = ConcurrentLinkedQueue<QueuedMessage>()

    // === TTS播放事件流 ===
    private val _ttsPlayEvents = MutableSharedFlow<TtsPlayEvent>()
    val ttsPlayEvents: SharedFlow<TtsPlayEvent> = _ttsPlayEvents.asSharedFlow()

    init {
        startQueueProcessor()
    }

    /**
     * 处理心流发言事件
     */
    suspend fun handleSpeakEvent(event: ProactiveSpeakEvent) {
        try {
            Timber.d("[FlowSpeakEventHandler] 收到心流发言: ${event.message.take(30)}... (优先级: ${event.priority.name})")

            // 创建队列消息
            val queuedMessage = QueuedMessage(
                event = event,
                timestamp = System.currentTimeMillis()
            )

            // 立即尝试播放（如果条件允许）
            if (canPlayNow(event.priority)) {
                playMessage(queuedMessage)
            } else {
                // 加入队列等待
                messageQueue.offer(queuedMessage)
                Timber.d("[FlowSpeakEventHandler] 消息加入队列 (队列大小: ${messageQueue.size})")
            }

        } catch (e: Exception) {
            Timber.e(e, "[FlowSpeakEventHandler] 处理发言事件失败")
        }
    }

    /**
     * 判断当前是否可以播放（只做必要的状态检查，信任LLM决策）
     */
    private fun canPlayNow(priority: SpeakPriority): Boolean {
        // TTS正在播放时，只有HIGH/URGENT优先级可以打断
        if (isTtsPlaying && priority != SpeakPriority.HIGH && priority != SpeakPriority.URGENT) {
            Timber.d("[FlowSpeakEventHandler] TTS正在播放，非高优先级消息等待")
            return false
        }

        // 用户忙碌时，只有HIGH/URGENT优先级可以播放
        if (isUserBusy && priority != SpeakPriority.HIGH && priority != SpeakPriority.URGENT) {
            Timber.d("[FlowSpeakEventHandler] 用户忙碌，非高优先级消息等待")
            return false
        }

        // 通话中时，只有URGENT优先级可以播放
        if (isInCall && priority != SpeakPriority.URGENT) {
            Timber.d("[FlowSpeakEventHandler] 通话中，非紧急消息等待")
            return false
        }

        // 其他情况信任LLM的决策
        return true
    }

    /**
     * 播放消息
     */
    private suspend fun playMessage(queuedMessage: QueuedMessage) {
        try {
            val event = queuedMessage.event

            Timber.i("[FlowSpeakEventHandler] 🔊 准备播放: ${event.message.take(30)}...")

            // 发送TTS播放事件
            _ttsPlayEvents.emit(TtsPlayEvent(
                messageId = java.util.UUID.randomUUID().toString(),
                content = event.message,
                priority = event.priority,
                reason = event.reason,
                timestamp = System.currentTimeMillis()
            ))

            Timber.d("[FlowSpeakEventHandler] TTS播放事件已发送")

        } catch (e: Exception) {
            Timber.e(e, "[FlowSpeakEventHandler] 播放消息失败")
        }
    }

    /**
     * 启动队列处理器（定期检查队列）
     */
    private fun startQueueProcessor() {
        scope.launch {
            while (true) {
                try {
                    // 每5秒检查一次队列
                    delay(5000)

                    processQueue()

                } catch (e: Exception) {
                    Timber.e(e, "[FlowSpeakEventHandler] 队列处理异常")
                }
            }
        }
    }

    /**
     * 处理队列中的消息
     */
    private suspend fun processQueue() {
        if (messageQueue.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val processedMessages = mutableListOf<QueuedMessage>()

        // 遍历队列
        val iterator = messageQueue.iterator()
        while (iterator.hasNext()) {
            val queuedMessage = iterator.next()

            // 检查是否过期（超过5分钟）
            val age = currentTime - queuedMessage.timestamp
            if (age > 5 * 60 * 1000) {
                Timber.w("[FlowSpeakEventHandler] 消息过期，丢弃: ${queuedMessage.event.message.take(30)}... (${age / 1000}秒前)")
                iterator.remove()
                continue
            }

            // 检查是否可以播放
            if (canPlayNow(queuedMessage.event.priority)) {
                playMessage(queuedMessage)
                processedMessages.add(queuedMessage)
                iterator.remove()
                break  // 一次只播放一条
            }
        }

        if (processedMessages.isNotEmpty()) {
            Timber.d("[FlowSpeakEventHandler] 队列处理完成: 播放 ${processedMessages.size} 条消息，剩余 ${messageQueue.size} 条")
        }
    }

    /**
     * 设置用户忙碌状态
     */
    fun setUserBusy(busy: Boolean) {
        isUserBusy = busy
        Timber.d("[FlowSpeakEventHandler] 用户忙碌状态: $busy")
    }

    /**
     * 设置通话状态
     */
    fun setInCall(inCall: Boolean) {
        isInCall = inCall
        Timber.d("[FlowSpeakEventHandler] 通话状态: $inCall")
    }

    /**
     * 设置TTS播放状态
     */
    fun setTtsPlaying(playing: Boolean) {
        isTtsPlaying = playing
        Timber.d("[FlowSpeakEventHandler] TTS播放状态: $playing")

        // TTS播放结束时，立即处理队列
        if (!playing) {
            scope.launch {
                processQueue()
            }
        }
    }

    /**
     * 清空队列
     */
    fun clearQueue() {
        val size = messageQueue.size
        messageQueue.clear()
        Timber.i("[FlowSpeakEventHandler] 队列已清空 (清除 $size 条消息)")
    }

    /**
     * 获取队列统计信息
     */
    fun getQueueStats(): QueueStats {
        val currentTime = System.currentTimeMillis()
        val oldestTimestamp = messageQueue.minOfOrNull { it.timestamp } ?: currentTime

        return QueueStats(
            size = messageQueue.size,
            oldestMessageAge = (currentTime - oldestTimestamp) / 1000,  // 秒
            priorityCounts = messageQueue.groupingBy { it.event.priority }.eachCount()
        )
    }

    /**
     * 获取用户忙碌状态
     */
    fun isUserBusy(): Boolean = isUserBusy

    /**
     * 获取通话状态
     */
    fun isInCall(): Boolean = isInCall

    /**
     * 获取TTS播放状态
     */
    fun isTtsPlaying(): Boolean = isTtsPlaying
}

/**
 * 队列中的消息
 */
private data class QueuedMessage(
    val event: ProactiveSpeakEvent,
    val timestamp: Long
)

/**
 * TTS播放事件
 */
data class TtsPlayEvent(
    val messageId: String,
    val content: String,
    val priority: SpeakPriority,
    val reason: String,
    val timestamp: Long
)

/**
 * 队列统计信息
 */
data class QueueStats(
    val size: Int,
    val oldestMessageAge: Long,  // 秒
    val priorityCounts: Map<SpeakPriority, Int>
) {
    override fun toString(): String {
        return "队列大小: $size, 最旧消息: ${oldestMessageAge}秒前, 优先级分布: $priorityCounts"
    }
}
