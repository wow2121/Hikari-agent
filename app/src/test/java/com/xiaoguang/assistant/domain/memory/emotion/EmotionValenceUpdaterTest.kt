package com.xiaoguang.assistant.domain.memory.emotion

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * EmotionValenceUpdater 单元测试
 *
 * 测试覆盖：
 * 1. 情感检测和更新
 * 2. Valence-Arousal模型
 * 3. 情感衰减机制
 * 4. 情感强化
 * 5. 情感变化追踪
 * 6. 统计分析
 *
 * @author Claude Code
 */
class EmotionValenceUpdaterTest {

    private lateinit var memoryCore: MemoryCore
    private lateinit var emotionDetector: EmotionDetector
    private lateinit var updater: EmotionValenceUpdater

    @Before
    fun setup() {
        memoryCore = mockk()
        emotionDetector = KeywordEmotionDetector()
        updater = EmotionValenceUpdater(
            memoryCore = memoryCore,
            emotionDetector = emotionDetector
        )
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    // ==================== EmotionState测试 ====================

    @Test
    fun `EmotionState创建和验证`() {
        // Given/When
        val emotion = EmotionState(
            valence = 0.8f,
            arousal = 0.6f,
            dominantEmotion = EmotionLabel.JOY
        )

        // Then
        assertEquals(0.8f, emotion.valence, 0.01f)
        assertEquals(0.6f, emotion.arousal, 0.01f)
        assertEquals(EmotionLabel.JOY, emotion.dominantEmotion)
        assertTrue(emotion.isPositive)
        assertFalse(emotion.isNegative)
        assertEquals(EmotionQuadrant.HIGH_POSITIVE, emotion.quadrant)
    }

    @Test
    fun `情感强度计算`() {
        // Given
        val highIntensity = EmotionState(valence = 0.9f, arousal = 0.8f)
        val lowIntensity = EmotionState(valence = 0.3f, arousal = 0.2f)

        // Then
        assertTrue("高强度应>低强度", highIntensity.intensity > lowIntensity.intensity)
        assertEquals(0.72f, highIntensity.intensity, 0.01f)  // 0.9 * 0.8 = 0.72
        assertEquals(0.06f, lowIntensity.intensity, 0.01f)   // 0.3 * 0.2 = 0.06
    }

    @Test
    fun `valence超出范围抛出异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmotionState(valence = 1.5f, arousal = 0.5f)
        }

        assertThrows(IllegalArgumentException::class.java) {
            EmotionState(valence = -1.5f, arousal = 0.5f)
        }
    }

    @Test
    fun `arousal超出范围抛出异常`() {
        assertThrows(IllegalArgumentException::class.java) {
            EmotionState(valence = 0.5f, arousal = 1.5f)
        }

        assertThrows(IllegalArgumentException::class.java) {
            EmotionState(valence = 0.5f, arousal = -0.5f)
        }
    }

    @Test
    fun `情感象限判定`() {
        // High Positive (兴奋)
        val joy = EmotionState(valence = 0.8f, arousal = 0.7f)
        assertEquals(EmotionQuadrant.HIGH_POSITIVE, joy.quadrant)

        // Low Positive (平静)
        val calm = EmotionState(valence = 0.3f, arousal = 0.2f)
        assertEquals(EmotionQuadrant.LOW_POSITIVE, calm.quadrant)

        // High Negative (愤怒)
        val anger = EmotionState(valence = -0.8f, arousal = 0.9f)
        assertEquals(EmotionQuadrant.HIGH_NEGATIVE, anger.quadrant)

        // Low Negative (悲伤)
        val sadness = EmotionState(valence = -0.7f, arousal = 0.3f)
        assertEquals(EmotionQuadrant.LOW_NEGATIVE, sadness.quadrant)
    }

    // ==================== 情感检测测试 ====================

    @Test
    fun `检测正面情感`() {
        // Given
        val positiveText = "今天真的太开心了！我非常喜欢这个礼物"

        // When
        val emotion = emotionDetector.detect(positiveText)

        // Then
        assertTrue("应检测为正面情感", emotion.valence > 0.5f)
        assertTrue("应为高唤醒", emotion.arousal > 0.6f)  // 包含"太"、"非常"
        assertEquals(EmotionLabel.JOY, emotion.dominantEmotion)
    }

    @Test
    fun `检测负面情感`() {
        // Given
        val negativeText = "我太难过了，感觉非常失望"

        // When
        val emotion = emotionDetector.detect(negativeText)

        // Then
        assertTrue("应检测为负面情感", emotion.valence < -0.5f)
        assertTrue("应为高唤醒", emotion.arousal > 0.6f)
    }

    @Test
    fun `检测中性情感`() {
        // Given
        val neutralText = "今天天气不错，去了公园散步"

        // When
        val emotion = emotionDetector.detect(neutralText)

        // Then
        assertTrue("应检测为中性", emotion.valence >= -0.2f && emotion.valence <= 0.2f)
        assertEquals(EmotionLabel.NEUTRAL, emotion.dominantEmotion)
    }

    // ==================== 情感更新测试 ====================

    @Test
    fun `更新记忆情感成功`() = runTest {
        // Given
        val memory = createTestMemory(
            content = "今天很开心",
            emotionalValence = 0.0f,
            emotionIntensity = 0.0f
        )

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = updater.updateEmotion(
            memoryId = memory.id,
            context = memory.content
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue("情感应更新为正面", updated.emotionalValence > 0f)
        coVerify { memoryCore.updateMemory(any()) }
    }

    @Test
    fun `手动指定情感状态`() = runTest {
        // Given
        val memory = createTestMemory()
        val manualEmotion = EmotionState(
            valence = -0.8f,
            arousal = 0.9f,
            dominantEmotion = EmotionLabel.ANGER
        )

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = updater.updateEmotion(
            memoryId = memory.id,
            context = memory.content,
            manualEmotion = manualEmotion
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertEquals(-0.8f, updated.emotionalValence, 0.01f)
        assertEquals(0.9f, updated.emotionIntensity, 0.01f)
        assertEquals("愤怒", updated.emotionTag)
    }

    @Test
    fun `批量更新多个记忆`() = runTest {
        // Given
        val memories = listOf(
            createTestMemory(id = "m1", content = "开心"),
            createTestMemory(id = "m2", content = "难过"),
            createTestMemory(id = "m3", content = "平静")
        )

        memories.forEach { memory ->
            coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        }
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val successCount = updater.batchUpdate(memories)

        // Then
        assertEquals(3, successCount)
        coVerify(exactly = 3) { memoryCore.updateMemory(any()) }
    }

    // ==================== 情感衰减测试 ====================

    @Test
    fun `正面情感向中性衰减`() {
        // Given
        val config = EmotionDecayConfig(
            decayRate = 0.1f,  // 每天衰减10%
            maxValence = 0.1f
        )
        val original = EmotionState(valence = 0.8f, arousal = 0.6f)

        // When: 10天后
        val decayed = config.applyDecay(original, daysPassed = 10)

        // Then
        assertTrue("正面情感应降低", decayed.valence < original.valence)
        assertTrue("不应低于maxValence", decayed.valence >= config.maxValence)
        assertTrue("唤醒度应降低", decayed.arousal < original.arousal)
    }

    @Test
    fun `负面情感向中性衰减`() {
        // Given
        val config = EmotionDecayConfig(
            decayRate = 0.1f,
            minValence = -0.1f
        )
        val original = EmotionState(valence = -0.8f, arousal = 0.7f)

        // When: 10天后
        val decayed = config.applyDecay(original, daysPassed = 10)

        // Then
        assertTrue("负面情感应减弱", decayed.valence > original.valence)
        assertTrue("不应高于minValence", decayed.valence <= config.minValence)
    }

    @Test
    fun `衰减后唤醒度降低`() {
        // Given
        val config = EmotionDecayConfig(arousalDecayRate = 0.1f)
        val original = EmotionState(valence = 0.5f, arousal = 0.9f)

        // When: 5天后
        val decayed = config.applyDecay(original, daysPassed = 5)

        // Then
        assertTrue("唤醒度应显著降低", decayed.arousal < original.arousal * 0.6f)
    }

    @Test
    fun `应用衰减到记忆`() = runTest {
        // Given: 3天前创建的正面记忆
        val threeDaysAgo = System.currentTimeMillis() - (3L * 24 * 3600 * 1000)

        val memory = createTestMemory(
            emotionalValence = 0.8f,
            emotionIntensity = 0.7f,
            lastAccessedAt = threeDaysAgo
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(memory))
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = updater.applyDecay()

        // Then
        assertTrue(result.isSuccess)
        val decayedCount = result.getOrThrow()

        assertTrue("应有记忆被衰减", decayedCount > 0)
        coVerify(atLeast = 1) { memoryCore.updateMemory(any()) }
    }

    // ==================== 情感强化测试 ====================

    @Test
    fun `强化情感增加强度`() = runTest {
        // Given
        val memory = createTestMemory(
            emotionalValence = 0.5f,
            emotionIntensity = 0.4f
        )

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 1.5倍强化
        val result = updater.amplifyEmotion(memory.id, amplification = 1.5f)

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue("valence应增加", updated.emotionalValence > memory.emotionalValence)
        assertTrue("arousal应增加", updated.emotionIntensity > memory.emotionIntensity)
        assertEquals(0.75f, updated.emotionalValence, 0.01f)  // 0.5 * 1.5 = 0.75
        assertEquals(0.6f, updated.emotionIntensity, 0.01f)   // 0.4 * 1.5 = 0.6
    }

    @Test
    fun `强化不超过极限值`() = runTest {
        // Given: 已经很高的情感值
        val memory = createTestMemory(
            emotionalValence = 0.9f,
            emotionIntensity = 0.95f
        )

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 2倍强化
        val result = updater.amplifyEmotion(memory.id, amplification = 2.0f)

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue("valence不应超过1.0", updated.emotionalValence <= 1.0f)
        assertTrue("arousal不应超过1.0", updated.emotionIntensity <= 1.0f)
    }

    @Test
    fun `放大系数超出范围抛出异常`() = runTest {
        // Given
        val memory = createTestMemory()
        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)

        // When/Then
        val result = updater.amplifyEmotion(memory.id, amplification = 2.5f)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // ==================== 情感变化追踪测试 ====================

    @Test
    fun `记录情感变化历史`() = runTest {
        // Given
        val memory = createTestMemory(emotionalValence = 0.0f)

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 更新情感3次
        repeat(3) {
            updater.updateEmotion(memory.id, "开心")
        }

        // Then
        val history = updater.getEmotionHistory(memory.id)
        assertEquals(3, history.size)
    }

    @Test
    fun `检测显著情感变化`() = runTest {
        // Given: 从中性到强烈正面
        val memory = createTestMemory(emotionalValence = 0.0f)
        val strongEmotion = EmotionState(valence = 0.9f, arousal = 0.8f)

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        updater.updateEmotion(
            memoryId = memory.id,
            context = memory.content,
            manualEmotion = strongEmotion
        )

        // Then
        val significantChanges = updater.getSignificantChanges(threshold = 0.3f)

        assertTrue("应检测到显著变化", significantChanges.isNotEmpty())
        assertTrue("变化应为显著", significantChanges[0].isSignificant(0.3f))
    }

    @Test
    fun `情感变化历史限制50条`() = runTest {
        // Given
        val memory = createTestMemory()

        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 更新60次
        repeat(60) {
            updater.updateEmotion(memory.id, "测试")
        }

        // Then
        val history = updater.getEmotionHistory(memory.id)
        assertEquals(50, history.size)  // 最多保留50条
    }

    // ==================== 统计分析测试 ====================

    @Test
    fun `统计情感分布`() = runTest {
        // Given
        val memories = listOf(
            createTestMemory(id = "m1", emotionalValence = 0.8f),  // 正面
            createTestMemory(id = "m2", emotionalValence = -0.7f), // 负面
            createTestMemory(id = "m3", emotionalValence = 0.1f),  // 中性
            createTestMemory(id = "m4", emotionalValence = 0.6f)   // 正面
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(memories)

        // When
        val stats = updater.getStatistics()

        // Then
        assertEquals(4, stats.totalMemories)
        assertEquals(2, stats.positiveCount)
        assertEquals(1, stats.negativeCount)
        assertEquals(1, stats.neutralCount)
        assertEquals(0.5f, stats.positiveRate, 0.01f)  // 2/4 = 0.5
    }

    @Test
    fun `统计平均情感值`() = runTest {
        // Given
        val memories = listOf(
            createTestMemory(emotionalValence = 0.8f, emotionIntensity = 0.6f),
            createTestMemory(emotionalValence = -0.4f, emotionIntensity = 0.4f),
            createTestMemory(emotionalValence = 0.2f, emotionIntensity = 0.2f)
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(memories)

        // When
        val stats = updater.getStatistics()

        // Then
        val expectedAvgValence = (0.8f - 0.4f + 0.2f) / 3
        val expectedAvgArousal = (0.6f + 0.4f + 0.2f) / 3

        assertEquals(expectedAvgValence, stats.avgValence, 0.01f)
        assertEquals(expectedAvgArousal, stats.avgArousal, 0.01f)
    }

    @Test
    fun `统计主导象限`() = runTest {
        // Given: 大部分记忆为高激动正面
        val memories = listOf(
            createTestMemory(emotionalValence = 0.8f, emotionIntensity = 0.7f),  // HIGH_POSITIVE
            createTestMemory(emotionalValence = 0.7f, emotionIntensity = 0.6f),  // HIGH_POSITIVE
            createTestMemory(emotionalValence = 0.3f, emotionIntensity = 0.2f)   // LOW_POSITIVE
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(memories)

        // When
        val stats = updater.getStatistics()

        // Then
        assertEquals(EmotionQuadrant.HIGH_POSITIVE, stats.dominantQuadrant)
    }

    // ==================== EmotionChange测试 ====================

    @Test
    fun `EmotionChange计算变化量`() {
        // Given
        val change = EmotionChange(
            memoryId = "test",
            previousEmotion = EmotionState(valence = 0.3f, arousal = 0.4f),
            newEmotion = EmotionState(valence = 0.8f, arousal = 0.7f),
            reason = "测试"
        )

        // Then
        assertEquals(0.5f, change.valenceDelta, 0.01f)  // 0.8 - 0.3 = 0.5
        assertEquals(0.3f, change.arousalDelta, 0.01f)  // 0.7 - 0.4 = 0.3
        assertTrue(change.isSignificant(0.3f))
    }

    // ==================== 辅助方法 ====================

    private fun createTestMemory(
        id: String = "test_${System.currentTimeMillis()}",
        content: String = "测试内容",
        emotionalValence: Float = 0.0f,
        emotionIntensity: Float = 0.0f,
        emotionTag: String? = null,
        lastAccessedAt: Long = System.currentTimeMillis()
    ): Memory {
        return Memory(
            id = id,
            content = content,
            category = MemoryCategory.EPISODIC,
            importance = 0.5f,
            emotionalValence = emotionalValence,
            emotionIntensity = emotionIntensity,
            emotionTag = emotionTag,
            timestamp = System.currentTimeMillis(),
            lastAccessedAt = lastAccessedAt
        )
    }
}
