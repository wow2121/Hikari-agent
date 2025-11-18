package com.xiaoguang.assistant.domain.flow

import android.content.Context
import com.xiaoguang.assistant.domain.flow.layer.ProactiveSpeakEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 心流系统初始化器
 * 负责在应用启动时初始化和启动心流系统
 */
@Singleton
class FlowSystemInitializer @Inject constructor(
    private val heartFlowCoordinator: HeartFlowCoordinator,
    // ✅ Phase 2: 说话人分离服务
    private val speakerDiarizationService: com.xiaoguang.assistant.domain.diarization.SpeakerDiarizationService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isInitialized = false

    /**
     * 初始化心流系统
     */
    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (isInitialized) {
            Timber.w("[FlowSystemInitializer] 心流系统已初始化")
            return
        }

        Timber.i("[FlowSystemInitializer] 开始初始化心流系统...")

        try {
            // ✅ Phase 2: 初始化说话人分离服务（后台异步）
            scope.launch(Dispatchers.IO) {
                try {
                    Timber.i("[FlowSystemInitializer] 初始化 Phase 2 说话人分离服务...")
                    speakerDiarizationService.initialize()
                    Timber.i("[FlowSystemInitializer] ✅ Phase 2 初始化成功")
                } catch (e: Exception) {
                    Timber.w(e, "[FlowSystemInitializer] ⚠️ Phase 2 初始化失败，将使用 Phase 1 降级方案")
                }
            }

            // 启动心流协调器
            heartFlowCoordinator.start()

            isInitialized = true
            Timber.i("[FlowSystemInitializer] ✨ 心流系统初始化成功！")

        } catch (e: Exception) {
            Timber.e(e, "[FlowSystemInitializer] 心流系统初始化失败")
        }
    }

    /**
     * 获取TTS播放事件流（供UI层订阅）
     * 注意：返回的是经过FlowSpeakEventHandler处理后的TTS播放事件
     */
    fun getTtsPlayEvents(): Flow<com.xiaoguang.assistant.domain.flow.TtsPlayEvent> {
        return heartFlowCoordinator.getTtsPlayEvents()
    }

    /**
     * 获取原始发言事件流（供FlowConversationBridge等内部服务订阅）
     * 注意：这是ActionLayer的原始事件，未经队列管理和条件判断
     */
    fun getRawSpeakEvents(): Flow<ProactiveSpeakEvent> {
        return heartFlowCoordinator.getSpeakEvents()
    }

    /**
     * 获取协调器（供外部访问）
     */
    fun getCoordinator(): HeartFlowCoordinator {
        return heartFlowCoordinator
    }

    /**
     * 关闭心流系统
     */
    suspend fun shutdown() {
        Timber.i("[FlowSystemInitializer] 关闭心流系统...")
        heartFlowCoordinator.stop()
        isInitialized = false
    }
}
