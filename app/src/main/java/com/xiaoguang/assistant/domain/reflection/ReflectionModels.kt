package com.xiaoguang.assistant.domain.reflection

import java.time.LocalDateTime

/**
 * 反思类型
 */
enum class ReflectionType {
    CONTRADICTION,      // 矛盾检测
    PATTERN_DISCOVERY,  // 模式发现
    GAP_IDENTIFICATION, // 知识缺口识别
    BELIEF_REVISION,    // 信念修正
    COGNITIVE_BIAS,     // 认知偏差检测
    QUALITY_EVALUATION, // 质量评估
    FAILURE_ANALYSIS,   // 失败分析
    STRATEGY_ADJUSTMENT // 策略调整
}

/**
 * 反思优先级
 */
enum class ReflectionPriority {
    CRITICAL,   // 严重矛盾，需立即处理
    HIGH,       // 重要模式或偏差
    MEDIUM,     // 一般性发现
    LOW         // 轻微问题
}

/**
 * 反思结果
 */
data class ReflectionResult(
    val id: String,
    val type: ReflectionType,
    val priority: ReflectionPriority,
    val title: String,
    val description: String,
    val affectedMemoryIds: List<String>,
    val evidenceSnippets: List<String>,
    val suggestedAction: ReflectionAction,
    val confidence: Float,              // 0.0-1.0
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 反思建议的行动
 */
sealed class ReflectionAction {
    data class ReconstructMemory(
        val memoryId: String,
        val reconstructionType: String,
        val newContent: String
    ) : ReflectionAction()

    data class MergeMemories(
        val memoryIds: List<String>,
        val mergeReason: String
    ) : ReflectionAction()

    data class MarkAsUnreliable(
        val memoryId: String,
        val reason: String
    ) : ReflectionAction()

    data class CreateInquiry(
        val question: String,
        val relatedMemoryIds: List<String>
    ) : ReflectionAction()

    data class UpdateBelief(
        val beliefStatement: String,
        val newConfidence: Float,
        val reason: String
    ) : ReflectionAction()
}

/**
 * 矛盾检测结果
 */
data class ContradictionDetection(
    val memoryA: String,
    val memoryB: String,
    val contradictionType: ContradictionType,
    val severity: Float,                // 0.0-1.0
    val conflictingClaims: Pair<String, String>,
    val temporalDistance: Long          // 两条记忆的时间间隔（分钟）
)

/**
 * 矛盾类型
 */
enum class ContradictionType {
    FACTUAL,            // 事实性矛盾（"A是X" vs "A是Y"）
    TEMPORAL,           // 时间性矛盾（"昨天做了X" vs "上周没做过X"）
    EMOTIONAL,          // 情感性矛盾（"喜欢A" vs "讨厌A"）
    INTENTIONAL,        // 意图性矛盾（"打算做X" vs "决定不做X"）
    BELIEF              // 信念性矛盾（"相信A" vs "怀疑A"）
}

/**
 * 模式发现结果
 */
data class PatternDiscovery(
    val patternType: PatternType,
    val description: String,
    val supportingMemories: List<String>,
    val frequency: Int,                 // 模式出现次数
    val confidence: Float,              // 0.0-1.0
    val insight: String                 // 从模式中得出的洞察
)

/**
 * 模式类型
 */
enum class PatternType {
    BEHAVIORAL,         // 行为模式（"每天早上跑步"）
    EMOTIONAL,          // 情绪模式（"压力大时吃甜食"）
    RELATIONAL,         // 关系模式（"与A聊天后心情好"）
    TEMPORAL,           // 时间模式（"每周五加班"）
    CAUSAL              // 因果模式（"做X导致Y"）
}

/**
 * 知识缺口
 */
data class KnowledgeGap(
    val topic: String,
    val missingInformation: String,
    val relatedMemories: List<String>,
    val importance: Float,              // 0.0-1.0
    val suggestedQuery: String          // 建议的询问问题
)

/**
 * 认知偏差检测
 */
data class CognitiveBias(
    val biasType: BiasType,
    val description: String,
    val affectedMemories: List<String>,
    val severity: Float,                // 0.0-1.0
    val correction: String              // 建议的纠正方式
)

/**
 * 偏差类型
 */
enum class BiasType {
    CONFIRMATION,       // 确认偏差（只记住支持已有观点的信息）
    RECENCY,           // 近因偏差（过度重视最近的信息）
    AVAILABILITY,      // 可得性偏差（高估容易想起的事件）
    NEGATIVITY,        // 负面偏差（过度关注负面信息）
    HALO_EFFECT        // 光环效应（一个好特质影响整体评价）
}

/**
 * 反思配置
 */
data class ReflectionConfig(
    val contradictionThreshold: Float = 0.7f,      // 矛盾检测阈值
    val patternMinSupport: Int = 3,                // 模式最小支持次数
    val patternMinConfidence: Float = 0.6f,        // 模式最小置信度
    val biasDetectionEnabled: Boolean = true,      // 是否启用偏差检测
    val maxReflectionsPerSession: Int = 10,        // 每次反思最大结果数
    val reflectionInterval: Long = 24 * 60         // 反思间隔（分钟）
)

/**
 * 反思统计
 */
data class ReflectionStats(
    val totalReflections: Int,
    val contradictionsFound: Int,
    val patternsDiscovered: Int,
    val gapsIdentified: Int,
    val biasesDetected: Int,
    val actionsExecuted: Int,
    val lastReflectionTime: LocalDateTime?
)