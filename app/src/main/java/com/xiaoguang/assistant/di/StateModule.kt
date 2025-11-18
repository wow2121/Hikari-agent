package com.xiaoguang.assistant.di

import com.xiaoguang.assistant.domain.state.XiaoguangCoreStateManager
import com.xiaoguang.assistant.domain.state.XiaoguangCoreStateManagerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * 状态管理依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StateModule {

    @Binds
    @Singleton
    abstract fun bindCoreStateManager(
        impl: XiaoguangCoreStateManagerImpl
    ): XiaoguangCoreStateManager
}

/**
 * 协程作用域模块
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {

    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }
}
