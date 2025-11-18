package com.xiaoguang.assistant.domain.memory.working

/**
 * 工作记忆配置
 *
 * 定义工作记忆的容量、晋升策略等参数。
 *
 * 参考人类认知科学：
 * - 工作记忆容量：7±2个单位（Miller's Law）
 * - 本系统采用10轮对话作为容量上限
 *
 * @property maxCapacity 最大容量（对话轮次数）
 * @property promotionThreshold 晋升阈值（重要性评分）
 * @property autoPromoteEnabled 是否启用自动晋升
 * @property retentionTimeSeconds 保留时间（秒），超过此时间的对话会被清理
 */
data class WorkingMemoryConfig(
    val maxCapacity: Int = 10,
    val promotionThreshold: Float = 0.7f,
    val autoPromoteEnabled: Boolean = true,
    val retentionTimeSeconds: Long = 3600  // 1小时
) {

    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = WorkingMemoryConfig()

        /**
         * 高容量配置（适用于长对话场景）
         */
        val HIGH_CAPACITY = WorkingMemoryConfig(
            maxCapacity = 20,
            promotionThreshold = 0.6f,
            retentionTimeSeconds = 7200  // 2小时
        )

        /**
         * 精简配置（适用于资源受限场景）
         */
        val MINIMAL = WorkingMemoryConfig(
            maxCapacity = 5,
            promotionThreshold = 0.8f,
            retentionTimeSeconds = 1800  // 30分钟
        )
    }

    /**
     * 验证配置有效性
     */
    fun validate(): Result<Unit> {
        return when {
            maxCapacity <= 0 -> Result.failure(
                IllegalArgumentException("maxCapacity must be > 0, got $maxCapacity")
            )
            promotionThreshold !in 0f..1f -> Result.failure(
                IllegalArgumentException("promotionThreshold must be in [0, 1], got $promotionThreshold")
            )
            retentionTimeSeconds <= 0 -> Result.failure(
                IllegalArgumentException("retentionTimeSeconds must be > 0, got $retentionTimeSeconds")
            )
            else -> Result.success(Unit)
        }
    }
}
