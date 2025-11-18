package com.xiaoguang.assistant.domain.flow

import com.xiaoguang.assistant.domain.flow.layer.ActionLayer
import com.xiaoguang.assistant.domain.flow.layer.ActionType
import com.xiaoguang.assistant.domain.flow.layer.DecisionLayer
import com.xiaoguang.assistant.domain.flow.layer.PerceptionLayer
import com.xiaoguang.assistant.domain.flow.layer.ThinkingLayer
import com.xiaoguang.assistant.domain.flow.model.FlowConfig
import com.xiaoguang.assistant.domain.flow.model.InternalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * 心流循环 - 小光AI的核心决策引擎
 *
 * 架构：
 * 持续运行的四阶段循环：感知 → 思考 → 决策 → 行动
 *
 * 循环阶段：
 * 1. 感知阶段：收集环境信息、用户状态、情绪数据
 * 2. 思考阶段：基于感知生成内部想法和推理
 * 3. 决策阶段：评估是否发言及发言内容
 * 4. 行动阶段：执行决策（发言或保持沉默）
 *
 * 特性：
 * - 每3秒执行一次完整循环（可动态调整）
 * - 基于环境噪音和沉默时长动态调整循环间隔
 * - 记录循环历史用于调试和优化
 * - 支持冲动值管理，控制发言频率
 */
