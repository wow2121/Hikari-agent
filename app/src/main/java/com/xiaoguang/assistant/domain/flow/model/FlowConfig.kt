package com.xiaoguang.assistant.domain.flow.model

/**
 * 心流系统配置
 */
data class FlowConfig(
    // 人格参数
    val talkativeLevel: Float = 1.0f,  // 话痨程度 0.5-1.5，1.0是正常
    val personalityType: PersonalityType = PersonalityType.BALANCED,

    // 循环参数（拉长间隔，避免过于频繁）
    val baseLoopInterval: Long = 3000L,  // 基础循环间隔（毫秒）3秒
    val minLoopInterval: Long = 2000L,   // 最小间隔 2秒（环境活跃时）
    val maxLoopInterval: Long = 10000L,  // 最大间隔 10秒（长时间沉默）

    // 决策阈值（提高阈值，减少话痨）
    val speakThreshold: Float = 0.65f,   // 发言阈值（针对主人）
    val speakThresholdNormal: Float = 0.75f,  // 普通人的阈值
    val speakThresholdStranger: Float = 0.85f, // 陌生人的阈值

    // 评分权重
    val timeWeight: Float = 0.2f,
    val emotionWeight: Float = 0.25f,
    val relationWeight: Float = 0.25f,
    val contextWeight: Float = 0.15f,
    val biologicalWeight: Float = 0.10f,  // 生物钟权重
    val curiosityWeight: Float = 0.1f,
    val urgencyWeight: Float = 0.05f,

    // 兴趣话题
    val interests: List<String> = listOf(
        "动漫", "游戏", "音乐", "电影", "编程",
        "美食", "旅游", "宠物", "手工", "摄影"
    ),

    // 功能开关
    val enableInnerThoughts: Boolean = true,
    val enableCuriosity: Boolean = true,
    val enableProactiveCare: Boolean = true,

    // 频率控制（降低发言频率）
    val maxSpeakRatio: Float = 0.15f,  // 最大发言占比 15%（原来30%太高）
    val minSilenceDuration: Long = 30000L,  // 最小沉默时长（毫秒）

    // 调试模式
    val debugMode: Boolean = false,
    val logDecisions: Boolean = false
) {
    /**
     * 根据关系获取发言阈值
     */
    fun getThresholdForRelation(isMaster: Boolean, isFriend: Boolean): Float {
        return when {
            isMaster -> speakThreshold
            isFriend -> speakThresholdNormal
            else -> speakThresholdStranger
        }
    }

    /**
     * 调整循环间隔（基于环境活跃度）
     */
    fun adjustLoopInterval(environmentNoise: Float): Long {
        return when {
            environmentNoise > 0.7f -> minLoopInterval  // 活跃，高频检查
            environmentNoise > 0.3f -> baseLoopInterval // 正常
            environmentNoise > 0.1f -> (baseLoopInterval * 2.5).toLong()  // 较安静
            else -> maxLoopInterval  // 完全安静
        }
    }
}

/**
 * 人格类型
 */
enum class PersonalityType(
    val displayName: String,
    val talkativeMultiplier: Float,
    val emotionMultiplier: Float
) {
    SHY(
        "内向害羞",
        0.6f,  // 话少
        1.2f   // 情绪敏感
    ),
    BALANCED(
        "平衡",
        1.0f,
        1.0f
    ),
    OUTGOING(
        "外向活泼",
        1.4f,  // 话多
        1.3f   // 情绪外露
    ),
    PROFESSIONAL(
        "专业克制",
        0.8f,
        0.7f   // 情绪稳定
    )
}
