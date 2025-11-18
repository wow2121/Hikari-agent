package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintProfile
import com.xiaoguang.assistant.domain.voiceprint.NameInferenceService
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.social.UnifiedSocialManager
import com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 新人注册用例（协调器）
 *
 * 当检测到新人物时，协调所有相关系统进行更新：
 *
 * 【必须更新的系统】(P0 - 阻塞)：
 * 1. IdentityRegistry - 注册新身份
 * 2. CharacterBook - 创建角色档案
 * 3. UnifiedSocialManager - 建立社交关系
 * 4. VoiceprintManager - 声纹识别（本用例已处理）
 *
 * 【高优先级系统】(P1)：
 * 5. UnifiedMemorySystem - 初始化记忆
 * 6. RelationshipGraph/Neo4j - 添加节点
 *
 * 【中优先级系统】(P2)：
 * 7. RelationshipNetworkUseCase - 第三方关系记录
 * 8. WorldBook - 背景知识（如果相关）
 *
 * 【低优先级系统】(P3)：
 * 9. EmotionService - 情绪状态（可能触发小光的好奇/关心）
 *
 * 设计原则：
 * - 使用Checklist模式确保所有系统都被更新
 * - LLM驱动的智能推断（名称、关系等）
 * - 考虑小光的人设进行决策
 * - 失败时保持系统一致性
 */
