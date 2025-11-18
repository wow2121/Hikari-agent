package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.data.local.database.dao.RelationshipEventDao
import com.xiaoguang.assistant.data.local.database.entity.RelationshipEventEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系事件管理UseCase
 * 负责记录和管理与每个人之间发生的重要事件
 */
@Singleton
class RelationshipEventManagementUseCase @Inject constructor(
    private val relationshipEventDao: RelationshipEventDao,
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager
) {

    /**
     * 记录事件
     */
    suspend fun recordEvent(
        personName: String,
        eventType: String,
        description: String,
        affectionImpact: Int = 0,
        importance: Int = 3,
        tags: List<String> = emptyList(),
        relatedMemoryId: Long? = null
    ): Long {
        val event = RelationshipEventEntity(
            personName = personName,
            eventType = eventType,
            description = description,
            affectionImpact = affectionImpact,
            importance = importance,
            timestamp = System.currentTimeMillis(),
            tags = com.google.gson.Gson().toJson(tags),
            relatedMemoryId = relatedMemoryId
        )

        val id = relationshipEventDao.insertEvent(event)

        // 如果事件影响好感度，同步更新
        if (affectionImpact != 0) {
            unifiedSocialManager.updateAffection(
                personName = personName,
                delta = affectionImpact,
                reason = description
            )
        }

        Timber.d("记录事件: $personName - $eventType ($description)")
        return id
    }

    /**
     * 记录初次见面
     */
    suspend fun recordFirstMeet(personName: String, description: String = "初次见面"): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_FIRST_MEET,
            description = description,
            affectionImpact = 0,
            importance = 5  // 初次见面很重要
        )
    }

    /**
     * 记录帮助事件
     */
    suspend fun recordHelp(
        personName: String,
        isReceived: Boolean,
        description: String
    ): Long {
        return recordEvent(
            personName = personName,
            eventType = if (isReceived) {
                RelationshipEventEntity.TYPE_HELP_RECEIVED
            } else {
                RelationshipEventEntity.TYPE_HELP_GIVEN
            },
            description = description,
            affectionImpact = if (isReceived) 5 else 3,  // 得到帮助好感度+5，给予帮助+3
            importance = 4
        )
    }

    /**
     * 记录冲突
     */
    suspend fun recordConflict(personName: String, description: String): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_CONFLICT,
            description = description,
            affectionImpact = -10,  // 冲突降低好感度
            importance = 4
        )
    }

    /**
     * 记录和解
     */
    suspend fun recordReconciliation(personName: String, description: String): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_RECONCILIATION,
            description = description,
            affectionImpact = 8,  // 和解提升好感度
            importance = 4
        )
    }

    /**
     * 记录庆祝事件（如生日）
     */
    suspend fun recordCelebration(
        personName: String,
        description: String,
        celebrationType: String = "生日"
    ): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_CELEBRATION,
            description = description,
            affectionImpact = 5,
            importance = 4,
            tags = listOf(celebrationType, "纪念日")
        )
    }

    /**
     * 记录深度对话
     */
    suspend fun recordDeepConversation(personName: String, topic: String): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_DEEP_CONVERSATION,
            description = "进行了关于「$topic」的深入对话",
            affectionImpact = 3,
            importance = 3,
            tags = listOf("深度对话", topic)
        )
    }

    /**
     * 记录里程碑（关系升级）
     */
    suspend fun recordMilestone(
        personName: String,
        oldLevel: String,
        newLevel: String
    ): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_MILESTONE,
            description = "关系升级：从「$oldLevel」变成了「$newLevel」",
            affectionImpact = 0,
            importance = 5,
            tags = listOf("里程碑", "关系升级", oldLevel, newLevel)
        )
    }

    /**
     * 记录长时间未见
     */
    suspend fun recordLongAbsence(personName: String, days: Int): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_LONG_ABSENCE,
            description = "已经${days}天没见到了",
            affectionImpact = 0,
            importance = 2
        )
    }

    /**
     * 记录重逢
     */
    suspend fun recordReturn(personName: String, description: String = "再次见面"): Long {
        return recordEvent(
            personName = personName,
            eventType = RelationshipEventEntity.TYPE_RETURN,
            description = description,
            affectionImpact = 5,  // 重逢提升好感
            importance = 3
        )
    }

    /**
     * 获取某人的事件历史
     */
    suspend fun getEventHistory(personName: String): List<RelationshipEventEntity> {
        return relationshipEventDao.getEventsByPerson(personName)
    }

    /**
     * 获取某人的重要事件
     */
    suspend fun getImportantEvents(personName: String): List<RelationshipEventEntity> {
        return relationshipEventDao.getImportantEvents(personName)
    }

    /**
     * 获取最近的事件
     */
    suspend fun getRecentEvents(limit: Int = 20): List<RelationshipEventEntity> {
        return relationshipEventDao.getRecentEvents(limit)
    }

    /**
     * 生成事件摘要（用于回忆）
     */
    suspend fun generateEventSummary(personName: String): String {
        val events = getImportantEvents(personName)

        if (events.isEmpty()) {
            return "还没有什么特别的回忆呢..."
        }

        return buildString {
            appendLine("【与${personName}的回忆】")
            events.take(5).forEach { event ->
                val timeAgo = getTimeAgo(event.timestamp)
                appendLine("· $timeAgo: ${event.description}")
            }
        }
    }

    /**
     * 计算时间差描述
     */
    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val days = diff / (1000 * 60 * 60 * 24)

        return when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days < 7 -> "${days}天前"
            days < 30 -> "${days / 7}周前"
            days < 365 -> "${days / 30}个月前"
            else -> "${days / 365}年前"
        }
    }
}
