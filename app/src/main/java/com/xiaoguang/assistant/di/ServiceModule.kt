package com.xiaoguang.assistant.di

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xiaoguang.assistant.service.tts.TtsService
import com.xiaoguang.assistant.util.ErrorHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 服务层依赖注入模块
 * 提供各种服务的单例实例
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    /**
     * 提供 TTS 语音播放服务
     */
    @Provides
    @Singleton
    fun provideTtsService(
        @ApplicationContext context: Context
    ): TtsService {
        return TtsService(context)
    }

    /**
     * 提供统一错误处理器
     */
    @Provides
    @Singleton
    fun provideErrorHandler(
        @ApplicationContext context: Context
    ): ErrorHandler {
        return ErrorHandler(context)
    }

    /**
     * 提供Gson实例
     * 用于JSON序列化和反序列化
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    /**
     * 提供知识检索配置
     */
    @Provides
    @Singleton
    fun provideRetrievalConfig(): com.xiaoguang.assistant.domain.knowledge.retrieval.RetrievalConfig {
        return com.xiaoguang.assistant.domain.knowledge.retrieval.RetrievalConfig()
    }
}
