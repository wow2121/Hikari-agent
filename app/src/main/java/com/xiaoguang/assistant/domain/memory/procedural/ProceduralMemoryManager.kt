package com.xiaoguang.assistant.domain.memory.procedural

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 程序性记忆管理器
 *
 * 管理"如何做"的知识，包括：
 * - 技能学习和强化
 * - 习惯检测和形成
 * - 条件规则匹配
 * - 自动化行为触发
 *
 * 核心机制：
 * 1. **学习曲线**：通过重复执行提高熟练度
 * 2. **成功率追踪**：记录执行成功/失败，调整可靠性
 * 3. **自动化判定**：高熟练度+高成功率 → 自动执行
 * 4. **遗忘机制**：长期未使用的技能会退化
 *
 * @property storage 存储层（可替换为数据库）
 *
 * @author Claude Code
 */
@Singleton
class ProceduralMemoryManager @Inject constructor(
    private val storage: ProceduralMemoryStorage = InMemoryProceduralStorage()
) {

    private val mutex = Mutex()

    // 执行历史（用于学习分析）
    private val executionHistory = mutableMapOf<String, MutableList<ExecutionRecord>>()

    // 配置参数
    private val learningRate = 0.05f  // 每次成功执行提升5%
    private val decayRate = 0.01f     // 每天未使用衰减1%
    private val minExecutionsForAutomation = 10  // 自动化最少执行次数

    // ==================== 核心功能 ====================

    /**
     * 创建新的程序性记忆
     *
     * @param name 名称
     * @param type 类型
     * @param pattern 行为模式描述
     * @param conditions 触发条件
     * @param actions 执行动作
     * @return 创建的记忆
     */
    suspend fun create(
        name: String,
        type: ProceduralType,
        pattern: String,
        conditions: List<Condition> = emptyList(),
        actions: List<Action> = emptyList(),
        tags: List<String> = emptyList()
    ): Result<ProceduralMemory> = runCatching {
        mutex.withLock {
            val memory = ProceduralMemory(
                name = name,
                type = type,
                pattern = pattern,
                conditions = conditions,
                actions = actions,
                proficiency = 0.0f,  // 新技能从0开始
                tags = tags
            )

            storage.save(memory)
            Timber.i("[ProceduralMemory] 创建: ${memory.getSummary()}")

            memory
        }
    }

    /**
     * 执行程序性记忆
     *
     * 记录执行结果，更新熟练度和成功率
     *
     * @param memoryId 记忆ID
     * @param context 执行上下文
     * @param success 是否成功
     * @param executionTime 执行时长（毫秒）
     * @return 更新后的记忆
     */
    suspend fun execute(
        memoryId: String,
        context: Map<String, Any> = emptyMap(),
        success: Boolean,
        executionTime: Long
    ): Result<ProceduralMemory> = runCatching {
        mutex.withLock {
            val memory = storage.getById(memoryId)
                ?: throw NoSuchElementException("程序性记忆不存在: $memoryId")

            // 记录执行历史
            val record = ExecutionRecord(
                proceduralMemoryId = memoryId,
                success = success,
                executionTime = executionTime,
                context = context
            )
            addExecutionRecord(record)

            // 更新熟练度
            val newProficiency = if (success) {
                // 成功执行 → 提升熟练度（但有上限）
                (memory.proficiency + learningRate * (1 - memory.proficiency)).coerceIn(0f, 1f)
            } else {
                // 失败 → 轻微降低
                (memory.proficiency - learningRate * 0.2f).coerceIn(0f, 1f)
            }

            // 更新成功率（指数移动平均）
            val alpha = 0.1f  // 平滑系数
            val newSuccessRate = if (memory.executionCount > 0) {
                alpha * (if (success) 1f else 0f) + (1 - alpha) * memory.successRate
            } else {
                if (success) 1f else 0f
            }

            // 更新平均执行时间
            val newAvgTime = if (memory.executionCount > 0) {
                (memory.averageExecutionTime * memory.executionCount + executionTime) / (memory.executionCount + 1)
            } else {
                executionTime
            }

            // 更新记忆
            val updated = memory.copy(
                proficiency = newProficiency,
                executionCount = memory.executionCount + 1,
                successRate = newSuccessRate,
                averageExecutionTime = newAvgTime,
                lastExecutedAt = System.currentTimeMillis()
            )

            storage.save(updated)

            Timber.i(
                "[ProceduralMemory] 执行: ${memory.name} | " +
                        "成功=$success | 熟练度: %.2f→%.2f | 成功率: %.2f".format(
                            memory.proficiency,
                            newProficiency,
                            newSuccessRate
                        )
            )

            updated
        }
    }

    /**
     * 查找匹配条件的程序性记忆
     *
     * @param context 当前上下文
     * @param type 记忆类型过滤（可选）
     * @param minProficiency 最小熟练度要求
     * @return 匹配的记忆列表（按匹配度排序）
     */
    suspend fun findMatching(
        context: Map<String, Any>,
        type: ProceduralType? = null,
        minProficiency: Float = 0.3f
    ): List<ProceduralMemory> {
        return mutex.withLock {
            val allMemories = storage.getAll()

            // 过滤和评分
            val matching = allMemories
                .filter { memory ->
                    // 类型过滤
                    if (type != null && memory.type != type) return@filter false

                    // 熟练度过滤
                    if (memory.proficiency < minProficiency) return@filter false

                    // 条件匹配
                    if (memory.conditions.isEmpty()) return@filter true
                    memory.conditions.all { condition -> condition.evaluate(context) }
                }
                .sortedByDescending { it.proficiency }  // 按熟练度降序

            Timber.d("[ProceduralMemory] 匹配到${matching.size}个程序性记忆")

            matching
        }
    }

    /**
     * 获取自动化技能
     *
     * 返回可以自动执行的高熟练度技能
     */
    suspend fun getAutomatedSkills(): List<ProceduralMemory> {
        return mutex.withLock {
            storage.getAll()
                .filter { it.isAutomated() }
                .sortedByDescending { it.proficiency }
        }
    }

    /**
     * 应用遗忘机制
     *
     * 对长期未使用的技能降低熟练度
     *
     * @param days 距今天数
     * @return 衰减的记忆数量
     */
    suspend fun applyDecay(days: Int = 1): Int {
        return mutex.withLock {
            val now = System.currentTimeMillis()
            val threshold = now - (days * 24 * 3600 * 1000L)

            var decayedCount = 0

            storage.getAll().forEach { memory ->
                val lastUsed = memory.lastExecutedAt ?: memory.createdAt

                if (lastUsed < threshold) {
                    // 应用衰减
                    val decayAmount = decayRate * days
                    val newProficiency = (memory.proficiency - decayAmount).coerceAtLeast(0f)

                    if (newProficiency != memory.proficiency) {
                        val updated = memory.copy(proficiency = newProficiency)
                        storage.save(updated)
                        decayedCount++

                        Timber.d(
                            "[ProceduralMemory] 衰减: ${memory.name} | " +
                                    "%.2f→%.2f".format(memory.proficiency, newProficiency)
                        )
                    }
                }
            }

            Timber.i("[ProceduralMemory] 衰减完成，影响${decayedCount}个技能")

            decayedCount
        }
    }

    /**
     * 获取学习进度
     *
     * @param memoryId 记忆ID
     * @return 学习进度信息
     */
    suspend fun getLearningProgress(memoryId: String): Result<LearningProgress> = runCatching {
        val history = executionHistory[memoryId]
            ?: throw NoSuchElementException("无执行历史: $memoryId")

        val memory = storage.getById(memoryId)
            ?: throw NoSuchElementException("程序性记忆不存在: $memoryId")

        // 计算改进率
        val improvementRate = if (history.size > 1) {
            val successCount = history.count { it.success }
            successCount.toFloat() / history.size * learningRate
        } else {
            0f
        }

        LearningProgress(
            proceduralMemoryId = memoryId,
            initialProficiency = 0f,  // 简化：假设从0开始
            currentProficiency = memory.proficiency,
            executionHistory = history.toList(),
            improvementRate = improvementRate
        )
    }

    /**
     * 删除程序性记忆
     */
    suspend fun delete(memoryId: String): Result<Unit> = runCatching {
        mutex.withLock {
            storage.delete(memoryId)
            executionHistory.remove(memoryId)
            Timber.i("[ProceduralMemory] 删除: $memoryId")
        }
    }

    /**
     * 获取所有程序性记忆
     */
    suspend fun getAll(): List<ProceduralMemory> {
        return storage.getAll()
    }

    /**
     * 按类型获取
     */
    suspend fun getByType(type: ProceduralType): List<ProceduralMemory> {
        return storage.getAll().filter { it.type == type }
    }

    /**
     * 按标签获取
     */
    suspend fun getByTag(tag: String): List<ProceduralMemory> {
        return storage.getAll().filter { tag in it.tags }
    }

    // ==================== 统计与分析 ====================

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): ProceduralStatistics {
        val all = storage.getAll()

        return ProceduralStatistics(
            totalCount = all.size,
            byType = all.groupingBy { it.type }.eachCount(),
            automatedCount = all.count { it.isAutomated() },
            proficientCount = all.count { it.isProficient() },
            avgProficiency = if (all.isNotEmpty()) {
                all.map { it.proficiency }.average().toFloat()
            } else {
                0f
            },
            avgSuccessRate = if (all.isNotEmpty()) {
                all.map { it.successRate }.average().toFloat()
            } else {
                0f
            },
            totalExecutions = all.sumOf { it.executionCount }
        )
    }

    // ==================== 辅助方法 ====================

    private fun addExecutionRecord(record: ExecutionRecord) {
        val history = executionHistory.getOrPut(record.proceduralMemoryId) { mutableListOf() }
        history.add(record)

        // 限制历史记录数量
        if (history.size > 100) {
            history.removeAt(0)
        }
    }
}

