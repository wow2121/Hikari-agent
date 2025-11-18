package com.xiaoguang.assistant.domain.identity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 身份注册中心
 *
 * ⭐ 核心职责：统一管理所有身份标识符的映射关系
 *
 * 解决的问题：
 * - personIdentifier（声纹ID）、characterId（角色书ID）、speakerName（消息名）无法关联
 * - 不同子系统使用不同的ID，导致数据查询失败
 * - 缺少别名系统，"主人"、"master_default"、"小明" 无法识别为同一人
 *
 * 设计理念：
 * - 所有身份查询的唯一入口
 * - 支持多种标识符类型和别名
 * - 线程安全的内存缓存 + 持久化存储
 * - 自动同步到其他系统（CharacterBook、UnifiedSocialManager）
 */
@Singleton
class IdentityRegistry @Inject constructor(
    private val identityRepository: IdentityRepository
) {

    // 内存缓存：canonicalId → Identity
    private val identityCache = mutableMapOf<String, Identity>()

    // 别名索引：alias → canonicalId
    private val aliasIndex = mutableMapOf<String, String>()

    // 并发控制
    private val mutex = Mutex()

    // 初始化状态
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: Flow<Boolean> = _isInitialized.asStateFlow()

    /**
     * 初始化：从数据库加载所有身份
     */
    suspend fun initialize() {
        mutex.withLock {
            try {
                Timber.i("[IdentityRegistry] 开始初始化...")

                val identities = identityRepository.getAllIdentities()
                identities.forEach { identity ->
                    cacheIdentity(identity)
                }

                _isInitialized.value = true
                Timber.i("[IdentityRegistry] ✅ 初始化完成，加载 ${identities.size} 个身份")
            } catch (e: Exception) {
                Timber.e(e, "[IdentityRegistry] ❌ 初始化失败")
                throw e
            }
        }
    }

    /**
     * 注册新身份
     *
     * @param identity 身份信息
     * @param persist 是否持久化到数据库（默认true）
     */
    suspend fun register(identity: Identity, persist: Boolean = true) {
        mutex.withLock {
            Timber.d("[IdentityRegistry] 注册身份: ${identity.displayName} (canonical=${identity.canonicalId})")

            // 更新缓存
            cacheIdentity(identity)

            // 持久化
            if (persist) {
                identityRepository.saveIdentity(identity)
            }
        }
    }

    /**
     * 通过任意标识符查询身份
     *
     * 支持的标识符：
     * - canonicalId (主键)
     * - characterId (CharacterBook ID)
     * - personIdentifier (声纹ID)
     * - displayName
     * - 任何别名
     *
     * @param identifier 任意标识符
     * @return 找到的身份，如果不存在返回null
     */
    suspend fun resolve(identifier: String?): Identity? {
        if (identifier.isNullOrBlank()) return null

        return mutex.withLock {
            // 1. 先尝试作为主键查询
            val byCanonical = identityCache[identifier]
            if (byCanonical != null) return@withLock byCanonical

            // 2. 查询别名索引
            val canonicalId = aliasIndex[identifier]
            if (canonicalId != null) {
                return@withLock identityCache[canonicalId]
            }

            // 3. 遍历查询（支持 characterId、personIdentifier）
            identityCache.values.firstOrNull { identity ->
                identifier == identity.characterId ||
                identifier == identity.personIdentifier ||
                identifier == identity.displayName ||
                identity.aliases.contains(identifier)
            }
        }
    }

    /**
     * 获取 CharacterBook ID
     */
    suspend fun getCharacterId(identifier: String?): String? {
        return resolve(identifier)?.characterId
    }

    /**
     * 获取显示名称
     */
    suspend fun getDisplayName(identifier: String?): String? {
        return resolve(identifier)?.displayName
    }

    /**
     * 获取主人身份
     */
    suspend fun getMasterIdentity(): Identity? {
        return mutex.withLock {
            identityCache.values.firstOrNull { it.isMaster }
        }
    }

    /**
     * 检查是否已注册主人
     */
    suspend fun hasMaster(): Boolean {
        return getMasterIdentity() != null
    }

    /**
     * 更新身份信息
     */
    suspend fun update(canonicalId: String, updater: (Identity) -> Identity) {
        mutex.withLock {
            val existing = identityCache[canonicalId] ?: return@withLock

            // 移除旧的别名索引
            existing.aliases.forEach { alias ->
                aliasIndex.remove(alias)
            }

            // 更新
            val updated = updater(existing)
            cacheIdentity(updated)
            identityRepository.saveIdentity(updated)

            Timber.d("[IdentityRegistry] 更新身份: ${updated.displayName}")
        }
    }

    /**
     * 添加别名
     */
    suspend fun addAlias(identifier: String, newAlias: String) {
        val identity = resolve(identifier) ?: return

        update(identity.canonicalId) { oldIdentity ->
            oldIdentity.copy(aliases = oldIdentity.aliases + newAlias)
        }
    }

    /**
     * 获取所有身份（用于调试）
     */
    suspend fun getAllIdentities(): List<Identity> {
        return mutex.withLock {
            identityCache.values.toList()
        }
    }

    // ==================== 私有方法 ====================

    private fun cacheIdentity(identity: Identity) {
        identityCache[identity.canonicalId] = identity

        // 建立别名索引
        identity.aliases.forEach { alias ->
            aliasIndex[alias] = identity.canonicalId
        }

        // 常用字段也作为索引
        if (identity.characterId != null) {
            aliasIndex[identity.characterId] = identity.canonicalId
        }
        if (identity.personIdentifier != null) {
            aliasIndex[identity.personIdentifier] = identity.canonicalId
        }
        aliasIndex[identity.displayName] = identity.canonicalId
    }
}

/**
 * 身份信息
 *
 * @param canonicalId 规范ID（主键，唯一标识符）
 * @param characterId CharacterBook 中的角色ID
 * @param personIdentifier 声纹识别系统的 personIdentifier
 * @param displayName 显示名称（用户看到的名字）
 * @param aliases 所有别名（包括昵称、旧名字等）
 * @param isMaster 是否为主人
 * @param createdAt 创建时间戳
 * @param updatedAt 最后更新时间戳
 */
data class Identity(
    val canonicalId: String,
    val characterId: String? = null,
    val personIdentifier: String? = null,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
    val isMaster: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 生成规范ID
         */
        fun generateCanonicalId(prefix: String = "id"): String {
            return "${prefix}_${System.currentTimeMillis()}_${(0..9999).random()}"
        }

        /**
         * 创建主人身份
         */
        fun createMaster(
            displayName: String = "主人",
            characterId: String? = null,
            personIdentifier: String? = null
        ): Identity {
            return Identity(
                canonicalId = "master_001",  // 主人使用固定ID
                characterId = characterId,
                personIdentifier = personIdentifier,
                displayName = displayName,
                aliases = setOf("主人", "master", "master_default"),
                isMaster = true
            )
        }
    }
}
