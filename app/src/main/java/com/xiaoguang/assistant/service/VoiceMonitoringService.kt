package com.xiaoguang.assistant.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.xiaoguang.assistant.R
import com.xiaoguang.assistant.XiaoGuangApplication
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.domain.model.MonitoringMode
import com.xiaoguang.assistant.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class VoiceMonitoringService : Service() {

    @Inject
    lateinit var environmentMonitor: EnvironmentMonitor

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var wakeWordDetector: com.xiaoguang.assistant.service.wakeword.WakeWordDetector

    @Inject
    lateinit var wakeWordEventManager: com.xiaoguang.assistant.service.wakeword.WakeWordEventManager

    @Inject
    lateinit var audioCaptureService: com.xiaoguang.assistant.service.speech.AudioCaptureService

    // Service自己的CoroutineScope
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val binder = LocalBinder()
    private var isListening = false
    private var currentMode: MonitoringMode = MonitoringMode.DISABLED

    inner class LocalBinder : Binder() {
        fun getService(): VoiceMonitoringService = this@VoiceMonitoringService
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("[VoiceMonitoring] ========== 服务创建 ==========")
        Timber.d("[VoiceMonitoring] 创建前台通知...")

        try {
            startForeground(NOTIFICATION_ID, createNotification("准备监听..."))
            Timber.i("[VoiceMonitoring] ✅ 前台服务启动成功，通知ID: $NOTIFICATION_ID")
        } catch (e: Exception) {
            Timber.e(e, "[VoiceMonitoring] ❌ 启动前台服务失败")
        }

        // 初始化唤醒词检测器
        Timber.d("[VoiceMonitoring] 初始化唤醒词检测器...")
        serviceScope.launch {
            val initResult = wakeWordDetector.initialize()
            if (initResult.isSuccess) {
                Timber.i("[VoiceMonitoring] ✅ 唤醒词检测器初始化成功")
                // 设置音频处理器，将音频数据传递给唤醒词检测器
                environmentMonitor.setAudioProcessor { audioData ->
                    val detected = wakeWordDetector.processAudio(audioData)
                    if (detected) {
                        Timber.i("[VoiceMonitoring] 🎙️ 检测到唤醒词，发送事件")
                        wakeWordEventManager.emitWakeWordDetected()
                    }
                }
            } else {
                Timber.w("[VoiceMonitoring] ⚠️ 唤醒词检测器初始化失败: ${initResult.exceptionOrNull()?.message}")
                Timber.w("[VoiceMonitoring] 唤醒词功能将不可用，但环境监听仍可正常工作")
            }
        }

        // 监听配置变化
        Timber.d("[VoiceMonitoring] 开始监听配置变化...")
        observePreferences()
        Timber.i("[VoiceMonitoring] =========================================")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("[VoiceMonitoring] ========== 服务启动命令 ==========")
        Timber.d("[VoiceMonitoring] Intent: $intent, Flags: $flags, StartId: $startId")

        serviceScope.launch {
            try {
                Timber.d("[VoiceMonitoring] 准备启动监听...")
                startListening()
                Timber.i("[VoiceMonitoring] ✅ 监听启动流程完成")
            } catch (e: Exception) {
                Timber.e(e, "[VoiceMonitoring] ❌ 监听启动失败")
            }
        }

        Timber.d("[VoiceMonitoring] 返回 START_STICKY（服务会在被杀死后自动重启）")
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        Timber.d("VoiceMonitoringService destroyed")

        // 使用runBlocking确保清理完成后再取消scope
        runBlocking {
            stopListening()
            environmentMonitor.cleanup()
            wakeWordDetector.cleanup()
        }

        serviceScope.cancel() // 取消所有协程
        super.onDestroy()
    }

    /**
     * 监听配置变化
     */
    private fun observePreferences() {
        // 监听模式变化
        serviceScope.launch {
            appPreferences.monitoringMode.collect { mode ->
                Timber.d("[VoiceMonitoring] 监听模式变更: $mode")
                currentMode = mode
                handleModeChange(mode)
            }
        }

        // 监听识别结果并更新通知
        serviceScope.launch {
            environmentMonitor.currentTranscript.collect { transcript ->
                if (transcript.isNotEmpty()) {
                    updateNotification("正在监听: ${transcript.take(50)}...")
                }
            }
        }

        // ✅ 监听录音状态
        serviceScope.launch {
            audioCaptureService.isRecording.collect { isRecording ->
                Timber.d("[VoiceMonitoring] 录音状态变化: $isRecording")
                if (isListening) {
                    if (isRecording) {
                        Timber.i("[VoiceMonitoring] ✅ 录音正常进行中")
                        // 不需要更新通知，会被 currentTranscript 覆盖
                    } else {
                        Timber.w("[VoiceMonitoring] ⚠️ 录音已停止但监听仍在进行")
                        updateNotification("⚠️ 录音失败，请检查麦克风权限")
                    }
                }
            }
        }

        // ✅ 监听音频级别
        serviceScope.launch {
            audioCaptureService.audioLevel.collect { level ->
                // 可以用于显示音频可视化，目前仅记录
                if (level > 0.3f) {
                    Timber.v("[VoiceMonitoring] 检测到音频: 电平=${String.format("%.2f", level)}")
                }
            }
        }
    }

    /**
     * 处理监听模式变化
     */
    private suspend fun handleModeChange(mode: MonitoringMode) {
        when (mode) {
            MonitoringMode.ALWAYS_ON -> {
                if (!isListening) {
                    startListening()
                }
            }
            MonitoringMode.FOREGROUND_ONLY -> {
                // 前台模式在后面由Activity控制暂停/恢复
                if (!isListening) {
                    startListening()
                }
            }
            MonitoringMode.SCHEDULED -> {
                // 定时模式 - 暂时简化,默认启动监听
                // TODO: 实现完整的定时调度逻辑
                if (!isListening) {
                    startListening()
                }
            }
            MonitoringMode.DISABLED -> {
                if (isListening) {
                    stopListening()
                }
            }
        }
    }

    /**
     * 开始监听
     */
    private suspend fun startListening() {
        if (isListening) {
            Timber.w("[VoiceMonitoring] 监听已在进行中，跳过重复启动")
            return
        }

        Timber.i("[VoiceMonitoring] ========== 开始环境监听 ==========")
        Timber.d("[VoiceMonitoring] 当前模式: $currentMode")

        try {
            isListening = true
            updateNotification("正在监听环境...")

            Timber.d("[VoiceMonitoring] 调用 EnvironmentMonitor.startMonitoring()...")
            environmentMonitor.startMonitoring { conversationSegment ->
                // 对话分段完成，记录日志
                // 注意：环境对话已通过EnvironmentState传递给心流系统，不需要保存到聊天记录
                Timber.d("[VoiceMonitoring] 收到对话分段 (${conversationSegment.length} 字符): ${conversationSegment.take(100)}...")
                Timber.d("[VoiceMonitoring] 环境对话已更新至 EnvironmentState，心流系统会自动感知并处理")
            }

            Timber.i("[VoiceMonitoring] ✅ 环境监听已启动")
            Timber.i("[VoiceMonitoring] =========================================")
        } catch (e: Exception) {
            Timber.e(e, "[VoiceMonitoring] ❌ 启动监听失败")
            isListening = false
            updateNotification("监听启动失败: ${e.message}")
        }
    }

    /**
     * 暂停监听（前台模式）
     */
    suspend fun pauseListening() {
        if (!isListening) return

        Timber.d("暂停监听")
        updateNotification("监听已暂停")
        environmentMonitor.pauseMonitoring()
    }

    /**
     * 恢复监听（前台模式）
     */
    suspend fun resumeListening() {
        if (!isListening) return

        Timber.d("恢复监听")
        updateNotification("正在监听环境...")
        environmentMonitor.resumeMonitoring()
    }

    /**
     * 停止监听
     */
    private suspend fun stopListening() {
        if (!isListening) return

        isListening = false
        Timber.d("停止环境监听")
        updateNotification("监听已停止")

        environmentMonitor.stopMonitoring()
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, XiaoGuangApplication.CHANNEL_SERVICE_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, XiaoGuangApplication.CHANNEL_SERVICE_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
