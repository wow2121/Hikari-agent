package com.xiaoguang.assistant.domain.state

import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import com.xiaoguang.assistant.domain.flow.model.EnvironmentState as FlowEnvironmentState
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 小光核心状态管理器实现
 *
 * 从各个系统聚合状态，提供统一的状态访问接口
 */
@Singleton
class XiaoguangCoreStateManagerImpl @Inject constructor(
    private val emotionService: XiaoguangEmotionService,
    private val voiceprintManager: VoiceprintManager,
    private val flowEnvironmentState: FlowEnvironmentState,
    private val flowSystemInitializer: com.xiaoguang.assistant.domain.flow.FlowSystemInitializer,
    private val coroutineScope: CoroutineScope
) : XiaoguangCoreStateManager {

    private val _coreState = MutableStateFlow(XiaoguangCoreState())
    override val coreState: StateFlow<XiaoguangCoreState> = _coreState.asStateFlow()

    init {
        // 监听各个系统的状态变化
        observeEmotionChanges()
        observeEnvironmentChanges()
        observeFlowChanges()
    }

    /**
     * 监听情绪变化
     */
    private fun observeEmotionChanges() {
        coroutineScope.launch {
            emotionService.currentEmotion.collect { emotionalState ->
                // 从情绪服务获取强度和历史
                val intensity = emotionService.getEmotionIntensity()
                val history = emotionService.emotionHistory.value
                val lastChange = history.lastOrNull()

                val newEmotionState = EmotionState(
                    currentEmotion = emotionalState.toEmotionType(),
                    intensity = intensity,
                    reason = lastChange?.reason,
                    previousEmotion = _coreState.value.emotion.currentEmotion,
                    duration = 0 // TODO: 计算持续时间
                )
                updateEmotion(newEmotionState)
            }
        }
    }

    /**
     * 监听环境变化
     */
    private fun observeEnvironmentChanges() {
        coroutineScope.launch {
            flowEnvironmentState.currentSpeaker.collect { speakerData ->
                val newSpeakerState = SpeakerState(
                    currentSpeakerId = speakerData.speakerId,
                    currentSpeakerName = speakerData.speakerName,
                    isMaster = speakerData.isMaster,
                    isListening = flowEnvironmentState.isVoiceActive.value.isActive,
                    isIdentifying = false,
                    confidence = speakerData.confidence,
                    strangerDetected = speakerData.speakerId == null && speakerData.speakerName == null,
                    lastIdentificationTimestamp = speakerData.timestamp
                )
                updateSpeaker(newSpeakerState)
            }
        }

        coroutineScope.launch {
            flowEnvironmentState.isVoiceActive.collect { voiceActivity ->
                val currentSpeaker = _coreState.value.speaker
                updateSpeaker(currentSpeaker.copy(isListening = voiceActivity.isActive))
            }
        }
    }

    /**
     * 监听心流状态变化
     */
    private fun observeFlowChanges() {
        coroutineScope.launch {
            val coordinator = flowSystemInitializer.getCoordinator()

            // 订阅心流运行状态
            coordinator.isRunning.collect { isRunning ->
                // 每次运行状态变化时更新
                updateFlowState(coordinator, isRunning)
            }
        }

        // 定期轮询心流详细状态（每1秒）
        coroutineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                try {
                    val coordinator = flowSystemInitializer.getCoordinator()
                    val isRunning = coordinator.isRunning.value
                    updateFlowState(coordinator, isRunning)
                } catch (e: Exception) {
                    // 忽略错误，继续轮询
                }
            }
        }
    }

    /**
     * 更新心流状态
     */
    private fun updateFlowState(
        coordinator: com.xiaoguang.assistant.domain.flow.HeartFlowCoordinator,
        isRunning: Boolean
    ) {
        if (!isRunning) {
            // 心流未运行
            val newFlowState = FlowState(
                isRunning = false,
                currentPhase = FlowPhase.IDLE,
                lastFlowTimestamp = System.currentTimeMillis()
            )
            coroutineScope.launch { updateFlow(newFlowState) }
            return
        }

        // 获取详细的内部状态
        val internalState = coordinator.getCurrentState()
        val statistics = coordinator.getStatistics()

        // 根据冲动值推断当前阶段
        val currentPhase = when {
            internalState.impulseValue > 0.75f -> FlowPhase.DECIDING
            internalState.impulseValue > 0.5f -> FlowPhase.THINKING
            internalState.pendingThoughts.isNotEmpty() -> FlowPhase.THINKING
            else -> FlowPhase.PERCEIVING
        }

        // 获取当前想法
        val currentThought = internalState.pendingThoughts.firstOrNull()?.content

        val newFlowState = FlowState(
            isRunning = true,
            currentPhase = currentPhase,
            currentThought = currentThought,
            impulse = internalState.impulseValue,
            lastFlowTimestamp = System.currentTimeMillis()
        )

        coroutineScope.launch { updateFlow(newFlowState) }
    }

    override suspend fun updateEmotion(emotion: EmotionState) {
        _coreState.update { it.copy(emotion = emotion) }
    }

    override suspend fun updateFlow(flow: FlowState) {
        _coreState.update { it.copy(flow = flow) }
    }

    override suspend fun updateSpeaker(speaker: SpeakerState) {
        _coreState.update { it.copy(speaker = speaker) }
    }

    override suspend fun updateEnvironment(environment: com.xiaoguang.assistant.domain.state.EnvironmentState) {
        _coreState.update { it.copy(environment = environment) }
    }

    override suspend fun updateConversation(conversation: ConversationState) {
        _coreState.update { it.copy(conversation = conversation) }
    }

    override suspend fun updateSystem(system: SystemState) {
        _coreState.update { it.copy(system = system) }
    }

    override suspend fun reset() {
        _coreState.value = XiaoguangCoreState()
    }

    /**
     * 获取当前时间段
     */
    private fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..8 -> TimeOfDay.MORNING
            in 9..11 -> TimeOfDay.FORENOON
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..19 -> TimeOfDay.EVENING
            in 20..22 -> TimeOfDay.NIGHT
            else -> TimeOfDay.LATE_NIGHT
        }
    }
}

