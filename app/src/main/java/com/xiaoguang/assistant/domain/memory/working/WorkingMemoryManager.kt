package com.xiaoguang.assistant.domain.memory.working

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作记忆管理器
 *
 * 模拟人类的短期记忆/工作记忆系统，负责：
 * 1. 维护最近N轮对话的短期缓冲区
 * 2. 容量管理（FIFO淘汰策略）
 * 3. 自动晋升重要对话到长期记忆
 * 4. 提供对话上下文快速访问
 *
 * ## 设计理念
 *
 * 参考Miller's Law（7±2法则）：人类工作记忆容量约为7±2个单位。
 * 本系统默认维护10轮对话，可通过配置调整。
 *
 * ## 晋升策略
 *
 * 重要性评分 >= 阈值（默认0.7）时，自动晋升为长期记忆：
 * - 用户提及重要事件
 * - 情感强烈的对话
 * - 包含关键实体的信息
 *
 * ## 线程安全
 *
 * 使用Mutex保证并发访问的线程安全。
 *
 * @property memoryCore 长期记忆核心服务
 * @property config 工作记忆配置
 *
 * @author Claude Code
 * @since v2.3 - Human-like Memory System
 */
@Singleton
class WorkingMemoryManager @Inject constructor(
    private val memoryCore: MemoryCore,
    private val config: WorkingMemoryConfig = WorkingMemoryConfig.DEFAULT
) {

    // 对话上下文缓冲区（LinkedList支持高效FIFO操作）
    private val context = LinkedList<ConversationTurn>()

    // 线程安全锁
    private val mutex = Mutex()

    // 对话上下文Flow（用于UI观察）
    private val _contextFlow = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val contextFlow: StateFlow<List<ConversationTurn>> = _contextFlow.asStateFlow()

    // 统计数据
    private var _totalTurns: Long = 0
    private var _promotedTurns: Long = 0
    private var _evictedTurns: Long = 0

    init {
        // 验证配置
        config.validate().onFailure { e ->
            Timber.e(e, "[WorkingMemoryManager] 配置无效")
            throw e
        }
        Timber.i("[WorkingMemoryManager] 初始化完成，容量: ${config.maxCapacity}, 晋升阈值: ${config.promotionThreshold}")
    }

    /**
     * 添加新的对话轮次
     *
     * @param turn 对话轮次
     * @return 操作结果
     */
    suspend fun addTurn(turn: ConversationTurn): Result<Unit> = mutex.withLock {
        return try {
            _totalTurns++

            // 1. 检查容量，FIFO淘汰最旧的对话
            if (context.size >= config.maxCapacity) {
                val evicted = context.removeFirst()
                _evictedTurns++
                Timber.d("[WorkingMemoryManager] 淘汰最旧对话: ${evicted.getSummary()}")
            }

            // 2. 添加到缓冲区
            context.addLast(turn)
            Timber.d("[WorkingMemoryManager] 添加对话: ${turn.getSummary()}, 当前容量: ${context.size}/${config.maxCapacity}")

            // 3. 判断是否晋升为长期记忆
            if (config.autoPromoteEnabled && shouldPromote(turn)) {
                promoteToLongTerm(turn)
            }

            // 4. 更新Flow
            _contextFlow.value = context.toList()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[WorkingMemoryManager] 添加对话失败")
            Result.failure(e)
        }
    }

    /**
     * 判断是否应该晋升为长期记忆
     *
     * 晋升条件（满足任一即可）：
     * 1. 重要性评分 >= 阈值
     * 2. 对话已标记为shouldPromote
     * 3. 情感强度很高（>0.8）
     */
    private fun shouldPromote(turn: ConversationTurn): Boolean {
        return turn.importance >= config.promotionThreshold ||
                turn.shouldPromote ||
                turn.emotionIntensity > 0.8f
    }

    /**
     * 晋升对话到长期记忆
     *
     * @param turn 对话轮次
     */
    private suspend fun promoteToLongTerm(turn: ConversationTurn) {
        try {
            val memory = turn.toMemory()
            memoryCore.saveMemory(memory).onSuccess {
                _promotedTurns++
                Timber.i("[WorkingMemoryManager] ✅ 晋升为长期记忆: ${turn.getSummary()}")
            }.onFailure { e ->
                Timber.e(e, "[WorkingMemoryManager] ❌ 晋升失败")
            }
        } catch (e: Exception) {
            Timber.e(e, "[WorkingMemoryManager] 晋升异常")
        }
    }

    /**
     * 手动晋升指定对话
     *
     * @param turnId 对话ID
     * @param reason 晋升原因
     * @return 操作结果
     */
    suspend fun promoteManually(turnId: String, reason: String): Result<Unit> = mutex.withLock {
        val turn = context.find { it.turnId == turnId }
            ?: return Result.failure(NoSuchElementException("对话不存在: $turnId"))

        val updatedTurn = turn.copy(
            shouldPromote = true,
            promotionReason = reason
        )

        // 替换缓冲区中的对话
        val index = context.indexOfFirst { it.turnId == turnId }
        if (index != -1) {
            context[index] = updatedTurn
            promoteToLongTerm(updatedTurn)
            _contextFlow.value = context.toList()
            return Result.success(Unit)
        } else {
            return Result.failure(IllegalStateException("对话索引异常"))
        }
    }

    /**
     * 获取当前所有对话上下文
     *
     * @return 对话列表（从旧到新）
     */
    suspend fun getContext(): List<ConversationTurn> = mutex.withLock {
        return context.toList()
    }

    /**
     * 获取最近N轮对话
     *
     * @param count 对话轮次数
     * @return 对话列表（从旧到新）
     */
    suspend fun getRecentTurns(count: Int): List<ConversationTurn> = mutex.withLock {
        return context.takeLast(count.coerceAtMost(context.size))
    }

    /**
     * 获取最后一轮对话
     *
     * @return 最后一轮对话，如果为空则返回null
     */
    suspend fun getLastTurn(): ConversationTurn? = mutex.withLock {
        return context.lastOrNull()
    }

    /**
     * 搜索包含关键词的对话
     *
     * @param keyword 关键词
     * @return 匹配的对话列表
     */
    suspend fun search(keyword: String): List<ConversationTurn> = mutex.withLock {
        return context.filter {
            it.userInput.contains(keyword, ignoreCase = true) ||
                    it.aiResponse.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 清理过期对话
     *
     * 移除超过保留时间的对话
     *
     * @return 清理数量
     */
    suspend fun cleanupExpired(): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        val threshold = now - config.retentionTimeSeconds * 1000

        val sizeBefore = context.size
        context.removeIf { turn ->
            turn.timestamp < threshold
        }
        val removed = sizeBefore - context.size

        if (removed > 0) {
            _evictedTurns += removed
            _contextFlow.value = context.toList()
            Timber.i("[WorkingMemoryManager] 清理过期对话: $removed 轮")
        }

        return removed
    }

    /**
     * 清空工作记忆
     *
     * ⚠️ 慎用：将清空所有对话上下文
     *
     * @param promoteAll 是否将所有对话晋升为长期记忆
     */
    suspend fun clear(promoteAll: Boolean = false) = mutex.withLock {
        if (promoteAll) {
            context.forEach { turn ->
                promoteToLongTerm(turn)
            }
            Timber.i("[WorkingMemoryManager] 清空前晋升所有对话 (${context.size}轮)")
        }

        context.clear()
        _contextFlow.value = emptyList()
        Timber.i("[WorkingMemoryManager] 工作记忆已清空")
    }

    /**
     * 获取统计信息
     *
     * @return 统计数据
     */
    fun getStatistics(): WorkingMemoryStatistics = WorkingMemoryStatistics(
        currentSize = context.size,
        maxCapacity = config.maxCapacity,
        totalTurnsProcessed = _totalTurns,
        promotedToLongTerm = _promotedTurns,
        evictedByFIFO = _evictedTurns,
        promotionRate = if (_totalTurns > 0) _promotedTurns.toFloat() / _totalTurns else 0f
    )

    /**
     * 生成对话上下文摘要（用于LLM prompt）
     *
     * @param maxTurns 最大对话轮次数（默认5轮）
     * @return 格式化的对话历史文本
     */
    suspend fun generateContextSummary(maxTurns: Int = 5): String = mutex.withLock {
        val recentTurns = context.takeLast(maxTurns.coerceAtMost(context.size))

        return buildString {
            appendLine("【最近对话上下文】")
            appendLine("对话轮次: ${recentTurns.size}")
            appendLine("")

            recentTurns.forEachIndexed { index, turn ->
                appendLine("轮次 ${index + 1}:")
                if (turn.speakerName != null) {
                    appendLine("  用户[${turn.speakerName}]: ${turn.userInput}")
                } else {
                    appendLine("  用户: ${turn.userInput}")
                }
                appendLine("  小光: ${turn.aiResponse}")

                if (turn.emotionTag != null) {
                    appendLine("  情感: ${turn.emotionTag} (强度: ${turn.emotionIntensity}, 效价: ${turn.emotionalValence})")
                }
                if (turn.relatedEntities.isNotEmpty()) {
                    appendLine("  相关实体: ${turn.relatedEntities.joinToString(", ")}")
                }
                appendLine("")
            }
        }
    }
}

/**
 * 工作记忆统计数据
 *
 * @property currentSize 当前缓冲区大小
 * @property maxCapacity 最大容量
 * @property totalTurnsProcessed 累计处理对话数
 * @property promotedToLongTerm 晋升为长期记忆数
 * @property evictedByFIFO FIFO淘汰数
 * @property promotionRate 晋升率（0.0-1.0）
 */
data class WorkingMemoryStatistics(
    val currentSize: Int,
    val maxCapacity: Int,
    val totalTurnsProcessed: Long,
    val promotedToLongTerm: Long,
    val evictedByFIFO: Long,
    val promotionRate: Float
) {
    override fun toString(): String {
        return """
            工作记忆统计:
            - 当前容量: $currentSize / $maxCapacity
            - 累计处理: $totalTurnsProcessed 轮
            - 晋升长期记忆: $promotedToLongTerm 轮
            - FIFO淘汰: $evictedByFIFO 轮
            - 晋升率: ${(promotionRate * 100).toInt()}%
        """.trimIndent()
    }
}
