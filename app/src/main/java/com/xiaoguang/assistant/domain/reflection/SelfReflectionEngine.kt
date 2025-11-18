package com.xiaoguang.assistant.domain.reflection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自我反思引擎
 *
 * 功能：
 * 1. 对话质量评估 - 评估AI回复的质量和用户满意度
 * 2. 失败案例分析 - 识别对话中的失败点和问题
 * 3. 策略自动调整 - 基于反思结果自动调整对话策略
 */
@Singleton
class SelfReflectionEngine @Inject constructor() {

    private val mutex = Mutex()

    // 反思配置
    private var config = ReflectionConfig()

    // 反思历史记录
    private val _reflectionHistory = MutableStateFlow<List<ReflectionRecord>>(emptyList())
    val reflectionHistory: StateFlow<List<ReflectionRecord>> = _reflectionHistory.asStateFlow()

    // 策略调整建议
    private val _strategyAdjustments = MutableStateFlow<List<StrategyAdjustment>>(emptyList())
    val strategyAdjustments: StateFlow<List<StrategyAdjustment>> = _strategyAdjustments.asStateFlow()

    // 统计数据
    private val _stats = MutableStateFlow(
        ReflectionStats(
            totalReflections = 0,
            contradictionsFound = 0,
            patternsDiscovered = 0,
            gapsIdentified = 0,
            biasesDetected = 0,
            actionsExecuted = 0,
            lastReflectionTime = null
        )
    )
    val stats: StateFlow<ReflectionStats> = _stats.asStateFlow()

