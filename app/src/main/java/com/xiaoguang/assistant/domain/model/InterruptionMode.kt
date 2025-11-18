package com.xiaoguang.assistant.domain.model

/**
 * 主动插话模式
 * 定义小光在监听到对话时的反应方式
 */
enum class InterruptionMode(
    val value: String,
    val displayName: String,
    val description: String
) {
    /**
     * 完全启用
     * 小光可以直接打断对话，立即TTS播放回复
     */
    FULLY_ENABLED(
        value = "fully_enabled",
        displayName = "完全启用",
        description = "小光可以直接参与对话，立即语音回复"
    ),

    /**
     * 需要确认
     * 小光想说话时显示通知，需要用户批准
     */
    NEED_CONFIRMATION(
        value = "need_confirmation",
        displayName = "需要确认",
        description = "小光想说话时会发送通知，点击通知后才会播放"
    ),

    /**
     * 禁用
     * 小光只是静默监听，不会主动插话
     */
    DISABLED(
        value = "disabled",
        displayName = "禁用",
        description = "小光只是静默监听，不会主动插话"
    );

    companion object {
        /**
         * 从字符串值获取枚举
         */
        fun fromValue(value: String): InterruptionMode {
            return values().find { it.value == value } ?: NEED_CONFIRMATION
        }
    }
}