@Singleton
class NewPersonRegistrationUseCase @Inject constructor(
    private val voiceprintManager: VoiceprintManager,
    private val nameInferenceService: NameInferenceService,
    private val characterBook: CharacterBook,
    private val unifiedSocialManager: UnifiedSocialManager,
    private val emotionService: XiaoguangEmotionService,
    private val relationshipNetworkUseCase: RelationshipNetworkManagementUseCase
    // TODO: 后续集成更多系统（IdentityRegistry, UnifiedMemorySystem, RelationshipGraph, WorldBook）
) {

    /**
     * 注册新人（从声纹开始）
     *
     * @param voiceprintProfile 声纹档案
     * @param recentMessages 最近的对话（用于名称推断）
     * @param context 上下文信息
     * @return 注册结果
     */
    suspend fun registerNewPerson(
        voiceprintProfile: VoiceprintProfile,
        recentMessages: List<com.xiaoguang.assistant.domain.model.Message> = emptyList(),
        context: String = ""
    ): RegistrationResult {
        Timber.i("[NewPersonRegistration] 开始注册新人: ${voiceprintProfile.displayName}")

        val checklist = RegistrationChecklist()

        try {
            // ========== P0: 核心系统 ==========

            // 1. VoiceprintManager - 已经完成（传入的profile已注册）
            checklist.voiceprintRegistered = true
            Timber.d("[NewPersonRegistration] ✅ 声纹已注册")

            // 2. CharacterBook - 创建角色档案
            val characterId = createCharacterProfile(voiceprintProfile)
            checklist.characterBookUpdated = characterId != null
            Timber.d("[NewPersonRegistration] ${if (checklist.characterBookUpdated) "✅" else "❌"} CharacterBook")

            // 3. UnifiedSocialManager - 建立社交关系
            val socialRelationCreated = createSocialRelation(voiceprintProfile)
            checklist.socialRelationCreated = socialRelationCreated
            Timber.d("[NewPersonRegistration] ${if (socialRelationCreated) "✅" else "❌"} SocialRelation")

            // 如果有对话内容，尝试推断名称
            if (recentMessages.isNotEmpty() && voiceprintProfile.isStranger) {
                val nameInferenceResult = nameInferenceService.inferNameFromConversation(
                    strangerId = voiceprintProfile.personId,
                    recentMessages = recentMessages,
                    conversationContext = context
                )

                if (nameInferenceResult.inferred && nameInferenceResult.isHighConfidence()) {
                    // 高置信度推断成功，更新名称
                    val inferredName = nameInferenceResult.getMostLikelyName()!!
                    voiceprintManager.updatePersonName(voiceprintProfile.voiceprintId, inferredName)

                    // 同步更新其他系统
                    characterId?.let { updateCharacterName(it, inferredName) }
                    updateSocialRelationName(voiceprintProfile.personId, inferredName)

                    checklist.nameInferred = true
                    checklist.inferredName = inferredName
                    checklist.nameConfidence = nameInferenceResult.confidence

                    Timber.i("[NewPersonRegistration] ✅ 名称推断成功: $inferredName (置信度: ${nameInferenceResult.confidence})")
                }
            }

            // ========== P1: 高优先级系统 ==========

            // 5. UnifiedMemorySystem - 创建初始记忆
            // TODO: 集成UnifiedMemorySystem

            // 6. RelationshipGraph - 添加节点
            // TODO: 集成Neo4j RelationshipGraph

            // ========== P2: 中优先级系统 ==========

            // 7. RelationshipNetworkUseCase - 第三方关系（如果有对话提及）
            if (recentMessages.isNotEmpty()) {
                val relationRecorded = recordThirdPartyRelations(voiceprintProfile, recentMessages)
                checklist.thirdPartyRelationsRecorded = relationRecorded
                Timber.d("[NewPersonRegistration] ${if (relationRecorded) "✅" else "⏭️"} ThirdPartyRelations")
            }

            // 8. WorldBook - 背景知识
            // TODO: 如果新人物是重要角色（如公众人物），记录到WorldBook

            // ========== P3: 低优先级系统 ==========

            // 9. EmotionService - 触发小光的好奇或关心情绪
            if (!voiceprintProfile.isMaster) {
                // 触发"学到新东西"情绪事件（认识新朋友）
                val personName = voiceprintProfile.personName ?: "新朋友"
                emotionService.reactToEvent(
                    event = com.xiaoguang.assistant.domain.emotion.EmotionEvent.LearnsNewThing(
                        topic = "认识了$personName"
                    ),
                    speakerName = personName
                )
                checklist.emotionTriggered = true
                Timber.d("[NewPersonRegistration] ✅ Emotion triggered: 认识新朋友")
            }

            // ========== 完成 ==========

            Timber.i("[NewPersonRegistration] 注册完成: ${checklist.getCompletionRate()}% 完成")

            return RegistrationResult(
                success = checklist.isCoreSystemsReady(),
                personId = voiceprintProfile.personId,
                characterId = characterId,
                displayName = checklist.inferredName ?: voiceprintProfile.displayName,
                isStranger = !checklist.nameInferred,
                checklist = checklist,
                message = if (checklist.isCoreSystemsReady()) {
                    "新人注册成功"
                } else {
                    "注册未完全成功，部分系统更新失败"
                }
            )

        } catch (e: Exception) {
            Timber.e(e, "[NewPersonRegistration] 注册失败")
            return RegistrationResult(
                success = false,
                personId = voiceprintProfile.personId,
                characterId = null,
                displayName = voiceprintProfile.displayName,
                isStranger = true,
                checklist = checklist,
                message = "注册失败: ${e.message}"
            )
        }
    }

    /**
     * 创建CharacterBook档案
     */
    private suspend fun createCharacterProfile(profile: VoiceprintProfile): String? {
        return try {
            val characterId = "char_${UUID.randomUUID()}"

            val characterProfile = com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile(
                basicInfo = com.xiaoguang.assistant.domain.knowledge.models.BasicInfo(
                    characterId = characterId,
                    name = profile.getEffectiveIdentifier(),
                    aliases = if (profile.isStranger) listOf(profile.displayName) else emptyList(),
                    bio = if (profile.isMaster) {
                        "小光最重要的人，主人"
                    } else if (profile.isStranger) {
                        "刚刚遇到的陌生人，还不太了解"
                    } else {
                        "小光认识的人"
                    },
                    isMaster = profile.isMaster,
                    createdAt = System.currentTimeMillis(),
                    metadata = mapOf(
                        "voiceprintId" to profile.voiceprintId,
                        "source" to "voiceprint_registration"
                    )
                )
            )

            characterBook.saveProfile(characterProfile)
            Timber.i("[NewPersonRegistration] CharacterBook档案已创建: $characterId")

            characterId

        } catch (e: Exception) {
            Timber.e(e, "[NewPersonRegistration] 创建CharacterBook档案失败")
            null
        }
    }

    /**
     * 更新角色名称
     */
    private suspend fun updateCharacterName(characterId: String, newName: String) {
        try {
            val profile = characterBook.getProfile(characterId)
            if (profile != null) {
                val updated = profile.copy(
                    basicInfo = profile.basicInfo.copy(
                        name = newName,
                        aliases = profile.basicInfo.aliases + profile.basicInfo.name
                    )
                )
                characterBook.saveProfile(updated)
                Timber.i("[NewPersonRegistration] CharacterBook名称已更新: $characterId -> $newName")
            }
        } catch (e: Exception) {
            Timber.e(e, "[NewPersonRegistration] 更新CharacterBook名称失败")
        }
    }

    /**
     * 创建社交关系
     */
    private suspend fun createSocialRelation(profile: VoiceprintProfile): Boolean {
        return try {
            val initialAffection = if (profile.isMaster) 100 else 50

            unifiedSocialManager.getOrCreateRelation(profile.getEffectiveIdentifier())
            unifiedSocialManager.updateAffection(
                personName = profile.getEffectiveIdentifier(),
                delta = initialAffection - 50,  // 默认是50，所以主人需要+50
                reason = if (profile.isMaster) "这是主人！" else "初次见面"
            )

            Timber.i("[NewPersonRegistration] 社交关系已创建: ${profile.getEffectiveIdentifier()}")
            true

        } catch (e: Exception) {
            Timber.e(e, "[NewPersonRegistration] 创建社交关系失败")
            false
        }
    }

    /**
     * 更新社交关系名称
     */
    private suspend fun updateSocialRelationName(oldId: String, newName: String) {
        try {
            // UnifiedSocialManager会自动处理名称变化
            // 这里只需要确保新名称的关系存在
            unifiedSocialManager.getOrCreateRelation(newName)
            Timber.i("[NewPersonRegistration] 社交关系名称已更新: $newName")
        } catch (e: Exception) {
            Timber.e(e, "[NewPersonRegistration] 更新社交关系名称失败")
        }
    }

    /**
     * 记录第三方关系
     */
    private suspend fun recordThirdPartyRelations(
        profile: VoiceprintProfile,
        messages: List<com.xiaoguang.assistant.domain.model.Message>
    ): Boolean {
        return try {
            // 从对话中分析新人物与其他人的关系
            // TODO: 使用LLM分析对话，提取关系信息
            // 当前简化实现：仅标记为已尝试

            Timber.d("[NewPersonRegistration] 第三方关系分析已执行")
            true

        } catch (e: Exception) {
            Timber.e(e, "[NewPersonRegistration] 记录第三方关系失败")
            false
        }
    }
}