    /**
     * 评估对话质量
     *
     * @param conversationTurn 对话轮次（包含用户输入和AI回复）
     * @return 质量评估结果
     */
    suspend fun evaluateQuality(conversationTurn: ConversationTurn): Result<QualityEvaluation> {
        return withContext(Dispatchers.Default) {
            try {
                val evaluation = QualityEvaluation(
                    conversationId = conversationTurn.id,
                    timestamp = LocalDateTime.now(),

                    // 评估维度
                    relevanceScore = evaluateRelevance(conversationTurn),
                    coherenceScore = evaluateCoherence(conversationTurn),
                    helpfulnessScore = evaluateHelpfulness(conversationTurn),
                    naturalness = evaluateNaturalness(conversationTurn),

                    // 综合得分
                    overallScore = 0f, // 将在下面计算

                    // 评估说明
                    strengths = identifyStrengths(conversationTurn),
                    weaknesses = identifyWeaknesses(conversationTurn),
                    suggestions = generateSuggestions(conversationTurn)
                )

                // 计算综合得分（加权平均）
                val overallScore = (
                    evaluation.relevanceScore * 0.3f +
                    evaluation.coherenceScore * 0.25f +
                    evaluation.helpfulnessScore * 0.25f +
                    evaluation.naturalness * 0.2f
                )

                val finalEvaluation = evaluation.copy(overallScore = overallScore)

                // 记录评估
                recordEvaluation(finalEvaluation)

                Result.success(finalEvaluation)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 分析失败案例
     *
     * @param conversationTurn 对话轮次
     * @return 失败分析结果
     */
    suspend fun analyzeFailure(conversationTurn: ConversationTurn): Result<FailureAnalysis> {
        return withContext(Dispatchers.Default) {
            try {
                // 检测失败信号
                val failureSignals = detectFailureSignals(conversationTurn)

                if (failureSignals.isEmpty()) {
                    return@withContext Result.success(
                        FailureAnalysis(
                            conversationId = conversationTurn.id,
                            timestamp = LocalDateTime.now(),
                            isFailed = false,
                            failureType = null,
                            rootCause = "无失败信号",
                            impactLevel = 0f,
                            recoveryStrategy = null
                        )
                    )
                }

                // 分析失败类型
                val failureType = classifyFailureType(failureSignals)

                // 找出根本原因
                val rootCause = identifyRootCause(conversationTurn, failureType, failureSignals)

                // 评估影响程度
                val impactLevel = assessImpactLevel(failureSignals)

                // 制定恢复策略
                val recoveryStrategy = formulateRecoveryStrategy(failureType, rootCause)

                val analysis = FailureAnalysis(
                    conversationId = conversationTurn.id,
                    timestamp = LocalDateTime.now(),
                    isFailed = true,
                    failureType = failureType,
                    rootCause = rootCause,
                    impactLevel = impactLevel,
                    recoveryStrategy = recoveryStrategy
                )

                // 记录失败案例
                recordFailure(analysis)

                Result.success(analysis)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 自动调整策略
     *
     * @param recentEvaluations 最近的质量评估
     * @param recentFailures 最近的失败案例
     * @return 策略调整建议
     */
    suspend fun adjustStrategy(
        recentEvaluations: List<QualityEvaluation>,
        recentFailures: List<FailureAnalysis>
    ): Result<List<StrategyAdjustment>> {
        return withContext(Dispatchers.Default) {
            try {
                mutex.withLock {
                    val adjustments = mutableListOf<StrategyAdjustment>()

                    // 分析质量趋势
                    if (recentEvaluations.isNotEmpty()) {
                        val avgScore = recentEvaluations.map { it.overallScore }.average().toFloat()

                        // 如果质量下降，调整策略
                        if (avgScore < 0.6f) {
                            adjustments.add(
                                StrategyAdjustment(
                                    type = AdjustmentType.IMPROVE_RELEVANCE,
                                    priority = AdjustmentPriority.HIGH,
                                    description = "对话质量偏低，需提升回复相关性",
                                    actionPlan = "增强上下文理解，更精确匹配用户意图",
                                    expectedImprovement = 0.2f
                                )
                            )
                        }

                        // 分析常见弱点
                        val commonWeaknesses = recentEvaluations
                            .flatMap { it.weaknesses }
                            .groupingBy { it }
                            .eachCount()
                            .toList()
                            .sortedByDescending { it.second }
                            .take(3)

                        commonWeaknesses.forEach { (weakness, count) ->
                            if (count >= 3) {
                                adjustments.add(
                                    StrategyAdjustment(
                                        type = AdjustmentType.ADDRESS_WEAKNESS,
                                        priority = AdjustmentPriority.MEDIUM,
                                        description = "发现频繁问题: $weakness",
                                        actionPlan = "针对性优化该问题的处理方式",
                                        expectedImprovement = 0.15f
                                    )
                                )
                            }
                        }
                    }

                    // 分析失败模式
                    if (recentFailures.filter { it.isFailed }.size >= 2) {
                        val commonFailureTypes = recentFailures
                            .filter { it.isFailed }
                            .mapNotNull { it.failureType }
                            .groupingBy { it }
                            .eachCount()
                            .toList()
                            .sortedByDescending { it.second }
                            .firstOrNull()

                        commonFailureTypes?.let { (type, count) ->
                            adjustments.add(
                                StrategyAdjustment(
                                    type = AdjustmentType.FIX_FAILURE_PATTERN,
                                    priority = AdjustmentPriority.CRITICAL,
                                    description = "检测到重复失败模式: $type",
                                    actionPlan = "改进该类型对话的处理逻辑",
                                    expectedImprovement = 0.3f
                                )
                            )
                        }
                    }

                    // 更新状态
                    _strategyAdjustments.value = adjustments
                    updateStats(adjustments.size)

                    Result.success(adjustments)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 评估回复的相关性
     */
    private fun evaluateRelevance(turn: ConversationTurn): Float {
        val userInput = turn.userInput.lowercase()
        val aiResponse = turn.aiResponse.lowercase()

        // 简化版本：检查关键词匹配度
        val keywords = extractKeywords(userInput)
        val matchCount = keywords.count { aiResponse.contains(it) }

        return if (keywords.isEmpty()) 0.5f
               else (matchCount.toFloat() / keywords.size).coerceIn(0f, 1f)
    }

    /**
     * 评估回复的连贯性
     */
    private fun evaluateCoherence(turn: ConversationTurn): Float {
        val response = turn.aiResponse

        // 简化版本：检查句子结构
        val hasPunctuation = response.any { it in setOf('。', '！', '？', '，', '.', '!', '?', ',') }
        val hasProperLength = response.length in 10..500
        val notTooRepetitive = !hasRepetitivePattern(response)

        var score = 0.5f
        if (hasPunctuation) score += 0.2f
        if (hasProperLength) score += 0.2f
        if (notTooRepetitive) score += 0.1f

        return score.coerceIn(0f, 1f)
    }

    /**
     * 评估回复的有用性
     */
    private fun evaluateHelpfulness(turn: ConversationTurn): Float {
        val response = turn.aiResponse

        // 简化版本：检查有用信息指标
        val hasSubstantiveContent = response.length > 20
        val hasSpecificInfo = response.any { it.isDigit() } ||
                              response.contains("例如") ||
                              response.contains("具体")
        val notJustAcknowledgment = !response.matches(Regex("好的|知道了|明白了|收到"))

        var score = 0.3f
        if (hasSubstantiveContent) score += 0.3f
        if (hasSpecificInfo) score += 0.2f
        if (notJustAcknowledgment) score += 0.2f

        return score.coerceIn(0f, 1f)
    }

    /**
     * 评估回复的自然度
     */
    private fun evaluateNaturalness(turn: ConversationTurn): Float {
        val response = turn.aiResponse

        // 简化版本：检查自然语言特征
        val hasConversationalMarkers = response.contains("呢") ||
                                       response.contains("吧") ||
                                       response.contains("哦") ||
                                       response.contains("嗯")
        val notTooFormal = !response.contains("敬请") && !response.contains("阁下")
        val hasEmotionalTone = response.contains("😊") || response.contains("😀") || response.contains("🙂")

        var score = 0.5f
        if (hasConversationalMarkers) score += 0.2f
        if (notTooFormal) score += 0.2f
        if (hasEmotionalTone) score += 0.1f

        return score.coerceIn(0f, 1f)
    }

    /**
     * 识别优点
     */
    private fun identifyStrengths(turn: ConversationTurn): List<String> {
        val strengths = mutableListOf<String>()

        if (turn.aiResponse.length > 50) strengths.add("回复详细充分")
        if (turn.aiResponse.contains("？")) strengths.add("主动询问用户需求")
        if (turn.aiResponse.split("。").size > 2) strengths.add("结构清晰分层")

        return strengths
    }

    /**
     * 识别缺点
     */
    private fun identifyWeaknesses(turn: ConversationTurn): List<String> {
        val weaknesses = mutableListOf<String>()

        if (turn.aiResponse.length < 10) weaknesses.add("回复过于简短")
        if (turn.aiResponse.matches(Regex(".*对不起.*"))) weaknesses.add("过度道歉")
        if (hasRepetitivePattern(turn.aiResponse)) weaknesses.add("内容重复冗余")
        if (!turn.aiResponse.any { it in setOf('。', '！', '？') }) weaknesses.add("缺少标点符号")

        return weaknesses
    }

    /**
     * 生成改进建议
     */
    private fun generateSuggestions(turn: ConversationTurn): List<String> {
        val suggestions = mutableListOf<String>()

        val weaknesses = identifyWeaknesses(turn)

        if ("回复过于简短" in weaknesses) suggestions.add("增加更多细节和解释")
        if ("过度道歉" in weaknesses) suggestions.add("减少不必要的道歉，更加自信")
        if ("内容重复冗余" in weaknesses) suggestions.add("精简表达，避免重复")

        return suggestions
    }

    /**
     * 检测失败信号
     */
    private fun detectFailureSignals(turn: ConversationTurn): List<FailureSignal> {
        val signals = mutableListOf<FailureSignal>()

        // 用户不满信号
        val userInput = turn.userInput.lowercase()
        if (userInput.contains("不对") || userInput.contains("错了") || userInput.contains("不是")) {
            signals.add(FailureSignal.USER_CORRECTION)
        }
        if (userInput.contains("听不懂") || userInput.contains("什么意思")) {
            signals.add(FailureSignal.USER_CONFUSION)
        }
        if (userInput.contains("算了") || userInput.contains("不用了")) {
            signals.add(FailureSignal.USER_GIVE_UP)
        }

        // AI回复质量信号
        val response = turn.aiResponse
        if (response.length < 5) {
            signals.add(FailureSignal.TOO_SHORT)
        }
        if (response.contains("抱歉") && response.contains("无法")) {
            signals.add(FailureSignal.INABILITY_TO_HELP)
        }
        if (hasRepetitivePattern(response)) {
            signals.add(FailureSignal.REPETITIVE_CONTENT)
        }

        return signals
    }

    /**
     * 分类失败类型
     */
    private fun classifyFailureType(signals: List<FailureSignal>): FailureType {
        return when {
            FailureSignal.USER_CORRECTION in signals -> FailureType.INCORRECT_INFO
            FailureSignal.USER_CONFUSION in signals -> FailureType.UNCLEAR_EXPLANATION
            FailureSignal.USER_GIVE_UP in signals -> FailureType.UNHELPFUL_RESPONSE
            FailureSignal.INABILITY_TO_HELP in signals -> FailureType.CAPABILITY_LIMITATION
            FailureSignal.TOO_SHORT in signals -> FailureType.INSUFFICIENT_RESPONSE
            else -> FailureType.OTHER
        }
    }

    /**
     * 识别根本原因
     */
    private fun identifyRootCause(
        turn: ConversationTurn,
        failureType: FailureType,
        signals: List<FailureSignal>
    ): String {
        return when (failureType) {
            FailureType.INCORRECT_INFO -> "提供了不准确或错误的信息"
            FailureType.UNCLEAR_EXPLANATION -> "解释不够清晰，用户难以理解"
            FailureType.UNHELPFUL_RESPONSE -> "回复未能满足用户需求"
            FailureType.CAPABILITY_LIMITATION -> "超出AI当前能力范围"
            FailureType.INSUFFICIENT_RESPONSE -> "回复内容不充分"
            FailureType.OTHER -> "未明确的失败原因"
        }
    }

    /**
     * 评估影响程度
     */
    private fun assessImpactLevel(signals: List<FailureSignal>): Float {
        var impact = 0f

        if (FailureSignal.USER_GIVE_UP in signals) impact += 0.4f
        if (FailureSignal.USER_CORRECTION in signals) impact += 0.3f
        if (FailureSignal.USER_CONFUSION in signals) impact += 0.2f
        if (FailureSignal.INABILITY_TO_HELP in signals) impact += 0.2f
        if (FailureSignal.TOO_SHORT in signals) impact += 0.1f
        if (FailureSignal.REPETITIVE_CONTENT in signals) impact += 0.1f

        return impact.coerceIn(0f, 1f)
    }

    /**
     * 制定恢复策略
     */
    private fun formulateRecoveryStrategy(failureType: FailureType, rootCause: String): String {
        return when (failureType) {
            FailureType.INCORRECT_INFO -> "立即纠正错误，提供准确信息，并道歉"
            FailureType.UNCLEAR_EXPLANATION -> "用更简单的语言重新解释，增加例子"
            FailureType.UNHELPFUL_RESPONSE -> "询问用户具体需求，提供更有针对性的帮助"
            FailureType.CAPABILITY_LIMITATION -> "诚实说明限制，建议替代方案"
            FailureType.INSUFFICIENT_RESPONSE -> "补充更多细节和信息"
            FailureType.OTHER -> "总结讨论，询问用户还需要什么帮助"
        }
    }

    /**
     * 提取关键词
     */
    private fun extractKeywords(text: String): List<String> {
        // 简化版本：移除停用词
        val stopWords = setOf("的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "个")
        return text.split(" ", "，", "。", "？", "！")
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
    }

    /**
     * 检测重复模式
     */
    private fun hasRepetitivePattern(text: String): Boolean {
        if (text.length < 20) return false

        // 检查连续3个字符的重复
        for (i in 0 until text.length - 5) {
            val pattern = text.substring(i, i + 3)
            val remaining = text.substring(i + 3)
            if (remaining.contains(pattern)) {
                return true
            }
        }

        return false
    }

    /**
     * 记录质量评估
     */
    private fun recordEvaluation(evaluation: QualityEvaluation) {
        val record = ReflectionRecord(
            timestamp = evaluation.timestamp,
            type = ReflectionType.QUALITY_EVALUATION,
            content = "质量得分: ${String.format("%.2f", evaluation.overallScore)}",
            details = "相关性=${evaluation.relevanceScore}, 连贯性=${evaluation.coherenceScore}, " +
                     "有用性=${evaluation.helpfulnessScore}, 自然度=${evaluation.naturalness}"
        )

        _reflectionHistory.value = (_reflectionHistory.value + record).takeLast(100)

        _stats.value = _stats.value.copy(
            totalReflections = _stats.value.totalReflections + 1,
            lastReflectionTime = LocalDateTime.now()
        )
    }

    /**
     * 记录失败案例
     */
    private fun recordFailure(analysis: FailureAnalysis) {
        val record = ReflectionRecord(
            timestamp = analysis.timestamp,
            type = ReflectionType.FAILURE_ANALYSIS,
            content = "失败类型: ${analysis.failureType}",
            details = "根本原因: ${analysis.rootCause}, 影响程度: ${analysis.impactLevel}"
        )

        _reflectionHistory.value = (_reflectionHistory.value + record).takeLast(100)

        _stats.value = _stats.value.copy(
            totalReflections = _stats.value.totalReflections + 1,
            lastReflectionTime = LocalDateTime.now()
        )
    }

    /**
     * 更新统计数据
     */
    private fun updateStats(adjustmentCount: Int) {
        _stats.value = _stats.value.copy(
            totalReflections = _stats.value.totalReflections + adjustmentCount,
            lastReflectionTime = LocalDateTime.now()
        )
    }

    /**
     * 更新配置
     */
    fun updateConfig(newConfig: ReflectionConfig) {
        config = newConfig
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): ReflectionConfig = config
}

// ========== 数据模型 ==========

/**
 * 对话轮次（简化版本）
 */
data class ConversationTurn(
    val id: String,
    val userInput: String,
    val aiResponse: String,
    val timestamp: LocalDateTime
)

/**
 * 质量评估结果
 */
data class QualityEvaluation(
    val conversationId: String,
    val timestamp: LocalDateTime,
    val relevanceScore: Float,        // 相关性 0-1
    val coherenceScore: Float,        // 连贯性 0-1
    val helpfulnessScore: Float,      // 有用性 0-1
    val naturalness: Float,           // 自然度 0-1
    val overallScore: Float,          // 综合得分 0-1
    val strengths: List<String>,      // 优点
    val weaknesses: List<String>,     // 缺点
    val suggestions: List<String>     // 改进建议
)

/**
 * 失败分析结果
 */
data class FailureAnalysis(
    val conversationId: String,
    val timestamp: LocalDateTime,
    val isFailed: Boolean,
    val failureType: FailureType?,
    val rootCause: String,
    val impactLevel: Float,           // 影响程度 0-1
    val recoveryStrategy: String?
)

/**
 * 失败类型
 */
enum class FailureType {
    INCORRECT_INFO,         // 信息错误
    UNCLEAR_EXPLANATION,    // 解释不清
    UNHELPFUL_RESPONSE,     // 回复无用
    CAPABILITY_LIMITATION,  // 能力限制
    INSUFFICIENT_RESPONSE,  // 回复不足
    OTHER                   // 其他
}

/**
 * 失败信号
 */
enum class FailureSignal {
    USER_CORRECTION,        // 用户纠正
    USER_CONFUSION,         // 用户困惑
    USER_GIVE_UP,          // 用户放弃
    TOO_SHORT,             // 回复过短
    INABILITY_TO_HELP,     // 无法帮助
    REPETITIVE_CONTENT     // 内容重复
}

/**
 * 策略调整
 */
data class StrategyAdjustment(
    val type: AdjustmentType,
    val priority: AdjustmentPriority,
    val description: String,
    val actionPlan: String,
    val expectedImprovement: Float    // 预期改进幅度 0-1
)

/**
 * 调整类型
 */
enum class AdjustmentType {
    IMPROVE_RELEVANCE,      // 提升相关性
    IMPROVE_COHERENCE,      // 提升连贯性
    IMPROVE_HELPFULNESS,    // 提升有用性
    IMPROVE_NATURALNESS,    // 提升自然度
    ADDRESS_WEAKNESS,       // 解决弱点
    FIX_FAILURE_PATTERN     // 修复失败模式
}

/**
 * 调整优先级
 */
enum class AdjustmentPriority {
    CRITICAL,   // 严重
    HIGH,       // 高
    MEDIUM,     // 中
    LOW         // 低
}

/**
 * 反思记录
 */
data class ReflectionRecord(
    val timestamp: LocalDateTime,
    val type: ReflectionType,
    val content: String,
    val details: String
)