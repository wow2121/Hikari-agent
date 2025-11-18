package com.xiaoguang.assistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xiaoguang.assistant.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一错误处理器
 *
 * 功能：
 * - 记录错误日志
 * - 向用户显示友好的错误提示
 * - 上报严重错误（用于未来的崩溃分析）
 * - 提供降级策略建议
 */
@Singleton
class ErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID = "error_notifications"
        private const val CHANNEL_NAME = "错误通知"
        private var notificationId = 1000
    }

    init {
        createNotificationChannel()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "小光助手错误通知"
                enableVibration(false)
                enableLights(false)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 处理错误
     *
     * @param error 异常对象
     * @param userMessage 显示给用户的友好消息
     * @param severity 错误严重程度
     * @param showNotification 是否显示通知
     * @param action 错误相关的业务操作（用于日志上下文）
     */
    fun handleError(
        error: Throwable,
        userMessage: String,
        severity: Severity = Severity.WARNING,
        showNotification: Boolean = true,
        action: String = "Unknown"
    ) {
        // 1. 记录详细日志
        val logMessage = "[$action] $userMessage"
        when (severity) {
            Severity.INFO -> Timber.i(error, logMessage)
            Severity.WARNING -> Timber.w(error, logMessage)
            Severity.ERROR -> Timber.e(error, logMessage)
            Severity.CRITICAL -> Timber.wtf(error, logMessage)
        }

        // 2. 显示用户通知（WARNING 以上）
        if (showNotification && severity >= Severity.WARNING) {
            showErrorNotification(userMessage, severity)
        }

        // 3. 上报崩溃（ERROR 以上）
        if (severity >= Severity.ERROR) {
            reportCrash(error, action, userMessage)
        }
    }

    /**
     * 显示错误通知
     */
    private fun showErrorNotification(message: String, severity: Severity) {
        try {
            val icon = when (severity) {
                Severity.INFO -> android.R.drawable.ic_dialog_info
                Severity.WARNING -> android.R.drawable.ic_dialog_alert
                Severity.ERROR -> android.R.drawable.ic_dialog_alert
                Severity.CRITICAL -> android.R.drawable.stat_notify_error
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle("小光助手")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(notificationId++, notification)
        } catch (e: Exception) {
            Timber.e(e, "显示错误通知失败")
        }
    }

    /**
     * 上报崩溃信息
     * TODO: 未来集成 Firebase Crashlytics 或其他崩溃分析工具
     */
    private fun reportCrash(error: Throwable, action: String, userMessage: String) {
        Timber.e(error, "[CrashReport] Action: $action, Message: $userMessage")
        // TODO: 集成 Firebase Crashlytics
        // FirebaseCrashlytics.getInstance().recordException(error)
        // FirebaseCrashlytics.getInstance().setCustomKey("action", action)
        // FirebaseCrashlytics.getInstance().setCustomKey("user_message", userMessage)
    }

    /**
     * 处理带降级策略的错误
     *
     * @param error 异常对象
     * @param userMessage 显示给用户的消息
     * @param fallbackAction 降级操作（如果降级成功返回 true）
     * @return 是否成功处理（包括降级）
     */
    suspend fun handleWithFallback(
        error: Throwable,
        userMessage: String,
        action: String = "Unknown",
        fallbackAction: suspend () -> Boolean
    ): Boolean {
        Timber.w(error, "[$action] $userMessage，尝试降级处理...")

        return try {
            val fallbackSuccess = fallbackAction()
            if (fallbackSuccess) {
                Timber.i("[$action] 降级处理成功")
                // 降级成功，不显示错误通知
                false
            } else {
                Timber.e("[$action] 降级处理也失败了")
                handleError(error, userMessage, Severity.ERROR, action = action)
                false
            }
        } catch (fallbackError: Exception) {
            Timber.e(fallbackError, "[$action] 降级处理时发生异常")
            handleError(error, "$userMessage（降级也失败）", Severity.ERROR, action = action)
            false
        }
    }

    /**
     * 处理网络错误
     */
    fun handleNetworkError(
        error: Throwable,
        action: String = "Network",
        showNotification: Boolean = true
    ) {
        val userMessage = when {
            error.message?.contains("timeout", ignoreCase = true) == true ->
                "网络超时，请检查网络连接"
            error.message?.contains("connection", ignoreCase = true) == true ->
                "网络连接失败，请检查网络设置"
            error.message?.contains("401", ignoreCase = true) == true ->
                "API 密钥无效或已过期"
            error.message?.contains("429", ignoreCase = true) == true ->
                "请求过于频繁，请稍后再试"
            else -> "网络请求失败"
        }

        handleError(error, userMessage, Severity.WARNING, showNotification, action)
    }

    /**
     * 处理语音识别错误
     */
    fun handleSpeechRecognitionError(
        error: Throwable,
        showNotification: Boolean = false  // 语音识别错误通常不显示通知
    ) {
        val userMessage = "语音识别失败，请重新说话"
        handleError(error, userMessage, Severity.INFO, showNotification, "SpeechRecognition")
    }

    /**
     * 处理声纹识别错误
     */
    fun handleVoiceprintError(
        error: Throwable,
        userMessage: String = "声纹识别失败"
    ) {
        handleError(error, userMessage, Severity.WARNING, true, "Voiceprint")
    }

    /**
     * 处理 TTS 错误
     */
    fun handleTtsError(
        error: Throwable,
        showNotification: Boolean = false  // TTS 错误通常不显示通知，只记录日志
    ) {
        val userMessage = "语音播放失败"
        handleError(error, userMessage, Severity.WARNING, showNotification, "TTS")
    }

    /**
     * 处理 LLM 调用错误
     */
    fun handleLlmError(
        error: Throwable,
        showNotification: Boolean = false  // LLM 错误通常在降级后不显示通知
    ) {
        val userMessage = "AI 回复生成失败"
        handleError(error, userMessage, Severity.WARNING, showNotification, "LLM")
    }

    /**
     * 处理数据库错误
     */
    fun handleDatabaseError(
        error: Throwable,
        userMessage: String = "数据库操作失败"
    ) {
        handleError(error, userMessage, Severity.ERROR, true, "Database")
    }

    /**
     * 处理心流系统错误
     */
    fun handleFlowSystemError(
        error: Throwable,
        userMessage: String = "心流系统异常"
    ) {
        handleError(error, userMessage, Severity.WARNING, false, "FlowSystem")
    }
}

/**
 * 错误严重程度
 */
enum class Severity {
    INFO,       // 信息：不需要特殊处理，仅记录日志
    WARNING,    // 警告：需要显示通知，但不影响核心功能
    ERROR,      // 错误：严重问题，需要上报崩溃信息
    CRITICAL    // 致命：应用无法继续运行
}
