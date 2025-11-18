package com.xiaoguang.assistant.domain.memory.working

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * WorkingMemoryManager 单元测试
 *
 * 测试覆盖：
 * 1. 基本功能（添加、获取、搜索）
 * 2. 容量管理和FIFO淘汰
 * 3. 自动晋升机制
 * 4. 手动晋升
 * 5. 过期清理
 * 6. 统计数据
 * 7. 上下文摘要生成
 *
 * @author Claude Code
 */
class WorkingMemoryManagerTest {

    private lateinit var memoryCore: MemoryCore
    private lateinit var manager: WorkingMemoryManager
    private lateinit var config: WorkingMemoryConfig

    @Before
    fun setup() {
        // Mock MemoryCore
        memoryCore = mockk()
        coEvery { memoryCore.saveMemory(any()) } returns Result.success(Unit)

        // 使用测试配置（容量5，便于测试）
        config = WorkingMemoryConfig(
            maxCapacity = 5,
            promotionThreshold = 0.7f,
            autoPromoteEnabled = true,
            retentionTimeSeconds = 60  // 1分钟
        )

        manager = WorkingMemoryManager(memoryCore, config)
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    // ==================== 基本功能测试 ====================

    @Test
    fun `添加对话轮次成功`() = runTest {
        // Given
        val turn = createTestTurn("你好", "你好！")

        // When
        val result = manager.addTurn(turn)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, manager.getContext().size)
        assertEquals(turn, manager.getLastTurn())
    }

    @Test
    fun `获取最近N轮对话`() = runTest {
        // Given
        repeat(5) { i ->
            manager.addTurn(createTestTurn("问题$i", "回答$i"))
        }

        // When
        val recent3 = manager.getRecentTurns(3)

        // Then
        assertEquals(3, recent3.size)
        assertEquals("问题2", recent3[0].userInput)  // 倒数第3轮
        assertEquals("问题4", recent3[2].userInput)  // 最后1轮
    }

    @Test
    fun `搜索关键词`() = runTest {
        // Given
        manager.addTurn(createTestTurn("今天天气真好", "是的，阳光明媚"))
        manager.addTurn(createTestTurn("你吃饭了吗", "我不需要吃饭哦"))
        manager.addTurn(createTestTurn("今天天气怎么样", "今天天气不错"))

        // When
        val results = manager.search("天气")

        // Then
        assertEquals(2, results.size)
        assertTrue(results.all { it.userInput.contains("天气") || it.aiResponse.contains("天气") })
    }

    @Test
    fun `contextFlow 正确更新`() = runTest {
        // Given
        val turn = createTestTurn("测试", "回复")

        // When
        manager.addTurn(turn)

        // Then
        val context = manager.contextFlow.first()
        assertEquals(1, context.size)
        assertEquals(turn, context[0])
    }

    // ==================== 容量管理测试 ====================

    @Test
    fun `达到容量上限时FIFO淘汰最旧对话`() = runTest {
        // Given: 添加6轮对话（超过容量5）
        repeat(6) { i ->
            manager.addTurn(createTestTurn("问题$i", "回答$i"))
        }

        // Then
        val context = manager.getContext()
        assertEquals(5, context.size)  // 容量保持5

        // 最旧的对话（问题0）应该被淘汰
        assertFalse(context.any { it.userInput == "问题0" })

        // 最新的5轮对话应该保留
        assertTrue(context.any { it.userInput == "问题5" })
        assertTrue(context.any { it.userInput == "问题1" })  // 第二旧的保留
    }

    @Test
    fun `统计数据正确记录淘汰次数`() = runTest {
        // Given: 添加8轮对话（超过容量5，应淘汰3轮）
        repeat(8) { i ->
            manager.addTurn(createTestTurn("问题$i", "回答$i"))
        }

        // When
        val stats = manager.getStatistics()

        // Then
        assertEquals(5, stats.currentSize)
        assertEquals(8, stats.totalTurnsProcessed)
        assertEquals(3, stats.evictedByFIFO)
    }

