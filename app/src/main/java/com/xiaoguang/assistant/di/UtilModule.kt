package com.xiaoguang.assistant.di

import com.xiaoguang.assistant.domain.common.RetryPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 通用工具类的依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object UtilModule {

    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy {
        return RetryPolicy.DEFAULT
    }
}