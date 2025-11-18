package com.xiaoguang.assistant.domain.flow.layer

import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.toTimeOfDay
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.model.MessageRole
import com.xiaoguang.assistant.domain.repository.ConversationRepository
import com.xiaoguang.assistant.domain.usecase.PersonalityAnalysisUseCase
import com.xiaoguang.assistant.domain.social.UnifiedSocialManager
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * 感知层
 * 负责收集环境、时间、情感、关系等所有感知信息
 */
@Singleton
class PerceptionLayer @Inject constructor(
    private val emotionService: XiaoguangEmotionService,
    private val characterBook: com.xiaoguang.assistant.domain.knowledge.CharacterBook,  // ⭐ 新系统：Character Book
    private val unifiedSocialManager: UnifiedSocialManager,  // ⚠️ 过渡期保留
    private val voiceprintManager: VoiceprintManager,  // ✅ 新的声纹管理器
    private val conversationRepository: ConversationRepository,
    private val personalityAnalysisUseCase: PersonalityAnalysisUseCase,
    private val environmentState: com.xiaoguang.assistant.domain.flow.model.EnvironmentState
) {
    private var lastPerceptionTime: Long = System.currentTimeMillis()
    private var lastInteractionTime: Long = System.currentTimeMillis()
    private var lastSpeakTime: Long = System.currentTimeMillis()
    private var cachedPerception: Perception? = null

    /**
     * 执行感知，收集所有环境信息
     */
    suspend fun perceive(): Perception = coroutineScope {
        val currentTime = System.currentTimeMillis()

        // 并行收集各项感知信息
        val emotionDeferred = async { emotionService.getCurrentEmotion() }
        val masterDeferred = async { voiceprintManager.getMasterProfile() }
        val recentMessagesDeferred = async { getRecentMessages() }

        val emotion = emotionDeferred.await()
        val masterIdentity = masterDeferred.await()
        val recentMessages = recentMessagesDeferred.await()

        // 分析最近消息
        val hasRecentMessages = recentMessages.isNotEmpty()
        val environmentNoise = calculateEnvironmentNoise(recentMessages)
        // ✅ 简单检测是否包含"小光"，具体是否呼唤由心流LLM智能判断
        val mentionsXiaoguang = recentMessages.any { it.content.contains("小光", ignoreCase = true) }

        // 检测主人和朋友
        val masterPresent = checkMasterPresence(recentMessages, masterIdentity?.personId)
        val friendsPresent = checkFriendsPresence(recentMessages)
        val strangerPresent = checkStrangerPresence(recentMessages)

        // 检测特殊情况
        val isPrivateConversation = detectPrivateConversation(recentMessages)
        val isInClass = detectClassroom(recentMessages)

        // ✅ Phase 1: 分析多说话人情况
        val multiSpeakerInfo = analyzeMultiSpeakerSituation()

        // 时间信息
        val now = LocalDateTime.now()
        val timeOfDay = now.toTimeOfDay()
        val isWorkingHours = now.hour in 9..17

        // 计算时间间隔
        val timeSinceLastInteraction = (currentTime - lastInteractionTime).milliseconds
        val silenceDuration = if (hasRecentMessages) {
            0L.milliseconds
        } else {
            (currentTime - lastInteractionTime).milliseconds
        }

        // 更新时间记录
        if (hasRecentMessages) {
            lastInteractionTime = currentTime
        }
        lastPerceptionTime = currentTime

        // ⭐ 获取主人的性格和关系数据（使用统一社交管理器）
        val masterName = masterIdentity?.personName ?: masterIdentity?.personId ?: "主人"
        val masterPersonality = try {
            val profile = personalityAnalysisUseCase.getPersonalityProfile(masterName)
            if (profile.confidence > 10) {  // 有一定置信度才使用
                profile.getDescription()
            } else null
        } catch (e: Exception) {
            Timber.e(e, "[PerceptionLayer] 获取主人性格失败")
            null
        }

        val relationshipIntimacy = try {
            // ⭐ 新系统：使用 Character Book
            val profile = characterBook.getProfileByName(masterName)
            if (profile != null) {
                val relationship = characterBook.getRelationship(
                    fromCharacterId = "xiaoguang_main",
                    toCharacterId = profile.basicInfo.characterId
                )
                // 主人的intimacy永远是1.0
                relationship?.intimacyLevel ?: 1.0f
            } else {
                // ⚠️ 过渡期：回退到旧系统
                val relation = unifiedSocialManager.getOrCreateRelation(masterName)
                relation.affectionLevel / 100f
            }
        } catch (e: Exception) {
            Timber.e(e, "[PerceptionLayer] 获取关系亲密度失败")
            1.0f  // 主人默认最高亲密度
        }

        // ⭐ 获取当前说话人的性格和关系数据（使用统一社交管理器）
        val currentSpeakerName = if (hasRecentMessages && recentMessages.isNotEmpty()) {
            // 获取最近一条USER消息的说话人
            val lastUserMessage = recentMessages.lastOrNull { it.role == MessageRole.USER }
            lastUserMessage?.let {
                // 优先使用 speakerName，其次 speakerId，最后fallback到主人
                it.speakerName ?: it.speakerId ?: if (masterPresent) masterName else null
            }
        } else null

        val currentSpeakerPersonality = currentSpeakerName?.let { speakerName ->
            try {
                val profile = personalityAnalysisUseCase.getPersonalityProfile(speakerName)
                if (profile.confidence > 10) {
                    profile.getDescription()
                } else null
            } catch (e: Exception) {
                null
            }
        }

        val currentSpeakerIntimacy = currentSpeakerName?.let { speakerName ->
            try {
                // ⭐ 如果当前说话人是主人，直接返回满值亲密度
                if (masterPresent && hasRecentMessages && recentMessages.isNotEmpty()) {
                    val lastUserMessage = recentMessages.lastOrNull { it.role == MessageRole.USER }
                    val lastSpeakerIsMatch = lastUserMessage?.let {
                        it.speakerName == speakerName || it.speakerId == speakerName
                    } ?: false

                    if (lastSpeakerIsMatch && (speakerName == masterName || speakerName == "主人" || speakerName.startsWith("master_"))) {
                        return@let 1.0f  // 主人固定100%亲密度
                    }
                }

                // ⭐ 新系统：使用 Character Book
                val profile = characterBook.getProfileByName(speakerName)
                if (profile != null) {
                    // 如果是主人档案，返回满值
                    if (profile.basicInfo.isMaster) {
                        return@let 1.0f
                    }

                    val relationship = characterBook.getRelationship(
                        fromCharacterId = "xiaoguang_main",
                        toCharacterId = profile.basicInfo.characterId
                    )
                    relationship?.intimacyLevel ?: 0.5f
                } else {
                    // ⚠️ 过渡期：回退到旧系统
                    val relation = unifiedSocialManager.getOrCreateRelation(speakerName)
                    val affection = relation.affectionLevel / 100f
                    // 如果好感度100，认为是主人
                    if (affection >= 1.0f) {
                        1.0f
                    } else {
                        affection
                    }
                }
            } catch (e: Exception) {
                0.5f
            }
        } ?: 0.5f

        val perception = Perception(
            currentTime = now,
            timeSinceLastInteraction = timeSinceLastInteraction,
            silenceDuration = silenceDuration,
            lastSpeakTime = lastSpeakTime,
            currentEmotion = emotion,
            emotionIntensity = 0.5f,  // 默认中等强度，后续可增强
            recentMessages = recentMessages,
            hasRecentMessages = hasRecentMessages,
            environmentNoise = environmentNoise,
            masterPresent = masterPresent,
            friendsPresent = friendsPresent,
            strangerPresent = strangerPresent,
            // ✅ Phase 1: 多说话人检测信息
            hasMultipleSpeakers = multiSpeakerInfo.hasMultipleSpeakers,
            estimatedSpeakerCount = multiSpeakerInfo.estimatedSpeakerCount,
            recentMultiSpeakerUtterances = multiSpeakerInfo.recentMultiSpeakerUtterances,
            masterPersonality = masterPersonality,
            relationshipIntimacy = relationshipIntimacy,
            currentSpeakerName = currentSpeakerName,
            currentSpeakerPersonality = currentSpeakerPersonality,
            currentSpeakerIntimacy = currentSpeakerIntimacy,
            mentionsXiaoguang = mentionsXiaoguang,
            isPrivateConversation = isPrivateConversation,
            isInClass = isInClass,
            timeOfDay = timeOfDay,
            isWorkingHours = isWorkingHours
        )

        cachedPerception = perception
        return@coroutineScope perception
    }

    /**
     * 获取最近一次感知结果
     */
    fun getLastPerception(): Perception? = cachedPerception

    /**
     * 记录小光发言
     */
    fun recordSpeak() {
        lastSpeakTime = System.currentTimeMillis()
        lastInteractionTime = lastSpeakTime
        Timber.d("[PerceptionLayer] 记录发言时间: $lastSpeakTime")
    }

    /**
     * 获取最近的消息（最近5分钟内，真正的"最近"）
     */
    private suspend fun getRecentMessages(): List<Message> {
        return try {
            val currentTime = System.currentTimeMillis()
            val fiveMinutesAgo = currentTime - 5 * 60 * 1000  // 5分钟前

            // 获取最近的消息，然后按时间过滤
            conversationRepository.getRecentMessages(count = 20)
                .filter { it.timestamp > fiveMinutesAgo }
                .also { messages ->
                    if (messages.isEmpty()) {
                        Timber.d("[PerceptionLayer] 最近5分钟内没有消息")
                    } else {
                        Timber.d("[PerceptionLayer] 找到 ${messages.size} 条最近5分钟的消息")
                    }
                }
        } catch (e: Exception) {
            Timber.e(e, "[PerceptionLayer] 获取最近消息失败")
            emptyList()
        }
    }

    /**
     * 计算环境噪音（真实环境感知）
     *
     * ✅ 改进：现在使用实时环境状态，而不是根据消息数量假算
     */
    private fun calculateEnvironmentNoise(messages: List<Message>): Float {
        // ✅ 优先使用实时音频级别
        val audioLevelData = environmentState.audioLevel.value
        if (!audioLevelData.isExpired(maxAgeMs = 2000)) {  // 2秒内的数据有效
            // 实时音频级别已经是 0.0-1.0，直接使用
            return audioLevelData.level
        }

        // ✅ 备选：基于最近30秒的语句数量
        val recentUtterances = environmentState.recentUtterances.value
        val utterancesIn10s = recentUtterances.count { it.getAgeSeconds() < 10 }

        if (utterancesIn10s > 0) {
            return when {
                utterancesIn10s >= 5 -> 0.9f  // 非常活跃
                utterancesIn10s >= 3 -> 0.7f  // 活跃
                utterancesIn10s >= 2 -> 0.5f  // 中等
                else -> 0.3f  // 较少
            }
        }

        // ✅ 再备选：检查VAD状态
        val vadData = environmentState.isVoiceActive.value
        if (!vadData.isExpired(maxAgeMs = 1000) && vadData.isActive) {
            // 正在有人说话
            return 0.6f
        }

        // 完全安静
        return 0.1f
    }

    /**
     * 检测主人是否在场
     *
     * 注意：Message 类没有 speaker 属性，所以通过以下方式判断：
     * 1. 如果有 USER 角色的消息，说明主人在对话
     * 2. 结合 speakerIdentificationService 的身份识别
     */
    private fun checkMasterPresence(messages: List<Message>, masterIdentifier: String?): Boolean {
        // 检查是否有用户消息（主人发言）
        val hasUserMessages = messages.any { it.role == MessageRole.USER }

        // 如果有主人标识符，进一步验证
        if (masterIdentifier != null && hasUserMessages) {
            // 可以通过声纹或其他方式进一步确认
            // 这里假设最近的 USER 消息就是主人
            return true
        }

        return hasUserMessages
    }

    /**
     * 检测朋友在场
     *
     * ✅ 改进：现在使用实时环境状态的在场人员列表
     */
    private suspend fun checkFriendsPresence(messages: List<Message>): List<String> {
        try {
            // ✅ 优先使用实时环境状态
            val presentPeople = environmentState.presentPeople.value
            if (presentPeople.isNotEmpty()) {
                val masterIdentity = voiceprintManager.getMasterProfile()
                val masterId = masterIdentity?.personId

                // 排除主人，获取朋友列表
                val friends = presentPeople
                    .filter { !it.isMaster && it.speakerId != masterId }
                    .mapNotNull { it.speakerName ?: it.speakerId }

                if (friends.isNotEmpty()) {
                    Timber.d("[PerceptionLayer] 检测到朋友在场（实时）: $friends")
                    return friends
                }
            }

            // ✅ 备选：从最近消息中提取
            val userMessages = messages.filter { it.role == MessageRole.USER }

            if (userMessages.size < 2) {
                return emptyList()
            }

            // 从消息中提取所有不同的说话人（排除主人）
            val masterIdentity = voiceprintManager.getMasterProfile()
            val masterId = masterIdentity?.personId

            val speakerIds = userMessages
                .mapNotNull { it.speakerId }
                .distinct()
                .filter { it != masterId }  // 排除主人

            // 如果有其他说话人，认为有朋友在场
            val friends = speakerIds.mapNotNull { speakerId ->
                // 尝试获取说话人名称
                userMessages.find { it.speakerId == speakerId }?.speakerName ?: speakerId
            }

            if (friends.isNotEmpty()) {
                Timber.d("[PerceptionLayer] 检测到朋友在场（从消息）: $friends")
            }

            return friends
        } catch (e: Exception) {
            Timber.e(e, "[PerceptionLayer] 检测朋友在场失败")
            return emptyList()
        }
    }

    /**
     * 检测陌生人在场
     *
     * 通过分析对话内容判断：
     * 1. 提到"陌生人"、"不认识"等关键词
     * 2. 对话中出现礼貌用语（和陌生人说话时更礼貌）
     * 3. 检测到回避性话题
     */
    private suspend fun checkStrangerPresence(messages: List<Message>): Boolean {
        try {
            val strangerKeywords = listOf("陌生人", "不认识", "第一次见", "您好", "请问")

            return messages.any { message ->
                strangerKeywords.any { keyword ->
                    message.content.contains(keyword, ignoreCase = true)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[PerceptionLayer] 检测陌生人在场失败")
            return false
        }
    }

    /**
     * 检测私密对话
     */
    private fun detectPrivateConversation(messages: List<Message>): Boolean {
        val keywords = listOf("隐私", "秘密", "别说", "不要告诉", "保密")
        return messages.any { message ->
            keywords.any { message.content.contains(it) }
        }
    }

    /**
     * 检测是否在上课
     */
    private fun detectClassroom(messages: List<Message>): Boolean {
        val keywords = listOf("上课", "老师", "讲课", "课堂", "教室")
        return messages.any { message ->
            keywords.any { message.content.contains(it) }
        }
    }

    /**
     * ✅ Phase 1: 分析多说话人情况
     * 从 EnvironmentState 的 recentUtterances 中提取多说话人信息
     */
    private fun analyzeMultiSpeakerSituation(): MultiSpeakerInfo {
        val recentUtterances = environmentState.recentUtterances.value

        if (recentUtterances.isEmpty()) {
            return MultiSpeakerInfo(
                hasMultipleSpeakers = false,
                estimatedSpeakerCount = 0,
                recentMultiSpeakerUtterances = 0
            )
        }

        // 统计最近10秒内的多人语句
        val recentMultiSpeaker = recentUtterances.count {
            it.getAgeSeconds() < 10 && it.isMultiSpeaker()
        }

        // 收集所有不同的说话人ID
        val uniqueSpeakers = recentUtterances
            .mapNotNull { it.speakerId }
            .distinct()
            .size

        // 获取最新语句的说话人数量估计
        val latestSpeakerCount = recentUtterances
            .maxByOrNull { it.timestamp }
            ?.speakerCount ?: 1

        // 判断是否有多人场景
        val hasMultiple = recentMultiSpeaker > 0 || uniqueSpeakers > 1 || latestSpeakerCount > 1

        val estimatedCount = maxOf(uniqueSpeakers, latestSpeakerCount)

        if (hasMultiple) {
            Timber.d("[PerceptionLayer] 多说话人场景: 估计$estimatedCount 人, 最近10秒内${recentMultiSpeaker}条多人语句")
        }

        return MultiSpeakerInfo(
            hasMultipleSpeakers = hasMultiple,
            estimatedSpeakerCount = estimatedCount,
            recentMultiSpeakerUtterances = recentMultiSpeaker
        )
    }
}

/**
 * ✅ Phase 1: 多说话人分析结果
 */
private data class MultiSpeakerInfo(
    val hasMultipleSpeakers: Boolean,
    val estimatedSpeakerCount: Int,
    val recentMultiSpeakerUtterances: Int
)