@Singleton
class FlowLoop @Inject constructor(
    private val perceptionLayer: PerceptionLayer,
    private val thinkingLayer: ThinkingLayer,
    private val decisionLayer: DecisionLayer,
    private val actionLayer: ActionLayer,
    private val config: FlowConfig
) {
    private var loopJob: Job? = null
    private var isRunning = false
    private var cycleCount = 0L

    // 内在状态
    private var internalState = InternalState(
        lastSpeakTime = System.currentTimeMillis(),
        lastInteractionTime = System.currentTimeMillis()
    )

    // ✅ 循环历史记录（保存最近20次）
    private val cycleHistory = mutableListOf<com.xiaoguang.assistant.domain.flow.model.FlowCycleRecord>()
    private val maxHistorySize = 20

    /**
     * 启动心流循环
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) {
            Timber.w("[FlowLoop] 心流循环已在运行")
            return
        }

        loopJob = scope.launch(Dispatchers.Default) {
            isRunning = true
            Timber.i("[FlowLoop] 心流循环启动！")

            while (isActive && isRunning) {
                try {
                    val cycleStartTime = System.currentTimeMillis()
                    cycleCount++

                    // 执行一次完整循环
                    executeCycle()

                    // 计算本次循环耗时
                    val cycleTime = System.currentTimeMillis() - cycleStartTime

                    // 计算下次循环间隔
                    val waitTime = calculateWaitTime(cycleTime)

                    // 等待
                    delay(waitTime)

                } catch (e: Exception) {
                    Timber.e(e, "[FlowLoop] 心流循环异常")
                    // 出错后等待一段时间再继续
                    delay(5.seconds)
                }
            }

            Timber.i("[FlowLoop] 心流循环结束，共执行 $cycleCount 次")
            isRunning = false
        }
    }

    /**
     * 停止心流循环
     */
    suspend fun stop() {
        Timber.i("[FlowLoop] 停止心流循环...")
        isRunning = false
        loopJob?.cancelAndJoin()
        loopJob = null
    }

    /**
     * 执行一次完整循环
     */
    private suspend fun executeCycle() {
        val cycleStartTime = System.currentTimeMillis()
        val timings = mutableMapOf<String, Long>()

        logCycleStatus()

        // 执行心流循环的各个阶段
        val perception = executePerceptionPhase(timings)
        val thoughts = executeThinkingPhase(perception, timings)
        val decision = executeDecisionPhase(perception, thoughts, timings)
        val actionResult = executeActionPhase(decision, perception, thoughts, timings)

        // 更新状态并保存记录
        updateStateAfterAction(actionResult)
        saveCycleRecord(
            perception = perception,
            thoughts = thoughts,
            decision = decision,
            actionResult = actionResult,
            timings = timings,
            totalDuration = System.currentTimeMillis() - cycleStartTime
        )
    }

    /**
     * 记录循环状态
     */
    private fun logCycleStatus() {
        // ✅ 每30秒打印一次状态（10次循环 = 30秒，因为间隔改为3秒）
        if (cycleCount % 10 == 0L) {
            Timber.d("[FlowLoop] 🔄 Cycle #$cycleCount | 冲动: %.2f | ${internalState.getStateDescription()}",
                internalState.impulseValue)
        }
    }

    /**
     * 执行感知阶段
     */
    private suspend fun executePerceptionPhase(timings: MutableMap<String, Long>): com.xiaoguang.assistant.domain.flow.model.Perception {
        val perceptionStart = System.currentTimeMillis()
        val perception = perceptionLayer.perceive()
        timings["perception"] = System.currentTimeMillis() - perceptionStart

        updateInternalState(perception, timings)
        return perception
    }

    /**
     * 更新内在状态
     */
    private fun updateInternalState(
        perception: com.xiaoguang.assistant.domain.flow.model.Perception,
        timings: MutableMap<String, Long>
    ) {
        val stateUpdateStart = System.currentTimeMillis()
        val deltaTime = (System.currentTimeMillis() - internalState.lastInteractionTime).milliseconds

        internalState = internalState.updateImpulse(
            deltaTime = deltaTime,
            emotionInfluence = calculateEmotionInfluence(perception.currentEmotion, perception.emotionIntensity)
        ).copy(
            currentEmotion = perception.currentEmotion,
            emotionIntensity = perception.emotionIntensity,
            lastInteractionTime = if (perception.hasRecentMessages) System.currentTimeMillis() else internalState.lastInteractionTime,
            timeSinceLastSpeak = safeDuration(internalState.lastSpeakTime),
            timeSinceLastProactiveSpeak = safeDuration(internalState.lastProactiveSpeakTime),
            timeSinceLastPassiveReply = safeDuration(internalState.lastPassiveReplyTime),
            timeSinceLastInteraction = perception.timeSinceLastInteraction,
            recentSpeakRatio = calculateRecentSpeakRatio()
        )
        timings["stateUpdate"] = System.currentTimeMillis() - stateUpdateStart
    }

    /**
     * 安全计算时间差（避免1970年时间戳问题）
     */
    private fun safeDuration(lastTime: Long): kotlin.time.Duration {
        return if (lastTime == 0L) {
            // 如果从未发生过，返回一个合理的默认值（例如24小时）
            kotlin.time.Duration.ZERO
        } else {
            val diff = System.currentTimeMillis() - lastTime
            // 如果时间差超过30天，说明可能是初始化问题，返回0
            if (diff > 30L * 24 * 60 * 60 * 1000) {
                kotlin.time.Duration.ZERO
            } else {
                diff.milliseconds
            }
        }
    }

    /**
     * 执行思考阶段
     */
    private suspend fun executeThinkingPhase(
        perception: com.xiaoguang.assistant.domain.flow.model.Perception,
        timings: MutableMap<String, Long>
    ): com.xiaoguang.assistant.domain.flow.model.Thoughts {
        val thinkingStart = System.currentTimeMillis()
        val thoughts = thinkingLayer.think(perception)
        timings["thinking"] = System.currentTimeMillis() - thinkingStart

        // 将想法加入内在状态
        thoughts.innerThoughts.forEach { thought ->
            internalState = internalState.addThought(thought)
        }

        return thoughts
    }

    /**
     * 执行决策阶段
     */
    private suspend fun executeDecisionPhase(
        perception: com.xiaoguang.assistant.domain.flow.model.Perception,
        thoughts: com.xiaoguang.assistant.domain.flow.model.Thoughts,
        timings: MutableMap<String, Long>
    ): com.xiaoguang.assistant.domain.flow.model.SpeakDecision {
        val decisionStart = System.currentTimeMillis()
        val decision = decisionLayer.decide(perception, thoughts, internalState)
        timings["decision"] = System.currentTimeMillis() - decisionStart
        return decision
    }

    /**
     * 执行行动阶段
     */
    private suspend fun executeActionPhase(
        decision: com.xiaoguang.assistant.domain.flow.model.SpeakDecision,
        perception: com.xiaoguang.assistant.domain.flow.model.Perception,
        thoughts: com.xiaoguang.assistant.domain.flow.model.Thoughts,
        timings: MutableMap<String, Long>
    ): com.xiaoguang.assistant.domain.flow.layer.ActionResult {
        val actionStart = System.currentTimeMillis()
        val actionResult = actionLayer.execute(decision, perception, thoughts)
        timings["action"] = System.currentTimeMillis() - actionStart
        return actionResult
    }

    /**
     * 根据行动结果更新状态
     */
    private fun updateStateAfterAction(actionResult: com.xiaoguang.assistant.domain.flow.layer.ActionResult) {
        if (actionResult.success) {
            // 记录决策
            internalState = internalState.addDecisionRecord(actionResult.decisionRecord)

            // 如果说话了，重置冲动值并记录时间
            if (actionResult.actionType == ActionType.SPEAK) {
                handleSuccessfulSpeech(actionResult)
            }
        }
    }

    /**
     * 处理成功的发言
     */
    private fun handleSuccessfulSpeech(actionResult: com.xiaoguang.assistant.domain.flow.layer.ActionResult) {
        val currentTime = System.currentTimeMillis()
        internalState = internalState.resetImpulse().copy(
            lastSpeakTime = currentTime,
            lastProactiveSpeakTime = currentTime  // ✅ 记录主动发言时间
        )
        perceptionLayer.recordSpeak()

        Timber.i("[FlowLoop] ✨ 小光主动发言: ${actionResult.message}")
    }

    /**
     * 计算等待时间（根据环境动态调整）
     */
    private fun calculateWaitTime(cycleTime: Long): Long {
        val perception = perceptionLayer.getLastPerception()

        // 根据环境噪音和沉默时长动态调整间隔
        val baseInterval = when {
            // 环境非常活跃（有人说话）→ 稍快一点（2秒）
            perception != null && perception.environmentNoise > 0.7f -> config.minLoopInterval

            // 环境活跃 → 正常间隔（3秒）
            perception != null && perception.environmentNoise > 0.3f -> config.baseLoopInterval

            // 长时间沉默（30分钟+）→ 降低频率（5秒）
            perception != null && perception.silenceDuration.inWholeMinutes > 30 -> 5000L

            // 极长时间沉默（2小时+）→ 最低频率（10秒）
            perception != null && perception.silenceDuration.inWholeHours >= 2 -> config.maxLoopInterval

            // 默认使用配置的基础间隔
            else -> config.baseLoopInterval
        }

        // 减去本次循环耗时，确保总间隔符合预期
        val actualInterval = (baseInterval - cycleTime).coerceIn(
            config.minLoopInterval,
            config.maxLoopInterval
        )

        return actualInterval
    }

    /**
     * 计算情绪对冲动的影响
     */
    private fun calculateEmotionInfluence(
        emotion: com.xiaoguang.assistant.domain.model.EmotionalState,
        intensity: Float
    ): Float {
        val baseInfluence = when (emotion) {
            com.xiaoguang.assistant.domain.model.EmotionalState.EXCITED -> 1.5f
            com.xiaoguang.assistant.domain.model.EmotionalState.HAPPY -> 1.2f
            com.xiaoguang.assistant.domain.model.EmotionalState.CALM -> 1.4f  // 平静但想互动
            com.xiaoguang.assistant.domain.model.EmotionalState.CURIOUS -> 1.3f
            com.xiaoguang.assistant.domain.model.EmotionalState.WORRIED -> 1.1f
            com.xiaoguang.assistant.domain.model.EmotionalState.SAD -> 0.8f
            else -> 1.0f
        }

        return baseInfluence * (0.5f + intensity * 0.5f)
    }

    /**
     * 计算最近发言占比（用于频率控制）
     */
    private fun calculateRecentSpeakRatio(): Float {
        val recentDecisions = internalState.recentDecisions.takeLast(20)
        if (recentDecisions.isEmpty()) return 0f

        val spokeCount = recentDecisions.count { it.actuallySpoke }
        return spokeCount.toFloat() / recentDecisions.size
    }

    /**
     * ✅ 记录被动回复（用户直接对话触发的回复）
     * 当小光被动回复用户时调用此方法
     */
    fun recordPassiveReply() {
        val currentTime = System.currentTimeMillis()
        internalState = internalState.copy(
            lastSpeakTime = currentTime,
            lastPassiveReplyTime = currentTime  // ✅ 记录被动回复时间
        )
        Timber.d("[FlowLoop] 记录被动回复时间")
    }

    /**
     * 获取当前状态（用于调试和监控）
     */
    fun getCurrentState(): InternalState = internalState

    /**
     * 是否正在运行
     */
    fun isRunning(): Boolean = isRunning

    /**
     * ✅ 保存循环记录
     */
    private fun saveCycleRecord(
        perception: com.xiaoguang.assistant.domain.flow.model.Perception,
        thoughts: com.xiaoguang.assistant.domain.flow.model.Thoughts,
        decision: com.xiaoguang.assistant.domain.flow.model.SpeakDecision,
        actionResult: com.xiaoguang.assistant.domain.flow.layer.ActionResult,
        timings: Map<String, Long>,
        totalDuration: Long
    ) {
        val record = com.xiaoguang.assistant.domain.flow.model.FlowCycleRecord(
            cycleId = cycleCount,
            perception = com.xiaoguang.assistant.domain.flow.model.PerceptionSnapshot.fromPerception(perception),
            thoughts = thoughts.innerThoughts,
            decisionReasoning = decision.reason,
            decision = decision,
            action = actionResult,
            timings = timings,
            totalDuration = totalDuration
        )

        synchronized(cycleHistory) {
            cycleHistory.add(record)
            if (cycleHistory.size > maxHistorySize) {
                cycleHistory.removeAt(0)
            }
        }

        Timber.d("[FlowLoop] 保存循环记录 #$cycleCount: ${record.getDecisionSummary()}")
    }

    /**
     * ✅ 获取循环历史记录
     */
    fun getCycleHistory(): List<com.xiaoguang.assistant.domain.flow.model.FlowCycleRecord> {
        synchronized(cycleHistory) {
            return cycleHistory.toList()
        }
    }

    /**
     * ✅ 获取最近N次循环记录
     */
    fun getRecentCycles(count: Int): List<com.xiaoguang.assistant.domain.flow.model.FlowCycleRecord> {
        synchronized(cycleHistory) {
            return cycleHistory.takeLast(count)
        }
    }

    /**
     * ✅ 获取循环统计信息
     */
    fun getCycleStats(): CycleStats {
        synchronized(cycleHistory) {
            val speakCount = cycleHistory.count { it.decision.shouldSpeak }
            val successCount = cycleHistory.count { it.isSuccessful() }
            val avgDuration = if (cycleHistory.isNotEmpty()) {
                cycleHistory.map { it.totalDuration }.average()
            } else 0.0

            return CycleStats(
                totalCycles = cycleHistory.size,
                speakCount = speakCount,
                silenceCount = cycleHistory.size - speakCount,
                successRate = if (cycleHistory.isNotEmpty()) successCount.toFloat() / cycleHistory.size else 0f,
                avgDurationMs = avgDuration.toLong()
            )
        }
    }

    /**
     * 获取循环次数
     */
    fun getCycleCount(): Long = cycleCount
}

/**
 * ✅ 循环统计信息
 */
data class CycleStats(
    val totalCycles: Int,
    val speakCount: Int,
    val silenceCount: Int,
    val successRate: Float,
    val avgDurationMs: Long
)