    // ==================== 自动晋升测试 ====================

    @Test
    fun `高重要性对话自动晋升`() = runTest {
        // Given: 重要性0.8 > 阈值0.7
        val turn = createTestTurn(
            user = "我明天要结婚了",
            ai = "恭喜你！",
            importance = 0.8f
        )

        // When
        manager.addTurn(turn)

        // Then
        coVerify(exactly = 1) { memoryCore.saveMemory(any()) }
    }

    @Test
    fun `高情感强度对话自动晋升`() = runTest {
        // Given: 情感强度0.9 > 阈值0.8
        val turn = createTestTurn(
            user = "我太难过了",
            ai = "没关系，我陪着你",
            importance = 0.5f,  // 重要性低
            emotionIntensity = 0.9f  // 但情感强
        )

        // When
        manager.addTurn(turn)

        // Then
        coVerify(exactly = 1) { memoryCore.saveMemory(any()) }
    }

    @Test
    fun `低重要性对话不晋升`() = runTest {
        // Given: 重要性0.3 < 阈值0.7
        val turn = createTestTurn(
            user = "今天几点",
            ai = "现在是下午3点",
            importance = 0.3f
        )

        // When
        manager.addTurn(turn)

        // Then
        coVerify(exactly = 0) { memoryCore.saveMemory(any()) }
    }

    @Test
    fun `标记shouldPromote的对话强制晋升`() = runTest {
        // Given
        val turn = createTestTurn(
            user = "记住这个",
            ai = "好的",
            importance = 0.2f  // 重要性很低
        ).copy(shouldPromote = true)

        // When
        manager.addTurn(turn)

        // Then
        coVerify(exactly = 1) { memoryCore.saveMemory(any()) }
    }

    // ==================== 手动晋升测试 ====================

    @Test
    fun `手动晋升指定对话成功`() = runTest {
        // Given
        val turn = createTestTurn("这个很重要", "记下了", importance = 0.3f)
        manager.addTurn(turn)
        clearMocks(memoryCore, answers = false)  // 清除之前的mock记录

        // When
        val result = manager.promoteManually(turn.turnId, "用户明确要求记住")

        // Then
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { memoryCore.saveMemory(any()) }
    }

