package com.xiaoguang.assistant.domain.memory.cleanup

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆清理调度器
 *
 * 实现主动遗忘机制，基于Ebbinghaus遗忘曲线自动清理低强度记忆。
 *
 * 核心功能：
 * 1. 定期扫描所有记忆
 * 2. 计算每个记忆的强度
 * 3. 清理/归档低于阈值的记忆
 * 4. 保护重要记忆和新记忆
 * 5. 提供统计信息
 *
 * 调度策略：
 * - 使用Kotlin协程实现后台调度
 * - 支持立即执行和定期执行
 * - 可暂停/恢复调度
 *
 * @property memoryCore 记忆核心
 * @property strengthCalculator 强度计算器
 * @property config 清理配置
 *
 * @author Claude Code
 */
@Singleton
class MemoryCleanupScheduler @Inject constructor(
    private val memoryCore: MemoryCore,
    private val strengthCalculator: MemoryStrengthCalculator = MemoryStrengthCalculator(),
    private var config: CleanupConfig = CleanupConfig()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var schedulerJob: Job? = null

    // 统计信息
    private val _statistics = MutableStateFlow(CleanupStatistics())
    val statistics: StateFlow<CleanupStatistics> = _statistics.asStateFlow()

    // 调度状态
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // 归档存储（软删除的记忆）
    private val archivedMemories = mutableListOf<Memory>()

    init {
        Timber.i("[MemoryCleanup] 调度器已初始化，配置: 间隔=${config.cleanupIntervalHours}h, 阈值=${config.minimumStrengthThreshold}")
    }

    // ==================== 调度控制 ====================

    /**
     * 启动定期清理调度
     *
     * @param startImmediately 是否立即执行一次清理
     */
    fun start(startImmediately: Boolean = false) {
        if (!config.enabled) {
            Timber.w("[MemoryCleanup] 自动清理已禁用")
            return
        }

        if (_isRunning.value) {
            Timber.w("[MemoryCleanup] 调度器已在运行中")
            return
        }

        Timber.i("[MemoryCleanup] 启动定期清理调度")

        schedulerJob = scope.launch {
            _isRunning.value = true

            // 立即执行一次
            if (startImmediately) {
                runCleanup()
            }

            // 定期执行
            while (isActive) {
                delay(config.cleanupIntervalHours * 3600 * 1000)  // 转换为毫秒
                runCleanup()
            }
        }
    }

    /**
     * 停止调度
     */
    fun stop() {
        Timber.i("[MemoryCleanup] 停止调度器")
        schedulerJob?.cancel()
        _isRunning.value = false
    }

    /**
     * 立即执行一次清理（不影响定期调度）
     *
     * @return 清理结果
     */
    suspend fun runNow(): Result<CleanupStatistics> = runCatching {
        Timber.i("[MemoryCleanup] 手动触发清理")
        runCleanup()
    }

    // ==================== 核心清理逻辑 ====================

    /**
     * 执行清理流程
     */
    private suspend fun runCleanup(): CleanupStatistics {
        val startTime = System.currentTimeMillis()

        Timber.i("[MemoryCleanup] 开始清理流程...")

        try {
            // 1. 获取所有记忆
            val allMemories = try {
                memoryCore.getAllMemories()
            } catch (e: Exception) {
                Timber.e("[MemoryCleanup] 获取记忆失败", e)
                emptyList()
            }

            Timber.d("[MemoryCleanup] 扫描到${allMemories.size}条记忆")

            // 2. 过滤需要评估的记忆
            val candidateMemories = filterCandidates(allMemories)

            Timber.d("[MemoryCleanup] 候选清理记忆: ${candidateMemories.size}条")

            // 3. 计算强度
            val strengthMap = strengthCalculator.calculateBatch(candidateMemories)

            // 4. 筛选低强度记忆
            val weakMemories = candidateMemories.filter { memory ->
                (strengthMap[memory.id] ?: 1f) < config.minimumStrengthThreshold
            }

            Timber.i("[MemoryCleanup] 检测到${weakMemories.size}条低强度记忆")

            // 5. 执行清理
            val (deleted, archived) = if (weakMemories.isNotEmpty()) {
                performCleanup(weakMemories.take(config.batchSize))
            } else {
                0 to 0
            }

            // 6. 更新统计
            val avgStrength = if (candidateMemories.isNotEmpty()) {
                strengthMap.values.average().toFloat()
            } else {
                0f
            }

            val stats = CleanupStatistics(
                totalMemoriesScanned = allMemories.size,
                memoriesDeleted = deleted,
                memoriesArchived = archived,
                averageStrength = avgStrength,
                cleanupDurationMs = System.currentTimeMillis() - startTime,
                lastCleanupTimestamp = System.currentTimeMillis()
            )

            _statistics.value = stats

            Timber.i("[MemoryCleanup] 清理完成: 删除${deleted}条, 归档${archived}条, 耗时${stats.cleanupDurationMs}ms")

            return stats

        } catch (e: Exception) {
            Timber.e(e, "[MemoryCleanup] 清理过程出错")
            return _statistics.value
        }
    }

    /**
     * 过滤候选清理记忆
     *
     * 排除：
     * - 受保护的类别
     * - 新创建的记忆（在保护期内）
     * - 高重要性记忆
     */
    private fun filterCandidates(memories: List<Memory>): List<Memory> {
        val now = System.currentTimeMillis()
        val protectionPeriod = config.minRetentionDays * 24 * 3600 * 1000L

        return memories.filter { memory ->
            // 排除受保护类别
            if (memory.category.name in config.protectedCategories) {
                return@filter false
            }

            // 排除保护期内的记忆
            val age = now - memory.createdAt
            if (age < protectionPeriod) {
                return@filter false
            }

            // 排除高重要性记忆（>0.8）
            if (memory.importance > 0.8f) {
                return@filter false
            }

            true
        }
    }

    /**
     * 执行清理操作
     *
     * @param memories 要清理的记忆列表
     * @return (删除数, 归档数)
     */
    private suspend fun performCleanup(memories: List<Memory>): Pair<Int, Int> {
        var deletedCount = 0
        var archivedCount = 0

        for (memory in memories) {
            try {
                if (config.enableSoftDelete) {
                    // 软删除：移至归档
                    archiveMemory(memory)
                    archivedCount++
                } else {
                    // 硬删除：永久删除
                    val deleteSuccess = memoryCore.deleteMemory(memory.id)
                    if (!deleteSuccess) {
                        Timber.e("删除记忆失败: ${memory.id}")
                        throw RuntimeException("删除记忆失败")
                    }
                    deletedCount++
                }

                Timber.v("[MemoryCleanup] 清理记忆: ${memory.id.take(8)} (${if (config.enableSoftDelete) "归档" else "删除"})")

            } catch (e: Exception) {
                Timber.e(e, "[MemoryCleanup] 清理失败: ${memory.id}")
            }
        }

        return deletedCount to archivedCount
    }

    /**
     * 归档记忆（软删除）
     */
    private suspend fun archiveMemory(memory: Memory) {
        // 从主存储删除
        val deleteSuccess = memoryCore.deleteMemory(memory.id)
        if (!deleteSuccess) {
            Timber.e("归档记忆失败: ${memory.id}")
            throw RuntimeException("归档记忆失败")
        }

        // 添加到归档
        archivedMemories.add(memory)

        // 限制归档大小（可选：持久化到单独的归档数据库）
        if (archivedMemories.size > 1000) {
            archivedMemories.removeAt(0)
        }
    }

    // ==================== 归档管理 ====================

    /**
     * 恢复归档的记忆
     *
     * @param memoryId 记忆ID
     * @return 恢复结果
     */
    suspend fun restoreFromArchive(memoryId: String): Result<Memory> = runCatching {
        val memory = archivedMemories.find { it.id == memoryId }
            ?: throw NoSuchElementException("归档中未找到记忆: $memoryId")

        // 恢复到主存储
        memoryCore.saveMemory(memory)

        // 从归档移除
        archivedMemories.removeIf { it.id == memoryId }

        Timber.i("[MemoryCleanup] 恢复归档记忆: ${memoryId.take(8)}")

        memory
    }

    /**
     * 获取归档列表
     */
    fun getArchivedMemories(): List<Memory> = archivedMemories.toList()

    /**
     * 清空归档
     */
    fun clearArchive() {
        archivedMemories.clear()
        Timber.i("[MemoryCleanup] 归档已清空")
    }

    // ==================== 配置管理 ====================

    /**
     * 更新配置
     *
     * @param newConfig 新配置
     * @param restartScheduler 是否重启调度器
     */
    fun updateConfig(newConfig: CleanupConfig, restartScheduler: Boolean = true) {
        val validationResult = newConfig.validate()
        if (validationResult.isFailure) {
            throw IllegalArgumentException("配置验证失败", validationResult.exceptionOrNull())
        }

        config = newConfig
        Timber.i("[MemoryCleanup] 配置已更新: $newConfig")

        if (restartScheduler && _isRunning.value) {
            stop()
            start()
        }
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): CleanupConfig = config

    // ==================== 统计与分析 ====================

    /**
     * 获取记忆强度分布
     *
     * @return 强度区间到记忆数量的映射
     */
    suspend fun getStrengthDistribution(): Map<String, Int> = runCatching {
        val allMemories = try {
            memoryCore.getAllMemories()
        } catch (e: Exception) {
            emptyList()
        }
        val strengthMap = strengthCalculator.calculateBatch(allMemories)

        val distribution = mutableMapOf(
            "0.0-0.2" to 0,
            "0.2-0.4" to 0,
            "0.4-0.6" to 0,
            "0.6-0.8" to 0,
            "0.8-1.0" to 0
        )

        strengthMap.values.forEach { strength ->
            when {
                strength < 0.2f -> distribution["0.0-0.2"] = distribution["0.0-0.2"]!! + 1
                strength < 0.4f -> distribution["0.2-0.4"] = distribution["0.2-0.4"]!! + 1
                strength < 0.6f -> distribution["0.4-0.6"] = distribution["0.4-0.6"]!! + 1
                strength < 0.8f -> distribution["0.6-0.8"] = distribution["0.6-0.8"]!! + 1
                else -> distribution["0.8-1.0"] = distribution["0.8-1.0"]!! + 1
            }
        }

        distribution
    }.getOrDefault(emptyMap())

    /**
     * 预测未来需要清理的记忆数量
     *
     * @param days 未来天数
     * @return 预测的清理数量
     */
    suspend fun predictCleanupCount(days: Int): Int = runCatching {
        val futureTime = System.currentTimeMillis() + (days * 24 * 3600 * 1000L)

        val allMemories = try {
            memoryCore.getAllMemories()
        } catch (e: Exception) {
            emptyList()
        }
        val candidates = filterCandidates(allMemories)

        candidates.count { memory ->
            val futureStrength = strengthCalculator.predictStrength(memory, futureTime)
            futureStrength < config.minimumStrengthThreshold
        }
    }.getOrDefault(0)

    // ==================== 清理 ====================

    /**
     * 关闭调度器，释放资源
     */
    fun shutdown() {
        Timber.i("[MemoryCleanup] 调度器关闭")
        stop()
        scope.cancel()
    }
}
