package com.xiaoguang.assistant.domain.memory.models

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 统一的记忆数据模型
 */
data class Memory(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val embedding: List<Float>? = null,
    val importance: Float,
    val confidence: Float = 1.0f,

    // 情感维度
    val emotionTag: String? = null,
    val emotionIntensity: Float = 0f,
    val emotionalValence: Float = 0f,  // -1(消极) 到 +1(积极)

    // 时间维度
    val timestamp: Long,
    val createdAt: Long = timestamp,  // 创建时间（用于遗忘曲线和保护期）
    val lastAccessedAt: Long = timestamp,  // 最后访问时间
    val reinforcementCount: Int = 0,
    val accessCount: Int = 0,  // 访问次数（影响记忆强度）

    // 实体维度
    val relatedEntities: List<String> = emptyList(),  // 提及的人、地点、事物
    val relatedCharacters: List<String> = emptyList(), // 关联的角色ID

    // 认知维度
    val recallDifficulty: Float = 0.5f,  // 回忆难度 0-1
    val contextRelevance: Float = 0.5f,  // 上下文相关性
    val intent: IntentType = IntentType.UNKNOWN,
    val strength: Float? = null,  // 缓存的记忆强度（由MemoryStrengthCalculator计算）
    val similarity: Float = 0f,  // 相似度分数（用于搜索结果排序）

    // 元数据
    val tags: List<String> = emptyList(),
    val source: String? = null,  // 记忆来源（对话、主动记录等）
    val isForgotten: Boolean = false,
    val expiresAt: Long? = null
)

/**
 * 记忆分类
 */
enum class MemoryCategory {
    // 事件记忆（发生了什么）
    EPISODIC,

    // 语义记忆（抽象知识）
    SEMANTIC,

    // 程序记忆（如何做）
    PROCEDURAL,

    // 情景记忆（特定场景）
    CONTEXTUAL,

    // 人物记忆（关于某人的）
    PERSON,

    // 偏好记忆（喜好、习惯）
    PREFERENCE,

    // 事实记忆（客观事实）
    FACT,

    // 纪念日记忆
    ANNIVERSARY
}

/**
 * 意图类型
 */
enum class IntentType {
    UNKNOWN,
    QUESTION,      // 提问
    STATEMENT,     // 陈述
    COMMAND,       // 命令
    EMOTION,       // 情感表达
    GREETING,      // 问候
    FAREWELL,      // 告别
    GRATITUDE,     // 感谢
    APOLOGY,       // 道歉
    AGREEMENT,     // 同意
    DISAGREEMENT,  // 不同意
    DESIRE,        // 表达愿望、需求
    PREFERENCE,    // 表达偏好、喜恶
    INFORM         // 告知信息、通知
}

/**
 * 记忆查询
 */
data class MemoryQuery(
    // 语义维度
    val semantic: String? = null,
    val semanticThreshold: Float = 0.7f,

    // 实体维度
    val entities: List<String>? = null,
    val characters: List<String>? = null,

    // 时间维度
    val temporal: TemporalQuery? = null,

    // 情感维度
    val emotion: EmotionQuery? = null,

    // 分类维度
    val category: MemoryCategory? = null,
    val categories: List<MemoryCategory>? = null,

    // 意图维度
    val intent: IntentType? = null,

    // 过滤条件
    val minImportance: Float? = null,
    val minConfidence: Float? = null,
    val excludeForgotten: Boolean = true,
    val tags: List<String>? = null,

    // 返回控制
    val limit: Int = 10,
    val diversified: Boolean = false  // 是否多样化采样
)

/**
 * 时间查询
 */
sealed class TemporalQuery {
    /**
     * 最近N小时
     */
    data class RecentHours(val hours: Int) : TemporalQuery()

    /**
     * 最近N天
     */
    data class RecentDays(val days: Int) : TemporalQuery()

    /**
     * N天前
     */
    data class DaysAgo(val days: Int) : TemporalQuery()

    /**
     * 时间范围
     */
    data class Range(val from: Long, val to: Long) : TemporalQuery()

    /**
     * 纪念日匹配（月-日，如"12-25"）
     */
    data class AnniversaryMatch(val monthDay: String) : TemporalQuery()

    /**
     * 特定日期
     */
    data class SpecificDate(val date: LocalDate) : TemporalQuery()
}

/**
 * 情感查询
 */
sealed class EmotionQuery {
    /**
     * 任意正面情感
     */
    data object AnyPositive : EmotionQuery()

    /**
     * 任意负面情感
     */
    data object AnyNegative : EmotionQuery()

    /**
     * 高强度情感范围
     */
    data class IntenseRange(val range: ClosedFloatingPointRange<Float>) : EmotionQuery()

    /**
     * 特定情感标签
     */
    data class SpecificEmotion(val tags: List<String>) : EmotionQuery()

    /**
     * 情感效价范围（-1到1）
     */
    data class ValenceRange(val range: ClosedFloatingPointRange<Float>) : EmotionQuery()
}

/**
 * 排序后的记忆
 */
