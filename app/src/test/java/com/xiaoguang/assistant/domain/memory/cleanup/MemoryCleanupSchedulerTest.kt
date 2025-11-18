package com.xiaoguang.assistant.domain.memory.cleanup

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MemoryCleanupScheduler 单元测试
 *
 * 测试覆盖：
 * 1. 强度计算（Ebbinghaus遗忘曲线）
 * 2. 清理逻辑（过滤、删除、归档）
 * 3. 调度控制（启动、停止、立即执行）
 * 4. 配置管理
 * 5. 归档管理
 * 6. 统计信息
 * 7. 预测分析
 *
 * @author Claude Code
 */
class MemoryCleanupSchedulerTest {

    private lateinit var memoryCore: MemoryCore
    private lateinit var strengthCalculator: MemoryStrengthCalculator
    private lateinit var scheduler: MemoryCleanupScheduler

    @Before
    fun setup() {
        memoryCore = mockk()
        strengthCalculator = MemoryStrengthCalculator()

        // 使用测试配置
        val testConfig = CleanupConfig(
            enabled = true,
            cleanupIntervalHours = 1,  // 1小时（测试用）
            minimumStrengthThreshold = 0.2f,
            minRetentionDays = 1,  // 1天保护期
            batchSize = 10
        )

        scheduler = MemoryCleanupScheduler(
            memoryCore = memoryCore,
            strengthCalculator = strengthCalculator,
            config = testConfig
        )
    }

    @After
    fun teardown() {
        scheduler.shutdown()
        clearAllMocks()
    }

    // ==================== 强度计算测试 ====================

    @Test
    fun `计算新记忆强度 - 应该很高`() {
        // Given: 刚创建的高重要性记忆
        val memory = createTestMemory(
            importance = 0.8f,
            confidence = 0.9f,
            emotionalValence = 0.7f,
            accessCount = 5,
            reinforcementCount = 2,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )

        // When
        val strength = strengthCalculator.calculateStrength(memory)

        // Then
        assertTrue("新记忆强度应>0.5", strength > 0.5f)
    }

    @Test
    fun `计算旧记忆强度 - 应该衰减`() {
        // Given: 30天前的记忆，未被访问
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 3600 * 1000)
        val memory = createTestMemory(
            importance = 0.5f,
            createdAt = thirtyDaysAgo,
            lastAccessedAt = thirtyDaysAgo,
            accessCount = 1,
            reinforcementCount = 0
        )

        // When
        val strength = strengthCalculator.calculateStrength(memory)

