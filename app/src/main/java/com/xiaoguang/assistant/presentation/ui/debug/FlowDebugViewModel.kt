package com.xiaoguang.assistant.presentation.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.flow.FlowSystemInitializer
import com.xiaoguang.assistant.domain.flow.HeartFlowCoordinator
import com.xiaoguang.assistant.domain.flow.model.DecisionRecord
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.InternalState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 心流调试界面 ViewModel
 *
 * 功能：
 * - 显示实时感知数据
 * - 显示内在状态
 * - 显示决策历史
 * - 手动触发发言
 * - 调整心流配置
 */
@HiltViewModel
class FlowDebugViewModel @Inject constructor(
    private val flowSystemInitializer: FlowSystemInitializer,
    private val environmentState: com.xiaoguang.assistant.domain.flow.model.EnvironmentState,  // ✅ 注入环境状态
    private val flowSpeakEventHandler: com.xiaoguang.assistant.domain.flow.FlowSpeakEventHandler  // ✅ 注入发言事件处理器
) : ViewModel() {

    private val coordinator: HeartFlowCoordinator = flowSystemInitializer.getCoordinator()

    private val _uiState = MutableStateFlow(FlowDebugUiState())
    val uiState: StateFlow<FlowDebugUiState> = _uiState.asStateFlow()

    // 心流运行状态
    val isRunning: StateFlow<Boolean> = coordinator.isRunning

    // 心流配置
    val config = coordinator.config

    init {
        startAutoRefresh()
    }

    /**
     * 自动刷新状态（每2秒更新一次）
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                refreshState()
                delay(2000)
            }
        }
    }

    /**
     * 刷新当前状态
     */
    fun refreshState() {
        try {
            val internalState = coordinator.getCurrentState()
            val statistics = coordinator.getStatistics()

            // ✅ 收集环境状态
            val recentUtterances = environmentState.recentUtterances.value.takeLast(5)
            val currentSpeaker = environmentState.currentSpeaker.value
            val presentPeople = environmentState.presentPeople.value
            val audioLevel = environmentState.audioLevel.value

            // ✅ 收集发言处理状态
            val queueStats = flowSpeakEventHandler.getQueueStats()
            val isTtsPlaying = flowSpeakEventHandler.isTtsPlaying()
            val isUserBusy = flowSpeakEventHandler.isUserBusy()
            val isInCall = flowSpeakEventHandler.isInCall()

            _uiState.value = _uiState.value.copy(
                internalState = internalState,
                cycleCount = statistics.cycleCount,
                currentImpulse = statistics.currentImpulse,
                recentDecisionsCount = statistics.recentDecisions,
                pendingThoughtsCount = statistics.pendingThoughts,
                ignoredCount = statistics.ignoredCount,
                recentSpeakRatio = statistics.recentSpeakRatio,
                lastUpdateTime = System.currentTimeMillis(),
                // ✅ 环境状态
                recentUtterances = recentUtterances,
                currentSpeaker = currentSpeaker,
                presentPeople = presentPeople,
                audioLevel = audioLevel.level,
                isVoiceActive = audioLevel.level > 0.1f,  // 根据音量判断是否活跃
                // ✅ 发言处理状态
                queueSize = queueStats.size,
                isTtsPlaying = isTtsPlaying,
                isUserBusy = isUserBusy,
                isInCall = isInCall
            )

            Timber.d("[FlowDebugViewModel] 状态已刷新: 冲动=${statistics.currentImpulse}, 循环=${statistics.cycleCount}, 队列=${queueStats.size}")

        } catch (e: Exception) {
            Timber.e(e, "[FlowDebugViewModel] 刷新状态失败")
        }
    }

    /**
     * 手动触发发言（测试用）
     */
    fun triggerManualSpeak(message: String) {
        coordinator.triggerManualSpeak(message)
        _uiState.value = _uiState.value.copy(
            successMessage = "已触发发言: $message"
        )
        viewModelScope.launch {
            delay(2000)
            clearMessages()
        }
    }

    /**
     * 调整话痨度
     */
    fun adjustTalkativeLevel(level: Float) {
        coordinator.adjustTalkativeLevel(level)
        Timber.i("[FlowDebugViewModel] 话痨度已调整: $level")
    }

    /**
     * 启用/禁用内心想法
     */
    fun toggleInnerThoughts(enable: Boolean) {
        coordinator.enableInnerThoughts(enable)
        Timber.i("[FlowDebugViewModel] 内心想法: ${if (enable) "启用" else "禁用"}")
    }

    /**
     * 启用/禁用好奇心
     */
    fun toggleCuriosity(enable: Boolean) {
        coordinator.enableCuriosity(enable)
        Timber.i("[FlowDebugViewModel] 好奇心: ${if (enable) "启用" else "禁用"}")
    }

    /**
     * 启用/禁用主动关心
     */
    fun toggleProactiveCare(enable: Boolean) {
        coordinator.enableProactiveCare(enable)
        Timber.i("[FlowDebugViewModel] 主动关心: ${if (enable) "启用" else "禁用"}")
    }

    /**
     * 清除消息
     */
    private fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }
}

/**
 * 心流调试 UI 状态
 */
data class FlowDebugUiState(
    // 内在状态
    val internalState: InternalState? = null,

    // 统计信息
    val cycleCount: Long = 0,
    val currentImpulse: Float = 0f,
    val recentDecisionsCount: Int = 0,
    val pendingThoughtsCount: Int = 0,
    val ignoredCount: Int = 0,
    val recentSpeakRatio: Float = 0f,

    // ✅ 环境监听状态
    val recentUtterances: List<com.xiaoguang.assistant.domain.flow.model.Utterance> = emptyList(),
    val currentSpeaker: com.xiaoguang.assistant.domain.flow.model.SpeakerData? = null,
    val presentPeople: List<com.xiaoguang.assistant.domain.flow.model.SpeakerData> = emptyList(),
    val audioLevel: Float = 0f,
    val isVoiceActive: Boolean = false,

    // ✅ 发言处理状态
    val queueSize: Int = 0,
    val isTtsPlaying: Boolean = false,
    val isUserBusy: Boolean = false,
    val isInCall: Boolean = false,

    // 更新时间
    val lastUpdateTime: Long = 0,

    // 消息
    val successMessage: String? = null,
    val errorMessage: String? = null
) {
    /**
     * 获取决策历史（最近10条）
     */
    fun getRecentDecisions(): List<DecisionRecord> {
        return internalState?.recentDecisions?.takeLast(10) ?: emptyList()
    }

    /**
     * 获取待处理想法
     */
    fun getPendingThoughts(): List<InnerThought> {
        return internalState?.pendingThoughts ?: emptyList()
    }

    /**
     * 获取状态描述
     */
    fun getStateDescription(): String {
        return internalState?.getStateDescription() ?: "未知"
    }
}