data class RankedMemory(
    val memory: Memory,
    val score: Float,
    val scoreBreakdown: ScoreBreakdown? = null
)

/**
 * 打分详情
 */
data class ScoreBreakdown(
    val semanticScore: Float,
    val recencyScore: Float,
    val importance: Float,
    val emotionScore: Float,
    val entityScore: Float,
    val intentScore: Float
)

/**
 * 记忆统计
 */
data class MemoryStatistics(
    val totalMemories: Int,
    val byCategory: Map<MemoryCategory, Int>,
    val avgImportance: Float,
    val avgReinforcement: Float,
    val forgottenCount: Int,
    val strongMemories: Int,  // 强度>0.7
    val weakMemories: Int,    // 强度<0.3
    val recentlyAccessed: Int // 7天内访问过的
)

/**
 * 迁移结果
 */
data class MigrationResult(
    val migrated: Int,
    val total: Int,
    val failed: List<String> = emptyList()
) {
    val successRate: Float
        get() = if (total > 0) migrated.toFloat() / total else 0f
}

/**
 * 验证结果
 */
data class VerificationResult(
    val oldCount: Int,
    val newCount: Int,
    val inconsistencies: List<String>,
    val sampleSize: Int = 100
) {
    val isConsistent: Boolean
        get() = inconsistencies.isEmpty() && oldCount == newCount

    val consistencyRate: Float
        get() = if (sampleSize > 0)
            (sampleSize - inconsistencies.size).toFloat() / sampleSize
        else 0f
}

// ==================== Memory扩展函数 ====================

/**
 * 增加访问次数并更新最后访问时间
 */
fun Memory.incrementAccess(currentTime: Long = System.currentTimeMillis()): Memory {
    return this.copy(
        accessCount = this.accessCount + 1,
        lastAccessedAt = currentTime
    )
}

/**
 * 增加强化次数
 */
fun Memory.reinforce(amount: Int = 1): Memory {
    return this.copy(
        reinforcementCount = this.reinforcementCount + amount
    )
}

/**
 * 判断是否为新创建的记忆（在保护期内）
 */
fun Memory.isNew(protectionDays: Int = 7, currentTime: Long = System.currentTimeMillis()): Boolean {
    val age = currentTime - this.createdAt
    val protectionPeriod = protectionDays * 24 * 3600 * 1000L
    return age < protectionPeriod
}

/**
 * 计算记忆年龄（天数）
 */
fun Memory.getAgeDays(currentTime: Long = System.currentTimeMillis()): Int {
    val age = currentTime - this.createdAt
    return (age / (24 * 3600 * 1000L)).toInt()
}

/**
 * 计算距上次访问的时间（天数）
 */
fun Memory.getDaysSinceLastAccess(currentTime: Long = System.currentTimeMillis()): Int {
    val timeSinceAccess = currentTime - this.lastAccessedAt
    return (timeSinceAccess / (24 * 3600 * 1000L)).toInt()
}

/**
 * 判断是否为高强度记忆
 */
fun Memory.isStrong(threshold: Float = 0.7f): Boolean {
    return this.strength?.let { it >= threshold } ?: (this.importance >= threshold)
}

/**
 * 判断是否为低强度记忆
 */
fun Memory.isWeak(threshold: Float = 0.3f): Boolean {
    return this.strength?.let { it < threshold } ?: (this.importance < threshold)
}

/**
 * 判断是否为高情感记忆
 */
fun Memory.isEmotional(threshold: Float = 0.5f): Boolean {
    return kotlin.math.abs(this.emotionalValence) >= threshold || this.emotionIntensity >= threshold
}

/**
 * 判断是否经常被访问
 */
fun Memory.isFrequentlyAccessed(threshold: Int = 5): Boolean {
    return this.accessCount >= threshold
}

/**
 * 判断记忆是否应该被清理
 *
 * 综合考虑强度、重要性、年龄等因素
 */
fun Memory.shouldCleanup(
    minimumStrength: Float = 0.2f,
    protectionDays: Int = 7,
    currentTime: Long = System.currentTimeMillis()
): Boolean {
    // 保护新记忆
    if (isNew(protectionDays, currentTime)) return false

    // 保护高重要性记忆
    if (this.importance > 0.8f) return false

    // 检查强度
    val effectiveStrength = this.strength ?: this.importance
    return effectiveStrength < minimumStrength
}

/**
 * 生成记忆摘要（用于日志和调试）
 */
fun Memory.getSummary(maxLength: Int = 50): String {
    val contentPreview = if (this.content.length > maxLength) {
        this.content.take(maxLength) + "..."
    } else {
        this.content
    }

    return buildString {
        append("[${this@getSummary.id.take(8)}] ")
        append("$contentPreview | ")
        append("importance=${String.format("%.2f", this@getSummary.importance)} ")
        if (this@getSummary.strength != null) {
            append("strength=${String.format("%.2f", this@getSummary.strength)} ")
        }
        append("访问${this@getSummary.accessCount}次 ")
        append("强化${this@getSummary.reinforcementCount}次")
    }
}
