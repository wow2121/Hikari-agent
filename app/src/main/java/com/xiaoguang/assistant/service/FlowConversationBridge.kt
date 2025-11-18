package com.xiaoguang.assistant.service

import com.xiaoguang.assistant.domain.flow.FlowSystemInitializer
import com.xiaoguang.assistant.domain.flow.layer.ProactiveSpeakEvent
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.model.MessageRole
import com.xiaoguang.assistant.domain.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 心流-对话桥接服务
 *
 * 职责：
 * 1. 监听心流系统的主动发言事件
 * 2. 将心流的发言转换为对话消息
 * 3. 保存到对话历史
 * 4. 发出通知事件（供UI显示、TTS播放等）
 *
 * 注意：这个服务不负责TTS播放，只负责将心流的想法转换为对话记录
 */
@Singleton
class FlowConversationBridge @Inject constructor(
    private val flowSystemInitializer: FlowSystemInitializer,
    private val conversationRepository: ConversationRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 小光主动消息事件流（供外部订阅：UI显示、TTS播放等）
    private val _proactiveMessageEvents = MutableSharedFlow<ProactiveMessageEvent>()
    val proactiveMessageEvents: SharedFlow<ProactiveMessageEvent> = _proactiveMessageEvents.asSharedFlow()

    init {
        startListening()
    }

    /**
     * 开始监听心流事件（原始事件，未经队列管理）
     */
    private fun startListening() {
        scope.launch {
            flowSystemInitializer.getRawSpeakEvents().collect { event ->
                handleProactiveSpeakEvent(event)
            }
        }
    }

    /**
     * 处理心流的主动发言事件
     */
    private suspend fun handleProactiveSpeakEvent(event: ProactiveSpeakEvent) {
        try {
            Timber.i("[FlowBridge] 🌟 心流主动发言: ${event.message}")

            // 1. 创建对话消息（小光作为assistant角色）
            val message = Message(
                role = MessageRole.ASSISTANT,
                content = event.message,
                timestamp = System.currentTimeMillis(),
                speakerId = "xiaoguang",      // 小光的固定标识
                speakerName = "小光"
            )

            // 2. 保存到数据库
            conversationRepository.addMessage(message)

            // 3. 发出事件（供UI显示、TTS播放等）
            _proactiveMessageEvents.emit(ProactiveMessageEvent(
                messageId = message.id,
                content = event.message,
                priority = event.priority,
                reason = event.reason,
                timestamp = message.timestamp
            ))

            Timber.d("[FlowBridge] 心流消息已保存到对话历史: id=${message.id}")

        } catch (e: Exception) {
            Timber.e(e, "[FlowBridge] 处理心流发言失败")
        }
    }
}

/**
 * 小光主动消息事件
 * 这个事件表示小光主动说了一句话，可以用于：
 * - UI显示
 * - TTS播放
 * - 通知用户
 */
data class ProactiveMessageEvent(
    val messageId: String,
    val content: String,
    val priority: com.xiaoguang.assistant.domain.flow.model.SpeakPriority,
    val reason: String,
    val timestamp: Long
)
