package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记忆整合决策记录实体
 * 用于存储LLM对记忆整合的决策数据，支持后续的学习和优化
 */
@Entity(tableName = "memory_consolidation_decisions")
data class MemoryConsolidationEntity(
    @PrimaryKey
    val characterId: String,
    val memoryId: String,
    val wasConsolidated: Boolean,
    val llmScore: Float,
    val confidence: Float,
    val memoryImportance: Float,
    val memoryAccessCount: Int,
    val memoryAgeDays: Long,
    val reasoning: String,
    val timestamp: Long
)

/**
 * 记忆整合统计实体
 * 用于存储每个角色的整合统计数据
 */
@Entity(tableName = "memory_consolidation_statistics")
data class ConsolidationStatisticsEntity(
    @PrimaryKey
    val characterId: String,
    val totalDecisions: Int,
    val totalConsolidated: Int,
    val avgLlmScore: Float,
    val avgConfidence: Float,
    val lastUpdated: Long
)

/**
 * 评估阈值实体
 * 用于存储每个角色的个性化评估阈值
 */
@Entity(tableName = "memory_evaluation_thresholds")
data class EvaluationThresholdEntity(
    @PrimaryKey
    val characterId: String,
    val threshold: Float,
    val lastUpdated: Long
)

/**
 * 决策模式分析实体
 * 用于存储决策模式分析结果
 */
@Entity(tableName = "decision_pattern_analysis")
data class DecisionPatternAnalysisEntity(
    @PrimaryKey
    val characterId: String,
    val consolidatedRate: Float,
    val avgLlmScore: Float,
    val importanceGap: Float,
    val timestamp: Long
)