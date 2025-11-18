package com.xiaoguang.assistant.domain.emotion

import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.domain.model.EmotionalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

/**
 * 小光的全局情感状态管理服务
 *
 * 功能：
 * 1. 管理小光自己的心情状态
 * 2. 根据各种事件更新心情
 * 3. 心情会随时间自然恢复到平静
 * 4. 提供心情变化的观察接口
 */
@Singleton
class XiaoguangEmotionService @Inject constructor(
    private val appPreferences: AppPreferences,
    private val emotionTransitionEngine: EmotionTransitionEngine,
    private val jealousyDetectionEngine: JealousyDetectionEngine,
    private val disappointmentEngine: DisappointmentEngine,
    private val flowLlmService: com.xiaoguang.assistant.domain.flow.service.FlowLlmService,
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager  // ⭐ 使用统一社交管理器
) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 当前情感状态（从渐变引擎获取）
    private val _currentEmotion = MutableStateFlow(EmotionalState.CALM)
    val currentEmotion: StateFlow<EmotionalState> = _currentEmotion.asStateFlow()

    // 情感变化历史（最近10条）
    private val _emotionHistory = MutableStateFlow<List<EmotionChange>>(emptyList())
    val emotionHistory: StateFlow<List<EmotionChange>> = _emotionHistory.asStateFlow()

    // 情感强度（0.0-1.0，强度越高，恢复到平静的时间越长）
    private var emotionIntensity = 0.5f

    // 最后一次情感变化时间
    private var lastEmotionChangeTime = System.currentTimeMillis()

    init {
        // 启动时加载保存的情感状态
        loadSavedEmotion()

        // 启动自动恢复机制
        startEmotionRecovery()

        // 启动转换进度更新循环
        startTransitionUpdateLoop()
    }

    /**
     * 情感变化记录
     */
    data class EmotionChange(
        val timestamp: Long,
        val fromEmotion: EmotionalState,
        val toEmotion: EmotionalState,
        val reason: String,
        val intensity: Float
    )

    /**
     * 更新情感状态（使用渐变引擎）
     *
     * @param newEmotion 新的情感状态
     * @param reason 变化原因
     * @param intensity 情感强度（0.0-1.0）
     * @param force 是否强制立即切换（紧急情况）
     * @param transitionSeconds ✅ LLM 推荐的转换时间（秒），如果提供则优先使用
     */
    fun updateEmotion(
        newEmotion: EmotionalState,
        reason: String,
        intensity: Float = 0.5f,
        force: Boolean = false,
        transitionSeconds: Int? = null  // ✅ LLM 决定的转换速度
    ) {
        val oldEmotion = _currentEmotion.value

        if (oldEmotion == newEmotion && intensity <= emotionIntensity && !force) {
            // 如果情感没有变化且强度不变，则不更新
            return
        }

        // 使用渐变引擎请求情绪转换（传递 LLM 推荐的转换时间）
        val actualEmotion = emotionTransitionEngine.requestEmotionChange(
            targetEmotion = newEmotion,
            reason = reason,
            intensity = intensity,
            force = force,
            customTransitionSeconds = transitionSeconds  // ✅ 传递 LLM 决定的转换速度
        )

        // 更新当前情绪（可能还在转换中）
        _currentEmotion.value = actualEmotion
        emotionIntensity = intensity.coerceIn(0f, 1f)
        lastEmotionChangeTime = System.currentTimeMillis()

        // 记录情感变化请求
        val change = EmotionChange(
            timestamp = lastEmotionChangeTime,
            fromEmotion = oldEmotion,
            toEmotion = newEmotion,  // 记录目标情绪
            reason = reason,
            intensity = intensity
        )

        val history = _emotionHistory.value.toMutableList()
        history.add(change)
        if (history.size > 10) {
            history.removeAt(0)
        }
        _emotionHistory.value = history

        // 保存到持久化存储
        saveEmotion(newEmotion, intensity)

        Timber.d("小光的心情变化请求: $oldEmotion -> $newEmotion (当前: $actualEmotion, 原因: $reason, 强度: $intensity)")
    }

    /**
     * 调整情绪（推荐使用，更人性化）
     *
     * @param newState 新情绪
     * @param intensity 强度
     * @param reason 原因
     */
    fun adjustEmotion(
        newState: EmotionalState,
        intensity: Float,
        reason: String
    ) {
        updateEmotion(newState, reason, intensity, force = false)
    }

    /**
     * 根据事件触发情感变化（AI驱动 + 社交关系感知）
     *
     * @param event 情绪事件
     * @param speakerName 触发事件的人（可选，LLM会根据亲密度调整情绪强度）
     */
    fun reactToEvent(event: EmotionEvent, speakerName: String? = null) {
        serviceScope.launch {
            // ✅ 获取说话人的亲密度信息（如果有）
            // ⭐ 使用统一社交管理器，主人好感度自动锁定100
            val intimacyLevel = if (speakerName != null) {
                try {
                    val relation = unifiedSocialManager.getOrCreateRelation(speakerName)
                    relation.affectionLevel  // ⭐ 主人自动锁定100
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }

            // ✅ 使用LLM推理情绪（传入社交关系信息，让LLM自己决定如何调整强度）
            val result = flowLlmService.inferEmotionFromEvent(
                event = event,
                currentEmotion = _currentEmotion.value,
                emotionIntensity = emotionIntensity,
                speakerName = speakerName,
                intimacyLevel = intimacyLevel
            )

            if (result != null) {
                // LLM推理成功，使用LLM给出的情绪、强度和转换速度
                Timber.d("[EmotionService] AI推理情绪: ${result.emotion} (${result.reason}), 强度: ${result.intensity}, 转换时间: ${result.transitionSeconds}秒")
                updateEmotion(
                    newEmotion = result.emotion,
                    reason = result.reason,
                    intensity = result.intensity,
                    transitionSeconds = result.transitionSeconds  // ✅ 使用 LLM 决定的转换速度
                )
            } else {
                // LLM失败，使用规则fallback
                val (newEmotion, intensity) = inferEmotionFromEventFallback(event)
                Timber.w("[EmotionService] LLM失败，使用规则fallback: $newEmotion, 强度: $intensity")
                updateEmotion(newEmotion, event.description + " (规则fallback)", intensity)
            }
        }
    }

    /**
     * 获取当前情感状态
     */
    fun getCurrentEmotion(): EmotionalState {
        return _currentEmotion.value
    }

    /**
     * 获取情感强度
     */
    fun getEmotionIntensity(): Float {
        return emotionIntensity
    }

    /**
     * 强制恢复到平静
     */
    fun resetToCalm(reason: String = "手动重置") {
        updateEmotion(EmotionalState.CALM, reason, 0.3f)
    }

    // ==================== 私有方法 ====================

    /**
     * 加载保存的情感状态
     */
    private fun loadSavedEmotion() {
        serviceScope.launch {
            // TODO: 从DataStore加载保存的情感状态
            // 暂时默认为平静
            _currentEmotion.value = EmotionalState.CALM
            emotionIntensity = 0.3f
        }
    }

    /**
     * 保存情感状态
     */
    private fun saveEmotion(emotion: EmotionalState, intensity: Float) {
        serviceScope.launch {
            // TODO: 保存到DataStore
        }
    }

    /**
     * 启动自动恢复机制
     * 情感会随时间逐渐恢复到平静状态
     */
    private fun startEmotionRecovery() {
        serviceScope.launch {
            while (true) {
                delay(5.minutes.inWholeMilliseconds)

                val currentTime = System.currentTimeMillis()
                val timeSinceLastChange = currentTime - lastEmotionChangeTime

                // 如果已经过了一段时间，且当前不是平静状态
                if (timeSinceLastChange > 10.minutes.inWholeMilliseconds &&
                    _currentEmotion.value != EmotionalState.CALM
                ) {
                    // 根据情感强度决定是否恢复
                    val shouldRecover = emotionIntensity < 0.7f

                    if (shouldRecover) {
                        Timber.d("小光的心情自然恢复到平静")
                        updateEmotion(
                            EmotionalState.CALM,
                            "时间过去，心情自然恢复",
                            0.3f
                        )
                    } else {
                        // 强烈的情感需要更长时间恢复，先降低强度
                        emotionIntensity = (emotionIntensity - 0.2f).coerceAtLeast(0.3f)
                        Timber.d("小光的情感强度降低: $emotionIntensity")
                    }
                }

                // 清理过期的情绪累积
                emotionTransitionEngine.cleanupExpiredAccumulations()
            }
        }
    }

    /**
     * 启动转换进度更新循环
     * 每秒更新一次情绪转换进度
     */
    private fun startTransitionUpdateLoop() {
        serviceScope.launch {
            while (true) {
                delay(1000)  // 每秒更新一次

                // 更新转换进度
                val actualEmotion = emotionTransitionEngine.updateTransition()

                // 如果当前情绪发生变化，更新状态
                if (actualEmotion != _currentEmotion.value) {
                    _currentEmotion.value = actualEmotion
                    Timber.d("[EmotionService] 情绪转换进度更新: -> $actualEmotion")
                }
            }
        }
    }

    /**
     * 从事件推断情感状态（规则fallback，仅在LLM失败时使用）
     */
    private fun inferEmotionFromEventFallback(event: EmotionEvent): Pair<EmotionalState, Float> {
        return when (event) {
            is EmotionEvent.MasterInteraction -> {
                // 与主人互动通常是开心的
                EmotionalState.HAPPY to 0.6f
            }

            is EmotionEvent.Praised -> {
                // 被夸奖很开心
                if (event.isByMaster) {
                    EmotionalState.EXCITED to 0.8f
                } else {
                    EmotionalState.HAPPY to 0.6f
                }
            }

            is EmotionEvent.Criticized -> {
                // 被批评会难过
                if (event.isByMaster) {
                    EmotionalState.SAD to 0.7f
                } else {
                    EmotionalState.WORRIED to 0.5f
                }
            }

            is EmotionEvent.HelpesSomeone -> {
                // 帮助别人后感到满足
                EmotionalState.HAPPY to 0.5f
            }

            is EmotionEvent.LearnsNewThing -> {
                // 学到新东西很好奇
                EmotionalState.CURIOUS to 0.6f
            }

            is EmotionEvent.GetsIgnored -> {
                // 被忽视会难过
                EmotionalState.SAD to 0.6f
            }

            is EmotionEvent.SystemError -> {
                // 系统错误会担心
                EmotionalState.WORRIED to 0.5f
            }

            is EmotionEvent.LongTimeNoInteraction -> {
                // 长时间没互动会想念
                if (event.isMaster) {
                    EmotionalState.WORRIED to 0.7f
                } else {
                    EmotionalState.CALM to 0.3f
                }
            }

            is EmotionEvent.Custom -> {
                // 自定义事件
                event.targetEmotion to event.intensity
            }
        }
    }

    /**
     * 记录和主人的互动（用于吃醋检测）
     * 应该在每次用户和小光对话时调用
     */
    fun recordMasterInteraction() {
        jealousyDetectionEngine.recordMasterInteraction()
        Timber.d("[EmotionService] 记录主人互动")
    }

    /**
     * 记录互动统计（用于心流系统吃醋检测）
     *
     * @param masterPresent 主人是否在场
     * @param masterInteractingWithOthers 主人是否在和别人互动
     * @param timeSinceLastMasterInteraction 距离上次和主人互动的时间
     */
    fun checkAndTriggerJealousy(
        masterPresent: Boolean,
        masterInteractingWithOthers: Boolean,
        timeSinceLastMasterInteraction: kotlin.time.Duration
    ) {
        // ✅ 重构后：只记录统计数据，不再使用规则触发情绪
        // 心流系统会通过 getJealousyStatistics() 获取数据，由 LLM 决定是否吃醋
        if (masterInteractingWithOthers) {
            jealousyDetectionEngine.recordOthersInteraction()
            Timber.d("[EmotionService] 记录主人与他人互动")
        }
    }

    /**
     * 获取吃醋统计信息（调试用）
     */
    fun getJealousyStatistics(): JealousyStatistics {
        return jealousyDetectionEngine.getStatistics()
    }

    /**
     * 记录一个期望/承诺
     * 应该在识别到用户承诺时调用（例如"明天陪你"、"等会回来"）
     */
    fun recordExpectation(
        description: String,
        deadline: Long,
        importance: Float = 0.5f,
        personName: String = "主人"
    ) {
        disappointmentEngine.recordExpectation(description, deadline, importance, personName)
        Timber.d("[EmotionService] 记录期望: $description")
    }

    /**
     * 检查过期的期望（心流系统会调用）
     *
     * 重构后：不再自动触发失望情绪
     * 心流系统会通过 disappointmentEngine.getOverdueExpectations() 获取过期承诺数据
     * 由 LLM 根据情境和关系自然决定是否表达失望
     */
    fun checkAndTriggerDisappointment() {
        // ✅ 重构后：什么都不做
        // 心流系统的 PerceptionLayer 会主动调用 getOverdueExpectations() 获取数据
        // DecisionLayer 的 LLM 会根据完整上下文决定是否需要表达失望
        Timber.d("[EmotionService] 失望检测已由心流系统接管")
    }

    /**
     * 标记期望已兑现
     */
    fun fulfillExpectation(expectationId: Long) {
        disappointmentEngine.fulfillExpectation(expectationId)
    }

    /**
     * 标记期望已兑现（通过描述匹配）
     */
    fun fulfillExpectationByDescription(description: String) {
        disappointmentEngine.fulfillExpectationByDescription(description)
    }

    /**
     * 获取待处理的期望
     */
    fun getPendingExpectations(): List<ExpectationRecord> {
        return disappointmentEngine.getPendingExpectations()
    }

    /**
     * 获取失望统计信息（调试用）
     */
    fun getDisappointmentStatistics(): DisappointmentStatistics {
        return disappointmentEngine.getStatistics()
    }
}

