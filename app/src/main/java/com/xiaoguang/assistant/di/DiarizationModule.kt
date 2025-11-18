package com.xiaoguang.assistant.di

import com.xiaoguang.assistant.domain.diarization.SherpaOnnxDiarizationService
import com.xiaoguang.assistant.domain.diarization.SpeakerDiarizationService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 2: Speaker Diarization 依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DiarizationModule {

    /**
     * 绑定说话人分离服务实现
     */
    @Binds
    @Singleton
    abstract fun bindSpeakerDiarizationService(
        implementation: SherpaOnnxDiarizationService
    ): SpeakerDiarizationService
}
