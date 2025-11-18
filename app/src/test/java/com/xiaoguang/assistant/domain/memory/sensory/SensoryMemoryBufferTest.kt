package com.xiaoguang.assistant.domain.memory.sensory

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * SensoryMemoryBuffer 单元测试
 *
 * 测试覆盖：
 * 1. 感官记忆添加和检索
 * 2. 自动过期机制
 * 3. 选择性晋升
 * 4. 环境感知整合
 */
class SensoryMemoryBufferTest {

    private lateinit var buffer: SensoryMemoryBuffer
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        buffer = SensoryMemoryBuffer(testScope)
    }

    // ========== 基础功能测试 ==========

    @Test
    fun `test addSensoryMemory successfully adds memory`() = runTest {
        val input = SensoryInput(
            modality = SensoryModality.VISUAL,
            content = "看到一只猫",
            intensity = 0.8f
        )

        val result = buffer.addSensoryMemory(input)

        assertTrue(result.isSuccess)
        val memories = buffer.getActiveMemories(SensoryModality.VISUAL)
        assertEquals(1, memories.size)
        assertEquals("看到一只猫", memories[0].content)
    }

    @Test
    fun `test getActiveMemories returns only active memories`() = runTest {
        // 添加多个模态的记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "视觉信息", 0.7f)
        )
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.AUDITORY, "听觉信息", 0.6f)
        )
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.TACTILE, "触觉信息", 0.5f)
        )

        val allMemories = buffer.getActiveMemories()
        assertEquals(3, allMemories.size)

        val visualMemories = buffer.getActiveMemories(SensoryModality.VISUAL)
        assertEquals(1, visualMemories.size)
    }

    @Test
    fun `test buffer respects capacity limit per modality`() = runTest {
        val config = SensoryConfig(maxCapacityPerModality = 3)
        buffer.updateConfig(config)

        // 添加超过容量的记忆
        repeat(5) { i ->
            buffer.addSensoryMemory(
                SensoryInput(SensoryModality.VISUAL, "记忆$i", 0.5f)
            )
        }

        val memories = buffer.getActiveMemories(SensoryModality.VISUAL)
        assertEquals(3, memories.size)  // 应该只保留最新的3个
    }

    // ========== 显著性和晋升测试 ==========

    @Test
    fun `test high salience memories are promoted`() = runTest {
        val highIntensityInput = SensoryInput(
            modality = SensoryModality.VISUAL,
            content = "重要视觉信息",
            intensity = 0.9f  // 高强度
        )

        buffer.addSensoryMemory(highIntensityInput)

        // 等待一小段时间让晋升事件处理
        delay(100)

        val promotionEvents = buffer.promotionEvents.value
        assertTrue(promotionEvents.isNotEmpty())
        assertEquals("重要视觉信息", promotionEvents.last().content)
    }

    @Test
    fun `test low salience memories are not promoted`() = runTest {
        val initialPromotions = buffer.promotionEvents.value.size

        val lowIntensityInput = SensoryInput(
            modality = SensoryModality.ENVIRONMENTAL,
            content = "不重要的环境信息",
            intensity = 0.3f  // 低强度
        )

        buffer.addSensoryMemory(lowIntensityInput)
        delay(100)

        val finalPromotions = buffer.promotionEvents.value.size
        assertEquals(initialPromotions, finalPromotions)  // ���应该有新晋升
    }

    @Test
    fun `test salience calculation considers modality`() = runTest {
        // 视觉模态应该有更高的显著性系数
        val visualInput = SensoryInput(
            modality = SensoryModality.VISUAL,
            content = "视觉",
            intensity = 0.6f
        )

        val environmentalInput = SensoryInput(
            modality = SensoryModality.ENVIRONMENTAL,
            content = "环境",
            intensity = 0.6f  // 相同强度
        )

        buffer.addSensoryMemory(visualInput)
        buffer.addSensoryMemory(environmentalInput)

        val visualMemories = buffer.getActiveMemories(SensoryModality.VISUAL)
        val environmentalMemories = buffer.getActiveMemories(SensoryModality.ENVIRONMENTAL)

        // 视觉的显著性应该更高
        assertTrue(visualMemories[0].salience > environmentalMemories[0].salience)
    }

    // ========== 过期和清理测试 ==========

    @Test
    fun `test memories expire after retention period`() = runTest {
        val config = SensoryConfig(retentionSeconds = 1)  // 1秒过期
        buffer.updateConfig(config)

        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "即将过期", 0.5f)
        )

        // 立即检查，应该存在
        var memories = buffer.getActiveMemories()
        assertEquals(1, memories.size)

        // 等待超过过期时间
        delay(1500)
        buffer.cleanup()

        // 再次检查，应该已过期
        memories = buffer.getActiveMemories()
        assertEquals(0, memories.size)
    }

    @Test
    fun `test cleanup removes only expired memories`() = runTest {
        val config = SensoryConfig(retentionSeconds = 2)
        buffer.updateConfig(config)

        // 添加第一个记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "旧记忆", 0.5f)
        )

        // 等待1秒
        delay(1000)

        // 添加第二个记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "新记忆", 0.5f)
        )

        // 再等待1.5秒，此时第一个过期，第二个未过期
        delay(1500)
        val result = buffer.cleanup()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())  // 应该清理了1个

        val memories = buffer.getActiveMemories()
        assertEquals(1, memories.size)
        assertEquals("新记忆", memories[0].content)
    }

    @Test
    fun `test clear removes all memories`() = runTest {
        // 添加多个记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "视觉", 0.5f)
        )
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.AUDITORY, "听觉", 0.5f)
        )

        val result = buffer.clear()

        assertTrue(result.isSuccess)
        val memories = buffer.getActiveMemories()
        assertEquals(0, memories.size)
    }

    // ========== 环境感知测试 ==========

    @Test
    fun `test integrateEnvironmentalAwareness creates summary`() = runTest {
        val envData = EnvironmentalData(
            location = "办公室",
            timeOfDay = "下午",
            lightLevel = 0.7f,
            noiseLevel = 0.4f,
            temperature = 22.5f
        )

        val result = buffer.integrateEnvironmentalAwareness(envData)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertEquals("办公室", summary.location)
        assertEquals("下午", summary.timeOfDay)
        assertEquals(0.7f, summary.lightLevel, 0.01f)
    }

    @Test
    fun `test environmental summary includes dominant modality`() = runTest {
        // 添加多个视觉记忆
        repeat(3) {
            buffer.addSensoryMemory(
                SensoryInput(SensoryModality.VISUAL, "视觉$it", 0.5f)
            )
        }

        // 添加一个听觉记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.AUDITORY, "听觉", 0.5f)
        )

        val envData = EnvironmentalData(location = "测试")
        val result = buffer.integrateEnvironmentalAwareness(envData)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertEquals(SensoryModality.VISUAL, summary.dominantModality)
    }

    @Test
    fun `test attention level reflects high salience memories`() = runTest {
        // 添加高显著性记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "重要", 0.9f)
        )

        // 添加低显著性记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.ENVIRONMENTAL, "不重要", 0.2f)
        )

        val envData = EnvironmentalData(location = "测试")
        val result = buffer.integrateEnvironmentalAwareness(envData)

        assertTrue(result.isSuccess)
        val summary = result.getOrNull()!!
        assertTrue(summary.attentionLevel > 0.3f)  // 应该有一定的注意力水平
    }

    // ========== 状态和统计测试 ==========

    @Test
    fun `test bufferState is updated correctly`() = runTest {
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "视觉", 0.5f)
        )
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.AUDITORY, "听觉", 0.5f)
        )

        val state = buffer.bufferState.value
        assertEquals(1, state.visualCount)
        assertEquals(1, state.auditoryCount)
        assertEquals(2, state.totalCount)
        assertTrue(state.utilizationRate > 0f)
    }

    @Test
    fun `test stats are updated on operations`() = runTest {
        val initialStats = buffer.stats.value

        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "测试", 0.5f)
        )

        val updatedStats = buffer.stats.value
        assertEquals(initialStats.totalReceived + 1, updatedStats.totalReceived)
    }

    @Test
    fun `test stats track promotions`() = runTest {
        val initialStats = buffer.stats.value

        // 添加高显著性记忆
        buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "重要", 0.9f)
        )
        delay(100)

        val updatedStats = buffer.stats.value
        assertTrue(updatedStats.totalPromoted > initialStats.totalPromoted)
    }

    // ========== 配置测试 ==========

    @Test
    fun `test updateConfig changes retention time`() {
        val newConfig = SensoryConfig(retentionSeconds = 10)
        buffer.updateConfig(newConfig)

        val currentConfig = buffer.getConfig()
        assertEquals(10L, currentConfig.retentionSeconds)
    }

    @Test
    fun `test updateConfig changes promotion threshold`() {
        val newConfig = SensoryConfig(promotionThreshold = 0.5f)
        buffer.updateConfig(newConfig)

        val currentConfig = buffer.getConfig()
        assertEquals(0.5f, currentConfig.promotionThreshold, 0.01f)
    }

    // ========== 边界测试 ==========

    @Test
    fun `test handles empty buffer gracefully`() = runTest {
        val memories = buffer.getActiveMemories()
        assertEquals(0, memories.size)

        val result = buffer.cleanup()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `test handles extreme intensity values`() = runTest {
        val result = buffer.addSensoryMemory(
            SensoryInput(SensoryModality.VISUAL, "极端", 2.0f)  // 超出范围
        )

        assertTrue(result.isSuccess)
        val memories = buffer.getActiveMemories()
        assertEquals(1, memories.size)
        // 显著性应该被限制在0-1范围内
        assertTrue(memories[0].salience <= 1f)
    }
}