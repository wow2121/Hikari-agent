package com.xiaoguang.assistant.di

import com.xiaoguang.assistant.domain.flow.model.FlowConfig
import com.xiaoguang.assistant.domain.flow.model.PersonalityType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 心流系统依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object FlowModule {

    /**
     * 提供心流配置
     */
    @Provides
    @Singleton
    fun provideFlowConfig(): FlowConfig {
        return FlowConfig(
            // 默认配置：平衡型人格
            talkativeLevel = 1.0f,
            personalityType = PersonalityType.BALANCED,

            // 循环参数：200ms基础间隔
            baseLoopInterval = 200L,
            minLoopInterval = 100L,
            maxLoopInterval = 5000L,

            // 决策阈值：对主人更宽松
            speakThreshold = 0.5f,
            speakThresholdNormal = 0.6f,
            speakThresholdStranger = 0.75f,

            // 评分权重
            timeWeight = 0.2f,
            emotionWeight = 0.25f,
            relationWeight = 0.25f,
            contextWeight = 0.15f,
            curiosityWeight = 0.1f,
            urgencyWeight = 0.05f,

            // 兴趣话题
            interests = listOf(
                "动漫", "游戏", "音乐", "电影", "编程",
                "美食", "旅游", "宠物", "手工", "摄影",
                "运动", "阅读", "科技", "历史", "艺术"
            ),

            // 功能开关：全部启用
            enableInnerThoughts = true,
            enableCuriosity = true,
            enableProactiveCare = true,

            // 频率控制
            maxSpeakRatio = 0.3f,
            minSilenceDuration = 30000L,

            // 调试模式：默认关闭
            debugMode = false,
            logDecisions = false
        )
    }
}