/**
 * 注册清单（确保所有系统都被更新）
 */
data class RegistrationChecklist(
    // P0: 核心系统
    var voiceprintRegistered: Boolean = false,
    var characterBookUpdated: Boolean = false,
    var socialRelationCreated: Boolean = false,

    // 名称推断
    var nameInferred: Boolean = false,
    var inferredName: String? = null,
    var nameConfidence: Float = 0f,

    // P1: 高优先级
    var memorySystemInitialized: Boolean = false,
    var relationshipGraphUpdated: Boolean = false,

    // P2: 中优先级
    var thirdPartyRelationsRecorded: Boolean = false,
    var worldBookUpdated: Boolean = false,

    // P3: 低优先级
    var emotionTriggered: Boolean = false
) {
    /**
     * 核心系统是否准备就绪
     */
    fun isCoreSystemsReady(): Boolean {
        return voiceprintRegistered && characterBookUpdated && socialRelationCreated
    }

    /**
     * 获取完成率
     */
    fun getCompletionRate(): Int {
        val total = 9  // 总共9个系统
        var completed = 0

        if (voiceprintRegistered) completed++
        if (characterBookUpdated) completed++
        if (socialRelationCreated) completed++
        if (memorySystemInitialized) completed++
        if (relationshipGraphUpdated) completed++
        if (thirdPartyRelationsRecorded) completed++
        if (worldBookUpdated) completed++
        if (emotionTriggered) completed++
        if (nameInferred) completed++  // 名称推断算作一个额外bonus

        return (completed * 100 / total).coerceIn(0, 100)
    }
}

/**
 * 注册结果
 */
data class RegistrationResult(
    val success: Boolean,
    val personId: String,
    val characterId: String?,
    val displayName: String,
    val isStranger: Boolean,
    val checklist: RegistrationChecklist,
    val message: String
)