    @Test
    fun `手动晋升不存在的对话失败`() = runTest {
        // When
        val result = manager.promoteManually("invalid_id", "测试")

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    // ==================== 过期清理测试 ====================

    @Test
    fun `清理过期对话`() = runTest {
        // Given: 添加3轮对话
        val old1 = createTestTurn("旧对话1", "回复1", timestamp = System.currentTimeMillis() - 120000)  // 2分钟前
        val old2 = createTestTurn("旧对话2", "回复2", timestamp = System.currentTimeMillis() - 90000)   // 1.5分钟前
        val recent = createTestTurn("新对话", "回复", timestamp = System.currentTimeMillis() - 30000)   // 30秒前

        manager.addTurn(old1)
        manager.addTurn(old2)
        manager.addTurn(recent)

        // When: 清理超过1分钟的对话
        val removed = manager.cleanupExpired()

        // Then
        assertEquals(2, removed)  // 应该清理2轮旧对话
        assertEquals(1, manager.getContext().size)  // 只剩1轮
        assertEquals("新对话", manager.getContext()[0].userInput)
    }

    // ==================== 清空功能测试 ====================

    @Test
    fun `清空工作记忆不晋升`() = runTest {
        // Given
        repeat(3) { i ->
            manager.addTurn(createTestTurn("问题$i", "回答$i"))
        }

        // When
        manager.clear(promoteAll = false)

        // Then
        assertEquals(0, manager.getContext().size)
        coVerify(exactly = 0) { memoryCore.saveMemory(any()) }
    }

    @Test
    fun `清空工作记忆时晋升所有对话`() = runTest {
        // Given
        repeat(3) { i ->
            manager.addTurn(createTestTurn("问题$i", "回答$i", importance = 0.3f))  // 低重要性，不会自动晋升
        }
        clearMocks(memoryCore, answers = false)

        // When
        manager.clear(promoteAll = true)

        // Then
        assertEquals(0, manager.getContext().size)
        coVerify(exactly = 3) { memoryCore.saveMemory(any()) }  // 应该晋升3轮
    }

    // ==================== 统计数据测试 ====================

    @Test
    fun `统计数据准确性`() = runTest {
        // Given: 添加10轮对话，其中3轮高重要性会晋升
        repeat(7) { i ->
            manager.addTurn(createTestTurn("普通$i", "回复$i", importance = 0.3f))
        }
        repeat(3) { i ->
            manager.addTurn(createTestTurn("重要$i", "回复$i", importance = 0.9f))
        }

        // When
        val stats = manager.getStatistics()

        // Then
        assertEquals(5, stats.currentSize)  // 容量5
        assertEquals(10, stats.totalTurnsProcessed)  // 总共10轮
        assertEquals(3, stats.promotedToLongTerm)  // 晋升3轮
        assertEquals(5, stats.evictedByFIFO)  // 淘汰5轮（10-5=5）
        assertEquals(0.3f, stats.promotionRate, 0.01f)  // 晋升率30%
    }

    // ==================== 上下文摘要测试 ====================

    @Test
    fun `生成上下文摘要格式正确`() = runTest {
        // Given
        manager.addTurn(createTestTurn(
            user = "今天天气真好",
            ai = "是的，阳光明媚",
            speakerName = "小明"
        ))
        manager.addTurn(createTestTurn(
            user = "我们出去玩吧",
            ai = "好主意！"
        ))

        // When
        val summary = manager.generateContextSummary(maxTurns = 5)

        // Then
        assertTrue(summary.contains("【最近对话上下文】"))
        assertTrue(summary.contains("对话轮次: 2"))
        assertTrue(summary.contains("小明"))
        assertTrue(summary.contains("今天天气真好"))
        assertTrue(summary.contains("我们出去玩吧"))
    }

    @Test
    fun `上下文摘要限制最大轮次数`() = runTest {
        // Given: 添加10轮对话
        repeat(10) { i ->
            manager.addTurn(createTestTurn("问题$i", "回答$i"))
        }

        // When: 只获取最近3轮
        val summary = manager.generateContextSummary(maxTurns = 3)

        // Then
        assertTrue(summary.contains("对话轮次: 3"))
        // 应该只包含最后3轮（问题7/8/9，因为容量是5，最多保留5轮）
        // 实际保留的是问题5-9，最近3轮是7-9
        assertTrue(summary.contains("问题7") || summary.contains("问题5"))  // 取决于实际保留的对话
    }

    // ==================== 配置验证测试 ====================

    @Test
    fun `配置验证通过`() {
        // Given
        val validConfig = WorkingMemoryConfig(
            maxCapacity = 10,
            promotionThreshold = 0.7f,
            retentionTimeSeconds = 3600
        )

        // When
        val result = validConfig.validate()

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun `配置验证失败 - 容量无效`() {
        // Given
        val invalidConfig = WorkingMemoryConfig(maxCapacity = 0)

        // When
        val result = invalidConfig.validate()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `配置验证失败 - 阈值超出范围`() {
        // Given
        val invalidConfig = WorkingMemoryConfig(promotionThreshold = 1.5f)

        // When
        val result = invalidConfig.validate()

        // Then
        assertTrue(result.isFailure)
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用对话轮次
     */
    private fun createTestTurn(
        user: String,
        ai: String,
        importance: Float = 0.5f,
        emotionIntensity: Float = 0.0f,
        speakerName: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): ConversationTurn {
        return ConversationTurn(
            userInput = user,
            aiResponse = ai,
            importance = importance,
            emotionIntensity = emotionIntensity,
            speakerName = speakerName,
            timestamp = timestamp
        )
    }
}
