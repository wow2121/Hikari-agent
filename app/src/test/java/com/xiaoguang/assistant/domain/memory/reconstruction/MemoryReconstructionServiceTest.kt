package com.xiaoguang.assistant.domain.memory.reconstruction

import com.xiaoguang.assistant.domain.memory.MemoryCore
import com.xiaoguang.assistant.domain.memory.models.Memory
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import com.xiaoguang.assistant.domain.memory.storage.ChromaVectorStore
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MemoryReconstructionService 单元测试
 *
 * 测试覆盖：
 * 1. 记忆重构（6种类型）
 * 2. 语义相似记忆搜索
 * 3. 记忆合并（无冲突/有冲突）
 * 4. 自动去重
 * 5. 重构历史记录
 * 6. 冲突检测
 * 7. 统计信息
 *
 * @author Claude Code
 */
class MemoryReconstructionServiceTest {

    private lateinit var memoryCore: MemoryCore
    private lateinit var vectorStore: ChromaVectorStore
    private lateinit var service: MemoryReconstructionService

    @Before
    fun setup() {
        memoryCore = mockk()
        vectorStore = mockk()

        service = MemoryReconstructionService(
            memoryCore = memoryCore,
            vectorStore = vectorStore
        )
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    // ==================== 记忆重构测试 ====================

    @Test
    fun `APPEND类型重构 - 追加新证据`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            content = "小明喜欢打篮球"
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "他每周六都会去球场",
            reconstructionType = ReconstructionType.APPEND,
            reason = "补充信息"
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue(updated.content.contains("小明喜欢打篮球"))
        assertTrue(updated.content.contains("他每周六都会去球场"))
        assertTrue(updated.content.contains("【补充信息"))

        coVerify(exactly = 1) { memoryCore.updateMemory(any()) }
    }

