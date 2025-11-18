package com.xiaoguang.assistant.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.xiaoguang.assistant.domain.usecase.ConversationEmbeddingUseCase
import com.xiaoguang.assistant.domain.usecase.MemoryManagementUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 记忆维护后台任务
 * 定期执行记忆遗忘检查、清理和优化
 */
@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryManagementUseCase: MemoryManagementUseCase,
    private val conversationEmbeddingUseCase: ConversationEmbeddingUseCase,
    private val characterBook: com.xiaoguang.assistant.domain.knowledge.CharacterBook  // ✅ 注入CharacterBook
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Timber.i("开始执行记忆维护任务")

            // 0. ✅ LLM主导的记忆整合（短期→长期）- 对所有角色执行
            try {
                val allProfiles = characterBook.getAllProfiles()
                var totalConsolidated = 0
                var totalEvaluated = 0

                for (profile in allProfiles) {
                    val consolidateResult = characterBook.consolidateMemories(profile.basicInfo.characterId)
                    if (consolidateResult.isSuccess) {
                        val result = consolidateResult.getOrNull()
                        if (result != null) {
                            totalEvaluated += result.totalEvaluated
                            totalConsolidated += result.consolidated

                            // 记录详细的整合结果
                            Timber.i("角色 ${profile.basicInfo.name} LLM记忆整合: $result")

                            // 记录暂缓和拒绝的统计，便于分析
                            if (result.deferred > 0) {
                                Timber.i("  暂缓整合: ${result.deferred} 条记忆")
                            }
                            if (result.rejected > 0) {
                                Timber.i("  拒绝整合: ${result.rejected} 条记忆")
                            }
                        }
                    } else {
                        Timber.w("角色 ${profile.basicInfo.name} LLM记忆整合失败: ${consolidateResult.exceptionOrNull()?.message}")
                    }
                }

                if (totalEvaluated > 0) {
                    val consolidationRate = if (totalEvaluated > 0) {
                        (totalConsolidated.toFloat() / totalEvaluated * 100).toInt()
                    } else 0

                    Timber.i("LLM记忆整合汇总: 评估 $totalEvaluated 条，整合 $totalConsolidated 条 (成功率: $consolidationRate%)")
                }
            } catch (e: Exception) {
                Timber.w(e, "LLM记忆整合失败")
            }

            // 1. 执行遗忘周期
            val forgettingResult = memoryManagementUseCase.performForgettingCycle()
            if (forgettingResult.isSuccess) {
                val forgottenCount = forgettingResult.getOrNull() ?: 0
                Timber.i("遗忘周期完成: $forgottenCount 条记忆被遗忘")
            }

            // 2. 清理低质量记忆
            val cleanupResult = memoryManagementUseCase.cleanupLowQualityMemories()
            if (cleanupResult.isSuccess) {
                val cleanedCount = cleanupResult.getOrNull() ?: 0
                Timber.i("清理完成: $cleanedCount 条低质量记忆")
            }

            // 3. 清理旧的对话embeddings
            val embeddingCleanupResult = conversationEmbeddingUseCase.cleanupOldEmbeddings()
            if (embeddingCleanupResult.isSuccess) {
                val deletedCount = embeddingCleanupResult.getOrNull() ?: 0
                Timber.i("清理完成: $deletedCount 条旧embedding")
            }

            // 4. 清理长期遗忘的记忆(遗忘30天以上)
            val forgottenCleanupResult = memoryManagementUseCase.cleanupForgottenMemories(30)
            if (forgottenCleanupResult.isSuccess) {
                val deletedCount = forgottenCleanupResult.getOrNull() ?: 0
                Timber.i("清理完成: $deletedCount 条长期遗忘的记忆")
            }

            // 5. 生成健康报告
            val reportResult = memoryManagementUseCase.getMemoryHealthReport()
            if (reportResult.isSuccess) {
                val report = reportResult.getOrNull()!!
                Timber.i("记忆健康: ${report.healthStatus} (${report.healthScore}/100)")
            }

            Timber.i("记忆维护任务完成")
            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "记忆维护任务失败")
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "memory_maintenance"

        /**
         * 调度定期记忆维护任务
         * 每天凌晨3点执行
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<MemoryMaintenanceWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

            Timber.i("已调度记忆维护任务")
        }

        /**
         * 取消记忆维护任务
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
            Timber.i("已取消记忆维护任务")
        }

        /**
         * 立即执行一次维护任务
         */
        fun runNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MemoryMaintenanceWorker>()
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)

            Timber.i("已触发立即执行记忆维护任务")
        }

        /**
         * 计算到凌晨3点的延迟时间
         */
        private fun calculateInitialDelay(): Long {
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 3)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)

            var targetTime = calendar.timeInMillis
            if (targetTime < now) {
                // 如果今天凌晨3点已过,设置为明天凌晨3点
                targetTime += 24 * 60 * 60 * 1000
            }

            return targetTime - now
        }
    }
}
