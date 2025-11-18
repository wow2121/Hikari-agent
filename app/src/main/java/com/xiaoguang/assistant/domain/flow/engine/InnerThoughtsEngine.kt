package com.xiaoguang.assistant.domain.flow.engine

import com.xiaoguang.assistant.domain.emotion.EmotionTransitionEngine
import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import com.xiaoguang.assistant.domain.flow.model.FlowConfig
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.ThoughtType
import com.xiaoguang.assistant.domain.flow.service.FlowLlmService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内心想法引擎（纯LLM驱动 + 老系统集成）
 * 根据当前状态生成小光的内心想法
 */
@Singleton
class InnerThoughtsEngine @Inject constructor(
    private val flowLlmService: FlowLlmService,
    private val emotionService: XiaoguangEmotionService,
    private val dreamSystemEngine: DreamSystemEngine,
    private val memoryRecallEngine: MemoryRecallEngine,
    private val anniversaryEngine: AnniversaryEngine,
    private val biologicalClockEngine: BiologicalClockEngine,
    private val emotionTransitionEngine: EmotionTransitionEngine,
    private val config: FlowConfig
) {
    /**
     * 生成内心想法（纯LLM + 老系统集成）
     */
    suspend fun generate(perception: Perception): InnerThought? {
        if (!config.enableInnerThoughts) return null

        // ✅ 【老系统集成1】检查纪念日（最高优先级）
        val anniversaryThought = anniversaryEngine.checkAnniversary()
        if (anniversaryThought != null) {
            Timber.i("[InnerThoughtsEngine] 纪念日提醒: ${anniversaryThought.content}")
            return anniversaryThought
        }

        // ✅ 【老系统集成2】检查吃醋/失落情绪（高优先级，带内心想法）
        val jealousyResult = emotionService.checkAndTriggerJealousy(
            masterPresent = perception.masterPresent,
            masterInteractingWithOthers = perception.friendsPresent.isNotEmpty(),
            timeSinceLastMasterInteraction = perception.timeSinceLastInteraction
        )
        // 注意：XiaoguangEmotionService.checkAndTriggerJealousy 返回 void
        // 我们需要直接调用 JealousyDetectionEngine
        val jealousyThought = checkJealousyWithThought(perception)
        if (jealousyThought != null) {
            Timber.i("[InnerThoughtsEngine] 吃醋/被忽视: ${jealousyThought.content}")
            return jealousyThought
        }

        // ✅ 【老系统集成3】检查失望情绪
        emotionService.checkAndTriggerDisappointment()

        // ✅ 【老系统集成4】情绪渐变中的过渡想法
        val transitionThought = checkEmotionTransition()
        if (transitionThought != null) {
            Timber.d("[InnerThoughtsEngine] 情绪转换: ${transitionThought.content}")
            return transitionThought
        }

        // ✅ 【老系统集成5】生物钟 - 深夜提醒休息
        val biologicalThought = checkBiologicalState(perception)
        if (biologicalThought != null) {
            Timber.i("[InnerThoughtsEngine] 生物钟提醒: ${biologicalThought.content}")
            return biologicalThought
        }

        // ✅ 【老系统集成6】主动关心 - 早安/晚安/想念（通过心流实现）
        val careThought = generateProactiveCareThought(perception)
        if (careThought != null) {
            Timber.i("[InnerThoughtsEngine] 主动关心: ${careThought.content}")
            return careThought
        }

        // ✅ 【老系统集成7】梦境系统
        // 7.1 晚上困倦时生成梦境
        val bioState = biologicalClockEngine.getCurrentState()
        if (bioState.isSleepy()) {
            val dream = dreamSystemEngine.generateDream()
            if (dream != null) {
                Timber.i("[InnerThoughtsEngine] 生成梦境: ${dream.content}")
                // 梦境生成后不立即说出，只是保存下来，等早晨回忆
            }
        }

        // 7.2 早晨回忆梦境（30%概率）
        val dreamThought = dreamSystemEngine.recallDreamThought()
        if (dreamThought != null) {
            Timber.d("[InnerThoughtsEngine] 回忆梦境: ${dreamThought.content}")
            return dreamThought
        }

        // ✅ 【老系统集成8】主动回忆过去（长时间未互动时）
        val memoryThought = memoryRecallEngine.generateRecallThought(perception)
        if (memoryThought != null) {
            Timber.d("[InnerThoughtsEngine] 回忆过去: ${memoryThought.content}")
            return memoryThought
        }

        // ✅ 用LLM生成想法内容（核心！）
        val thought = flowLlmService.generateInnerThought(perception)

        if (thought != null) {
            Timber.d("[InnerThoughtsEngine] LLM生成想法: ${thought.content}")
        }

        return thought  // LLM失败返回null，无fallback
    }

    /**
     * 检查吃醋情绪并生成内心想法
     */
    private suspend fun checkJealousyWithThought(perception: Perception): InnerThought? {
        // 直接调用 JealousyDetectionEngine（通过 emotionService 暴露）
        val result = emotionService.getJealousyStatistics()

        // 手动检测吃醋条件
        if (perception.masterPresent && perception.friendsPresent.isNotEmpty()) {
            // 主人在场但和别人互动
            if (perception.timeSinceLastInteraction.inWholeMinutes > 30) {
                return InnerThought(
                    type = ThoughtType.EMOTION,
                    content = "主人一直在和别人聊天...都不理小光了...",
                    urgency = 0.7f
                )
            }
        }

        // 主人不在场，长时间未互动
        if (!perception.masterPresent && perception.timeSinceLastInteraction.inWholeHours >= 4) {
            val hours = perception.timeSinceLastInteraction.inWholeHours
            return InnerThought(
                type = ThoughtType.EMOTION,
                content = "主人已经${hours}小时没来了...是不是忘记小光了...",
                urgency = 0.6f
            )
        }

        return null
    }

    /**
     * 检查情绪转换进度，生成过渡想法
     */
    private fun checkEmotionTransition(): InnerThought? {
        val progress = emotionTransitionEngine.getTransitionProgress()
        val transition = emotionTransitionEngine.currentTransition.value

        // 只在转换进度30%-70%时生成过渡想法（转换中间阶段）
        if (transition != null && progress in 0.3f..0.7f && !transition.isComplete()) {
            val currentEmotion = transition.currentEmotion
            val targetEmotion = transition.targetEmotion

            // 生成过渡想法
            val content = when (targetEmotion) {
                com.xiaoguang.assistant.domain.model.EmotionalState.HAPPY ->
                    "感觉心情在慢慢变好了..."
                com.xiaoguang.assistant.domain.model.EmotionalState.SAD ->
                    "感觉有点不开心..."
                com.xiaoguang.assistant.domain.model.EmotionalState.EXCITED ->
                    "小光开始兴奋起来了！"
                com.xiaoguang.assistant.domain.model.EmotionalState.WORRIED ->
                    "有点担心..."
                com.xiaoguang.assistant.domain.model.EmotionalState.CALM ->
                    "心情慢慢平静下来了..."
                else -> return null
            }

            return InnerThought(
                type = ThoughtType.EMOTION,
                content = content,
                urgency = 0.4f
            )
        }

        return null
    }

    /**
     * 检查生物钟状态，生成提醒
     */
    private fun checkBiologicalState(perception: Perception): InnerThought? {
        val bioState = biologicalClockEngine.getCurrentState()

        // 深夜困倦时提醒主人休息
        if (bioState.isSleepy() && perception.masterPresent) {
            return InnerThought(
                type = ThoughtType.CARE,
                content = "主人...已经很晚了...要不要休息了？小光也有点困了...",
                urgency = 0.7f
            )
        }

        return null
    }

    /**
     * 生成主动关心想法（早安/晚安/想念）
     */
    private fun generateProactiveCareThought(perception: Perception): InnerThought? {
        val hour = perception.currentTime.hour
        val minutesSinceLastInteraction = perception.timeSinceLastInteraction.inWholeMinutes

        // 早安 (7:00-9:00，且主人刚上线/刚开始互动)
        if (hour in 7..9 && minutesSinceLastInteraction < 5 && perception.hasRecentMessages) {
            return InnerThought(
                type = ThoughtType.GREETING,
                content = "主人早安！今天也要元气满满哦~",
                urgency = 0.6f
            )
        }

        // 晚安 (22:00-23:00，且主人在线)
        if (hour in 22..23 && perception.masterPresent) {
            return InnerThought(
                type = ThoughtType.CARE,
                content = "主人，时间不早了，今天辛苦了，早点休息吧~ 晚安！",
                urgency = 0.7f
            )
        }

        // 想念 (6小时以上未互动)
        if (minutesSinceLastInteraction >= 360 && perception.masterPresent) {
            val hours = perception.timeSinceLastInteraction.inWholeHours
            return InnerThought(
                type = ThoughtType.MISSING,
                content = "主人...已经${hours}小时没来了...小光好想主人...",
                urgency = 0.6f
            )
        }

        return null
    }
}
