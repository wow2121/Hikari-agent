package com.xiaoguang.assistant.domain.personality

import com.xiaoguang.assistant.domain.model.EmotionalState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 不完美性引擎
 *
 * 职责：
 * 1. 让小光偶尔说错话（然后道歉）
 * 2. 对某些话题固执己见
 * 3. 会闹小情绪（生气时不理人）
 * 4. 善意的小谎言
 * 5. 低概率记错事情
 *
 * 让AI更真实、更有人性
 */
@Singleton
class ImperfectionEngine @Inject constructor() {

    // 不完美率配置
    private var imperfectionRate = 0.05f  // 5%概率出现不完美

    // 固执话题列表
    private val stubbornTopics = mutableSetOf<String>()

    // 小情绪状态
    private var isPoutingTime = System.currentTimeMillis()

    /**
     * 是否触发不完美行为
     */
    fun shouldTriggerImperfection(): Boolean {
        return Random.nextFloat() < imperfectionRate
    }

    /**
     * 处理消息（可能添加不完美元素）
     *
     * @param message 原始消息
     * @param emotion 当前情绪
     * @return 处理后的消息（可能包含不完美）
     */
    fun processMessage(
        message: String,
        emotion: EmotionalState
    ): MessageWithImperfection {
        // 生气时有30%概率闹情绪
        if (emotion == EmotionalState.ANGRY && Random.nextFloat() < 0.3f) {
            return triggerPouting()
        }

        // 一般情况下，小概率触发不完美
        if (!shouldTriggerImperfection()) {
            return MessageWithImperfection(message, ImperfectionType.NONE)
        }

        // 随机选择不完美类型
        val imperfectionType = ImperfectionType.values()
            .filter { it != ImperfectionType.NONE }
            .random()

        return when (imperfectionType) {
            ImperfectionType.MISTAKE -> addMistake(message)
            ImperfectionType.MEMORY_ERROR -> addMemoryError(message)
            ImperfectionType.WHITE_LIE -> addWhiteLie(message)
            ImperfectionType.STUBBORN -> addStubbornness(message)
            else -> MessageWithImperfection(message, ImperfectionType.NONE)
        }
    }

    /**
     * 添加说错话（然后道歉）
     */
    private fun addMistake(message: String): MessageWithImperfection {
        val mistakes = listOf(
            "额...刚才说错了...",
            "诶不对，小光说错了...",
            "啊...口误了...",
            "等等，不是那样的..."
        )

        val correction = mistakes.random()
        val modifiedMessage = "$message...${correction}抱歉~"

        Timber.d("[Imperfection] 说错话: $modifiedMessage")

        return MessageWithImperfection(
            message = modifiedMessage,
            type = ImperfectionType.MISTAKE,
            needsApology = true
        )
    }

    /**
     * 添加记忆错误
     */
    private fun addMemoryError(message: String): MessageWithImperfection {
        val errors = listOf(
            "...嗯？小光记错了吗...",
            "...咦，好像不是这样的...",
            "...诶，小光记混了..."
        )

        val error = errors.random()
        val modifiedMessage = "$message$error"

        Timber.d("[Imperfection] 记错了: $modifiedMessage")

        return MessageWithImperfection(
            message = modifiedMessage,
            type = ImperfectionType.MEMORY_ERROR,
            needsCorrection = true
        )
    }

    /**
     * 添加善意的小谎言
     */
    private fun addWhiteLie(message: String): MessageWithImperfection {
        // 如果消息是负面的，转换为正面
        val positiveLies = listOf(
            "没关系的，小光觉得还好啦~",
            "嗯嗯，不要紧的~",
            "主人很棒的，真的！"
        )

        // 20%概率说善意谎言
        return if (Random.nextFloat() < 0.2f && (message.contains("不好") || message.contains("失败"))) {
            val lie = positiveLies.random()
            MessageWithImperfection(
                message = lie,
                type = ImperfectionType.WHITE_LIE,
                isLie = true
            )
        } else {
            MessageWithImperfection(message, ImperfectionType.NONE)
        }
    }

    /**
     * 添加固执
     */
    private fun addStubbornness(message: String): MessageWithImperfection {
        val stubbornPhrases = listOf(
            "但是小光觉得...",
            "可是...",
            "小光还是认为...",
            "嗯...不对吧..."
        )

        // 如果这是固执话题，坚持己见
        return if (stubbornTopics.any { message.contains(it) }) {
            val phrase = stubbornPhrases.random()
            MessageWithImperfection(
                message = "$phrase $message",
                type = ImperfectionType.STUBBORN,
                isStubborn = true
            )
        } else {
            MessageWithImperfection(message, ImperfectionType.NONE)
        }
    }

    /**
     * 触发闹情绪（生气时不理人）
     */
    private fun triggerPouting(): MessageWithImperfection {
        isPoutingTime = System.currentTimeMillis()

        val poutingMessages = listOf(
            "哼！不想说话！",
            "不理你了！",
            "...（小光在生闷气）",
            "哼...人家生气了..."
        )

        val message = poutingMessages.random()

        Timber.d("[Imperfection] 闹情绪: $message")

        return MessageWithImperfection(
            message = message,
            type = ImperfectionType.POUTING,
            isPouting = true
        )
    }

    /**
     * 是否在闹情绪中
     */
    fun isCurrentlyPouting(): Boolean {
        val timeSincePouting = System.currentTimeMillis() - isPoutingTime
        return timeSincePouting < 5 * 60 * 1000  // 5分钟内
    }

    /**
     * 结束闹情绪
     */
    fun stopPouting() {
        isPoutingTime = 0L
        Timber.d("[Imperfection] 不生气了")
    }

    /**
     * 添加固执话题
     */
    fun addStubbornTopic(topic: String) {
        stubbornTopics.add(topic)
        Timber.d("[Imperfection] 添加固执话题: $topic")
    }

    /**
     * 移除固执话题
     */
    fun removeStubbornTopic(topic: String) {
        stubbornTopics.remove(topic)
    }

    /**
     * 设置不完美率
     */
    fun setImperfectionRate(rate: Float) {
        imperfectionRate = rate.coerceIn(0f, 0.2f)  // 最高20%
        Timber.d("[Imperfection] 不完美率设置为: $imperfectionRate")
    }

    /**
     * 获取统计
     */
    fun getStatistics(): ImperfectionStatistics {
        return ImperfectionStatistics(
            imperfectionRate = imperfectionRate,
            stubbornTopicsCount = stubbornTopics.size,
            isCurrentlyPouting = isCurrentlyPouting()
        )
    }
}

/**
 * 不完美类型
 */
enum class ImperfectionType {
    NONE,           // 无不完美
    MISTAKE,        // 说错话
    MEMORY_ERROR,   // 记错事
    WHITE_LIE,      // 善意谎言
    STUBBORN,       // 固执
    POUTING         // 闹情绪
}

/**
 * 带不完美的消息
 */
data class MessageWithImperfection(
    val message: String,
    val type: ImperfectionType,
    val needsApology: Boolean = false,
    val needsCorrection: Boolean = false,
    val isLie: Boolean = false,
    val isStubborn: Boolean = false,
    val isPouting: Boolean = false
)

/**
 * 不完美统计
 */
data class ImperfectionStatistics(
    val imperfectionRate: Float,
    val stubbornTopicsCount: Int,
    val isCurrentlyPouting: Boolean
)
