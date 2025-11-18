package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.data.local.database.entity.ConversationEmbeddingEntity
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话Embedding管理用例
 * 负责自动为对话生成和存储embeddings
 */
@Singleton
class ConversationEmbeddingUseCase @Inject constructor(
    private val generateEmbeddingUseCase: GenerateEmbeddingUseCase,
    private val embeddingRepository: EmbeddingRepository,
    private val appPreferences: AppPreferences
) {

    /**
     * 为单条消息生成并存储embedding
     *
     * @param message 消息对象
     * @param conversationId 对话ID
     * @param importance 重要性分数(0.0-1.0),默认自动计算
     * @return 存储的embedding ID
     */
    suspend fun processMessage(
        message: Message,
        conversationId: String,
        importance: Float? = null
    ): Result<Long> {
        return try {
            // 检查是否启用自动生成
            val autoGenerate = appPreferences.autoGenerateEmbeddings.first()
            if (!autoGenerate) {
                Timber.d("自动生成embeddings未启用")
                return Result.failure(Exception("自动生成embeddings未启用"))
            }

            // 检查是否已存在
            val existing = embeddingRepository.getConversationEmbeddingByMessageId(message.id)
            if (existing != null) {
                Timber.d("消息 ${message.id} 的embedding已存在")
                return Result.success(existing.id)
            }

            // 生成embedding
            val embeddingResult = generateEmbeddingUseCase.generateEmbedding(message.content)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull()!!)
            }

            val embedding = embeddingResult.getOrNull()!!

            // 计算重要性分数
            val importance = importance ?: calculateImportance(message)

            // 创建实体
            val entity = ConversationEmbeddingEntity(
                conversationId = conversationId,
                messageId = message.id,
                content = message.content,
                embeddingVector = embedding.vector,
                dimension = embedding.dimension,
                modelName = embedding.model,
                timestamp = message.timestamp,
                role = message.role.name.lowercase(),
                importance = importance
            )

            // 存储
            val id = embeddingRepository.saveConversationEmbedding(entity)
            Timber.d("成功存储消息embedding, ID: $id, 重要性: $importance")

            Result.success(id)

        } catch (e: Exception) {
            Timber.e(e, "处理消息embedding失败")
            Result.failure(e)
        }
    }

    /**
     * 批量处理多条消息
     *
     * @param messages 消息列表
     * @param conversationId 对话ID
     * @return 成功存储的数量
     */
    suspend fun processMessages(
        messages: List<Message>,
        conversationId: String
    ): Result<Int> {
        return try {
            val autoGenerate = appPreferences.autoGenerateEmbeddings.first()
            if (!autoGenerate) {
                return Result.failure(Exception("自动生成embeddings未启用"))
            }

            var successCount = 0

            for (message in messages) {
                val result = processMessage(message, conversationId)
                if (result.isSuccess) {
                    successCount++
                }
            }

            Timber.d("批量处理完成: $successCount / ${messages.size} 条消息")
            Result.success(successCount)

        } catch (e: Exception) {
            Timber.e(e, "批量处理消息失败")
            Result.failure(e)
        }
    }

    /**
     * 清理旧的embeddings
     *
     * @param retentionDays 保留天数
     * @return 删除的数量
     */
    suspend fun cleanupOldEmbeddings(retentionDays: Int? = null): Result<Int> {
        return try {
            val days = retentionDays ?: appPreferences.dataRetentionDays.first()
            val beforeTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)

            // 先归档
            val archivedCount = embeddingRepository.archiveConversationEmbeddingsOlderThan(beforeTime)
            Timber.d("归档了 $archivedCount 条旧embedding")

            // 再删除已归档的
            val deletedCount = embeddingRepository.deleteArchivedConversationEmbeddings()
            Timber.d("删除了 $deletedCount 条已归档embedding")

            Result.success(deletedCount)

        } catch (e: Exception) {
            Timber.e(e, "清理旧embeddings失败")
            Result.failure(e)
        }
    }

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): Result<EmbeddingStatistics> {
        return try {
            val stats = embeddingRepository.getConversationEmbeddingStats()

            Result.success(
                EmbeddingStatistics(
                    totalActive = stats.activeCount,
                    totalArchived = stats.archivedCount,
                    averageImportance = stats.averageImportance
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "获取统计信息失败")
            Result.failure(e)
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 计算消息的重要性分数
     * 基于消息长度、内容特征等因素
     */
    private fun calculateImportance(message: Message): Float {
        var score = 0.5f // 基础分数

        val content = message.content

        // 长度因素(较长的消息可能更重要)
        val length = content.length
        when {
            length > 500 -> score += 0.2f
            length > 200 -> score += 0.1f
            length < 20 -> score -= 0.1f
        }

        // 关键词检测
        val importantKeywords = listOf(
            "重要", "记住", "提醒", "待办", "任务", "会议", "约定",
            "important", "remember", "remind", "todo", "task", "meeting"
        )

        if (importantKeywords.any { content.contains(it, ignoreCase = true) }) {
            score += 0.2f
        }

        // 问题检测(问题通常更重要)
        if (content.contains("?") || content.contains("？") ||
            content.startsWith("什么") || content.startsWith("如何") ||
            content.startsWith("为什么") || content.startsWith("怎么")
        ) {
            score += 0.1f
        }

        // 用户消息比助手回复稍微重要一些
        if (message.role.name == "USER") {
            score += 0.05f
        }

        return score.coerceIn(0f, 1f)
    }
}

/**
 * Embedding统计信息
 */
data class EmbeddingStatistics(
    val totalActive: Int,
    val totalArchived: Int,
    val averageImportance: Float
)
