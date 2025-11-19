package com.xiaoguang.assistant.domain.identity

import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.models.BasicInfo
import com.xiaoguang.assistant.domain.knowledge.models.Background
import com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile
import com.xiaoguang.assistant.domain.knowledge.models.Personality
import com.xiaoguang.assistant.domain.knowledge.models.Preferences
import com.xiaoguang.assistant.domain.knowledge.models.Relationship
import com.xiaoguang.assistant.domain.knowledge.models.RelationType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 主人初始化器
 *
 * ⭐ 核心职责：统一管理主人身份的初始化流程
 *
 * 解决的问题：
 * - CharacterBook ↔ UnifiedSocialManager 循环依赖
 * - 文字聊天场景下主人档案从未创建
 * - 各系统使用不同的主人标识符，无法关联
 *
 * 设计理念：
 * - 主人初始化的唯一入口
 * - 自动检测现有主人档案（声纹/社交系统）
 * - 如果不存在则创建默认主人
 * - 同步注册到 IdentityRegistry
 * - 确保所有系统都能识别主人
 */
@Singleton
class MasterInitializer @Inject constructor(
    private val identityRegistry: IdentityRegistry,
    private val characterBook: CharacterBook
) {

    companion object {
        private const val MASTER_CANONICAL_ID = "master_001"
        private const val DEFAULT_MASTER_NAME = "主人"
    }

    /**
     * 初始化主人
     *
     * 流程：
     * 1. 检查 IdentityRegistry 是否已有主人
     * 2. 检查 CharacterBook 是否已有主人档案
     * 3. 如果都没有，创建默认主人
     * 4. 注册到 IdentityRegistry
     * 5. 创建 CharacterBook 档案和关系
     *
     * @return 主人身份
     */
    suspend fun initializeMaster(): Identity {
        Timber.i("[MasterInitializer] 开始初始化主人...")

        // 1. 检查 IdentityRegistry
        val existingIdentity = identityRegistry.getMasterIdentity()
        if (existingIdentity != null) {
            Timber.i("[MasterInitializer] ✅ 主人已存在于 IdentityRegistry: ${existingIdentity.displayName}")

            // 确保 CharacterBook 中有对应档案
            ensureCharacterBookProfile(existingIdentity)
            return existingIdentity
        }

        // 2. 检查 CharacterBook 是否有主人档案
        val existingProfile = characterBook.getAllProfiles().firstOrNull { it.basicInfo.isMaster }
        if (existingProfile != null) {
            Timber.i("[MasterInitializer] 发现 CharacterBook 中的主人档案: ${existingProfile.basicInfo.name}")

            // 创建并注册身份
            val identity = Identity(
                canonicalId = MASTER_CANONICAL_ID,
                characterId = existingProfile.basicInfo.characterId,
                personIdentifier = "master_default",  // 默认声纹ID
                displayName = existingProfile.basicInfo.name,
                aliases = setOf("主人", "master", "master_default", existingProfile.basicInfo.name),
                isMaster = true
            )
            identityRegistry.register(identity)

            // 确保有关系记录
            ensureMasterRelationship(existingProfile.basicInfo.characterId)

            Timber.i("[MasterInitializer] ✅ 主人档案已同步到 IdentityRegistry")
            return identity
        }

        // 3. ⭐ 检查是否有错误的主人档案（name="主人" 但 isMaster=false）
        val allProfiles = characterBook.getAllProfiles()
        val wrongMasterProfile = allProfiles.firstOrNull {
            (it.basicInfo.name == DEFAULT_MASTER_NAME || it.basicInfo.nickname == DEFAULT_MASTER_NAME)
            && !it.basicInfo.isMaster
        }

        if (wrongMasterProfile != null) {
            Timber.w("[MasterInitializer] ⚠️ 发现错误的主人档案（isMaster=false），正在修复: ${wrongMasterProfile.basicInfo.characterId}")

            // 修复档案：将 isMaster 设置为 true
            val correctedProfile = wrongMasterProfile.copy(
                basicInfo = wrongMasterProfile.basicInfo.copy(
                    isMaster = true,
                    name = DEFAULT_MASTER_NAME,
                    nickname = DEFAULT_MASTER_NAME
                )
            )
            characterBook.saveProfile(correctedProfile)

            // 创建并注册身份
            val identity = Identity(
                canonicalId = MASTER_CANONICAL_ID,
                characterId = correctedProfile.basicInfo.characterId,
                personIdentifier = "master_default",
                displayName = DEFAULT_MASTER_NAME,
                aliases = setOf("主人", "master", "master_default"),
                isMaster = true
            )
            identityRegistry.register(identity)

            // 确保有关系记录
            ensureMasterRelationship(correctedProfile.basicInfo.characterId)

            Timber.i("[MasterInitializer] ✅ 错误档案已修复为主人档案")
            return identity
        }

        // 4. 都没有，创建默认主人
        Timber.i("[MasterInitializer] 未发现主人档案，创建默认主人...")
        return createDefaultMaster()
    }

    /**
     * 创建默认主人
     */
    private suspend fun createDefaultMaster(): Identity {
        // 创建 CharacterBook 档案
        val characterId = "char_${System.currentTimeMillis()}_master"
        val masterProfile = CharacterProfile(
            basicInfo = BasicInfo(
                characterId = characterId,
                name = DEFAULT_MASTER_NAME,
                nickname = DEFAULT_MASTER_NAME,
                isMaster = true,
                bio = "小光的主人"
            ),
            personality = Personality(),
            preferences = Preferences(),
            background = Background(
                story = "小光的主人，通过文字聊天进行互动"
            )
        )

        // 保存档案
        characterBook.saveProfile(masterProfile)
        Timber.d("[MasterInitializer] 创建 CharacterBook 档案: $characterId")

        // 创建关系
        val relationship = Relationship(
            fromCharacterId = "xiaoguang_main",
            toCharacterId = characterId,
            relationType = RelationType.MASTER,
            intimacyLevel = 1.0f,  // 满值亲密度
            trustLevel = 1.0f,      // 满值信任
            isMasterRelationship = true
        )
        characterBook.saveRelationship(relationship)
        Timber.d("[MasterInitializer] 创建主人关系: intimacy=100%, trust=100%")

        // 创建并注册身份
        val identity = Identity(
            canonicalId = MASTER_CANONICAL_ID,
            characterId = characterId,
            personIdentifier = "master_default",
            displayName = DEFAULT_MASTER_NAME,
            aliases = setOf("主人", "master", "master_default"),
            isMaster = true
        )
        identityRegistry.register(identity)

        Timber.i("[MasterInitializer] ✅ 默认主人创建完成: $DEFAULT_MASTER_NAME ($characterId)")
        return identity
    }

    /**
     * 确保 CharacterBook 中有主人档案
     */
    private suspend fun ensureCharacterBookProfile(identity: Identity) {
        val characterId = identity.characterId ?: return

        // 检查是否已有档案
        val existingProfile = characterBook.getProfile(characterId)
        if (existingProfile != null) {
            Timber.d("[MasterInitializer] CharacterBook 档案已存在")
            ensureMasterRelationship(characterId)
            return
        }

        // 创建档案
        Timber.i("[MasterInitializer] 创建缺失的 CharacterBook 档案")
        val masterProfile = CharacterProfile(
            basicInfo = BasicInfo(
                characterId = characterId,
                name = identity.displayName,
                nickname = identity.displayName,
                isMaster = true,
                bio = "小光的主人"
            ),
            personality = Personality(),
            preferences = Preferences(),
            background = Background()
        )
        characterBook.saveProfile(masterProfile)
        ensureMasterRelationship(characterId)
    }

    /**
     * 确保主人关系存在
     */
    private suspend fun ensureMasterRelationship(characterId: String) {
        val existing = characterBook.getRelationship("xiaoguang_main", characterId)
        if (existing != null) {
            Timber.d("[MasterInitializer] 主人关系已存在")
            return
        }

        Timber.i("[MasterInitializer] 创建缺失的主人关系")
        val relationship = Relationship(
            fromCharacterId = "xiaoguang_main",
            toCharacterId = characterId,
            relationType = RelationType.MASTER,
            intimacyLevel = 1.0f,
            trustLevel = 1.0f,
            isMasterRelationship = true
        )
        characterBook.saveRelationship(relationship)
    }

    /**
     * 更新主人的声纹标识符
     * 当声纹识别注册主人时调用
     */
    suspend fun updateMasterVoiceprint(personIdentifier: String, personName: String? = null) {
        val masterIdentity = identityRegistry.getMasterIdentity()
        if (masterIdentity == null) {
            Timber.w("[MasterInitializer] 主人身份不存在，无法更新声纹")
            return
        }

        identityRegistry.update(masterIdentity.canonicalId) { identity ->
            val updatedAliases = if (personName != null && personName != identity.displayName) {
                identity.aliases + personName
            } else {
                identity.aliases
            }

            identity.copy(
                personIdentifier = personIdentifier,
                aliases = updatedAliases + personIdentifier
            )
        }

        Timber.i("[MasterInitializer] ✅ 主人声纹已更新: $personIdentifier")
    }

    /**
     * 添加主人的别名
     */
    suspend fun addMasterAlias(alias: String) {
        val masterIdentity = identityRegistry.getMasterIdentity()
        if (masterIdentity == null) {
            Timber.w("[MasterInitializer] 主人身份不存在，无法添加别名")
            return
        }

        identityRegistry.addAlias(masterIdentity.canonicalId, alias)
        Timber.d("[MasterInitializer] 添加主人别名: $alias")
    }
}
