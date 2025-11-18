package com.xiaoguang.assistant.domain.memory

import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.WorldBook
import com.xiaoguang.assistant.domain.memory.models.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一记忆系统
 *
 * 整合三套记忆子系统：
 * 1. WorldBook - 世界观知识
 * 2. CharacterBook - 角色档案和记忆
 * 3. MemoryCore - 事件记忆和知识事实
 *
 * 提供统一的记忆查询、保存和管理接口
 */
@Singleton
class UnifiedMemorySystem @Inject constructor(
    private val worldBook: WorldBook,
    private val characterBook: CharacterBook,
    private val memoryCore: MemoryCore
) {

    /**
     * 查询记忆（多维检索）
     *
     * 自动路由到合适的子系统
     */
    suspend fun queryMemories(query: MemoryQuery): List<RankedMemory> {
        return try {
            Timber.d("[UnifiedMemorySystem] 查询记忆: $query")

            // 主要从MemoryCore查询事件记忆
            val coreResults = memoryCore.queryMemories(query)

            Timber.d("[UnifiedMemorySystem] 从MemoryCore获取 ${coreResults.size} 条记忆")

            coreResults

        } catch (e: Exception) {
            Timber.e(e, "[UnifiedMemorySystem] 查询记忆失败")
            emptyList()
        }
    }

    /**
     * 保存记忆
     *
     * 根据记忆类型自动路由到合适的子系统
     */
    suspend fun saveMemory(memory: Memory): Result<Unit> {
        return try {
            when (memory.category) {
                // 角色相关记忆 → CharacterBook
                MemoryCategory.PERSON -> {
                    if (memory.relatedCharacters.isNotEmpty()) {
                        saveToCharacterBook(memory)
                    } else {
                        memoryCore.saveMemory(memory)
                    }
                }

                // 世界观知识 → WorldBook（未来扩展）
                MemoryCategory.SEMANTIC, MemoryCategory.FACT -> {
                    // 目前仍保存到MemoryCore
                    // 未来可以抽象知识自动添加到WorldBook
                    memoryCore.saveMemory(memory)
                }

                // 其他记忆 → MemoryCore
                else -> {
                    memoryCore.saveMemory(memory)
                }
            }

            Timber.d("[UnifiedMemorySystem] 保存记忆成功: ${memory.content.take(50)}")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[UnifiedMemorySystem] 保存记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 获取梦境素材
     *
     * 专为DreamSystemEngine设计
     * 返回高情感强度、多样化的最近记忆
     */
    suspend fun getDreamMaterial(
        recency: java.time.Duration = java.time.Duration.ofHours(24),
        emotionRange: ClosedFloatingPointRange<Float> = 0.5f..1.0f,
        diversified: Boolean = true,
        limit: Int = 10
    ): List<Memory> {
        val query = MemoryQuery(
            temporal = TemporalQuery.RecentHours(recency.toHours().toInt()),
            emotion = EmotionQuery.IntenseRange(emotionRange),
            diversified = diversified,
            limit = limit
        )

        return queryMemories(query).map { it.memory }
    }

    /**
     * 回忆记忆
     *
     * 专为MemoryRecallEngine设计
     * 返回过去的、重要的、有情感的记忆
     */
    suspend fun recallMemories(
        temporal: TemporalQuery,
        minImportance: Float = 0.6f,
        withEmotion: Boolean = true,
        limit: Int = 5
    ): List<Memory> {
        val query = MemoryQuery(
            temporal = temporal,
            minImportance = minImportance,
            emotion = if (withEmotion) EmotionQuery.AnyPositive else null,
            limit = limit
        )

        return queryMemories(query).map { it.memory }
    }

    /**
     * 根据ID获取记忆
     */
    suspend fun getMemoryById(id: String): Memory? {
        return memoryCore.getMemoryById(id)
    }

    /**
     * 强化记忆（访问时自动调用）
     */
    suspend fun reinforceMemory(id: String): Result<Unit> {
        return memoryCore.reinforceMemory(id)
    }

    /**
     * 标记为遗忘
     */
    suspend fun forgetMemory(id: String): Result<Unit> {
        return memoryCore.forgetMemory(id)
    }

    /**
     * 获取记忆统计
     */
    suspend fun getStatistics(): MemoryStatistics {
        return memoryCore.getStatistics()
    }

    /**
     * 获取记忆总数
     */
    suspend fun getMemoryCount(): Int {
        return memoryCore.getMemoryCount()
    }

    /**
     * 计算记忆强度
     */
    fun calculateMemoryStrength(memory: Memory, now: Long = System.currentTimeMillis()): Float {
        return memoryCore.calculateMemoryStrength(memory, now)
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 保存到CharacterBook
     */
    private suspend fun saveToCharacterBook(memory: Memory): Result<Unit> {
        return try {
            for (characterId in memory.relatedCharacters) {
                val characterMemory = com.xiaoguang.assistant.domain.knowledge.models.CharacterMemory(
                    memoryId = "memory_${memory.id}",
                    characterId = characterId,
                    category = mapToCharacterMemoryCategory(memory.category),
                    content = memory.content,
                    importance = memory.importance,
                    emotionalValence = memory.emotionalValence,
                    emotionTag = memory.emotionTag,
                    emotionIntensity = memory.emotionIntensity,
                    tags = memory.tags,
                    createdAt = memory.timestamp,
                    lastAccessed = memory.lastAccessedAt,
                    accessCount = memory.reinforcementCount
                )

                characterBook.addMemory(characterMemory, memory.embedding?.toFloatArray())
            }

            // 同时保存到MemoryCore（双写）
            memoryCore.saveMemory(memory)

            Timber.d("[UnifiedMemorySystem] 保存到CharacterBook: ${memory.relatedCharacters.size}个角色")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[UnifiedMemorySystem] 保存到CharacterBook失败")
            Result.failure(e)
        }
    }

    /**
     * 映射到CharacterBook的记忆分类
     */
    private fun mapToCharacterMemoryCategory(
        category: MemoryCategory
    ): com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory {
        return when (category) {
            MemoryCategory.EPISODIC -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.EPISODIC
            MemoryCategory.SEMANTIC -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.SEMANTIC
            MemoryCategory.PREFERENCE -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.PREFERENCE
            else -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.LONG_TERM
        }
    }
}
