package com.xiaoguang.assistant.domain.memory.procedural

/**
 * 程序性记忆（Procedural Memory）
 *
 * 代表"如何做"的知识，包括：
 * - 操作技能（如何使用某个功能）
 * - 习惯性行为（用户的常见操作模式）
 * - 条件规则（在X情况下应该Y）
 * - 工作流程（多步骤操作序列）
 *
 * 与陈述性记忆(Episodic/Semantic)不同：
 * - 更难用语言描述
 * - 通过重复强化形成
 * - 自动化执行，不需要意识努力
 *
 * @property id 唯一标识符
 * @property name 技能/习惯名称
 * @property type 程序性记忆类型
 * @property pattern 行为模式描述
 * @property conditions 触发条件列表
 * @property actions 执行动作列表
 * @property proficiency 熟练度（0.0-1.0）
 * @property executionCount 执行次数（影响自动化程度）
 * @property successRate 成功率（影响可靠性）
 * @property averageExecutionTime 平均执行时间（毫秒）
 * @property lastExecutedAt 最后执行时间
 * @property createdAt 创建时间
 * @property tags 标签（用于分类）
 * @property relatedMemoryIds 关联的陈述性记忆ID
 *
 * @author Claude Code
 */
data class ProceduralMemory(
    val id: String = "proc_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}",
    val name: String,
    val type: ProceduralType,
    val pattern: String,  // 行为模式的文字描述
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val proficiency: Float = 0.0f,  // 0.0=新手, 1.0=专家
    val executionCount: Int = 0,
    val successRate: Float = 1.0f,  // 0.0-1.0
    val averageExecutionTime: Long = 0,  // 毫秒
    val lastExecutedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val relatedMemoryIds: List<String> = emptyList()
) {

    /**
     * 判断是否为熟练技能
     */
    fun isProficient(threshold: Float = 0.7f): Boolean {
        return proficiency >= threshold
    }

    /**
     * 判断是否为可靠技能
     */
    fun isReliable(threshold: Float = 0.8f): Boolean {
        return successRate >= threshold && executionCount >= 3
    }

    /**
     * 判断是否为自动化技能（无需思考即可执行）
     */
    fun isAutomated(): Boolean {
        return proficiency >= 0.9f && executionCount >= 10
    }

    /**
     * 生成摘要
     */
    fun getSummary(): String {
        return buildString {
            append("[$type] $name")
            append(" | 熟练度: %.1f%%".format(proficiency * 100))
            append(" | 执行${executionCount}次")
            append(" | 成功率: %.1f%%".format(successRate * 100))
        }
    }
}

/**
 * 程序性记忆类型
 */
enum class ProceduralType {
    /**
     * 操作技能（如何使用功能）
     */
    SKILL,

    /**
     * 习惯行为（重复的行为模式）
     */
    HABIT,

    /**
     * 条件规则（IF-THEN规则）
     */
    RULE,

    /**
     * 工作流程（多步骤序列）
     */
    WORKFLOW,

    /**
     * 偏好设置（用户喜欢的方式）
     */
    PREFERENCE_PATTERN
}

/**
 * 触发条件
 */
data class Condition(
    val type: ConditionType,
    val parameter: String,
    val operator: Operator,
    val value: String
) {
    /**
     * 评估条件是否满足
     */
    fun evaluate(context: Map<String, Any>): Boolean {
        val actualValue = context[parameter] ?: return false

        return when (operator) {
            Operator.EQUALS -> actualValue.toString() == value
            Operator.NOT_EQUALS -> actualValue.toString() != value
            Operator.CONTAINS -> actualValue.toString().contains(value, ignoreCase = true)
            Operator.GREATER_THAN -> {
                val actual = actualValue.toString().toFloatOrNull() ?: return false
                val expected = value.toFloatOrNull() ?: return false
                actual > expected
            }
            Operator.LESS_THAN -> {
                val actual = actualValue.toString().toFloatOrNull() ?: return false
                val expected = value.toFloatOrNull() ?: return false
                actual < expected
            }
            Operator.IN_LIST -> value.split(",").any { it.trim() == actualValue.toString() }
        }
    }

    override fun toString(): String {
        return "$parameter $operator $value"
    }
}

/**
 * 条件类型
 */
enum class ConditionType {
    TIME,          // 时间条件（如"早上"、"晚上"）
    CONTEXT,       // 上下文条件（如"在家"、"在公司"）
    USER_STATE,    // 用户状态（如"忙碌"、"空闲"）
    EMOTION,       // 情绪状态
    INTENT,        // 用户意图
    CUSTOM         // 自定义条件
}

/**
 * 操作符
 */
enum class Operator {
    EQUALS,          // ==
    NOT_EQUALS,      // !=
    CONTAINS,        // 包含
    GREATER_THAN,    // >
    LESS_THAN,       // <
    IN_LIST          // 在列表中
}

/**
 * 执行动作
 */
data class Action(
    val type: ActionType,
    val description: String,
    val parameters: Map<String, String> = emptyMap()
) {
    override fun toString(): String {
        return "[$type] $description"
    }
}

/**
 * 动作类型
 */
enum class ActionType {
    SUGGEST,         // 建议（向用户提供建议）
    EXECUTE,         // 执行（自动执行某操作）
    REMEMBER,        // 记住（存储到记忆）
    NOTIFY,          // 通知（提醒用户）
    QUERY,           // 查询（从记忆中检索）
    ADJUST,          // 调整（修改设置）
    CUSTOM           // 自定义动作
}

/**
 * 执行记录
 */
data class ExecutionRecord(
    val proceduralMemoryId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val executionTime: Long,  // 毫秒
    val context: Map<String, Any> = emptyMap(),
    val error: String? = null
)

/**
 * 学习进度
 */
data class LearningProgress(
    val proceduralMemoryId: String,
    val initialProficiency: Float,
    val currentProficiency: Float,
    val executionHistory: List<ExecutionRecord>,
    val improvementRate: Float  // 进步速率（每次执行的平均提升）
) {
    val totalImprovement: Float
        get() = currentProficiency - initialProficiency

    override fun toString(): String {
        return buildString {
            appendLine("【学习进度】")
            appendLine("初始熟练度: %.1f%%".format(initialProficiency * 100))
            appendLine("当前熟练度: %.1f%%".format(currentProficiency * 100))
            appendLine("总提升: %.1f%%".format(totalImprovement * 100))
            appendLine("执行次数: ${executionHistory.size}")
            appendLine("进步速率: %.3f/次".format(improvementRate))
        }
    }
}
