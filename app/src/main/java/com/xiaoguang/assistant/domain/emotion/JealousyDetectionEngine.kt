package com.xiaoguang.assistant.domain.emotion

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * 吃醋检测引擎（重构版）
 *
 * 职责：
 * 1. 记录主人和其他人的互动统计
 * 2. 提供统计数据给心流系统
 * 3. 不再用规则判断，让 LLM 根据统计数据自然决策
 */
@Singleton
class JealousyDetectionEngine @Inject constructor() {

    // 互动记录
    private val interactionHistory = mutableListOf<InteractionRecord>()

    /**
     * 记录主人互动
     */
    fun recordMasterInteraction() {
        val currentTime = System.currentTimeMillis()
        recordInteraction(currentTime, isMaster = true)
        Timber.d("[Jealousy] 记录主人互动")
    }

    /**
     * 记录其他人互动
     */
    fun recordOthersInteraction() {
        val currentTime = System.currentTimeMillis()
        recordInteraction(currentTime, isMaster = false)
        Timber.d("[Jealousy] 记录其他人互动")
    }

    /**
     * 记录一次互动（内部）
     */
    private fun recordInteraction(timestamp: Long, isMaster: Boolean) {
        interactionHistory.add(InteractionRecord(
            timestamp = timestamp,
            isMaster = isMaster
        ))

        // 限制历史记录数量
        if (interactionHistory.size > 100) {
            interactionHistory.removeAt(0)
        }

        // 清理过期记录（超过2小时）
        val expiryTime = timestamp - (2 * 60 * 60 * 1000)
        interactionHistory.removeAll { it.timestamp < expiryTime }
    }

    /**
     * 统计最近N分钟内的互动次数
     */
    private fun countRecentInteractions(
        currentTime: Long,
        isMaster: Boolean,
        windowMinutes: Int
    ): Int {
        val windowMs = windowMinutes * 60 * 1000L
        val cutoffTime = currentTime - windowMs

        return interactionHistory.count {
            it.timestamp > cutoffTime && it.isMaster == isMaster
        }
    }

    /**
     * 获取统计信息（供心流系统使用）
     */
    fun getStatistics(): JealousyStatistics {
        val currentTime = System.currentTimeMillis()
        return JealousyStatistics(
            totalInteractions = interactionHistory.size,
            masterInteractionsLast30Min = countRecentInteractions(currentTime, true, 30),
            othersInteractionsLast30Min = countRecentInteractions(currentTime, false, 30)
        )
    }

    /**
     * 重置状态
     */
    fun reset() {
        interactionHistory.clear()
        Timber.d("[Jealousy] 重置吃醋检测状态")
    }
}

/**
 * 互动记录
 */
private data class InteractionRecord(
    val timestamp: Long,
    val isMaster: Boolean
)

/**
 * 吃醋统计数据（供心流系统参考）
 */
data class JealousyStatistics(
    val totalInteractions: Int,
    val masterInteractionsLast30Min: Int,
    val othersInteractionsLast30Min: Int
) {
    /**
     * 获取互动比例描述（仅供参考，不做规则判断）
     */
    fun getInteractionRatioDescription(): String {
        return when {
            masterInteractionsLast30Min == 0 && othersInteractionsLast30Min > 0 ->
                "主人最近30分钟只和别人说话(${othersInteractionsLast30Min}次)，没和小光说话"
            othersInteractionsLast30Min > masterInteractionsLast30Min * 3 ->
                "主人和别人说话(${othersInteractionsLast30Min}次)远多于和小光(${masterInteractionsLast30Min}次)"
            othersInteractionsLast30Min > masterInteractionsLast30Min * 2 ->
                "主人和别人说话(${othersInteractionsLast30Min}次)比和小光(${masterInteractionsLast30Min}次)多不少"
            othersInteractionsLast30Min > masterInteractionsLast30Min ->
                "主人和别人说话(${othersInteractionsLast30Min}次)比和小光(${masterInteractionsLast30Min}次)多一些"
            else ->
                "主人和小光说话(${masterInteractionsLast30Min}次)，和别人说话(${othersInteractionsLast30Min}次)"
        }
    }
}
