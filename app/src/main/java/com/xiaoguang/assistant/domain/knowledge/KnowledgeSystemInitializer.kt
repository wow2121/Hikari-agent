package com.xiaoguang.assistant.domain.knowledge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 知识系统初始化器
 * 在应用启动时执行必要的初始化操作
 *
 * ⚠️ 重要：initialize()必须在心流系统启动前完成
 */
@Singleton
class KnowledgeSystemInitializer @Inject constructor(
    private val identityRegistry: com.xiaoguang.assistant.domain.identity.IdentityRegistry,
    private val masterInitializer: com.xiaoguang.assistant.domain.identity.MasterInitializer,
    private val worldBook: WorldBook,
    private val characterBook: CharacterBook,
    private val chromaVectorStore: com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore,
    private val graphService: com.xiaoguang.assistant.domain.knowledge.graph.RelationshipGraphService,
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,
    private val voiceprintManager: com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var isInitialized = false

    /**
     * 初始化知识系统（同步执行）
     * ⚠️ 应在应用启动时调用，且必须在心流系统启动前完成
     *
     * @return 是否初始化成功
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) {
            Timber.d("[KnowledgeSystem] 已初始化，跳过")
            return true
        }

        return try {
            Timber.i("[KnowledgeSystem] 开始初始化...")

            // 1. 初始化向量数据库集合（Chroma）
            try {
                val chromaResult = chromaVectorStore.initializeCollections()
                if (chromaResult.isSuccess) {
                    Timber.i("[KnowledgeSystem] ✅ Chroma向量数据库已初始化")
                } else {
                    Timber.w("[KnowledgeSystem] ⚠️ Chroma初始化失败（可能未启动服务）: ${chromaResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.w(e, "[KnowledgeSystem] ⚠️ Chroma初始化异常（可选功能，不影响核心功能）")
            }

            // 4. 初始化图数据库schema（Neo4j）
            try {
                val neo4jResult = graphService.initialize()
                if (neo4jResult.isSuccess) {
                    Timber.i("[KnowledgeSystem] ✅ Neo4j图数据库已初始化")
                } else {
                    Timber.w("[KnowledgeSystem] ⚠️ Neo4j初始化失败（可能未启动服务）: ${neo4jResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.w(e, "[KnowledgeSystem] ⚠️ Neo4j初始化异常（可选功能，不影响核心功能）")
            }

            // 4.5 初始化声纹集合（VoiceprintManager）
            try {
                val voiceprintResult = voiceprintManager.initialize()
                if (voiceprintResult.isSuccess) {
                    Timber.i("[KnowledgeSystem] ✅ Voiceprint声纹集合已初始化")
                } else {
                    Timber.w("[KnowledgeSystem] ⚠️ Voiceprint初始化失败（可能未启动服务）: ${voiceprintResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.w(e, "[KnowledgeSystem] ⚠️ Voiceprint初始化异常（可选功能，不影响核心功能）")
            }

            // 5. 初始化 IdentityRegistry（身份映射系统）
            Timber.i("[KnowledgeSystem] 初始化身份注册中心...")
            identityRegistry.initialize()

            // 6. 初始化World Book默认条目
            worldBook.initializeDefaultEntries()

            // 7. 初始化小光的角色档案
            characterBook.initializeXiaoguangProfile()

            // 8. 初始化主人档案（新系统：IdentityRegistry + MasterInitializer）
            Timber.i("[KnowledgeSystem] 初始化主人档案...")
            val masterIdentity = masterInitializer.initializeMaster()
            Timber.i("[KnowledgeSystem] ✅ 主人档案已就绪: ${masterIdentity.displayName}")

            // 9. 标记为已初始化和就绪
            isInitialized = true
            _isReady.value = true
            Timber.i("[KnowledgeSystem] ✅ 初始化完成")

            true

        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeSystem] ❌ 初始化失败")
            false
        }
    }

    /**
     * 重新初始化（强制）
     */
    suspend fun reinitialize(): Boolean {
        isInitialized = false
        _isReady.value = false
        return initialize()
    }

    /**
     * 获取初始化状态（同步）
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * 获取系统统计信息
     */
    suspend fun getSystemStats(): SystemStats {
        return try {
            val worldStats = worldBook.getStatistics()
            val characterCount = characterBook.getAllProfiles().size

            SystemStats(
                worldEntriesCount = worldStats.totalEntries,
                worldEnabledCount = worldStats.enabledEntries,
                characterCount = characterCount,
                isInitialized = isInitialized
            )
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeSystem] 获取统计信息失败")
            SystemStats()
        }
    }
}

/**
 * 系统统计信息
 */
data class SystemStats(
    val worldEntriesCount: Int = 0,
    val worldEnabledCount: Int = 0,
    val characterCount: Int = 0,
    val isInitialized: Boolean = false
)
