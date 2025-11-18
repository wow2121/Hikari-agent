package com.xiaoguang.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.work.Configuration
import com.xiaoguang.assistant.domain.flow.FlowSystemInitializer
import com.xiaoguang.assistant.service.MemoryMaintenanceWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class XiaoGuangApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var flowSystemInitializer: FlowSystemInitializer

    @Inject
    lateinit var knowledgeSystemInitializer: com.xiaoguang.assistant.domain.knowledge.KnowledgeSystemInitializer

    @Inject
    lateinit var flowConversationBridge: com.xiaoguang.assistant.service.FlowConversationBridge

    @Inject
    lateinit var systemEventObserver: com.xiaoguang.assistant.domain.event.SystemEventObserver

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            // ⭐ 使用过滤树防止日志爆炸（输出到Logcat）
            Timber.plant(com.xiaoguang.assistant.util.FilteredDebugTree())
        }

        // ⭐ 安装内存日志树（用于日志查看器）
        val memoryTree = com.xiaoguang.assistant.core.logging.MemoryLogTree()
        Timber.plant(memoryTree)
        com.xiaoguang.assistant.core.logging.LogCollector.install(memoryTree)

        // Create notification channels
        createNotificationChannels()

        // Schedule memory maintenance worker
        scheduleMemoryMaintenance()

        // 按顺序初始化系统：知识系统 → 心流系统
        // ⚠️ 重要：心流系统依赖知识系统，必须等待知识系统初始化完成
        initializeSystemsSequentially()

        // 初始化心流-对话桥接（自动开始监听）
        // Bridge会将心流的主动发言转换为对话记录
        Timber.d("心流对话桥接已启动")

        // 初始化系统事件观察者（自动开始监听）
        // 让小光能感知到自己做了什么（比如记了日程）并主动反馈
        Timber.d("系统事件观察者已启动")

        Timber.d("小光助手已启动")
    }

    /**
     * 按顺序初始化系统
     * 确保知识系统完全初始化后，再启动心流系统
     */
    private fun initializeSystemsSequentially() {
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 首先初始化知识系统（同步等待完成）
                Timber.i("📚 开始初始化知识系统...")
                val knowledgeSuccess = knowledgeSystemInitializer.initialize()

                if (knowledgeSuccess) {
                    Timber.i("📚 ✅ 知识系统已就绪，小光拥有了记忆和世界观")

                    // 2. 知识系统就绪后，启动心流系统
                    Timber.i("✨ 开始初始化心流系统...")
                    initializeFlowSystem()
                } else {
                    Timber.w("📚 ⚠️ 知识系统初始化失败，但将继续启动心流系统（降级模式）")
                    // 即使知识系统失败，仍然启动心流系统（降级运行）
                    initializeFlowSystem()
                }

            } catch (e: Exception) {
                Timber.e(e, "❌ 系统初始化异常")
                // 异常情况下仍然尝试启动心流系统
                try {
                    initializeFlowSystem()
                } catch (flowException: Exception) {
                    Timber.e(flowException, "❌ 心流系统初始化也失败了")
                }
            }
        }
    }

    /**
     * 初始化心流系统
     */
    private fun initializeFlowSystem() {
        try {
            flowSystemInitializer.initialize(this)
            Timber.i("✨ ✅ 心流系统已启动，小光开始持续观察和思考...")
        } catch (e: Exception) {
            Timber.e(e, "❌ 初始化心流系统失败")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun scheduleMemoryMaintenance() {
        try {
            MemoryMaintenanceWorker.schedule(this)
            Timber.i("已调度记忆维护任务")
        } catch (e: Exception) {
            Timber.e(e, "调度记忆维护任务失败")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_ID = "voice_monitoring_channel"
    }
}