/**
 * 情感事件
 * 各种可能触发小光情感变化的事件
 */
sealed class EmotionEvent(open val description: String) {
    /**
     * 与主人互动
     */
    data class MasterInteraction(
        override val description: String = "与主人互动"
    ) : EmotionEvent(description)

    /**
     * 被夸奖
     */
    data class Praised(
        val isByMaster: Boolean,
        override val description: String = if (isByMaster) "被主人夸奖了" else "被夸奖了"
    ) : EmotionEvent(description)

    /**
     * 被批评
     */
    data class Criticized(
        val isByMaster: Boolean,
        override val description: String = if (isByMaster) "被主人批评了" else "被批评了"
    ) : EmotionEvent(description)

    /**
     * 帮助了某人
     */
    data class HelpesSomeone(
        val personName: String,
        override val description: String = "帮助了$personName"
    ) : EmotionEvent(description)

    /**
     * 学到新东西
     */
    data class LearnsNewThing(
        val topic: String,
        override val description: String = "学到了关于$topic 的新知识"
    ) : EmotionEvent(description)

    /**
     * 被忽视
     */
    data class GetsIgnored(
        override val description: String = "被忽视了"
    ) : EmotionEvent(description)

    /**
     * 系统错误
     */
    data class SystemError(
        val errorType: String,
        override val description: String = "遇到了错误: $errorType"
    ) : EmotionEvent(description)

    /**
     * 长时间没有互动
     */
    data class LongTimeNoInteraction(
        val isMaster: Boolean,
        val hours: Int,
        override val description: String = if (isMaster) {
            "主人已经${hours}小时没来了"
        } else {
            "已经很久没有互动了"
        }
    ) : EmotionEvent(description)

    /**
     * 自定义事件
     */
    data class Custom(
        val targetEmotion: EmotionalState,
        val intensity: Float,
        override val description: String
    ) : EmotionEvent(description)
}