/**
 * 程序性记忆存储接口
 */
interface ProceduralMemoryStorage {
    suspend fun save(memory: ProceduralMemory)
    suspend fun getById(id: String): ProceduralMemory?
    suspend fun getAll(): List<ProceduralMemory>
    suspend fun delete(id: String)
}

/**
 * 内存存储实现（测试用）
 */
class InMemoryProceduralStorage : ProceduralMemoryStorage {
    private val memories = mutableMapOf<String, ProceduralMemory>()

    override suspend fun save(memory: ProceduralMemory) {
        memories[memory.id] = memory
    }

    override suspend fun getById(id: String): ProceduralMemory? {
        return memories[id]
    }

    override suspend fun getAll(): List<ProceduralMemory> {
        return memories.values.toList()
    }

    override suspend fun delete(id: String) {
        memories.remove(id)
    }
}

/**
 * 统计信息
 */
data class ProceduralStatistics(
    val totalCount: Int,
    val byType: Map<ProceduralType, Int>,
    val automatedCount: Int,
    val proficientCount: Int,
    val avgProficiency: Float,
    val avgSuccessRate: Float,
    val totalExecutions: Int
) {
    override fun toString(): String = buildString {
        appendLine("【程序性记忆统计】")
        appendLine("总数: $totalCount")
        appendLine("- 自动化技能: $automatedCount")
        appendLine("- 熟练技能: $proficientCount")
        appendLine("平均熟练度: %.1f%%".format(avgProficiency * 100))
        appendLine("平均成功率: %.1f%%".format(avgSuccessRate * 100))
        appendLine("总执行次数: $totalExecutions")
        appendLine("按类型分布:")
        byType.forEach { (type, count) ->
            appendLine("  · $type: $count")
        }
    }
}
