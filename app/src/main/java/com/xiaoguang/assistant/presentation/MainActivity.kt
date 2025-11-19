package com.xiaoguang.assistant.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.xiaoguang.assistant.presentation.navigation.XiaoguangNavHost
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.domain.state.XiaoguangCoreStateManager
import com.xiaoguang.assistant.presentation.ui.voice.VoiceAssistantViewModel
import com.xiaoguang.assistant.presentation.ui.voice.VoiceAssistantOverlay
import com.xiaoguang.assistant.service.VoiceMonitoringService
import com.xiaoguang.assistant.service.wakeword.WakeWordEventManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val voiceAssistantViewModel: VoiceAssistantViewModel by viewModels()

    @Inject
    lateinit var wakeWordEventManager: WakeWordEventManager

    @Inject
    lateinit var flowSystemInitializer: com.xiaoguang.assistant.domain.flow.FlowSystemInitializer

    @Inject
    lateinit var ttsService: com.xiaoguang.assistant.service.tts.TtsService

    @Inject
    lateinit var coreStateManager: XiaoguangCoreStateManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        Timber.d("[Permissions] ========== 权限检查结果 ==========")
        permissions.forEach { (permission, granted) ->
            val status = if (granted) "✅ 已授予" else "❌ 被拒绝"
            Timber.d("[Permissions] $permission: $status")
        }
        Timber.d("[Permissions] 全部授予: $allGranted")
        Timber.d("[Permissions] 麦克风权限: ${if (recordAudioGranted) "✅ 已授予" else "❌ 被拒绝"}")
        Timber.d("[Permissions] =====================================")

        if (allGranted) {
            Timber.i("[Permissions] ✅ 所有权限已授予，启动语音监听服务...")
            startVoiceMonitoringService()
        } else {
            Timber.w("[Permissions] ⚠️ 部分权限被拒绝")
            if (!recordAudioGranted) {
                Timber.e("[Permissions] ❌ 麦克风权限未授予，听觉系统将无法工作！")
                Timber.e("[Permissions] 请在设置 → 应用 → 小光助手 → 权限中手动授予麦克风权限")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 初始化 TTS 服务
        lifecycleScope.launch {
            val success = ttsService.initialize()
            if (success) {
                Timber.i("[MainActivity] TTS 服务初始化成功")
            } else {
                Timber.e("[MainActivity] TTS 服务初始化失败")
            }
        }

        // ✅ 监听心流系统状态（知识系统和心流系统在 Application 中已初始化）
        Timber.i("[MainActivity] ========== 监听心流系统状态 ==========")
        lifecycleScope.launch {
            try {
                val coordinator = flowSystemInitializer.getCoordinator()
                coordinator.isRunning.collect { isRunning ->
                    Timber.i("[MainActivity] 🌟 心流运行状态: ${if (isRunning) "运行中" else "已停止"}")
                }
            } catch (e: Exception) {
                Timber.e(e, "[MainActivity] ❌ 获取心流状态失败")
            }
        }


        setContent {
            // 获取核心状态
            val coreState by coreStateManager.coreState.collectAsState()

            XiaoguangTheme(emotion = coreState.emotion.currentEmotion) {
                // 使用新的导航系统
                XiaoguangNavHost(coreState = coreState)

                // 语音助手叠加层
                val voiceAssistantState by voiceAssistantViewModel.uiState.collectAsState()
                if (voiceAssistantState.isVisible) {
                    VoiceAssistantOverlay(
                        isListening = voiceAssistantState.isListening,
                        recognizedText = voiceAssistantState.recognizedText,
                        aiResponse = voiceAssistantState.aiResponse,
                        audioLevel = voiceAssistantState.audioLevel,
                        onDismiss = { voiceAssistantViewModel.hide() }
                    )
                }
            }

            // 监听唤醒词事件
            LaunchedEffect(Unit) {
                wakeWordEventManager.wakeWordEvent.collect {
                    Timber.d("收到唤醒词事件，显示语音助手")
                    voiceAssistantViewModel.show()
                }
            }

            // ✅ 监听心流主动发言事件（经过队列管理和条件判断）
            LaunchedEffect(Unit) {
                flowSystemInitializer.getTtsPlayEvents().collect { event ->
                    Timber.i("[MainActivity] 收到心流TTS播放事件: ${event.content}")

                    // ✅ 使用 TTS 播放小光的主动发言
                    if (ttsService.isReady.value) {
                        lifecycleScope.launch {
                            // 通知FlowSpeakEventHandler TTS开始播放
                            val coordinator = flowSystemInitializer.getCoordinator()
                            coordinator.setTtsPlayingStatus(true)

                            val success = ttsService.speak(
                                text = event.content,
                                priority = when (event.priority) {
                                    com.xiaoguang.assistant.domain.flow.model.SpeakPriority.URGENT,
                                    com.xiaoguang.assistant.domain.flow.model.SpeakPriority.HIGH ->
                                        com.xiaoguang.assistant.service.tts.SpeakPriority.HIGH
                                    com.xiaoguang.assistant.domain.flow.model.SpeakPriority.NORMAL,
                                    com.xiaoguang.assistant.domain.flow.model.SpeakPriority.LOW ->
                                        com.xiaoguang.assistant.service.tts.SpeakPriority.NORMAL
                                }
                            )

                            // 通知FlowSpeakEventHandler TTS播放结束
                            coordinator.setTtsPlayingStatus(false)

                            if (success) {
                                Timber.d("[MainActivity] TTS 播放成功")
                            } else {
                                Timber.w("[MainActivity] TTS 播放失败")
                            }
                        }
                    } else {
                        Timber.w("[MainActivity] TTS 未就绪，无法播放")
                    }
                }
            }
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun startVoiceMonitoringService() {
        try {
            Timber.i("[VoiceService] 准备启动语音监听服务...")
            val intent = Intent(this, VoiceMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Timber.d("[VoiceService] Android O+ 使用 startForegroundService")
                startForegroundService(intent)
            } else {
                Timber.d("[VoiceService] Android O 以下使用 startService")
                startService(intent)
            }
            Timber.i("[VoiceService] ✅ 语音监听服务启动命令已发送")
        } catch (e: Exception) {
            Timber.e(e, "[VoiceService] ❌ 启动语音监听服务失败")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ 释放 TTS 资源
        ttsService.release()
        Timber.d("[MainActivity] TTS 资源已释放")
    }
}
