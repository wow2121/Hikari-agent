package com.xiaoguang.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.flow.FlowSystemInitializer
import com.xiaoguang.assistant.domain.flow.FlowStatistics
import com.xiaoguang.assistant.domain.flow.HeartFlowCoordinator
import com.xiaoguang.assistant.domain.flow.model.FlowConfig
import com.xiaoguang.assistant.domain.flow.model.PersonalityType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 心流系统设置ViewModel
 */
@HiltViewModel
class FlowSettingsViewModel @Inject constructor(
    private val flowSystemInitializer: FlowSystemInitializer
) : ViewModel() {

    private val coordinator: HeartFlowCoordinator = flowSystemInitializer.getCoordinator()

    // UI状态
    private val _uiState = MutableStateFlow(FlowSettingsUiState())
    val uiState: StateFlow<FlowSettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentConfig()
        startStatisticsUpdate()
    }

    /**
     * 加载当前配置
     */
    private fun loadCurrentConfig() {
        viewModelScope.launch {
            coordinator.config.collect { config ->
                _uiState.value = _uiState.value.copy(
                    talkativeLevel = config.talkativeLevel,
                    personalityType = config.personalityType,
                    enableInnerThoughts = config.enableInnerThoughts,
                    enableCuriosity = config.enableCuriosity,
                    enableProactiveCare = config.enableProactiveCare,
                    debugMode = config.debugMode
                )
            }
        }
    }

    /**
     * 启动统计信息更新
     */
    private fun startStatisticsUpdate() {
        viewModelScope.launch {
            coordinator.isRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isRunning = running)

                if (running) {
                    updateStatistics()
                }
            }
        }
    }

    /**
     * 更新统计信息
     */
    fun updateStatistics() {
        val stats = coordinator.getStatistics()
        _uiState.value = _uiState.value.copy(statistics = stats)
    }

    /**
     * 调整话痨度
     */
    fun adjustTalkativeLevel(level: Float) {
        coordinator.adjustTalkativeLevel(level)
        Timber.i("话痨度已调整为: $level")
    }

    /**
     * 设置人格类型
     */
    fun setPersonalityType(type: PersonalityType) {
        coordinator.setPersonalityType(type)
        Timber.i("人格类型已设置为: ${type.displayName}")
    }

    /**
     * 切换内心想法
     */
    fun toggleInnerThoughts(enable: Boolean) {
        coordinator.enableInnerThoughts(enable)
        _uiState.value = _uiState.value.copy(enableInnerThoughts = enable)
    }

    /**
     * 切换好奇心
     */
    fun toggleCuriosity(enable: Boolean) {
        coordinator.enableCuriosity(enable)
        _uiState.value = _uiState.value.copy(enableCuriosity = enable)
    }

    /**
     * 切换主动关心
     */
    fun toggleProactiveCare(enable: Boolean) {
        coordinator.enableProactiveCare(enable)
        _uiState.value = _uiState.value.copy(enableProactiveCare = enable)
    }

    /**
     * 切换调试模式
     */
    fun toggleDebugMode(enable: Boolean) {
        val currentConfig = coordinator.config.value
        coordinator.updateConfig(currentConfig.copy(debugMode = enable))
        _uiState.value = _uiState.value.copy(debugMode = enable)
    }

    /**
     * 启动心流系统
     */
    fun startFlowSystem() {
        coordinator.start()
    }

    /**
     * 停止心流系统
     */
    fun stopFlowSystem() {
        viewModelScope.launch {
            coordinator.stop()
        }
    }

    /**
     * 暂停心流系统
     */
    fun pauseFlowSystem() {
        viewModelScope.launch {
            coordinator.pause()
        }
    }

    /**
     * 恢复心流系统
     */
    fun resumeFlowSystem() {
        coordinator.resume()
    }

    /**
     * 获取当前状态描述
     */
    fun getCurrentStateDescription(): String {
        val state = coordinator.getCurrentState()
        return state.getStateDescription()
    }
}

/**
 * UI状态
 */
data class FlowSettingsUiState(
    val isRunning: Boolean = false,
    val talkativeLevel: Float = 1.0f,
    val personalityType: PersonalityType = PersonalityType.BALANCED,
    val enableInnerThoughts: Boolean = true,
    val enableCuriosity: Boolean = true,
    val enableProactiveCare: Boolean = true,
    val debugMode: Boolean = false,
    val statistics: FlowStatistics? = null
)
