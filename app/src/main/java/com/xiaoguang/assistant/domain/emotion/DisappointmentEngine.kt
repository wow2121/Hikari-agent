package com.xiaoguang.assistant.domain.emotion

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 失望引擎（重构版）
 *
 * 职责：
 * 1. 记录用户承诺（"明天陪你"、"等会回来"等）
 * 2. 检测承诺是否过期
 * 3. 提供承诺数据给心流系统
 * 4. 不再用规则判断失望程度，让 LLM 根据承诺情况自然决策
 */
@Singleton
class DisappointmentEngine @Inject constructor() {

    // 期望记录
    private val expectations = mutableListOf<ExpectationRecord>()

    /**
     * 记录一个期望/承诺
     *
     * @param description 期望描述
     * @param deadline 期限（时间戳）
     * @param importance 重要程度 (0.0-1.0)
     * @param personName 相关人物（通常是主人）
     */
    fun recordExpectation(
        description: String,
        deadline: Long,
        importance: Float = 0.5f,
        personName: String = "主人"
    ) {
        val expectation = ExpectationRecord(
            id = System.currentTimeMillis(),
            description = description,
            deadline = deadline,
            importance = importance.coerceIn(0f, 1f),
            personName = personName,
            createdAt = System.currentTimeMillis(),
            fulfilled = false,
            checked = false
        )

        expectations.add(expectation)
        Timber.i("[Disappointment] 记录期望: $description (期限: ${formatTimestamp(deadline)})")
    }

    /**
     * 获取过期的未兑现承诺（供心流系统使用）
     */
    fun getOverdueExpectations(currentTime: Long = System.currentTimeMillis()): List<ExpectationRecord> {
        val overdue = expectations.filter { expectation ->
            !expectation.fulfilled &&
            !expectation.checked &&
            currentTime > expectation.deadline
        }

        // 标记为已检查
        overdue.forEach { it.checked = true }

        if (overdue.isNotEmpty()) {
            Timber.w("[Disappointment] 发现${overdue.size}个过期承诺")
        }

        return overdue
    }

    /**
     * 标记期望已兑现
     */
    fun fulfillExpectation(expectationId: Long) {
        val expectation = expectations.find { it.id == expectationId }
        if (expectation != null) {
            expectation.fulfilled = true
            Timber.i("[Disappointment] ✅ 期望已兑现: ${expectation.description}")
        }
    }

    /**
     * 标记期望已兑现（通过描述匹配）
     */
    fun fulfillExpectationByDescription(description: String) {
        val expectation = expectations.find {
            !it.fulfilled && it.description.contains(description, ignoreCase = true)
        }

        if (expectation != null) {
            fulfillExpectation(expectation.id)
        }
    }

    /**
     * 获取所有未兑现的期望
     */
    fun getPendingExpectations(): List<ExpectationRecord> {
        return expectations.filter { !it.fulfilled }
    }

    /**
     * 获取即将到期的期望（1小时内）
     */
    fun getUpcomingExpectations(currentTime: Long = System.currentTimeMillis()): List<ExpectationRecord> {
        val oneHourLater = currentTime + 60 * 60 * 1000
        return expectations.filter {
            !it.fulfilled && it.deadline in currentTime..oneHourLater
        }
    }

    /**
     * 清理过期记录（7天前的）
     */
    fun cleanupOldExpectations(currentTime: Long = System.currentTimeMillis()) {
        val sevenDaysAgo = currentTime - (7 * 24 * 60 * 60 * 1000)
        val removed = expectations.removeAll { it.createdAt < sevenDaysAgo }

        if (removed) {
            Timber.d("[Disappointment] 清理了过期的期望记录")
        }
    }

    /**
     * 重置状态
     */
    fun reset() {
        expectations.clear()
        Timber.d("[Disappointment] 重置失望检测状态")
    }

    /**
     * 格式化时间戳
     */
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now

        return when {
            diff < 0 -> "已过期"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟后"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时后"
            else -> "${diff / (24 * 60 * 60 * 1000)}天后"
        }
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): DisappointmentStatistics {
        return DisappointmentStatistics(
            totalExpectations = expectations.size,
            pendingExpectations = expectations.count { !it.fulfilled },
            fulfilledExpectations = expectations.count { it.fulfilled },
            overdueExpectations = expectations.count {
                !it.fulfilled && System.currentTimeMillis() > it.deadline
            }
        )
    }
}

/**
 * 期望记录
 */
data class ExpectationRecord(
    val id: Long,
    val description: String,
    val deadline: Long,
    val importance: Float,
    val personName: String,
    val createdAt: Long,
    var fulfilled: Boolean = false,
    var checked: Boolean = false
)

/**
 * 失望统计
 */
data class DisappointmentStatistics(
    val totalExpectations: Int,
    val pendingExpectations: Int,
    val fulfilledExpectations: Int,
    val overdueExpectations: Int
) {
    fun getFulfillmentRate(): Float {
        return if (totalExpectations > 0) {
            fulfilledExpectations.toFloat() / totalExpectations.toFloat()
        } else {
            1f
        }
    }

    fun getStatusDescription(): String {
        return when {
            overdueExpectations > 3 -> "最近有${overdueExpectations}个承诺没兑现"
            overdueExpectations > 1 -> "有${overdueExpectations}个承诺没兑现"
            overdueExpectations == 1 -> "有1个承诺没兑现"
            else -> "承诺都兑现了"
        }
    }
}