/**
 * 将EmotionalState映射到EmotionType
 */
private fun com.xiaoguang.assistant.domain.model.EmotionalState.toEmotionType(): EmotionType {
    return when (this) {
        com.xiaoguang.assistant.domain.model.EmotionalState.HAPPY -> EmotionType.HAPPY
        com.xiaoguang.assistant.domain.model.EmotionalState.EXCITED -> EmotionType.EXCITED
        com.xiaoguang.assistant.domain.model.EmotionalState.CALM -> EmotionType.CALM
        com.xiaoguang.assistant.domain.model.EmotionalState.CURIOUS -> EmotionType.CURIOUS
        com.xiaoguang.assistant.domain.model.EmotionalState.WORRIED -> EmotionType.ANXIOUS
        com.xiaoguang.assistant.domain.model.EmotionalState.SAD -> EmotionType.SAD
        com.xiaoguang.assistant.domain.model.EmotionalState.ANGRY -> EmotionType.FRUSTRATED
        com.xiaoguang.assistant.domain.model.EmotionalState.SHY -> EmotionType.CONFUSED
        com.xiaoguang.assistant.domain.model.EmotionalState.TIRED -> EmotionType.TIRED
        com.xiaoguang.assistant.domain.model.EmotionalState.TOUCHED -> EmotionType.SURPRISED
        com.xiaoguang.assistant.domain.model.EmotionalState.JEALOUS -> EmotionType.FRUSTRATED
        com.xiaoguang.assistant.domain.model.EmotionalState.DISAPPOINTED -> EmotionType.SAD
        com.xiaoguang.assistant.domain.model.EmotionalState.NEGLECTED -> EmotionType.SAD
        com.xiaoguang.assistant.domain.model.EmotionalState.LONELY -> EmotionType.SAD
    }
}
