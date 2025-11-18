package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * 记忆管理用例
 * 实现Ebbinghaus遗忘曲线和记忆生命周期管理
 */
@Singleton
class MemoryManagementUseCase @Inject constructor(
    private val embeddingRepository: EmbeddingRepository,
    private val appPreferences: AppPreferences
) {

    /**
     * 执行记忆遗忘检查
     * 根据Ebbinghaus遗忘曲线标记应该被遗忘的记忆
     *
     * @return 被标记为遗忘的记忆数量
     */
    suspend fun performForgettingCycle(): Result<Int> {
        return try {
            val allMemories = embeddingRepository.getAllActiveMemoryFacts()
            val now = System.currentTimeMillis()

            var forgottenCount = 0

            allMemories.forEach { memory ->
                val shouldForget = shouldForgetMemory(memory, now)

                if (shouldForget) {
                    embeddingRepository.markMemoryFactAsForgotten(memory.id, now)
                    forgottenCount++
                    Timber.d("遗忘记忆: ${memory.content.take(50)}")
                }
            }

            Timber.i("遗忘周期完成: $forgottenCount / ${allMemories.size} 条记忆被遗忘")
            Result.success(forgottenCount)

        } catch (e: Exception) {
            Timber.e(e, "执行遗忘周期失败")
            Result.failure(e)
        }
    }

    /**
     * 清理已遗忘的记忆
     * 删除长时间未恢复的遗忘记忆
     *
     * @param daysAfterForgotten 遗忘后保留的天数,默认30天
     * @return 删除的记忆数量
     */
    suspend fun cleanupForgottenMemories(daysAfterForgotten: Int = 30): Result<Int> {
        return try {
            val beforeTime = System.currentTimeMillis() - (daysAfterForgotten * 24 * 60 * 60 * 1000L)
            val deletedCount = embeddingRepository.deleteForgottenMemoryFactsBefore(beforeTime)

            Timber.i("清理了 $deletedCount 条长期遗忘的记忆")
            Result.success(deletedCount)

        } catch (e: Exception) {
            Timber.e(e, "清理遗忘记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 清理低质量记忆
     * 删除低重要性且长时间未强化的记忆
     *
     * @return 清理的记忆数量
     */
    suspend fun cleanupLowQualityMemories(): Result<Int> {
        return try {
            val retentionDays = appPreferences.memoryRetentionDays.first()
            val importanceThreshold = 0.3f // 低于0.3视为低重要性
            val lastReinforcedBefore = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)

            val candidates = embeddingRepository.getMemoryFactCandidatesForCleanup(
                importanceThreshold = importanceThreshold,
                lastReinforcedBefore = lastReinforcedBefore
            )

            var cleanedCount = 0
            val now = System.currentTimeMillis()

            candidates.forEach { memory ->
                // 计算记忆价值
                val value = calculateMemoryValue(memory, now)

                if (value < 0.1f) { // 价值极低,直接标记为遗忘
                    embeddingRepository.markMemoryFactAsForgotten(memory.id, now)
                    cleanedCount++
                    Timber.d("清理低质量记忆: ${memory.content.take(50)}, 价值: $value")
                }
            }

            Timber.i("清理了 $cleanedCount 条低质量记忆")
            Result.success(cleanedCount)

        } catch (e: Exception) {
            Timber.e(e, "清理低质量记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 获取记忆健康报告
     */
    suspend fun getMemoryHealthReport(): Result<MemoryHealthReport> {
        return try {
            val stats = embeddingRepository.getMemoryFactStats()
            val allActive = embeddingRepository.getAllActiveMemoryFacts()
            val now = System.currentTimeMillis()

            // 计算各种指标
            val strongMemories = allActive.count { memory ->
                calculateMemoryStrength(memory, now) > 0.7f
            }

            val weakMemories = allActive.count { memory ->
                calculateMemoryStrength(memory, now) < 0.3f
            }

            val recentlyAccessed = allActive.count { memory ->
                val daysSinceAccess = (now - memory.lastAccessedAt) / (24 * 60 * 60 * 1000)
                daysSinceAccess < 7
            }

            val staleMemories = allActive.count { memory ->
                val daysSinceAccess = (now - memory.lastAccessedAt) / (24 * 60 * 60 * 1000)
                daysSinceAccess > 90
            }

            val report = MemoryHealthReport(
                totalActive = stats.activeCount,
                totalForgotten = stats.forgottenCount,
                strongMemories = strongMemories,
                weakMemories = weakMemories,
                recentlyAccessed = recentlyAccessed,
                staleMemories = staleMemories,
                averageImportance = stats.averageImportance,
                averageReinforcementCount = stats.averageReinforcementCount,
                categoryDistribution = stats.categoryStats
            )

            Timber.d("记忆健康报告: 活跃 ${report.totalActive}, 遗忘 ${report.totalForgotten}")
            Result.success(report)

        } catch (e: Exception) {
            Timber.e(e, "获取记忆健康报告失败")
            Result.failure(e)
        }
    }

    /**
     * 恢复被遗忘的记忆
     * 当记忆被再次访问时可以恢复
     */
    suspend fun restoreMemory(memoryId: Long): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            embeddingRepository.restoreMemoryFact(memoryId, now)

            Timber.i("恢复记忆: $memoryId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "恢复记忆失败")
            Result.failure(e)
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 判断是否应该遗忘某个记忆
     * 基于Ebbinghaus遗忘曲线
     */
    private fun shouldForgetMemory(memory: MemoryFactEntity, now: Long): Boolean {
        // 计算记忆强度
        val strength = calculateMemoryStrength(memory, now)

        // 高重要性记忆更难遗忘
        val importanceBonus = memory.importance * 0.3f

        // 综合阈值
        val forgettingThreshold = 0.15f - importanceBonus

        return strength < forgettingThreshold
    }

    /**
     * 计算记忆强度（增强版）
     * 基于Ebbinghaus遗忘曲线: R = e^(-t/S)
     * 新增：情感强化、回忆难度、上下文相关性
     *
     * R: 保持率, t: 时间, S: 记忆强度
     */
    private fun calculateMemoryStrength(memory: MemoryFactEntity, now: Long): Float {
        // 计算距离上次访问的天数
        val daysSinceAccess = (now - memory.lastAccessedAt) / (24 * 60 * 60 * 1000f)

        // 基础强度S (基于强化次数)
        // 强化次数越多,遗忘越慢
        val baseStrength = 1f + ln(1f + memory.reinforcementCount.toFloat()) * 10f

        // 情感强化因子（高情感事件更持久）
        val emotionIntensity = abs(memory.emotionalValence)
        val emotionBonus = emotionIntensity * 5f

        // 重要性加权
        val importanceBonus = memory.importance * 3f

        // 回忆难度惩罚（难回忆的更易忘）
        val recallDifficulty = estimateRecallDifficulty(memory.content)
        val difficultyPenalty = recallDifficulty * 2f

        // 综合强度
        val effectiveStrength = baseStrength + emotionBonus + importanceBonus - difficultyPenalty

        // 计算保持率 R = e^(-t/S)
        val retention = exp(-daysSinceAccess / effectiveStrength.coerceAtLeast(1f)).toFloat()

        // 考虑置信度
        val adjustedStrength = retention * (0.5f + memory.confidence * 0.5f)

        return adjustedStrength.coerceIn(0f, 1f)
    }

    /**
     * 估算回忆难度
     * 根据内容复杂度估算
     */
    private fun estimateRecallDifficulty(content: String): Float {
        // 内容长度因子（越长越难回忆）
        val lengthFactor = kotlin.math.min(content.length / 500f, 1f)

        // 数字密度（数字多的内容更难记忆）
        val digitRatio = if (content.isNotEmpty()) {
            content.count { it.isDigit() } / content.length.toFloat()
        } else 0f

        // 复杂词汇密度（估算：长单词占比）
        val complexWordRatio = if (content.isNotEmpty()) {
            content.split("\\s+".toRegex())
                .count { it.length > 8 } / content.split("\\s+".toRegex()).size.toFloat()
        } else 0f

        return (lengthFactor * 0.5f + digitRatio * 0.3f + complexWordRatio * 0.2f)
            .coerceIn(0f, 1f)
    }

    /**
     * 计算记忆的综合价值
     */
    private fun calculateMemoryValue(memory: MemoryFactEntity, now: Long): Float {
        val strength = calculateMemoryStrength(memory, now)
        val importance = memory.importance
        val confidence = memory.confidence

        // 综合价值 = 强度 * 0.4 + 重要性 * 0.4 + 置信度 * 0.2
        return (strength * 0.4f + importance * 0.4f + confidence * 0.2f).coerceIn(0f, 1f)
    }
}

/**
 * 记忆健康报告
 */
data class MemoryHealthReport(
    val totalActive: Int,
    val totalForgotten: Int,
    val strongMemories: Int,
    val weakMemories: Int,
    val recentlyAccessed: Int,
    val staleMemories: Int,
    val averageImportance: Float,
    val averageReinforcementCount: Float,
    val categoryDistribution: Map<String, Int>
) {
    /**
     * 记忆健康评分 (0-100)
     */
    val healthScore: Int
        get() {
            if (totalActive == 0) return 100

            val strongRatio = strongMemories.toFloat() / totalActive
            val recentRatio = recentlyAccessed.toFloat() / totalActive
            val staleRatio = staleMemories.toFloat() / totalActive

            val score = (
                strongRatio * 40 +           // 强记忆比例40分
                recentRatio * 30 +           // 最近访问比例30分
                (1f - staleRatio) * 20 +     // 非陈旧记忆比例20分
                averageImportance * 10       // 平均重要性10分
            ) * 100

            return score.toInt().coerceIn(0, 100)
        }

    /**
     * 健康状态描述
     */
    val healthStatus: String
        get() = when {
            healthScore >= 80 -> "优秀"
            healthScore >= 60 -> "良好"
            healthScore >= 40 -> "一般"
            healthScore >= 20 -> "较差"
            else -> "需要优化"
        }
}