    @Test
    fun `UPDATE类型重构 - 智能更新`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            content = "今天天气不错"
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "下午开始下雨了",
            reconstructionType = ReconstructionType.UPDATE
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue(updated.content.contains("今天天气不错"))
        assertTrue(updated.content.contains("下午开始下雨了"))
        assertTrue(updated.content.contains("【更新"))
    }

    @Test
    fun `REPLACE类型重构 - 完全替换`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            content = "旧内容"
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "新内容",
            reconstructionType = ReconstructionType.REPLACE
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertEquals("新内容", updated.content)
        assertFalse(updated.content.contains("旧内容"))
    }

    @Test
    fun `CORRECTION类型重构 - 纠正错误`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            content = "小明的生日是5月1日"
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "小明的生日实际上是5月10日",
            reconstructionType = ReconstructionType.CORRECTION
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue(updated.content.contains("【已纠正】"))
        assertTrue(updated.content.contains("5月10日"))
        assertTrue(updated.content.contains("【原记录】"))
        assertTrue(updated.content.contains("5月1日"))
    }

    @Test
    fun `REINTERPRETATION类型重构 - 重新诠释`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            content = "他今天心情不好"
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "原来是因为工作压力大",
            reconstructionType = ReconstructionType.REINTERPRETATION
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertTrue(updated.content.contains("【新的理解】"))
        assertTrue(updated.content.contains("工作压力大"))
        assertTrue(updated.content.contains("【原记录】"))
        assertTrue(updated.content.contains("心情不好"))
    }

    @Test
    fun `重构时更新情感效价`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            content = "今天还不错",
            emotionalValence = 0.3f
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "晚上发生了很好的事",
            emotionalShift = 0.4f,  // 增加0.4
            reconstructionType = ReconstructionType.APPEND
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertEquals(0.7f, updated.emotionalValence, 0.01f)  // 0.3 + 0.4 = 0.7
    }

    @Test
    fun `情感效价更新时限制在-1到1之间`() = runTest {
        // Given
        val originalMemory = createTestMemory(
            emotionalValence = 0.8f
        )
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "非常好的消息",
            emotionalShift = 0.5f,  // 0.8 + 0.5 = 1.3，应被限制为1.0
            reconstructionType = ReconstructionType.APPEND
        )

        // Then
        assertTrue(result.isSuccess)
        val updated = result.getOrThrow()

        assertEquals(1.0f, updated.emotionalValence, 0.01f)  // 应该被限制为1.0
    }

    @Test
    fun `MERGE类型应该抛出异常提示使用mergeMemories方法`() = runTest {
        // Given
        val originalMemory = createTestMemory()
        coEvery { memoryCore.getMemoryById(originalMemory.id) } returns Result.success(originalMemory)

        // When
        val result = service.reconstructMemory(
            memoryId = originalMemory.id,
            newEvidence = "测试",
            reconstructionType = ReconstructionType.MERGE
        )

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message?.contains("mergeMemories") == true)
    }

    // ==================== 语义搜索测试 ====================

    @Test
    fun `查找相似记忆成功`() = runTest {
        // Given
        val memory = createTestMemory(
            content = "我喜欢吃披萨"
        )

        val similar1 = createTestMemory(
            id = "sim1",
            content = "披萨是我最爱的食物",
            similarity = 0.85f
        )
        val similar2 = createTestMemory(
            id = "sim2",
            content = "我经常点披萨外卖",
            similarity = 0.78f
        )

        coEvery {
            vectorStore.searchSimilar(
                query = memory.content,
                topK = 6,  // 5+1
                category = memory.category
            )
        } returns Result.success(listOf(memory, similar1, similar2))

        // When
        val results = service.findSimilarMemories(memory, topK = 5, minSimilarity = 0.75f)

        // Then
        assertEquals(2, results.size)  // 排除自己
        assertEquals("sim1", results[0].id)  // 按相似度降序
        assertEquals("sim2", results[1].id)
    }

    @Test
    fun `相似度低于阈值的记忆被过滤`() = runTest {
        // Given
        val memory = createTestMemory(content = "测试")

        val similar1 = createTestMemory(id = "high", similarity = 0.85f)
        val similar2 = createTestMemory(id = "low", similarity = 0.50f)

        coEvery {
            vectorStore.searchSimilar(any(), any(), any())
        } returns Result.success(listOf(memory, similar1, similar2))

        // When
        val results = service.findSimilarMemories(memory, minSimilarity = 0.75f)

        // Then
        assertEquals(1, results.size)
        assertEquals("high", results[0].id)
    }

    @Test
    fun `向量搜索失败时返回空列表`() = runTest {
        // Given
        val memory = createTestMemory()
        coEvery {
            vectorStore.searchSimilar(any(), any(), any())
        } returns Result.failure(Exception("搜索失败"))

        // When
        val results = service.findSimilarMemories(memory)

        // Then
        assertTrue(results.isEmpty())
    }

    // ==================== 记忆合并测试 ====================

    @Test
    fun `合并无冲突记忆成功`() = runTest {
        // Given
        val memory1 = createTestMemory(
            id = "mem1",
            content = "小明喜欢篮球",
            timestamp = 1000L
        )
        val memory2 = createTestMemory(
            id = "mem2",
            content = "他每周六打球",
            timestamp = 2000L
        )

        coEvery { memoryCore.getMemoryById("mem1") } returns Result.success(memory1)
        coEvery { memoryCore.getMemoryById("mem2") } returns Result.success(memory2)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.mergeMemories(listOf("mem1", "mem2"))

        // Then
        assertTrue(result.isSuccess)
        val merged = result.getOrThrow()

        // 验证内容合并
        assertTrue(merged.content.contains("小明喜欢篮球") || merged.content.contains("他每周六打球"))

        // 验证删除了旧记忆
        coVerify(atLeast = 1) { memoryCore.deleteMemory(any()) }
    }

    @Test
    fun `合并冲突记忆时使用冲突解决器`() = runTest {
        // Given: 包含冲突关键词"其实"
        val memory1 = createTestMemory(
            id = "mem1",
            content = "小明今年20岁"
        )
        val memory2 = createTestMemory(
            id = "mem2",
            content = "其实小明今年21岁"  // 包含冲突关键词
        )

        coEvery { memoryCore.getMemoryById("mem1") } returns Result.success(memory1)
        coEvery { memoryCore.getMemoryById("mem2") } returns Result.success(memory2)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.mergeMemories(
            memoryIds = listOf("mem1", "mem2"),
            preference = ConflictResolutionPreference.TRUST_LATEST
        )

        // Then
        assertTrue(result.isSuccess)
        // 应该使用冲突解决器而非普通合并策略
        coVerify { memoryCore.updateMemory(any()) }
    }

    @Test
    fun `合并少于2个记忆时抛出异常`() = runTest {
        // When
        val result = service.mergeMemories(listOf("only_one"))

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `合并时记录重构历史`() = runTest {
        // Given
        val memory1 = createTestMemory(id = "mem1", content = "内容1")
        val memory2 = createTestMemory(id = "mem2", content = "内容2")

        coEvery { memoryCore.getMemoryById("mem1") } returns Result.success(memory1)
        coEvery { memoryCore.getMemoryById("mem2") } returns Result.success(memory2)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        service.mergeMemories(listOf("mem1", "mem2"))

        // Then
        val stats = service.getStatistics()
        assertTrue(stats.totalReconstructionRecords > 0)
        assertTrue(stats.reconstructionsByType.containsKey(ReconstructionType.MERGE))
    }

    // ==================== 自动去重测试 ====================

    @Test
    fun `自动检测并合并重复记忆`() = runTest {
        // Given
        val memory1 = createTestMemory(
            id = "dup1",
            content = "今天天气很好"
        )
        val memory2 = createTestMemory(
            id = "dup2",
            content = "今天天气真不错",
            similarity = 0.90f
        )
        val memory3 = createTestMemory(
            id = "unique",
            content = "完全不同的内容"
        )

        coEvery { memoryCore.getAllMemories() } returns Result.success(listOf(memory1, memory2, memory3))

        // Mock相似度搜索
        coEvery {
            vectorStore.searchSimilar(
                query = memory1.content,
                topK = 6,
                category = any()
            )
        } returns Result.success(listOf(memory1, memory2))  // memory1和memory2相似

        coEvery {
            vectorStore.searchSimilar(
                query = memory3.content,
                topK = 6,
                category = any()
            )
        } returns Result.success(listOf(memory3))  // memory3无重复

        coEvery { memoryCore.getMemoryById("dup1") } returns Result.success(memory1)
        coEvery { memoryCore.getMemoryById("dup2") } returns Result.success(memory2)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)
        coEvery { memoryCore.deleteMemory(any()) } returns Result.success(Unit)

        // When
        val result = service.autoMergeDuplicates()

        // Then
        assertTrue(result.isSuccess)
        val mergeCount = result.getOrThrow()

        assertTrue(mergeCount >= 1)  // 至少合并了1组
        coVerify(atLeast = 1) { memoryCore.updateMemory(any()) }
    }

    @Test
    fun `自动去重时可以指定记忆类别`() = runTest {
        // Given
        val episodicMemory = createTestMemory(
            category = MemoryCategory.EPISODIC,
            content = "事件记忆"
        )

        coEvery {
            memoryCore.searchMemories("", category = MemoryCategory.EPISODIC)
        } returns Result.success(listOf(episodicMemory))

        coEvery {
            vectorStore.searchSimilar(any(), any(), any())
        } returns Result.success(listOf(episodicMemory))

        // When
        val result = service.autoMergeDuplicates(category = MemoryCategory.EPISODIC)

        // Then
        assertTrue(result.isSuccess)
        coVerify { memoryCore.searchMemories("", category = MemoryCategory.EPISODIC) }
    }

    // ==================== 重构历史测试 ====================

    @Test
    fun `获取重构历史按时间倒序排列`() = runTest {
        // Given
        val memory = createTestMemory(id = "test_mem")
        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 执行3次重构
        service.reconstructMemory(
            memoryId = memory.id,
            newEvidence = "第一次更新",
            reconstructionType = ReconstructionType.APPEND
        )

        Thread.sleep(10)  // 确保时间戳不同

        service.reconstructMemory(
            memoryId = memory.id,
            newEvidence = "第二次更新",
            reconstructionType = ReconstructionType.UPDATE
        )

        Thread.sleep(10)

        service.reconstructMemory(
            memoryId = memory.id,
            newEvidence = "第三次更新",
            reconstructionType = ReconstructionType.CORRECTION
        )

        // Then
        val history = service.getReconstructionHistory(memory.id)

        assertEquals(3, history.size)
        assertTrue(history[0].timestamp > history[1].timestamp)  // 倒序
        assertTrue(history[1].timestamp > history[2].timestamp)
        assertEquals("第三次更新", history[0].newEvidence)  // 最新的在前
    }

    @Test
    fun `历史记录限制为最多10条`() = runTest {
        // Given
        val memory = createTestMemory(id = "test_mem")
        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 执行15次重构
        repeat(15) { i ->
            service.reconstructMemory(
                memoryId = memory.id,
                newEvidence = "更新$i",
                reconstructionType = ReconstructionType.APPEND
            )
        }

        // Then
        val history = service.getReconstructionHistory(memory.id)

        assertEquals(10, history.size)  // 最多保留10条
        assertEquals("更新14", history[0].newEvidence)  // 最新的
    }

    @Test
    fun `清空历史记录`() = runTest {
        // Given
        val memory = createTestMemory()
        coEvery { memoryCore.getMemoryById(memory.id) } returns Result.success(memory)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        service.reconstructMemory(
            memoryId = memory.id,
            newEvidence = "测试",
            reconstructionType = ReconstructionType.APPEND
        )

        // When
        service.clearHistory()

        // Then
        val history = service.getReconstructionHistory(memory.id)
        assertTrue(history.isEmpty())
    }

    // ==================== 统计信息测试 ====================

    @Test
    fun `统计信息准确性`() = runTest {
        // Given
        val memory1 = createTestMemory(id = "mem1")
        val memory2 = createTestMemory(id = "mem2")

        coEvery { memoryCore.getMemoryById("mem1") } returns Result.success(memory1)
        coEvery { memoryCore.getMemoryById("mem2") } returns Result.success(memory2)
        coEvery { memoryCore.updateMemory(any()) } returns Result.success(Unit)

        // When: 执行不同类型的重构
        service.reconstructMemory("mem1", "证据1", reconstructionType = ReconstructionType.APPEND)
        service.reconstructMemory("mem1", "证据2", reconstructionType = ReconstructionType.UPDATE)
        service.reconstructMemory("mem2", "证据3", reconstructionType = ReconstructionType.CORRECTION)

        // Then
        val stats = service.getStatistics()

        assertEquals(3, stats.totalReconstructionRecords)
        assertEquals(2, stats.memoriesWithHistory)
        assertEquals(1.5f, stats.averageRecordsPerMemory, 0.01f)  // 3条记录 / 2个记忆
        assertEquals(1, stats.reconstructionsByType[ReconstructionType.APPEND])
        assertEquals(1, stats.reconstructionsByType[ReconstructionType.UPDATE])
        assertEquals(1, stats.reconstructionsByType[ReconstructionType.CORRECTION])
    }

    @Test
    fun `空统计信息`() {
        // Given: 没有任何重构

        // When
        val stats = service.getStatistics()

        // Then
        assertEquals(0, stats.totalReconstructionRecords)
        assertEquals(0, stats.memoriesWithHistory)
        assertEquals(0f, stats.averageRecordsPerMemory, 0.01f)
        assertTrue(stats.reconstructionsByType.isEmpty())
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用记忆
     */
    private fun createTestMemory(
        id: String = "test_${System.currentTimeMillis()}",
        content: String = "测试内容",
        category: MemoryCategory = MemoryCategory.EPISODIC,
        importance: Float = 0.5f,
        emotionalValence: Float = 0.0f,
        timestamp: Long = System.currentTimeMillis(),
        similarity: Float = 1.0f
    ): Memory {
        return Memory(
            id = id,
            content = content,
            category = category,
            importance = importance,
            emotionalValence = emotionalValence,
            timestamp = timestamp,
            similarity = similarity
        )
    }
}
