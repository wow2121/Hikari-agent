package com.xiaoguang.assistant.domain.model

/**
 * 监听模式
 */
enum class MonitoringMode {
    /**
     * 24小时持续监听
     * 全天候监听环境对话，最高电量消耗
     */
    ALWAYS_ON,

    /**
     * 仅在应用前台时监听
     * 只有当应用可见时才进行监听，节省电量
     */
    FOREGROUND_ONLY,

    /**
     * 定时监听
     * 用户可配置特定时间段进行监听（如上课时间）
     */
    SCHEDULED,

    /**
     * 关闭监听
     * 完全禁用环境监听功能
     */
    DISABLED
}

/**
 * 语音识别方案
 */
enum class RecognitionMethod {
    /**
     * 优先使用在线识别（需要网络）
     */
    ONLINE_FIRST,

    /**
     * 优先使用离线识别（需下载模型）
     */
    OFFLINE_FIRST,

    /**
     * 混合方案：有网络时用在线，无网络时用离线
     */
    HYBRID
}

/**
 * 定时监听配置
 */
data class ScheduleConfig(
    val enabled: Boolean = false,
    val timeSlots: List<TimeSlot> = emptyList()
)

/**
 * 时间段配置
 */
data class TimeSlot(
    val startHour: Int, // 0-23
    val startMinute: Int, // 0-59
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeek: Set<DayOfWeek> = setOf() // 空集表示每天
)

enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}
