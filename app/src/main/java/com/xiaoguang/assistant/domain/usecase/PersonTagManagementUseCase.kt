package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.data.local.database.dao.PersonTagDao
import com.xiaoguang.assistant.data.local.database.entity.PersonTagEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 人物标签管理UseCase
 * 负责管理人物的动态标签
 */
@Singleton
class PersonTagManagementUseCase @Inject constructor(
    private val personTagDao: PersonTagDao,
    private val gson: Gson = Gson()
) {

    /**
     * 添加或更新标签
     */
    suspend fun addOrUpdateTag(
        personName: String,
        tag: String,
        confidence: Float,
        source: String,
        evidence: String? = null
    ): Long {
        // 检查是否已存在
        val existing = personTagDao.getTagByName(personName, tag)

        return if (existing != null) {
            // 已存在，增加观察次数和置信度
            val newConfidence = (existing.confidence + confidence * 0.3f).coerceAtMost(1.0f)

            // 更新证据
            val updatedEvidence = if (evidence != null) {
                if (existing.evidence.isNotBlank()) {
                    "${existing.evidence}; $evidence"
                } else {
                    evidence
                }
            } else {
                existing.evidence
            }

            val updated = existing.copy(
                confidence = newConfidence,
                lastUpdated = System.currentTimeMillis(),
                evidence = updatedEvidence
            )

            personTagDao.updateTag(updated)
            Timber.d("更新标签: $personName - $tag (置信度: $newConfidence)")
            existing.id
        } else {
            // 不存在，创建新标签
            val evidenceText = evidence ?: ""

            val newTag = PersonTagEntity(
                personName = personName,
                tag = tag,
                confidence = confidence.coerceIn(0f, 1f),
                source = source,
                evidence = evidenceText
            )

            val id = personTagDao.insertTag(newTag)
            Timber.d("添加标签: $personName - $tag (置信度: $confidence)")
            id
        }
    }

    /**
     * 从用户提及中添加标签
     */
    suspend fun addTagFromUserMention(
        personName: String,
        tag: String,
        evidence: String
    ): Long {
        return addOrUpdateTag(
            personName = personName,
            tag = tag,
            confidence = 0.9f,  // 用户明确提到，置信度高
            source = "user_mentioned",
            evidence = evidence
        )
    }

    /**
     * 从AI推断添加标签
     */
    suspend fun addTagFromAiInference(
        personName: String,
        tag: String,
        confidence: Float = 0.6f,
        evidence: String? = null
    ): Long {
        return addOrUpdateTag(
            personName = personName,
            tag = tag,
            confidence = confidence,
            source = PersonTagEntity.SOURCE_AI_INFERRED,
            evidence = evidence
        )
    }

    /**
     * 从行为观察添加标签
     */
    suspend fun addTagFromBehavior(
        personName: String,
        tag: String,
        behavior: String
    ): Long {
        return addOrUpdateTag(
            personName = personName,
            tag = tag,
            confidence = 0.7f,
            source = PersonTagEntity.SOURCE_BEHAVIOR_OBSERVED,
            evidence = behavior
        )
    }

    /**
     * 从话题偏好添加标签
     */
    suspend fun addTagFromTopicPreference(
        personName: String,
        topic: String,
        preference: String
    ): Long {
        val tag = "喜欢$topic"
        return addOrUpdateTag(
            personName = personName,
            tag = tag,
            confidence = 0.6f,
            source = PersonTagEntity.SOURCE_TOPIC_PREFERENCE,
            evidence = preference
        )
    }

    /**
     * 获取某人的所有标签
     */
    suspend fun getPersonTags(personName: String): List<PersonTagEntity> {
        return personTagDao.getTagsByPerson(personName)
    }

    /**
     * 获取某人的高置信度标签
     */
    suspend fun getHighConfidenceTags(personName: String): List<PersonTagEntity> {
        return personTagDao.getHighConfidenceTags(personName)
    }

    /**
     * 生成标签摘要
     */
    suspend fun generateTagSummary(personName: String): String {
        val tags = getHighConfidenceTags(personName)

        if (tags.isEmpty()) {
            return "还不太了解这个人呢..."
        }

        return buildString {
            appendLine("【${personName}的特点】")
            tags.take(5).forEach { tag ->
                val confidenceDesc = when {
                    tag.confidence >= 0.9f -> "（很确定）"
                    tag.confidence >= 0.7f -> "（比较确定）"
                    else -> "（可能）"
                }
                appendLine("· ${tag.tag} $confidenceDesc")
            }
        }
    }

    /**
     * 删除低置信度标签
     */
    suspend fun cleanupLowConfidenceTags(threshold: Float = 0.3f) {
        personTagDao.deleteLowConfidenceTags(threshold)
        Timber.d("清理低置信度标签 (阈值: $threshold)")
    }

    /**
     * 批量添加标签
     */
    suspend fun addTagsBatch(
        personName: String,
        tags: List<Pair<String, Float>>,  // tag to confidence
        source: String,
        evidence: String? = null
    ) {
        tags.forEach { (tag, confidence) ->
            addOrUpdateTag(
                personName = personName,
                tag = tag,
                confidence = confidence,
                source = source,
                evidence = evidence
            )
        }
    }

    /**
     * 根据对话内容智能推断标签
     */
    suspend fun inferTagsFromConversation(
        personName: String,
        conversationText: String
    ) {
        // 性格类标签推断
        when {
            conversationText.contains("哈哈") || conversationText.contains("笑") -> {
                addTagFromBehavior(personName, "开朗", "对话中经常笑")
            }
            conversationText.length > 200 -> {
                addTagFromBehavior(personName, "话多", "对话内容丰富")
            }
            conversationText.contains("谢谢") || conversationText.contains("感谢") -> {
                addTagFromBehavior(personName, "有礼貌", "经常说谢谢")
            }
        }

        // 兴趣类标签推断
        val interests = mapOf(
            "动漫" to listOf("动漫", "漫画", "番剧", "二次元"),
            "游戏" to listOf("游戏", "打游戏", "玩游戏"),
            "音乐" to listOf("音乐", "唱歌", "歌曲"),
            "运动" to listOf("运动", "跑步", "健身"),
            "美食" to listOf("美食", "好吃", "吃饭")
        )

        interests.forEach { (interest, keywords) ->
            if (keywords.any { conversationText.contains(it) }) {
                addTagFromTopicPreference(personName, interest, "对话中提到$interest")
            }
        }
    }
}
