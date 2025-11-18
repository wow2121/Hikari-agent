package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.domain.model.EnhancedSocialRelation
import com.xiaoguang.assistant.domain.model.PersonalityProfile
import com.xiaoguang.assistant.domain.usecase.*
// TODO: 重新实现声纹识别系统后恢复
// import com.xiaoguang.assistant.domain.voiceprint.VoiceprintRecognitionUseCase
import com.xiaoguang.assistant.data.local.database.entity.RelationshipEventEntity
import com.xiaoguang.assistant.data.local.database.entity.PersonTagEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 增强的关系管理器
 *
 * 统一管理所有人际关系功能：
 * 1. 基础好感度
 * 2. 关系事件
 * 3. 动态标签
 * 4. 性格分析
 * 5. 关系网络
 * 6. 变化监听
 * 7. 主人身份自动检测（安全锁定）
 */
@Singleton
class EnhancedRelationshipManager @Inject constructor(
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,
    private val relationshipEventManagementUseCase: RelationshipEventManagementUseCase,
    private val personTagManagementUseCase: PersonTagManagementUseCase,
    private val personalityAnalysisUseCase: PersonalityAnalysisUseCase,
    private val relationshipNetworkManagementUseCase: RelationshipNetworkManagementUseCase,
    private val relationshipChangeObserver: RelationshipChangeObserver
    // TODO: 重新实现声纹识别系统后恢复
    // private val voiceprintRecognitionUseCase: VoiceprintRecognitionUseCase
) {

    /**
     * 完整的人物档案
     */
    data class CompletePersonProfile(
        val enhancedRelation: EnhancedSocialRelation,
        val recentEvents: List<RelationshipEventEntity>,
        val tags: List<PersonTagEntity>,
        val personality: PersonalityProfile,
        val networkConnections: List<String>
    )

    /**
     * 获取某人的完整档案
     */
    suspend fun getCompleteProfile(personName: String, isMaster: Boolean = false): CompletePersonProfile {
        // 基础关系（使用新系统UnifiedSocialManager）
        val unifiedRelation = unifiedSocialManager.getOrCreateRelation(personName)
        val enhancedRelation = EnhancedSocialRelation.fromUnifiedRelation(
            unifiedRelation = unifiedRelation,
            recentAffectionDelta = 0
        )

        // 关系事件
        val recentEvents = relationshipEventManagementUseCase.getImportantEvents(personName)

        // 标签
        val tags = personTagManagementUseCase.getHighConfidenceTags(personName)

        // 性格
        val personality = personalityAnalysisUseCase.getPersonalityProfile(personName)

        // 网络关系
        val networkRelations = relationshipNetworkManagementUseCase.getPersonRelations(personName)
        val connections = networkRelations.map {
            if (it.personA == personName) it.personB else it.personA
        }

        return CompletePersonProfile(
            enhancedRelation = enhancedRelation,
            recentEvents = recentEvents,
            tags = tags,
            personality = personality,
            networkConnections = connections
        )
    }

    /**
     * 处理对话并更新所有相关信息
     *
     * 这是核心方法，在每次对话后调用
     */
    suspend fun processConversation(
        personName: String,
        conversationText: String,
        messageLength: Int,
        isMaster: Boolean = false
    ) {
        // 1. 记录互动（通过unifiedSocialManager）
        unifiedSocialManager.recordInteraction(personName)

        // 2. 分析性格
        personalityAnalysisUseCase.analyzeFromConversation(
            personName = personName,
            conversationText = conversationText,
            messageLength = messageLength
        )

        // 3. 推断标签
        personTagManagementUseCase.inferTagsFromConversation(
            personName = personName,
            conversationText = conversationText
        )

        // 4. 推断人际网络
        val knownPeople = unifiedSocialManager.getAllRelations().map { it.personName }
        relationshipNetworkManagementUseCase.inferRelationFromConversation(
            conversationText = conversationText,
            knownPeople = knownPeople
        )
    }

    /**
     * 更新好感度并触发变化检测（✅ 自动检测主人身份）
     */
    suspend fun updateAffectionWithChangeDetection(
        personName: String,
        delta: Int,
        reason: String,
        isMaster: Boolean = false
    ): Int {
        // ✅ 自动检测主人身份（安全加固）
        val actualIsMaster = checkIfMaster(personName) || isMaster

        if (actualIsMaster) {
            Timber.d("[RelationshipManager] 检测到主人身份: $personName，好感度锁定在100")
        }

        // 获取旧值
        val oldRelation = unifiedSocialManager.getOrCreateRelation(personName)
        val oldAffection = oldRelation.affectionLevel

        // 更新好感度（主人身份会被底层拦截）
        val newAffection = unifiedSocialManager.updateAffection(
            personName = personName,
            delta = delta,
            reason = reason
        )

        // 触发变化检测
        relationshipChangeObserver.checkAndTriggerChanges(
            personName = personName,
            oldAffection = oldAffection,
            newAffection = newAffection,
            isMaster = actualIsMaster  // ✅ 使用自动检测的结果
        )

        return newAffection
    }

    /**
     * 检查某人是否是主人（多重验证）
     */
    private suspend fun checkIfMaster(personName: String): Boolean {
        return try {
            // 方法1：通过UnifiedSocialManager检查
            val isMasterByManager = unifiedSocialManager.isMaster(personName)
            if (isMasterByManager) {
                return true
            }

            // 方法2：通过声纹识别检查
            // TODO: 重新实现声纹识别系统后恢复
            // val masterVoiceprint = voiceprintRecognitionUseCase.getMasterVoiceprint()
            // if (masterVoiceprint != null) {
            //     val isMasterByVoiceprint = masterVoiceprint.personName == personName ||
            //             masterVoiceprint.personIdentifier == personName
            //
            //     if (isMasterByVoiceprint) {
            //         return true
            //     }
            // }

            false
        } catch (e: Exception) {
            Timber.e(e, "[RelationshipManager] 检查主人身份失败")
            false
        }
    }

    /**
     * 生成某人的完整描述（用于AI参考）
     */
    suspend fun generateCompleteDescription(personName: String, isMaster: Boolean = false): String {
        val profile = getCompleteProfile(personName, isMaster)

        return buildString {
            appendLine("=== ${personName}的完整档案 ===")
            appendLine()

            // 基础信息
            appendLine("【基础关系】")
            appendLine("关系层级: ${profile.enhancedRelation.relationshipLevel.displayName}")
            appendLine("好感度: ${profile.enhancedRelation.affectionLevel}/100")
            appendLine("对TA的态度: ${profile.enhancedRelation.attitude.displayName}")
            if (isMaster) {
                appendLine("⭐ 这是主人！最重要的人！")
            }
            appendLine()

            // 性格
            if (profile.personality.confidence > 30) {
                appendLine("【性格特点】")
                appendLine(profile.personality.getDescription())
                appendLine("(置信度: ${profile.personality.confidence}%)")
                appendLine()
            }

            // 标签
            if (profile.tags.isNotEmpty()) {
                appendLine("【特征标签】")
                profile.tags.take(5).forEach { tag ->
                    appendLine("· ${tag.tag}")
                }
                appendLine()
            }

            // 重要事件
            if (profile.recentEvents.isNotEmpty()) {
                appendLine("【重要回忆】")
                profile.recentEvents.take(3).forEach { event ->
                    appendLine("· ${event.description}")
                }
                appendLine()
            }

            // 人际网络
            if (profile.networkConnections.isNotEmpty()) {
                appendLine("【人际关系】")
                profile.networkConnections.take(5).forEach { connection ->
                    appendLine("· 认识 $connection")
                }
                appendLine()
            }
        }
    }

    /**
     * 定期维护任务（应定期调用，如每天一次）
     */
    suspend fun performMaintenance() {
        // 检查长时间未互动
        relationshipChangeObserver.checkLongTimeNoInteraction()

        // 清理低置信度标签
        personTagManagementUseCase.cleanupLowConfidenceTags()
    }
}
