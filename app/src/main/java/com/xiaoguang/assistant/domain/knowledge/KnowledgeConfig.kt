package com.xiaoguang.assistant.domain.knowledge

import com.xiaoguang.assistant.core.config.AppConfigManager

/**
 * 知识系统配置
 * 集中管理所有知识系统相关的配置项
 */
object KnowledgeConfig {

    /**
     * 向量嵌入配置
     */
    object Embedding {
        // 使用的嵌入模型
        const val MODEL = "Qwen/Qwen3-Embedding-0.6B"  // 1024维

        // 嵌入维度
        const val DIMENSION = 1024

        // SiliconFlow API Key - 从安全配置管理器中获取
        val API_KEY: String get() = AppConfigManager().siliconFlowApiKey
    }

    /**
     * Chroma向量数据库配置
     */
    object Chroma {
        // Chroma服务器地址
        const val BASE_URL = "http://localhost:8000/"

        // 集合名称
        const val COLLECTION_WORLD_BOOK = "xiaoguang_world_book"
        const val COLLECTION_CHARACTER_MEMORIES = "xiaoguang_character_memories"
        const val COLLECTION_CONVERSATIONS = "xiaoguang_conversations"

        // 是否启用Chroma（如果服务未启动，自动降级到纯关键词检索）
        var enabled = true

        // 语义搜索结果数量
        const val DEFAULT_TOP_K = 10
    }

    /**
     * Neo4j图数据库配置
     */
    object Neo4j {
        // Neo4j服务器地址
        const val BASE_URL = "http://localhost:7474/"

        // 数据库名称
        const val DATABASE = "neo4j"

        // 认证信息 - 从安全配置管理器中获取
        val USERNAME: String get() = AppConfigManager().neo4jUsername
        val PASSWORD: String get() = AppConfigManager().neo4jPassword

        // 是否启用Neo4j（如果服务未启动，自动降级到本地关系数据）
        var enabled = true

        // 关系查询最大深度
        const val MAX_RELATIONSHIP_DEPTH = 3
    }

    /**
     * World Book配置
     */
    object WorldBook {
        // 最大注入token数
        const val MAX_INJECTION_TOKENS = 2000

        // 默认优先级
        const val DEFAULT_PRIORITY = 100

        // 是否区分大小写
        const val DEFAULT_CASE_SENSITIVE = false

        // 关键词触发的最小匹配长度
        const val MIN_KEYWORD_LENGTH = 2
    }

    /**
     * Character Book配置
     */
    object CharacterBook {
        // 默认记忆重要性
        const val DEFAULT_IMPORTANCE = 0.5f

        // 记忆巩固阈值（访问次数）
        const val CONSOLIDATION_THRESHOLD = 5

        // 短期记忆最大保留时间（毫秒）7天
        const val SHORT_TERM_MAX_AGE = 7 * 24 * 60 * 60 * 1000L

        // 记忆强度计算权重
        const val IMPORTANCE_WEIGHT = 0.5f
        const val RECENCY_WEIGHT = 0.3f
        const val ACCESS_WEIGHT = 0.2f

        // 关系强度计算权重
        const val INTIMACY_WEIGHT = 0.4f
        const val TRUST_WEIGHT = 0.3f
        const val INTERACTION_WEIGHT = 0.3f
    }

    /**
     * 检索配置
     */
    object Retrieval {
        // 最大总token数
        const val MAX_TOTAL_TOKENS = 3000

        // 单次检索最大记忆数
        const val MAX_MEMORIES_PER_RETRIEVAL = 20

        // 最小相关性分数
        const val MIN_RELEVANCE_SCORE = 0.3f

        // 是否启用语义搜索
        var enableSemanticSearch = false  // 需要Chroma

        // 是否启用图检索
        var enableGraphRetrieval = false  // 需要Neo4j
    }

    /**
     * Lorebook兼容性配置
     */
    object Lorebook {
        // 支持的格式版本
        const val SUPPORTED_VERSION = "1.0"

        // 导入时的默认设置
        const val IMPORT_DEFAULT_ENABLED = true
        const val IMPORT_DEFAULT_PRIORITY = 100
    }

    /**
     * 性能配置
     */
    object Performance {
        // 批量操作的批次大小
        const val BATCH_SIZE = 10

        // 缓存大小
        const val CACHE_SIZE = 100

        // 缓存过期时间（毫秒）
        const val CACHE_EXPIRY = 5 * 60 * 1000L  // 5分钟
    }

    /**
     * 调试配置
     */
    object Debug {
        // 是否启用详细日志
        var verboseLogging = true

        // 是否记录检索统计
        var logRetrievalStats = true

        // 是否记录性能指标
        var logPerformanceMetrics = false
    }

    /**
     * 初始化配置
     * 从外部配置源加载配置（如SharedPreferences、文件等）
     */
    fun initialize() {
        // TODO: 从配置文件或SharedPreferences加载配置
        // 这里可以读取用户自定义的配置

        // 检查Chroma连接状态，决定是否启用
        Retrieval.enableSemanticSearch = Chroma.enabled

        // 检查Neo4j连接状态，决定是否启用
        Retrieval.enableGraphRetrieval = Neo4j.enabled
    }

    /**
     * 获取完整配置信息（用于调试）
     */
    fun getConfigInfo(): String {
        return """
            知识系统配置信息:

            向量嵌入:
              模型: ${Embedding.MODEL}
              维度: ${Embedding.DIMENSION}

            Chroma向量数据库:
              地址: ${Chroma.BASE_URL}
              启用: ${Chroma.enabled}
              语义搜索: ${Retrieval.enableSemanticSearch}

            Neo4j图数据库:
              地址: ${Neo4j.BASE_URL}
              数据库: ${Neo4j.DATABASE}
              启用: ${Neo4j.enabled}
              图检索: ${Retrieval.enableGraphRetrieval}

            World Book:
              最大token: ${WorldBook.MAX_INJECTION_TOKENS}

            Character Book:
              默认重要性: ${CharacterBook.DEFAULT_IMPORTANCE}
              巩固阈值: ${CharacterBook.CONSOLIDATION_THRESHOLD}

            检索配置:
              最大token: ${Retrieval.MAX_TOTAL_TOKENS}
              最大记忆数: ${Retrieval.MAX_MEMORIES_PER_RETRIEVAL}
        """.trimIndent()
    }
}
