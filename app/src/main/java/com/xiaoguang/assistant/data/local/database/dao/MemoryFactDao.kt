package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import kotlinx.coroutines.flow.Flow

/**
 * 记忆事实 DAO
 * MemoryCore的核心数据访问接口
 */
@Dao
interface MemoryFactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fact: MemoryFactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(facts: List<MemoryFactEntity>): List<Long>

    @Update
    suspend fun update(fact: MemoryFactEntity)

    @Delete
    suspend fun delete(fact: MemoryFactEntity)

    @Query("DELETE FROM memory_facts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM memory_facts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MemoryFactEntity?

    @Query("SELECT * FROM memory_facts WHERE category = :category AND isActive = 1 ORDER BY importance DESC")
    suspend fun getByCategory(category: String): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE isActive = 1 AND isForgotten = 0 ORDER BY importance DESC, lastAccessedAt DESC")
    suspend fun getAllActive(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts ORDER BY createdAt DESC")
    suspend fun getAll(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE isActive = 1 AND isForgotten = 0")
    fun observeActive(): Flow<List<MemoryFactEntity>>

    @Query("SELECT * FROM memory_facts WHERE isForgotten = 1 ORDER BY forgottenAt DESC")
    suspend fun getForgotten(): List<MemoryFactEntity>

    @Query("SELECT * FROM memory_facts WHERE relatedEntities LIKE '%' || :entity || '%' AND isActive = 1")
    suspend fun searchByEntity(entity: String): List<MemoryFactEntity>

    @Query("UPDATE memory_facts SET reinforcementCount = reinforcementCount + 1, importance = MIN(importance + 0.05, 1.0), lastAccessedAt = :reinforcedAt WHERE id = :id")
    suspend fun reinforce(id: Long, reinforcedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memory_facts SET reinforcementCount = reinforcementCount + 1, importance = MIN(importance + 0.05, 1.0), lastAccessedAt = :reinforcedAt WHERE id IN (:ids)")
    suspend fun reinforceBatch(ids: List<Long>, reinforcedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memory_facts SET isForgotten = 1, forgottenAt = :forgottenAt, isActive = 0 WHERE id = :id")
    suspend fun markAsForgotten(id: Long, forgottenAt: Long = System.currentTimeMillis())

    @Query("UPDATE memory_facts SET isForgotten = 1, forgottenAt = :forgottenAt, isActive = 0 WHERE id IN (:ids)")
    suspend fun markAsForgottenBatch(ids: List<Long>, forgottenAt: Long = System.currentTimeMillis())

    @Query("UPDATE memory_facts SET isForgotten = 0, forgottenAt = NULL, isActive = 1, lastAccessedAt = :restoredAt WHERE id = :id")
    suspend fun restore(id: Long, restoredAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM memory_facts WHERE isForgotten = 1 AND forgottenAt < :beforeTime")
    suspend fun deleteForgottenBefore(beforeTime: Long): Int

    @Query("DELETE FROM memory_facts WHERE createdAt < :beforeTime")
    suspend fun deleteCreatedBefore(beforeTime: Long): Int

    @Query("""
        SELECT * FROM memory_facts
        WHERE isActive = 1
        AND importance < :importanceThreshold
        AND (lastAccessedAt < :lastReinforcedBefore)
        ORDER BY importance ASC, lastAccessedAt ASC
    """)
    suspend fun getCleanupCandidates(
        importanceThreshold: Float,
        lastReinforcedBefore: Long
    ): List<MemoryFactEntity>

    @Query("UPDATE memory_facts SET accessCount = accessCount + 1, lastAccessedAt = :accessTime WHERE id = :id")
    suspend fun recordAccess(id: Long, accessTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM memory_facts WHERE confidence < :threshold")
    suspend fun deleteLowConfidence(threshold: Float): Int

    // 统计查询
    @Query("SELECT COUNT(*) FROM memory_facts WHERE isActive = 1 AND isForgotten = 0")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM memory_facts WHERE isForgotten = 1")
    suspend fun getForgottenCount(): Int

    @Query("SELECT AVG(importance) FROM memory_facts WHERE isActive = 1 AND isForgotten = 0")
    suspend fun getAverageImportance(): Float

    @Query("SELECT AVG(reinforcementCount) FROM memory_facts WHERE isActive = 1 AND isForgotten = 0")
    suspend fun getAverageReinforcementCount(): Float

    @Suppress("RoomWarnings.CURSOR_MISMATCH")
    @Query("SELECT category, COUNT(*) as count FROM memory_facts WHERE isActive = 1 AND isForgotten = 0 GROUP BY category")
    suspend fun getCategoryStatsRaw(): List<CategoryStat>

    // 数据类用于存储分类统计结果
    data class CategoryStat(
        val category: String,
        val count: Int
    )
}
