package com.xiaoguang.assistant.core.config

import com.xiaoguang.assistant.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一配置管理器
 *
 * 集中管理所有系统配置：
 * - API密钥
 * - 数据库连接
 * - 模型参数
 * - 性能参数
 *
 * 配置优先级：
 * 1. 运行时动态配置（最高）
 * 2. BuildConfig（编译时）
 * 3. 默认值（最低）
 */
@Singleton
class AppConfigManager @Inject constructor() {

    // ==================== API配置 ====================

    /**
     * Silicon Flow API配置
     */
    val siliconFlowApiKey: String
        get() = BuildConfig.SILICON_FLOW_API_KEY.takeIf { it.isNotBlank() }
            ?: error("SILICON_FLOW_API_KEY未配置，请在local.properties中添加")

    val siliconFlowBaseUrl: String = "https://api.siliconflow.cn"

    val siliconFlowEmbeddingModel: String = "BAAI/bge-large-zh-v1.5"

    /**
     * Neo4j图数据库配置
     */
    val neo4jUsername: String
        get() = BuildConfig.NEO4J_USERNAME.takeIf { it.isNotBlank() } ?: "neo4j"

    val neo4jPassword: String
        get() = BuildConfig.NEO4J_PASSWORD.takeIf { it.isNotBlank() } ?: "neo4j"

    val neo4jDatabase: String
        get() = BuildConfig.NEO4J_DATABASE.takeIf { it.isNotBlank() } ?: "neo4j"

    val neo4jBaseUrl: String = "http://60.204.249.132:7474"

    /**
     * Chroma向量数据库配置
     */
    val chromaBaseUrl: String = "http://60.204.249.132:8000"

    val chromaCollectionWorldBook: String = "world_book"

    val chromaCollectionCharacterMemories: String = "character_memories"

    // ==================== 模型配置 ====================

    /**
     * 声纹识别配置
     */
    data class VoiceprintConfig(
        val sampleRate: Int = 16000,
        val frameSize: Int = 512,
        val hopSize: Int = 256,
        val melBins: Int = 40,
        val mfccCoefficients: Int = 13,
        val featureDim: Int = 128
    )

    val voiceprintConfig = VoiceprintConfig()

    /**
     * 说话人分离配置
     */
    data class DiarizationConfig(
        val minSegmentDuration: Long = 1000,  // 毫秒
        val vadFrameSize: Int = 512,
        val embeddingDim: Int = 256,
        val similarityThreshold: Float = 0.75f,
        val modelDir: String = "models/speaker_diarization"
    )

    val diarizationConfig = DiarizationConfig()

    /**
     * Jieba分词配置
     */
    data class SegmentationConfig(
        val customDictPath: String = "jieba_custom_dict.txt",
        val maxWordLength: Int = 5,
        val stopWordsEnabled: Boolean = true
    )

    val segmentationConfig = SegmentationConfig()

    // ==================== 知识检索配置 ====================

    /**
     * 知识检索配置
     */
    data class RetrievalConfig(
        val maxTotalTokens: Int = 3000,
        val maxMemoriesPerRetrieval: Int = 20,
        val minRelevanceScore: Float = 0.3f,
        val enableSemanticSearch: Boolean = false,  // Chroma集成完成后启用
        val enableGraphRetrieval: Boolean = false   // Neo4j集成完成后启用
    )

    val retrievalConfig = RetrievalConfig()

    /**
     * 向量搜索优化配置
     */
    data class VectorSearchConfig(
        val enableLSH: Boolean = true,
        val numHashTables: Int = 10,
        val numHashFunctions: Int = 5,
        val vectorDimension: Int = 1024,
        val queryCacheSize: Int = 100
    )

    val vectorSearchConfig = VectorSearchConfig()

    /**
     * Louvain社群检测配置
     */
    data class LouvainConfig(
        val minModularityGain: Double = 0.0001,
        val maxIterations: Int = 100,
        val minCommunitySize: Int = 2
    )

    val louvainConfig = LouvainConfig()

    // ==================== 性能配置 ====================

    /**
     * 缓存配置
     */
    data class CacheConfig(
        val embeddingCacheSize: Int = 500,
        val queryCacheSize: Int = 100,
        val enableCache: Boolean = true
    )

    val cacheConfig = CacheConfig()

    /**
     * 并发配置
     */
    data class ConcurrencyConfig(
        val maxThreads: Int = 4,
        val ioDispatcherThreads: Int = 8,
        val batchSize: Int = 8
    )

    val concurrencyConfig = ConcurrencyConfig()

    // ==================== 调试配置 ====================

    /**
     * 日志配置
     */
    data class LogConfig(
        val enableVerboseLogging: Boolean = BuildConfig.DEBUG,
        val logPerformance: Boolean = true,
        val logApiCalls: Boolean = true
    )

    val logConfig = LogConfig()

    // ==================== 运行时配置更新 ====================

    private val runtimeOverrides = mutableMapOf<String, Any>()

    /**
     * 运行时设置配置值
     */
    fun setConfig(key: String, value: Any) {
        runtimeOverrides[key] = value
    }

    /**
     * 获取配置值（支持运行时覆盖）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getConfig(key: String, default: T): T {
        return (runtimeOverrides[key] as? T) ?: default
    }

    /**
     * 重置所有运行时覆盖
     */
    fun resetRuntimeConfig() {
        runtimeOverrides.clear()
    }

    // ==================== 配置验证 ====================

    /**
     * 验证所有必需配置
     * @return 缺失的配置列表
     */
    fun validateConfig(): List<String> {
        val missing = mutableListOf<String>()

        // 验证API密钥
        if (BuildConfig.SILICON_FLOW_API_KEY.isBlank()) {
            missing.add("SILICON_FLOW_API_KEY")
        }

        // Neo4j密码检查（警告而非错误）
        if (neo4jPassword == "neo4j") {
            // 不添加到missing，但可以记录警告
        }

        return missing
    }

    /**
     * 获取配置摘要（用于调试）
     */
    fun getConfigSummary(): Map<String, Any> {
        return mapOf(
            "api" to mapOf(
                "siliconFlow" to mapOf(
                    "configured" to BuildConfig.SILICON_FLOW_API_KEY.isNotBlank(),
                    "baseUrl" to siliconFlowBaseUrl,
                    "embeddingModel" to siliconFlowEmbeddingModel
                ),
                "neo4j" to mapOf(
                    "username" to neo4jUsername,
                    "database" to neo4jDatabase,
                    "baseUrl" to neo4jBaseUrl
                ),
                "chroma" to mapOf(
                    "baseUrl" to chromaBaseUrl,
                    "collections" to listOf(chromaCollectionWorldBook, chromaCollectionCharacterMemories)
                )
            ),
            "models" to mapOf(
                "voiceprint" to voiceprintConfig,
                "diarization" to diarizationConfig,
                "segmentation" to segmentationConfig
            ),
            "retrieval" to retrievalConfig,
            "vectorSearch" to vectorSearchConfig,
            "louvain" to louvainConfig,
            "cache" to cacheConfig,
            "concurrency" to concurrencyConfig,
            "logging" to logConfig,
            "runtimeOverrides" to runtimeOverrides.size
        )
    }
}