        // Then
        assertTrue("30天未访问的记忆强度应<0.3", strength < 0.3f)
    }

    @Test
    fun `高重要性记忆衰减慢`() {
        // Given: 7天前的高重要性记忆
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 3600 * 1000)

        val importantMemory = createTestMemory(
            importance = 0.9f,
            createdAt = sevenDaysAgo,
            lastAccessedAt = sevenDaysAgo
        )

        val normalMemory = createTestMemory(
            importance = 0.5f,
            createdAt = sevenDaysAgo,
            lastAccessedAt = sevenDaysAgo
        )

        // When
        val importantStrength = strengthCalculator.calculateStrength(importantMemory)
        val normalStrength = strengthCalculator.calculateStrength(normalMemory)

        // Then
        assertTrue("高重要性记忆强度应>普通记忆", importantStrength > normalStrength)
    }

    @Test
    fun `强化次数影响强度`() {
        // Given
        val baseMemory = createTestMemory(reinforcementCount = 0)
        val reinforcedMemory = createTestMemory(reinforcementCount = 10)

        // When
        val baseStrength = strengthCalculator.calculateStrength(baseMemory)
        val reinforcedStrength = strengthCalculator.calculateStrength(reinforcedMemory)

        // Then
        assertTrue("强化记忆强度应>未强化记忆", reinforcedStrength > baseStrength)
    }

    @Test
    fun `情感强度影响记忆强度`() {
        // Given
        val neutralMemory = createTestMemory(emotionalValence = 0.0f)
        val emotionalMemory = createTestMemory(emotionalValence = 0.9f)

        // When
        val neutralStrength = strengthCalculator.calculateStrength(neutralMemory)
        val emotionalStrength = strengthCalculator.calculateStrength(emotionalMemory)

        // Then
        assertTrue("高情感记忆强度应>中性记忆", emotionalStrength > neutralStrength)
    }

    @Test
    fun `访问频率影响强度`() {
        // Given
        val lowAccess = createTestMemory(accessCount = 1)
        val highAccess = createTestMemory(accessCount = 50)

        // When
        val lowStrength = strengthCalculator.calculateStrength(lowAccess)
        val highStrength = strengthCalculator.calculateStrength(highAccess)

        // Then
        assertTrue("高访问频率记忆强度应>低访问频率", highStrength > lowStrength)
    }

    @Test
    fun `计算半衰期`() {
        // Given
        val memory = createTestMemory(
            importance = 0.7f,
            reinforcementCount = 3
        )

        // When
        val halfLife = strengthCalculator.calculateHalfLife(memory)

        // Then
        assertTrue("半衰期应>0", halfLife > 0)
        assertTrue("半衰期应<1年", halfLife < 365L * 24 * 3600 * 1000)
    }

    // ==================== 清理逻辑测试 ====================

    @Test
    fun `清理低强度记忆`() = runTest {
        // Given: 旧的低重要性记忆
        val twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)

        val weakMemory = createTestMemory(
            id = "weak",
            importance = 0.2f,
            createdAt = twoDaysAgo,
            lastAccessedAt = twoDaysAgo
        )

        val strongMemory = createTestMemory(
            id = "strong",
            importance = 0.9f,
            createdAt = twoDaysAgo,
            lastAccessedAt = System.currentTimeMillis()  // 最近访问
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(weakMemory, strongMemory))
        coEvery { memoryCore.deleteMemory("weak") } returns Result.success(Unit)

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        assertTrue("应该清理了至少1条记忆", stats.memoriesDeleted + stats.memoriesArchived > 0)
        coVerify(atLeast = 1) { memoryCore.deleteMemory(any()) }
    }

    @Test
    fun `保护期内的记忆不被清理`() = runTest {
        // Given: 12小时前创建的记忆（保护期1天）
        val recentTime = System.currentTimeMillis() - (12L * 3600 * 1000)

        val recentMemory = createTestMemory(
            importance = 0.1f,  // 很低
            createdAt = recentTime,
            lastAccessedAt = recentTime
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(recentMemory))

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        assertEquals("保护期内的记忆不应被清理", 0, stats.memoriesDeleted + stats.memoriesArchived)
    }

    @Test
    fun `高重要性记忆不被清理`() = runTest {
        // Given: 旧的但高重要性记忆
        val oldTime = System.currentTimeMillis() - (30L * 24 * 3600 * 1000)

        val importantMemory = createTestMemory(
            importance = 0.9f,  // 高重要性
            createdAt = oldTime,
            lastAccessedAt = oldTime
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(importantMemory))

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        assertEquals("高重要性记忆不应被清理", 0, stats.memoriesDeleted + stats.memoriesArchived)
    }

    @Test
    fun `受保护类别不被清理`() = runTest {
        // Given: 受保护类别的记忆
        val protectedConfig = CleanupConfig(
            protectedCategories = setOf("EPISODIC"),
            minimumStrengthThreshold = 0.5f
        )
        scheduler.updateConfig(protectedConfig, restartScheduler = false)

        val twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)
        val protectedMemory = createTestMemory(
            category = MemoryCategory.EPISODIC,  // 受保护
            importance = 0.1f,
            createdAt = twoDaysAgo,
            lastAccessedAt = twoDaysAgo
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(protectedMemory))

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        assertEquals("受保护类别不应被清理", 0, stats.memoriesDeleted + stats.memoriesArchived)
    }

    @Test
    fun `软删除模式 - 归档而非删除`() = runTest {
        // Given: 启用软删除
        val softDeleteConfig = CleanupConfig(
            enableSoftDelete = true,
            minimumStrengthThreshold = 0.5f,
            minRetentionDays = 0
        )
        scheduler.updateConfig(softDeleteConfig, restartScheduler = false)

        val weakMemory = createTestMemory(
            importance = 0.1f,
            createdAt = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(weakMemory))
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        assertTrue("应该归档记忆", stats.memoriesArchived > 0)
        assertEquals("不应删除记忆", 0, stats.memoriesDeleted)
    }

    @Test
    fun `批量大小限制`() = runTest {
        // Given: 20条低强度记忆，批量大小10
        val twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)

        val weakMemories = (1..20).map { i ->
            createTestMemory(
                id = "weak_$i",
                importance = 0.1f,
                createdAt = twoDaysAgo,
                lastAccessedAt = twoDaysAgo
            )
        }

        coEvery { memoryCore.getAllMemories() } returns Result.success(weakMemories)
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        val totalCleaned = stats.memoriesDeleted + stats.memoriesArchived
        assertTrue("单次清理应<=批量大小10", totalCleaned <= 10)
    }

    // ==================== 归档管理测试 ====================

    @Test
    fun `归档和恢复记忆`() = runTest {
        // Given: 归档一条记忆
        val config = CleanupConfig(
            enableSoftDelete = true,
            minimumStrengthThreshold = 0.5f,
            minRetentionDays = 0
        )
        scheduler.updateConfig(config, restartScheduler = false)

        val memory = createTestMemory(
            id = "archive_test",
            importance = 0.1f,
            createdAt = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(memory))
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)
        coEvery { memoryCore.saveMemory(any()) } returns Result.success(Unit)

        // When: 执行清理
        scheduler.runNow()

        // Then: 应该在归档中
        val archived = scheduler.getArchivedMemories()
        assertTrue("归档中应有记忆", archived.isNotEmpty())
        assertEquals("archive_test", archived[0].id)

        // When: 恢复
        val result = scheduler.restoreFromArchive("archive_test")

        // Then
        assertTrue(result.isSuccess)
        assertTrue("恢复后归档应为空", scheduler.getArchivedMemories().isEmpty())
        coVerify { memoryCore.saveMemory(any()) }
    }

    @Test
    fun `恢复不存在的记忆失败`() = runTest {
        // When
        val result = scheduler.restoreFromArchive("non_existent")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `清空归档`() = runTest {
        // Given: 有归档记忆
        val config = CleanupConfig(
            enableSoftDelete = true,
            minimumStrengthThreshold = 0.5f,
            minRetentionDays = 0
        )
        scheduler.updateConfig(config, restartScheduler = false)

        val memory = createTestMemory(
            importance = 0.1f,
            createdAt = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(memory))
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        scheduler.runNow()

        // When
        scheduler.clearArchive()

        // Then
        assertTrue("归档应为空", scheduler.getArchivedMemories().isEmpty())
    }

    // ==================== 调度控制测试 ====================

    @Test
    fun `启动和停止调度器`() = runTest {
        // Given
        coEvery { memoryCore.getAllMemories() } returns Result.success(emptyList())

        // When: 启动
        scheduler.start(startImmediately = false)
        delay(100)  // 等待启动

        // Then
        assertTrue("调度器应在运行", scheduler.isRunning.first())

        // When: 停止
        scheduler.stop()
        delay(100)

        // Then
        assertFalse("调度器应已停止", scheduler.isRunning.first())
    }

    @Test
    fun `禁用配置时不启动调度器`() {
        // Given: 禁用清理
        val disabledConfig = CleanupConfig(enabled = false)
        scheduler.updateConfig(disabledConfig, restartScheduler = false)

        // When
        scheduler.start()

        // Then
        assertFalse("调度器不应启动", scheduler.isRunning.value)
    }

    // ==================== 统计信息测试 ====================

    @Test
    fun `统计信息准确性`() = runTest {
        // Given
        val twoDaysAgo = System.currentTimeMillis() - (2L * 24 * 3600 * 1000)

        val memories = listOf(
            createTestMemory(id = "m1", importance = 0.1f, createdAt = twoDaysAgo, lastAccessedAt = twoDaysAgo),
            createTestMemory(id = "m2", importance = 0.5f, createdAt = twoDaysAgo),
            createTestMemory(id = "m3", importance = 0.9f, createdAt = twoDaysAgo)
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(memories)
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        val result = scheduler.runNow()

        // Then
        assertTrue(result.isSuccess)
        val stats = result.getOrThrow()

        assertEquals("应扫描3条记忆", 3, stats.totalMemoriesScanned)
        assertTrue("平均强度应>0", stats.averageStrength > 0f)
        assertTrue("清理耗时应>0", stats.cleanupDurationMs > 0)
    }

    @Test
    fun `获取强度分布`() = runTest {
        // Given
        val memories = listOf(
            createTestMemory(importance = 0.1f),  // 低强度
            createTestMemory(importance = 0.5f),  // 中强度
            createTestMemory(importance = 0.9f, accessCount = 10)  // 高强度
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(memories)

        // When
        val distribution = scheduler.getStrengthDistribution()

        // Then
        assertEquals(5, distribution.size)  // 5个区间
        assertTrue("应有低强度记忆", distribution["0.0-0.2"]!! > 0 || distribution["0.2-0.4"]!! > 0)
    }

    @Test
    fun `预测未来清理数量`() = runTest {
        // Given: 一些会在未来衰减的记忆
        val memories = listOf(
            createTestMemory(
                importance = 0.3f,
                createdAt = System.currentTimeMillis() - (10L * 24 * 3600 * 1000),
                lastAccessedAt = System.currentTimeMillis() - (10L * 24 * 3600 * 1000)
            ),
            createTestMemory(
                importance = 0.9f,
                reinforcementCount = 10
            )
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(memories)

        // When: 预测30天后
        val predictedCount = scheduler.predictCleanupCount(days = 30)

        // Then
        assertTrue("预测数量应>=0", predictedCount >= 0)
    }

    // ==================== 配置管理测试 ====================

    @Test
    fun `更新配置成功`() {
        // Given
        val newConfig = CleanupConfig(
            cleanupIntervalHours = 48,
            minimumStrengthThreshold = 0.15f
        )

        // When
        scheduler.updateConfig(newConfig, restartScheduler = false)

        // Then
        val current = scheduler.getConfig()
        assertEquals(48, current.cleanupIntervalHours)
        assertEquals(0.15f, current.minimumStrengthThreshold, 0.01f)
    }

    @Test
    fun `无效配置抛出异常`() {
        // Given
        val invalidConfig = CleanupConfig(
            minimumStrengthThreshold = 1.5f  // 超出范围
        )

        // When/Then
        assertThrows(IllegalArgumentException::class.java) {
            scheduler.updateConfig(invalidConfig, restartScheduler = false)
        }
    }

    // ==================== 辅助方法 ====================

    private fun createTestMemory(
        id: String = "test_${System.currentTimeMillis()}",
        content: String = "测试内容",
        category: MemoryCategory = MemoryCategory.EPISODIC,
        importance: Float = 0.5f,
        confidence: Float = 0.8f,
        emotionalValence: Float = 0.0f,
        accessCount: Int = 1,
        reinforcementCount: Int = 0,
        createdAt: Long = System.currentTimeMillis(),
        lastAccessedAt: Long = System.currentTimeMillis(),
        timestamp: Long = System.currentTimeMillis()
    ): Memory {
        return Memory(
            id = id,
            content = content,
            category = category,
            importance = importance,
            confidence = confidence,
            emotionalValence = emotionalValence,
            accessCount = accessCount,
            reinforcementCount = reinforcementCount,
            createdAt = createdAt,
            lastAccessedAt = lastAccessedAt,
            timestamp = timestamp
        )
    }
}
