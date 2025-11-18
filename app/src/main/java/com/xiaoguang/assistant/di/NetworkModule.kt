package com.xiaoguang.assistant.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.core.config.AppConfigManager
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        networkMonitorInterceptor: com.xiaoguang.assistant.core.network.NetworkMonitorInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(networkMonitorInterceptor)  // ✅ 添加网络监控拦截器
            .addInterceptor(loggingInterceptor)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))  // ✅ 强制使用 HTTP/1.1（避免 HTTP/2 的内部超时问题）
            .connectTimeout(30, TimeUnit.SECONDS)           // 连接超时：30秒
            .readTimeout(300, TimeUnit.SECONDS)             // ✅ 读取超时：300秒（5分钟，DeepSeek 可以慢慢想）
            .writeTimeout(30, TimeUnit.SECONDS)             // 写入超时：30秒
            .callTimeout(0, TimeUnit.SECONDS)               // ✅ 禁用总超时（让 readTimeout 单独控制）
            .pingInterval(30, TimeUnit.SECONDS)             // ✅ 每30秒发送心跳，保持连接活跃
            .retryOnConnectionFailure(true)                 // 启用自动重试（连接失败时）
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SiliconFlowAPI.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideSiliconFlowAPI(retrofit: Retrofit): SiliconFlowAPI {
        return retrofit.create(SiliconFlowAPI::class.java)
    }

    /**
     * 提供 Chroma 向量数据库 API
     * 独立的 Retrofit 实例，因为 Chroma 使用不同的 base URL
     */
    @Provides
    @Singleton
    fun provideChromaAPI(okHttpClient: OkHttpClient, gson: Gson): com.xiaoguang.assistant.data.remote.api.ChromaAPI {
        // 从统一配置管理器中读取 Chroma 服务器地址
        val configManager = AppConfigManager()
        val chromaBaseUrl = configManager.chromaBaseUrl

        val chromaRetrofit = Retrofit.Builder()
            .baseUrl(chromaBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return chromaRetrofit.create(com.xiaoguang.assistant.data.remote.api.ChromaAPI::class.java)
    }

    /**
     * 提供 Neo4j 图数据库 API
     * 独立的 Retrofit 实例，因为 Neo4j 使用不同的 base URL
     */
    @Provides
    @Singleton
    fun provideNeo4jAPI(okHttpClient: OkHttpClient, gson: Gson): com.xiaoguang.assistant.data.remote.api.Neo4jAPI {
        // 从统一配置管理器中读取 Neo4j 服务器地址
        val configManager = AppConfigManager()
        val neo4jBaseUrl = configManager.neo4jBaseUrl

        val neo4jRetrofit = Retrofit.Builder()
            .baseUrl(neo4jBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return neo4jRetrofit.create(com.xiaoguang.assistant.data.remote.api.Neo4jAPI::class.java)
    }
}
