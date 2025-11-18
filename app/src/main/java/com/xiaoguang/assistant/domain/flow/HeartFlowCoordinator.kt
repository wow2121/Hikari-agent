package com.xiaoguang.assistant.domain.flow

import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import com.xiaoguang.assistant.domain.flow.layer.ActionLayer
import com.xiaoguang.assistant.domain.flow.layer.ProactiveSpeakEvent
import com.xiaoguang.assistant.domain.flow.model.FlowConfig
import com.xiaoguang.assistant.domain.flow.model.InternalState
import com.xiaoguang.assistant.domain.flow.model.PersonalityType
import com.xiaoguang.assistant.domain.relationship.EnhancedRelationshipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 心流协调器
 * 小光主动系统的顶层协调器，管理整个心流系统的运行
 */
@Singleton
class HeartFlowCoordinator @Inject constructor(
    private val flowLoop: FlowLoop,
    private val actionLayer: ActionLayer,
    private val emotionService: XiaoguangEmotionService,
    private val relationshipManager: EnhancedRelationshipManager,
    private val flowSpeakEventHandler: FlowSpeakEventHandler
) {
    // 协调器作用域
    private val scope = CoroutineScope(SupervisorJob())

    // 配置
    private val _config = MutableStateFlow(FlowConfig())
    val config: StateFlow<FlowConfig> = _config.asStateFlow()

    // 运行状态
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /**
     * 启动心流系统
     */
    fun start() {
        if (_isRunning.value) {
            Timber.w("[HeartFlowCoordinator] 心流系统已在运行")
            return
        }

        Timber.i("[HeartFlowCoordinator] 🌟 启动小光心流系统...")

        try {
            // 启动心流循环
            flowLoop.start(scope)

            // 订阅发言事件
            scope.launch {
                actionLayer.speakEvents.collect { event ->
                    handleSpeakEvent(event)
                }
            }

            // 订阅情感变化（用于触发额外的检查）
            scope.launch {
                emotionService.currentEmotion.collect { emotion ->
                    handleEmotionChange(emotion)
                }
            }

            _isRunning.value = true
            Timber.i("[HeartFlowCoordinator] ✨ 心流系统启动成功！小光开始持续观察和思考...")

        } catch (e: Exception) {
            Timber.e(e, "[HeartFlowCoordinator] 启动心流系统失败")
            _isRunning.value = false
        }
    }

    /**
     * 停止心流系统
     */
    suspend fun stop() {
        Timber.i("[HeartFlowCoordinator] 停止心流系统...")
        flowLoop.stop()
        _isRunning.value = false
        Timber.i("[HeartFlowCoordinator] 心流系统已停止")
    }

    /**
     * 暂停心流系统（临时）
     */
    suspend fun pause() {
        Timber.i("[HeartFlowCoordinator] 暂停心流系统")
        flowLoop.stop()
    }

    /**
     * 恢复心流系统
     */
    fun resume() {
        if (!_isRunning.value) {
            start()
        } else {
            Timber.i("[HeartFlowCoordinator] 恢复心流系统")
            flowLoop.start(scope)
        }
    }

    /**
     * 获取发言事件流（原始事件，内部使用）
     */
    fun getSpeakEvents(): Flow<ProactiveSpeakEvent> {
        return actionLayer.speakEvents
    }

    /**
     * 获取TTS播放事件流（经过队列管理和条件判断，供外部订阅）
     */
    fun getTtsPlayEvents(): Flow<TtsPlayEvent> {
        return flowSpeakEventHandler.ttsPlayEvents
    }

    /**
     * 获取当前内在状态
     */
    fun getCurrentState(): InternalState {
        return flowLoop.getCurrentState()
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: FlowConfig) {
        _config.value = newConfig
        Timber.i("[HeartFlowCoordinator] 配置已更新: 话痨度=${newConfig.talkativeLevel}, 人格=${newConfig.personalityType}")
    }

    /**
     * 调整话痨度
     */
    fun adjustTalkativeLevel(level: Float) {
        val currentConfig = _config.value
        updateConfig(currentConfig.copy(talkativeLevel = level.coerceIn(0.5f, 1.5f)))
    }

    /**
     * 设置人格类型
     */
    fun setPersonalityType(type: PersonalityType) {
        val currentConfig = _config.value
        updateConfig(currentConfig.copy(
            personalityType = type,
            talkativeLevel = type.talkativeMultiplier
        ))
    }

    /**
     * 启用/禁用功能
     */
    fun enableInnerThoughts(enable: Boolean) {
        updateConfig(_config.value.copy(enableInnerThoughts = enable))
    }

    fun enableCuriosity(enable: Boolean) {
        updateConfig(_config.value.copy(enableCuriosity = enable))
    }

    fun enableProactiveCare(enable: Boolean) {
        updateConfig(_config.value.copy(enableProactiveCare = enable))
    }

    /**
     * 处理发言事件
     */
    private fun handleSpeakEvent(event: ProactiveSpeakEvent) {
        Timber.i(
            "[HeartFlowCoordinator] 📢 发言事件: ${event.message} (${event.priority}, ${event.timing.displayName})"
        )

        // 转发给 FlowSpeakEventHandler 处理（判断播放条件、队列管理）
        scope.launch {
            flowSpeakEventHandler.handleSpeakEvent(event)
        }
    }

    /**
     * 处理情感变化
     */
    private fun handleEmotionChange(emotion: com.xiaoguang.assistant.domain.model.EmotionalState) {
        // 强烈情绪变化时，可以触发额外的主动性检查
        // 注意：这里只能获取到情绪状态，强度需要从 emotionService 单独获取
        val intensity = emotionService.getEmotionIntensity()

        if (intensity > 0.8f) {
            Timber.d(
                "[HeartFlowCoordinator] 检测到强烈情绪: ${emotion.displayName} (强度: $intensity)"
            )
            // 情绪系统已经会影响心流循环的评分，这里不需要额外操作
            // 但可以记录统计或触发特定的关心机制
        }
    }

    /**
     * 手动触发主动发言（用于测试）
     */
    fun triggerManualSpeak(message: String) {
        Timber.i("[HeartFlowCoordinator] 手动触发发言: $message")
        scope.launch {
            actionLayer.speakEvents.collect { /* 已在start()中订阅 */ }
        }
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): FlowStatistics {
        val state = getCurrentState()
        return FlowStatistics(
            cycleCount = flowLoop.getCycleCount(),
            currentImpulse = state.impulseValue,
            recentDecisions = state.recentDecisions.size,
            pendingThoughts = state.pendingThoughts.size,
            ignoredCount = state.ignoredCount,
            recentSpeakRatio = state.recentSpeakRatio
        )
    }

    /**
     * 设置TTS播放状态（供MainActivity通知FlowSpeakEventHandler）
     */
    fun setTtsPlayingStatus(playing: Boolean) {
        flowSpeakEventHandler.setTtsPlaying(playing)
    }

    /**
     * 设置用户忙碌状态
     */
    fun setUserBusyStatus(busy: Boolean) {
        flowSpeakEventHandler.setUserBusy(busy)
    }

    /**
     * 设置通话状态
     */
    fun setInCallStatus(inCall: Boolean) {
        flowSpeakEventHandler.setInCall(inCall)
    }
}

/**
 * 心流系统统计信息
 */
data class FlowStatistics(
    val cycleCount: Long,
    val currentImpulse: Float,
    val recentDecisions: Int,
    val pendingThoughts: Int,
    val ignoredCount: Int,
    val recentSpeakRatio: Float
) {
    override fun toString(): String {
        return """
            心流统计:
            - 循环次数: $cycleCount
            - 当前冲动值: ${String.format("%.2f", currentImpulse)}
            - 最近决策: $recentDecisions 条
            - 待处理想法: $pendingThoughts 个
            - 被忽视次数: $ignoredCount
            - 发言占比: ${String.format("%.1f%%", recentSpeakRatio * 100)}
        """.trimIndent()
    }
}
