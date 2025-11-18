package com.xiaoguang.assistant.domain.state

import com.xiaoguang.assistant.domain.emotion.EmotionType
import kotlinx.coroutines.flow.StateFlow

/**
 * 小光核心状态
 *
 * 聚合所有系统的状态，提供统一的状态访问接口。
 * 所有UI组件应该从这里获取小光的全局状态。
 *
 * 包含的状态：
 * - 情绪状态（当前情绪、情绪强度）
 * - 心流状态（冲动强度、当前想法、是否想说话）
 * - 说话人状态（当前说话人、是否在监听）
 * - 环境状态（环境类型、噪音等级）
 * - 对话状态（是否在对话中、最后消息时间）
 */
data class XiaoguangCoreState(
    /** 情绪状态 */
    val emotion: EmotionState = EmotionState(),

    /** 心流状态 */
    val flow: FlowState = FlowState(),

    /** 说话人识别状态 */
    val speaker: SpeakerState = SpeakerState(),

    /** 环境监听状态 */
    val environment: EnvironmentState = EnvironmentState(),

    /** 对话状态 */
    val conversation: ConversationState = ConversationState(),

    /** 系统状态 */
    val system: SystemState = SystemState()
)

/**
 * 情绪状态
 */
data class EmotionState(
    /** 当前情绪类型 */
    val currentEmotion: EmotionType = EmotionType.CALM,

    /** 情绪强度 (0.0 - 1.0) */
    val intensity: Float = 0.5f,

    /** 情绪变化原因（可选） */
    val reason: String? = null,

    /** 上一次情绪 */
    val previousEmotion: EmotionType? = null,

    /** 情绪持续时间（秒） */
    val duration: Long = 0
)

/**
 * 心流状态
 */
data class FlowState(
    /** 是否正在运行心流循环 */
    val isRunning: Boolean = false,

    /** 当前心流冲动强度 (0.0 - 1.0) */
    val impulse: Float = 0.0f,

    /** 当前想法/思考内容 */
    val currentThought: String? = null,

    /** 是否想要发言 */
    val wantsToSpeak: Boolean = false,

    /** 最后一次心流处理时间戳 */
    val lastFlowTimestamp: Long = 0,

    /** 心流处理延迟（毫秒） */
    val flowDelay: Long = 3000,

    /** 当前心流阶段 */
    val currentPhase: FlowPhase = FlowPhase.PERCEIVING
)

/**
 * 心流阶段
 */
enum class FlowPhase {
    /** 感知环境 */
    PERCEIVING,

    /** 思考分析 */
    THINKING,

    /** 决策判断 */
    DECIDING,

    /** 执行行动 */
    ACTING,

    /** 空闲等待 */
    IDLE
}

/**
 * 说话人识别状态
 */
data class SpeakerState(
    /** 当前识别到的说话人ID */
    val currentSpeakerId: String? = null,

    /** 当前说话人姓名 */
    val currentSpeakerName: String? = null,

    /** 是否为主人 */
    val isMaster: Boolean = false,

    /** 是否正在监听音频 */
    val isListening: Boolean = false,

    /** 是否正在识别声纹 */
    val isIdentifying: Boolean = false,

    /** 识别置信度 (0.0 - 1.0) */
    val confidence: Float = 0.0f,

    /** 是否检测到陌生人 */
    val strangerDetected: Boolean = false,

    /** 最后识别时间戳 */
    val lastIdentificationTimestamp: Long = 0
)

/**
 * 环境状态
 */
data class EnvironmentState(
    /** 环境类型 */
    val environmentType: EnvironmentType = EnvironmentType.QUIET,

    /** 噪音等级 (0.0 - 1.0) */
    val noiseLevel: Float = 0.0f,

    /** 是否有人在附近 */
    val peopleNearby: Boolean = false,

    /** 当前时间段 */
    val timeOfDay: TimeOfDay = TimeOfDay.UNKNOWN,

    /** 是否适合交谈 */
    val suitableForConversation: Boolean = true
)

/**
 * 环境类型
 */
enum class EnvironmentType {
    /** 安静环境 */
    QUIET,

    /** 嘈杂环境 */
    NOISY,

    /** 对话环境 */
    CONVERSATIONAL,

    /** 未知 */
    UNKNOWN
}

/**
 * 时间段
 */
enum class TimeOfDay {
    /** 早晨 */
    MORNING,

    /** 上午 */
    FORENOON,

    /** 下午 */
    AFTERNOON,

    /** 傍晚 */
    EVENING,

    /** 夜晚 */
    NIGHT,

    /** 深夜 */
    LATE_NIGHT,

    /** 未知 */
    UNKNOWN
}

/**
 * 对话状态
 */
data class ConversationState(
    /** 是否正在对话中 */
    val isInConversation: Boolean = false,

    /** 最后一条消息的时间戳 */
    val lastMessageTimestamp: Long = 0,

    /** 最后一条消息的发送者 */
    val lastMessageSender: String? = null,

    /** 是否正在生成回复 */
    val isGeneratingReply: Boolean = false,

    /** 当前对话轮次数 */
    val turnCount: Int = 0,

    /** 对话开始时间戳 */
    val conversationStartTimestamp: Long = 0
)

/**
 * 系统状态
 */
data class SystemState(
    /** 是否已初始化 */
    val isInitialized: Boolean = false,

    /** 网络连接状态 */
    val networkConnected: Boolean = false,

    /** 是否正在加载 */
    val isLoading: Boolean = false,

    /** 错误信息 */
    val error: String? = null,

    /** 系统版本 */
    val version: String = "1.0.0"
)

/**
 * 小光核心状态管理器
 *
 * 负责：
 * 1. 聚合各个系统的状态
 * 2. 提供统一的状态访问接口
 * 3. 处理状态变化事件
 */
interface XiaoguangCoreStateManager {
    /** 核心状态流 */
    val coreState: StateFlow<XiaoguangCoreState>

    /** 更新情绪状态 */
    suspend fun updateEmotion(emotion: EmotionState)

    /** 更新心流状态 */
    suspend fun updateFlow(flow: FlowState)

    /** 更新说话人状态 */
    suspend fun updateSpeaker(speaker: SpeakerState)

    /** 更新环境状态 */
    suspend fun updateEnvironment(environment: EnvironmentState)

    /** 更新对话状态 */
    suspend fun updateConversation(conversation: ConversationState)

    /** 更新系统状态 */
    suspend fun updateSystem(system: SystemState)

    /** 重置所有状态 */
    suspend fun reset()
}
