package com.xiaoguang.assistant.data.repository

import com.xiaoguang.assistant.domain.knowledge.models.WorldEntry
import com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import com.xiaoguang.assistant.domain.knowledge.vector.VectorSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * World Book数据仓库
 * 提供World Book的数据访问接口
 *
 * ⚠️ 重构说明：
 * - 保留Repository接口层不变
 * - 底层实现从Room改为ChromaDB
 * - 上层业务代码（WorldBook）无需修改
 */
@Singleton
class WorldBookRepository @Inject constructor(
    private val chromaVectorStore: ChromaVectorStore
) {

    companion object {
        private const val COLLECTION_NAME = "xiaoguang_world_book"
    }

    /**
     * 添加世界条目
     */
    suspend fun addEntry(entry: WorldEntry): Result<Unit> {
        return try {
            chromaVectorStore.addWorldEntry(
                entryId = entry.entryId,
                content = entry.content,
                metadata = entry.toMetadata()
            )
            Timber.d("[WorldBookRepository] 添加条目成功: ${entry.entryId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 添加条目失败")
            Result.failure(e)
        }
    }

    /**
     * 批量添加条目
     */
    suspend fun addEntries(entries: List<WorldEntry>): Result<Unit> {
        return try {
            entries.forEach { entry ->
                val result = addEntry(entry)
                if (result.isFailure) return result
            }
            Timber.d("[WorldBookRepository] 批量添加${entries.size}个条目成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 批量添加条目失败")
            Result.failure(e)
        }
    }

    /**
     * 更新条目
     */
    suspend fun updateEntry(entry: WorldEntry): Result<Unit> {
        return try {
            val updated = entry.copy(updatedAt = System.currentTimeMillis())
            chromaVectorStore.updateDocument(
                collectionName = COLLECTION_NAME,
                documentId = updated.entryId,
                content = updated.content,
                metadata = updated.toMetadata()
            )
            Timber.d("[WorldBookRepository] 更新条目成功: ${entry.entryId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 更新条目失败")
            Result.failure(e)
        }
    }

    /**
     * 删除条目
     */
    suspend fun deleteById(entryId: String): Result<Unit> {
        return try {
            chromaVectorStore.deleteDocument(COLLECTION_NAME, entryId)
            Timber.d("[WorldBookRepository] 删除条目成功: $entryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 删除条目失败")
            Result.failure(e)
        }
    }

    /**
     * 根据ID获取条目
     */
    suspend fun getEntryById(entryId: String): WorldEntry? {
        return try {
            val result = chromaVectorStore.getAllDocuments(COLLECTION_NAME, limit = 10000)
            result.getOrNull()
                ?.firstOrNull { it.id == entryId }
                ?.toWorldEntry()
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 获取条目失败: $entryId")
            null
        }
    }

    /**
     * 获取所有启用的条目
     */
    suspend fun getAllEnabledEntries(): List<WorldEntry> {
        return try {
            val result = chromaVectorStore.getDocumentsByMetadata(
                collectionName = COLLECTION_NAME,
                where = mapOf("enabled" to true),
                limit = 10000
            )
            result.getOrNull()
                ?.map { it.toWorldEntry() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 获取启用条目失败")
            emptyList()
        }
    }

    /**
     * 获取所有条目
     */
    suspend fun getAllEntries(): List<WorldEntry> {
        return try {
            val result = chromaVectorStore.getAllDocuments(COLLECTION_NAME, limit = 10000)
            result.getOrNull()
                ?.map { it.toWorldEntry() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 获取所有条目失败")
            emptyList()
        }
    }

    /**
     * 根据分类获取条目
     */
    suspend fun getEntriesByCategory(category: String): List<WorldEntry> {
        return try {
            val result = chromaVectorStore.getDocumentsByMetadata(
                collectionName = COLLECTION_NAME,
                where = mapOf("category" to category),
                limit = 10000
            )
            result.getOrNull()
                ?.map { it.toWorldEntry() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 获取分类条目失败: $category")
            emptyList()
        }
    }

    /**
     * 关键词触发检索
     */
    suspend fun triggerByQuery(query: String): List<WorldEntry> {
        return try {
            val result = chromaVectorStore.searchWorldEntries(
                query = query,
                nResults = 20
            )
            result.getOrNull()
                ?.map { it.toWorldEntry() }
                ?.filter { it.enabled }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 关键词触发失败")
            emptyList()
        }
    }

    /**
     * 全文搜索
     */
    suspend fun fullTextSearch(searchText: String): List<WorldEntry> {
        return try {
            val result = chromaVectorStore.searchWorldEntries(
                query = searchText,
                nResults = 50
            )
            result.getOrNull()
                ?.map { it.toWorldEntry() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 全文搜索失败")
            emptyList()
        }
    }

    /**
     * 搜索条目
     */
    suspend fun searchEntries(query: String): List<WorldEntry> {
        return fullTextSearch(query)
    }

    /**
     * 获取最高优先级的N个条目
     */
    suspend fun getTopPriorityEntries(limit: Int): List<WorldEntry> {
        return getAllEntries()
            .sortedByDescending { it.priority }
            .take(limit)
    }

    /**
     * 启用/禁用条目
     */
    suspend fun updateEntriesEnabled(entryIds: List<String>, enabled: Boolean): Result<Unit> {
        return try {
            entryIds.forEach { entryId ->
                val entry = getEntryById(entryId)
                if (entry != null) {
                    updateEntry(entry.copy(enabled = enabled))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 批量设置启用状态失败")
            Result.failure(e)
        }
    }

    /**
     * 更新条目优先级
     */
    suspend fun updateEntryPriority(entryId: String, priority: Int, timestamp: Long): Result<Unit> {
        return try {
            val entry = getEntryById(entryId)
            if (entry != null) {
                updateEntry(entry.copy(priority = priority, updatedAt = timestamp))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 更新优先级失败")
            Result.failure(e)
        }
    }

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): WorldBookStatistics {
        val allEntries = getAllEntries()
        return WorldBookStatistics(
            totalEntries = allEntries.size,
            enabledEntries = allEntries.count { it.enabled },
            categoryDistribution = allEntries
                .groupBy { it.category.name }
                .mapValues { it.value.size }
        )
    }

    /**
     * 获取条目数量
     */
    suspend fun getEntryCount(): Int {
        return getAllEntries().size
    }

    /**
     * 获取启用条目数量
     */
    suspend fun getEnabledEntryCount(): Int {
        return getAllEnabledEntries().size
    }

    /**
     * 获取分类统计
     */
    suspend fun getCategoryStatistics(): List<CategoryStat> {
        val allEntries = getAllEntries()
        return allEntries
            .groupBy { it.category.name }
            .map { (category, entries) ->
                CategoryStat(category, entries.size)
            }
    }

    /**
     * 清空所有条目
     */
    suspend fun deleteAllEntries(): Result<Unit> {
        return try {
            val allEntries = getAllEntries()
            allEntries.forEach { deleteById(it.entryId) }
            Timber.w("[WorldBookRepository] 清空所有条目")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBookRepository] 清空失败")
            Result.failure(e)
        }
    }

    /**
     * 观察所有条目变化（用于UI）
     */
    fun observeAllEntries(): Flow<List<WorldEntry>> = flow {
        // ChromaDB不支持实时监听，返回静态数据
        emit(getAllEntries())
    }

    /**
     * 观察启用的条目变化
     */
    fun observeEnabledEntries(): Flow<List<WorldEntry>> = flow {
        emit(getAllEnabledEntries())
    }

    // ==================== Helper Methods ====================

    /**
     * WorldEntry转Metadata
     */
    private fun WorldEntry.toMetadata(): Map<String, Any> = mapOf(
        "keys" to keys.joinToString(","),
        "category" to category.name,
        "priority" to priority,
        "enabled" to enabled,
        "insertionOrder" to insertionOrder,
        "caseSensitive" to caseSensitive,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    /**
     * VectorSearchResult转WorldEntry
     */
    private fun VectorSearchResult.toWorldEntry(): WorldEntry {
        val meta = metadata
        return WorldEntry(
            entryId = id,
            keys = (meta["keys"] as? String)?.split(",")?.map { it.trim() } ?: emptyList(),
            content = content,
            category = WorldEntryCategory.valueOf(meta["category"] as? String ?: "SETTING"),
            priority = (meta["priority"] as? Number)?.toInt() ?: 100,
            enabled = meta["enabled"] as? Boolean ?: true,
            insertionOrder = (meta["insertionOrder"] as? Number)?.toInt() ?: 0,
            caseSensitive = meta["caseSensitive"] as? Boolean ?: false,
            createdAt = (meta["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            updatedAt = (meta["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

/**
 * World Book统计信息
 */
data class WorldBookStatistics(
    val totalEntries: Int = 0,
    val enabledEntries: Int = 0,
    val categoryDistribution: Map<String, Int> = emptyMap()
)

/**
 * 分类统计
 */
data class CategoryStat(
    val category: String,
    val count: Int
)
