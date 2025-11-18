package com.xiaoguang.assistant.domain.memory.procedural

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ProceduralMemoryManager 单元测试
 *
 * 测试覆盖：
 * 1. 创建和基本操作
 * 2. 执行和学习曲线
 * 3. 条件匹配
 * 4. 自动化判定
 * 5. 遗忘机制
 * 6. 学习进度追踪
 * 7. 统计信息
 *
 * @author Claude Code
 */
class ProceduralMemoryManagerTest {

    private lateinit var manager: ProceduralMemoryManager

    @Before
    fun setup() {
        manager = ProceduralMemoryManager()
    }

    // ==================== 创建测试 ====================

    @Test
    fun `创建程序性记忆成功`() = runTest {
        // When
        val result = manager.create(
            name = "早上问候",
            type = ProceduralType.HABIT,
            pattern = "每天早上主动问候用户",
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME,
                    parameter = "hour",
                    operator = Operator.IN_LIST,
                    value = "6,7,8,9"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.SUGGEST,
                    description = "早上好！今天有什么计划吗？"
                )
            )
        )

        // Then
        assertTrue(result.isSuccess)
        val memory = result.getOrThrow()

        assertEquals("早上问候", memory.name)
        assertEquals(ProceduralType.HABIT, memory.type)
        assertEquals(0.0f, memory.proficiency, 0.01f)  // 新技能从0开始
        assertEquals(1, memory.conditions.size)
        assertEquals(1, memory.actions.size)
    }

    @Test
    fun `创建不同类型的程序性记忆`() = runTest {
        // Given
        val types = ProceduralType.values()

        // When: 创建各种类型
        types.forEach { type ->
            val result = manager.create(
                name = "测试_$type",
                type = type,
                pattern = "测试模式"
            )

            // Then
            assertTrue(result.isSuccess)
        }

        // Then: 验证总数
        val all = manager.getAll()
        assertEquals(types.size, all.size)
    }

    // ==================== 执行和学习测试 ====================

    @Test
    fun `成功执行提升熟练度`() = runTest {
        // Given
        val memory = manager.create(
            name = "测试技能",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When: 连续成功执行3次
        var updated = memory
        repeat(3) {
            updated = manager.execute(
                memoryId = memory.id,
                success = true,
                executionTime = 100
            ).getOrThrow()
        }

        // Then
        assertTrue("熟练度应提升", updated.proficiency > 0.0f)
        assertEquals(3, updated.executionCount)
        assertEquals(1.0f, updated.successRate, 0.01f)  // 全部成功
    }

    @Test
    fun `失败执行降低熟练度`() = runTest {
        // Given: 先提升到一定熟练度
        val memory = manager.create(
            name = "测试技能",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        var updated = memory
        repeat(5) {
            updated = manager.execute(
                memoryId = memory.id,
                success = true,
                executionTime = 100
            ).getOrThrow()
        }

        val proficiencyBefore = updated.proficiency

        // When: 失败一次
        updated = manager.execute(
            memoryId = memory.id,
            success = false,
            executionTime = 100
        ).getOrThrow()

        // Then
        assertTrue("失败应降低熟练度", updated.proficiency < proficiencyBefore)
        assertTrue("成功率应降低", updated.successRate < 1.0f)
    }

    @Test
    fun `熟练度有上限1_0`() = runTest {
        // Given
        val memory = manager.create(
            name = "测试技能",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When: 大量成功执行
        var updated = memory
        repeat(100) {
            updated = manager.execute(
                memoryId = memory.id,
                success = true,
                executionTime = 100
            ).getOrThrow()
        }

        // Then
        assertTrue("熟练度应接近1.0", updated.proficiency >= 0.95f)
        assertTrue("熟练度不应超过1.0", updated.proficiency <= 1.0f)
    }

    @Test
    fun `平均执行时间计算正确`() = runTest {
        // Given
        val memory = manager.create(
            name = "测试技能",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When: 执行3次，耗时100, 200, 300ms
        val times = listOf(100L, 200L, 300L)
        var updated = memory

        times.forEach { time ->
            updated = manager.execute(
                memoryId = memory.id,
                success = true,
                executionTime = time
            ).getOrThrow()
        }

        // Then: 平均应为200ms
        assertEquals(200L, updated.averageExecutionTime)
    }

    // ==================== 条件匹配测试 ====================

    @Test
    fun `EQUALS操作符匹配`() {
        // Given
        val condition = Condition(
            type = ConditionType.CONTEXT,
            parameter = "location",
            operator = Operator.EQUALS,
            value = "home"
        )

        // When/Then
        assertTrue(condition.evaluate(mapOf("location" to "home")))
        assertFalse(condition.evaluate(mapOf("location" to "office")))
    }

    @Test
    fun `CONTAINS操作符匹配`() {
        // Given
        val condition = Condition(
            type = ConditionType.INTENT,
            parameter = "query",
            operator = Operator.CONTAINS,
            value = "天气"
        )

        // When/Then
        assertTrue(condition.evaluate(mapOf("query" to "今天天气怎么样")))
        assertFalse(condition.evaluate(mapOf("query" to "你好")))
    }

    @Test
    fun `GREATER_THAN操作符匹配`() {
        // Given
        val condition = Condition(
            type = ConditionType.TIME,
            parameter = "hour",
            operator = Operator.GREATER_THAN,
            value = "18"
        )

        // When/Then
        assertTrue(condition.evaluate(mapOf("hour" to 20)))
        assertFalse(condition.evaluate(mapOf("hour" to 15)))
    }

    @Test
    fun `IN_LIST操作符匹配`() {
        // Given
        val condition = Condition(
            type = ConditionType.USER_STATE,
            parameter = "mood",
            operator = Operator.IN_LIST,
            value = "happy,excited,joyful"
        )

        // When/Then
        assertTrue(condition.evaluate(mapOf("mood" to "happy")))
        assertTrue(condition.evaluate(mapOf("mood" to "excited")))
        assertFalse(condition.evaluate(mapOf("mood" to "sad")))
    }

    @Test
    fun `查找匹配条件的程序性记忆`() = runTest {
        // Given: 创建带条件的记忆
        manager.create(
            name = "早上问候",
            type = ProceduralType.HABIT,
            pattern = "早上问候",
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME,
                    parameter = "hour",
                    operator = Operator.LESS_THAN,
                    value = "12"
                )
            )
        )

        manager.create(
            name = "晚上问候",
            type = ProceduralType.HABIT,
            pattern = "晚上问候",
            conditions = listOf(
                Condition(
                    type = ConditionType.TIME,
                    parameter = "hour",
                    operator = Operator.GREATER_THAN,
                    value = "18"
                )
            )
        )

        // When: 查找早上的情况
        val morningMatches = manager.findMatching(
            context = mapOf("hour" to 9),
            minProficiency = 0.0f  // 新技能也匹配
        )

        // Then
        assertEquals(1, morningMatches.size)
        assertEquals("早上问候", morningMatches[0].name)

        // When: 查找晚上的情况
        val eveningMatches = manager.findMatching(
            context = mapOf("hour" to 20),
            minProficiency = 0.0f
        )

        // Then
        assertEquals(1, eveningMatches.size)
        assertEquals("晚上问候", eveningMatches[0].name)
    }

    @Test
    fun `熟练度过滤`() = runTest {
        // Given: 创建高熟练度和低熟练度记忆
        val highSkill = manager.create(
            name = "高熟练度",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        val lowSkill = manager.create(
            name = "低熟练度",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // 提升高熟练度技能
        repeat(20) {
            manager.execute(highSkill.id, success = true, executionTime = 100)
        }

        // When: 查找高熟练度技能（阈值0.7）
        val matches = manager.findMatching(
            context = emptyMap(),
            minProficiency = 0.7f
        )

        // Then: 只应匹配高熟练度技能
        assertTrue("应找到至少1个高熟练度技能", matches.isNotEmpty())
        assertTrue("所有匹配的技能熟练度应>=0.7", matches.all { it.proficiency >= 0.7f })
    }

    // ==================== 自动化测试 ====================

    @Test
    fun `判断自动化技能`() = runTest {
        // Given
        val memory = manager.create(
            name = "测试自动化",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When: 执行15次（超过自动化阈值10次）
        repeat(15) {
            manager.execute(memory.id, success = true, executionTime = 100)
        }

        val updated = manager.execute(memory.id, success = true, executionTime = 100).getOrThrow()

        // Then
        assertTrue("应判定为自动化技能", updated.isAutomated())
        assertTrue("熟练度应>=0.9", updated.proficiency >= 0.9f)
        assertTrue("执行次数应>=10", updated.executionCount >= 10)
    }

    @Test
    fun `获取所有自动化技能`() = runTest {
        // Given: 创建2个技能，其中1个练到自动化
        val skill1 = manager.create(
            name = "自动化技能",
            type = ProceduralType.SKILL,
            pattern = "测试1"
        ).getOrThrow()

        val skill2 = manager.create(
            name = "非自动化技能",
            type = ProceduralType.SKILL,
            pattern = "测试2"
        ).getOrThrow()

        // 练习skill1到自动化
        repeat(20) {
            manager.execute(skill1.id, success = true, executionTime = 100)
        }

        // skill2只练习3次
        repeat(3) {
            manager.execute(skill2.id, success = true, executionTime = 100)
        }

        // When
        val automated = manager.getAutomatedSkills()

        // Then
        assertTrue("应有自动化技能", automated.isNotEmpty())
        assertTrue("所有技能应满足自动化条件", automated.all { it.isAutomated() })
        assertEquals("自动化技能", automated[0].name)
    }

    // ==================== 遗忘机制测试 ====================

    @Test
    fun `未使用的技能会衰减`() = runTest {
        // Given: 创建并练习技能
        val memory = manager.create(
            name = "会遗忘的技能",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        repeat(10) {
            manager.execute(memory.id, success = true, executionTime = 100)
        }

        val proficiencyBefore = manager.getAll().first().proficiency

        // When: 应用30天遗忘
        manager.applyDecay(days = 30)

        val proficiencyAfter = manager.getAll().first().proficiency

        // Then
        assertTrue("熟练度应降低", proficiencyAfter < proficiencyBefore)
    }

    @Test
    fun `遗忘不会让熟练度低于0`() = runTest {
        // Given
        val memory = manager.create(
            name = "低熟练度技能",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When: 应用大量遗忘
        manager.applyDecay(days = 1000)

        val updated = manager.getAll().first()

        // Then
        assertTrue("熟练度应>=0", updated.proficiency >= 0f)
    }

    // ==================== 学习进度测试 ====================

    @Test
    fun `获取学习进度`() = runTest {
        // Given
        val memory = manager.create(
            name = "学习测试",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When: 执行5次
        repeat(5) { i ->
            manager.execute(
                memoryId = memory.id,
                success = i < 4,  // 前4次成功，最后1次失败
                executionTime = 100
            )
        }

        // Then
        val progress = manager.getLearningProgress(memory.id)
        assertTrue(progress.isSuccess)

        val progressData = progress.getOrThrow()
        assertEquals(5, progressData.executionHistory.size)
        assertTrue("当前熟练度应>0", progressData.currentProficiency > 0f)
    }

    @Test
    fun `无执行历史时获取进度失败`() {
        // When
        val result = manager.getLearningProgress("non_existent")

        // Then
        assertTrue(result.isFailure)
    }

    // ==================== 统计信息测试 ====================

    @Test
    fun `统计信息准确性`() = runTest {
        // Given: 创建多种类型的记忆
        manager.create("技能1", ProceduralType.SKILL, "测试")
        manager.create("技能2", ProceduralType.SKILL, "测试")
        manager.create("习惯1", ProceduralType.HABIT, "测试")
        val workflow = manager.create("流程1", ProceduralType.WORKFLOW, "测试").getOrThrow()

        // 练习workflow到熟练
        repeat(15) {
            manager.execute(workflow.id, success = true, executionTime = 100)
        }

        // When
        val stats = manager.getStatistics()

        // Then
        assertEquals(4, stats.totalCount)
        assertEquals(2, stats.byType[ProceduralType.SKILL])
        assertEquals(1, stats.byType[ProceduralType.HABIT])
        assertEquals(1, stats.byType[ProceduralType.WORKFLOW])
        assertTrue("应有熟练技能", stats.proficientCount > 0)
        assertTrue("平均熟练度应>0", stats.avgProficiency > 0f)
        assertEquals(15, stats.totalExecutions)
    }

    // ==================== 删除和查询测试 ====================

    @Test
    fun `删除程序性记忆`() = runTest {
        // Given
        val memory = manager.create(
            name = "待删除",
            type = ProceduralType.SKILL,
            pattern = "测试"
        ).getOrThrow()

        // When
        val result = manager.delete(memory.id)

        // Then
        assertTrue(result.isSuccess)
        assertTrue("删除后应查不到", manager.getAll().none { it.id == memory.id })
    }

    @Test
    fun `按类型查询`() = runTest {
        // Given
        manager.create("技能1", ProceduralType.SKILL, "测试")
        manager.create("习惯1", ProceduralType.HABIT, "测试")
        manager.create("技能2", ProceduralType.SKILL, "测试")

        // When
        val skills = manager.getByType(ProceduralType.SKILL)
        val habits = manager.getByType(ProceduralType.HABIT)

        // Then
        assertEquals(2, skills.size)
        assertEquals(1, habits.size)
    }

    @Test
    fun `按标签查询`() = runTest {
        // Given
        manager.create("A", ProceduralType.SKILL, "测试", tags = listOf("重要"))
        manager.create("B", ProceduralType.SKILL, "测试", tags = listOf("普通"))
        manager.create("C", ProceduralType.SKILL, "测试", tags = listOf("重要", "紧急"))

        // When
        val important = manager.getByTag("重要")

        // Then
        assertEquals(2, important.size)
        assertTrue(important.all { "重要" in it.tags })
    }

    // ==================== ProceduralMemory方法测试 ====================

    @Test
    fun `判断熟练程度`() {
        // Given
        val novice = ProceduralMemory(
            name = "新手",
            type = ProceduralType.SKILL,
            pattern = "测试",
            proficiency = 0.3f,
            executionCount = 5
        )

        val expert = ProceduralMemory(
            name = "专家",
            type = ProceduralType.SKILL,
            pattern = "测试",
            proficiency = 0.95f,
            executionCount = 50
        )

        // Then
        assertFalse(novice.isProficient())
        assertTrue(expert.isProficient())
    }

    @Test
    fun `判断可靠性`() {
        // Given
        val reliable = ProceduralMemory(
            name = "可靠",
            type = ProceduralType.SKILL,
            pattern = "测试",
            successRate = 0.95f,
            executionCount = 10
        )

        val unreliable = ProceduralMemory(
            name = "不可靠",
            type = ProceduralType.SKILL,
            pattern = "测试",
            successRate = 0.50f,
            executionCount = 10
        )

        // Then
        assertTrue(reliable.isReliable())
        assertFalse(unreliable.isReliable())
    }
}
