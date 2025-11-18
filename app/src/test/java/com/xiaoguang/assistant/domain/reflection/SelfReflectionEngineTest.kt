package com.xiaoguang.assistant.domain.reflection

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * SelfReflectionEngine 单元测试
 *
 * 测试覆盖：
 * 1. 对话质量评估（4个维度）
 * 2. 失败案例分析（6种失败信号）
 * 3. 策略自动调整（基于评估和失败模式）
 */
class SelfReflectionEngineTest {

    private lateinit var engine: SelfReflectionEngine

    @Before
    fun setup() {
        engine = SelfReflectionEngine()
    }

    // ========== 质量评估测试 ==========

    @Test
    fun `test evaluateQuality with high quality response`() = runTest {
        val turn = ConversationTurn(
            id = "test1",
            userInput = "今天天气怎么样？",
            aiResponse = "今天天气很好呢！阳光明媚，温度适宜，非常适合外出活动。你有什么计划吗？",
            timestamp = LocalDateTime.now()
        )

        val result = engine.evaluateQuality(turn)

        assertTrue(result.isSuccess)
        val evaluation = result.getOrNull()!!
        assertTrue(evaluation.overallScore > 0.5f)
        assertTrue(evaluation.strengths.isNotEmpty())
    }

    @Test
    fun `test evaluateQuality with poor response`() = runTest {
        val turn = ConversationTurn(
            id = "test2",
            userInput = "帮我解释一下量子力学",
            aiResponse = "好的",
            timestamp = LocalDateTime.now()
        )

        val result = engine.evaluateQuality(turn)

        assertTrue(result.isSuccess)
        val evaluation = result.getOrNull()!!
        assertTrue(evaluation.overallScore < 0.6f)
        assertTrue(evaluation.weaknesses.contains("回复过于简短"))
    }

    @Test
    fun `test failure analysis detects user correction`() = runTest {
        val turn = ConversationTurn(
            id = "test3",
            userInput = "不对，你说错了",
            aiResponse = "抱歉，让我重新回答",
            timestamp = LocalDateTime.now()
        )

        val result = engine.analyzeFailure(turn)

        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertTrue(analysis.isFailed)
        assertEquals(FailureType.INCORRECT_INFO, analysis.failureType)
    }

    @Test
    fun `test strategy adjustment for low quality`() = runTest {
        val evaluations = listOf(
            QualityEvaluation(
                conversationId = "1",
                timestamp = LocalDateTime.now(),
                relevanceScore = 0.4f,
                coherenceScore = 0.5f,
                helpfulnessScore = 0.5f,
                naturalness = 0.5f,
                overallScore = 0.45f,
                strengths = emptyList(),
                weaknesses = emptyList(),
                suggestions = emptyList()
            )
        )

        val result = engine.adjustStrategy(evaluations, emptyList())

        assertTrue(result.isSuccess)
        val adjustments = result.getOrNull()!!
        assertTrue(adjustments.isNotEmpty())
    }
}