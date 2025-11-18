package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.xiaoguang.assistant.data.local.database.entity.MemoryConsolidationEntity
import com.xiaoguang.assistant.data.local.database.entity.ConsolidationStatisticsEntity
import com.xiaoguang.assistant.data.local.database.entity.EvaluationThresholdEntity
import com.xiaoguang.assistant.data.local.database.entity.DecisionPatternAnalysisEntity

/**
 * 记忆整合决策数据访问对象
 */
@Dao
interface MemoryConsolidationDao {

    // ==================== 记忆整合决策 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsolidationDecision(decision: MemoryConsolidationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsolidationDecisions(decisions: List<MemoryConsolidationEntity>)

    @Query("SELECT * FROM memory_consolidation_decisions WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentDecisions(characterId: String, limit: Int = 100): List<MemoryConsolidationEntity>

    @Query("SELECT * FROM memory_consolidation_decisions WHERE characterId = :characterId AND wasConsolidated = 1")
    suspend fun getConsolidatedDecisions(characterId: String): List<MemoryConsolidationEntity>

    @Query("DELETE FROM memory_consolidation_decisions WHERE characterId = :characterId AND timestamp < :cutoffTime")
    suspend fun deleteOldDecisions(characterId: String, cutoffTime: Long)

    // ==================== 整合统计 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConsolidationStatistics(statistics: ConsolidationStatisticsEntity)

    @Query("SELECT * FROM memory_consolidation_statistics WHERE characterId = :characterId")
    suspend fun getConsolidationStatistics(characterId: String): ConsolidationStatisticsEntity?

    @Query("SELECT * FROM memory_consolidation_statistics")
    suspend fun getAllConsolidationStatistics(): List<ConsolidationStatisticsEntity>

    @Query("DELETE FROM memory_consolidation_statistics WHERE characterId = :characterId")
    suspend fun deleteConsolidationStatistics(characterId: String)

    // ==================== 评估阈值 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEvaluationThreshold(threshold: EvaluationThresholdEntity)

    @Query("SELECT threshold FROM memory_evaluation_thresholds WHERE characterId = :characterId")
    suspend fun getEvaluationThreshold(characterId: String): Float?

    @Query("SELECT * FROM memory_evaluation_thresholds")
    suspend fun getAllEvaluationThresholds(): List<EvaluationThresholdEntity>

    @Query("DELETE FROM memory_evaluation_thresholds WHERE characterId = :characterId")
    suspend fun deleteEvaluationThreshold(characterId: String)

    // ==================== 决策模式分析 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecisionPatternAnalysis(analysis: DecisionPatternAnalysisEntity)

    @Query("SELECT * FROM decision_pattern_analysis WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentPatternAnalyses(characterId: String, limit: Int = 50): List<DecisionPatternAnalysisEntity>

    @Query("SELECT * FROM decision_pattern_analysis WHERE characterId = :characterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPatternAnalysis(characterId: String): DecisionPatternAnalysisEntity?

    @Query("DELETE FROM decision_pattern_analysis WHERE characterId = :characterId AND timestamp < :cutoffTime")
    suspend fun deleteOldPatternAnalyses(characterId: String, cutoffTime: Long)

    // ==================== 数据清理 ====================

    @Query("DELETE FROM memory_consolidation_decisions WHERE timestamp < :cutoffTime")
    suspend fun cleanupOldDecisions(cutoffTime: Long)

    @Query("DELETE FROM decision_pattern_analysis WHERE timestamp < :cutoffTime")
    suspend fun cleanupOldAnalyses(cutoffTime: Long)
}