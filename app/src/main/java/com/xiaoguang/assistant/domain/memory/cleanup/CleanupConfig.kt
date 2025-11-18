package com.xiaoguang.assistant.domain.memory.cleanup

/**
 * 记忆清理配置
 *
 * 控制主动遗忘机制的行为参数。
 *
 * @property enabled 是否启用自动清理
 * @property cleanupIntervalHours 清理间隔（小时）
 * @property minimumStrengthThreshold 最小强度阈值（低于此值会被清理）
 * @property protectedCategories 受保护的记忆类别（永不清理）
 * @property minRetentionDays 最小保留天数（新记忆的保护期）
 * @property batchSize 单次清理的最大数量
 * @property enableSoftDelete 启用软删除（移至归档而非直接删除）
 *
 * @author Claude Code
 */
data class CleanupConfig(
    val enabled: Boolean = true,
    val cleanupIntervalHours: Long = 24,  // 默认每天清理一次
    val minimumStrengthThreshold: Float = 0.1f,  // 强度<0.1会被清理
    val protectedCategories: Set<String> = setOf("CORE", "IMPORTANT"),
    val minRetentionDays: Int = 7,  // 7天内的记忆不会被清理
    val batchSize: Int = 100,  // 每次最多清理100条
    val enableSoftDelete: Boolean = true  // 默认软删除
) {

    companion object {
        /**
         * 激进清理策略：更快清理，更高阈值
         */
        val AGGRESSIVE = CleanupConfig(
            cleanupIntervalHours = 12,
            minimumStrengthThreshold = 0.15f,
            minRetentionDays = 3
        )

        /**
         * 保守清理策略：更慢清理，更低阈值
         */
        val CONSERVATIVE = CleanupConfig(
            cleanupIntervalHours = 72,  // 3天一次
            minimumStrengthThreshold = 0.05f,
            minRetentionDays = 14
        )

        /**
         * 仅归档策略：不删除，只归档
         */
        val ARCHIVE_ONLY = CleanupConfig(
            minimumStrengthThreshold = 0.01f,  // 极低阈值
            enableSoftDelete = true
        )
    }

    /**
     * 验证配置
     */
    fun validate(): Result<Unit> = runCatching {
        require(cleanupIntervalHours > 0) { "清理间隔必须>0" }
        require(minimumStrengthThreshold in 0.0f..1.0f) { "强度阈值必须在0-1之间" }
        require(minRetentionDays >= 0) { "最小保留天数必须>=0" }
        require(batchSize > 0) { "批量大小必须>0" }
    }
}

/**
 * 清理统计信息
 */
data class CleanupStatistics(
    val totalMemoriesScanned: Int = 0,
    val memoriesDeleted: Int = 0,
    val memoriesArchived: Int = 0,
    val averageStrength: Float = 0f,
    val cleanupDurationMs: Long = 0,
    val lastCleanupTimestamp: Long = 0
) {
    override fun toString(): String = buildString {
        appendLine("【记忆清理统计】")
        appendLine("- 扫描记忆数: $totalMemoriesScanned")
        appendLine("- 删除记忆数: $memoriesDeleted")
        appendLine("- 归档记忆数: $memoriesArchived")
        appendLine("- 平均强度: %.2f".format(averageStrength))
        appendLine("- 清理耗时: ${cleanupDurationMs}ms")
        appendLine("- 上次清理: $lastCleanupTimestamp")
    }
}
