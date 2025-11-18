package com.xiaoguang.assistant.di

import android.content.Context
import com.xiaoguang.assistant.data.local.database.VoiceprintDatabase
import com.xiaoguang.assistant.data.local.database.dao.VoiceprintDao
import com.xiaoguang.assistant.domain.voiceprint.SimpleVoiceprintFeatureExtractor
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintFeatureExtractor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 声纹相关的依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
object VoiceprintModule {

    @Provides
    @Singleton
    fun provideVoiceprintDatabase(
        @ApplicationContext context: Context
    ): VoiceprintDatabase {
        return VoiceprintDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideVoiceprintDao(
        database: VoiceprintDatabase
    ): VoiceprintDao {
        return database.voiceprintDao()
    }
}

/**
 * 声纹特征提取器绑定模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceprintBindingModule {

    @Binds
    @Singleton
    abstract fun bindVoiceprintFeatureExtractor(
        impl: SimpleVoiceprintFeatureExtractor
    ): VoiceprintFeatureExtractor
}
