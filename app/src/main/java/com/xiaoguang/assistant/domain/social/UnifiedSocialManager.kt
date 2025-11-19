package com.xiaoguang.assistant.domain.social

import com.xiaoguang.assistant.domain.identity.IdentityRegistry
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.models.*
import com.xiaoguang.assistant.domain.model.EnhancedSocialRelation
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一社交管理器
 *
 * 完全基于CharacterBook的社交关系管理系统
 * - 核心：CharacterProfile + CharacterRelationship
 * - 身份映射：IdentityRegistry（统一身份标识符）
 *
 * 策略：
 * - 读取优先级：IdentityRegistry > CharacterBook
 * - 写入策略：直接更新CharacterBook
 * - 数据一致性：通过CharacterBook统一管理
 *
 * ⭐ 主人规则（严格执行）：
 * 1. 主人身份唯一且永久锁定
 * 2. 主人好感度/亲密度永远锁定在满值（100 / 1.0）
 * 3. 陌生人关系再好也不能成为主人
 * 4. 只能通过明确的 setMaster() 方法设置主人
 *
 * ⚠️ 重要：所有与社交关系相关的代码应通过此管理器访问
 */
@Singleton
class UnifiedSocialManager @Inject constructor(
    private val identityRegistry: IdentityRegistry,
    private val characterBook: CharacterBook
) {

    // ⭐ 缓存主人信息（避免频繁查询）
    private var cachedMasterName: String? = null

    /**
     * 获取主人姓名
     * @return 主人姓名，如果未设置则返回null
     */
    suspend fun getMasterName(): String? {
        if (cachedMasterName != null) {
            return cachedMasterName
        }

        // ⭐ 优先从 IdentityRegistry 查找（新系统，解除循环依赖）
        val masterIdentity = identityRegistry.getMasterIdentity()
        if (masterIdentity != null) {
            cachedMasterName = masterIdentity.displayName
            return cachedMasterName
        }

        // 降级到新系统CharacterBook（查找主人档案）
        val allProfiles = characterBook.getAllProfiles()
        val masterProfile = allProfiles.firstOrNull { it.basicInfo.isMaster }
        if (masterProfile != null) {
            cachedMasterName = masterProfile.basicInfo.name
            return cachedMasterName
        }

        return null
    }

    /**
     * 检查某人是否是主人
     */
    suspend fun isMaster(personName: String): Boolean {
        val masterName = getMasterName()
        return masterName == personName
    }

    /**
     * 设置主人（唯一入口）
     * ⚠️ 主人只能设置一次，且不可更改
     *
     * @param personName 要设置为主人的人名
     * @param platformId 平台ID
     * @return 是否设置成功
     */
    suspend fun setMaster(personName: String, platformId: String = ""): Result<Unit> {
        return try {
            // 1. 检查是否已有主人
            val existingMaster = getMasterName()
            if (existingMaster != null) {
                if (existingMaster == personName) {
                    Timber.d("[UnifiedSocial] $personName 已经是主人")
                    return Result.success(Unit)
                } else {
                    return Result.failure(Exception("主人已存在：$existingMaster，不能设置新主人"))
                }
            }

            // 2. 在新系统中设置主人
            var profile = characterBook.getProfileByName(personName)
            if (profile == null) {
                // 创建新档案
                profile = CharacterProfile(
                    basicInfo = BasicInfo(
                        characterId = "master_$personName",
                        name = personName,
                        platformId = platformId,
                        isMaster = true  // ⭐ 标记为主人
                    )
                )
            } else {
                // 更新为主人
                profile = profile.copy(
                    basicInfo = profile.basicInfo.copy(isMaster = true)
                )
            }
            characterBook.saveProfile(profile)

            // 3. 创建主人关系（小光→主人）
            val masterRelationship = Relationship(
                fromCharacterId = "xiaoguang",
                toCharacterId = profile.basicInfo.characterId,
                relationType = RelationType.MASTER,
                intimacyLevel = 1.0f,  // ⭐ 锁定满值
                trustLevel = 1.0f,     // ⭐ 锁定满值
                description = "小光最重要的人",
                isMasterRelationship = true  // ⭐ 标记为主人关系
            )
            characterBook.saveRelationship(masterRelationship)

            // 4. 更新缓存
            cachedMasterName = personName

            Timber.i("[UnifiedSocial] ⭐ 成功设置主人: $personName")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[UnifiedSocial] 设置主人失败")
            Result.failure(e)
        }
    }

    /**
     * 获取或创建统一社交关系
     *
     * 读取优先级：CharacterBook > SocialRelation
     *
     * @param personName 人物名称
     * @param platformId 平台ID（如QQ号）
     * @param initialRelationType 初始关系类型
     * @param description 描述
     * @return 统一社交关系
     */
    suspend fun getOrCreateRelation(
        personName: String,
        platformId: String = "",
        initialRelationType: String = "unknown",
        description: String = ""
    ): UnifiedSocialRelation {
        // 1. 优先从CharacterBook读取
        val characterProfile = characterBook.getProfileByName(personName)
        if (characterProfile != null) {
            Timber.d("[UnifiedSocial] 从CharacterBook读取: $personName (isMaster=${characterProfile.basicInfo.isMaster})")
            return UnifiedSocialRelation.fromCharacterProfile(
                profile = characterProfile,
                characterBook = characterBook
            )
        }

        // 2. ⚠️ 检查是否是主人（通过IdentityRegistry查询，而不是名称匹配）
        val masterIdentity = identityRegistry.getMasterIdentity()
        val isMasterPerson = masterIdentity != null && (
            masterIdentity.displayName == personName ||
            masterIdentity.aliases.contains(personName) ||
            masterIdentity.personIdentifier == personName ||
            masterIdentity.characterId == personName
        )
        if (isMasterPerson) {
            Timber.i("[UnifiedSocial] ⭐ 通过IdentityRegistry识别主人: $personName")
        }

        // 3. ⭐ 直接在CharacterBook创建新档案（不再降级到旧系统）
        val characterId = "char_${System.currentTimeMillis()}_${personName.hashCode()}"
        val newProfile = CharacterProfile(
            basicInfo = BasicInfo(
                characterId = characterId,
                name = personName,
                platformId = platformId,
                isMaster = isMasterPerson,  // ⭐ 主人标记
                createdAt = System.currentTimeMillis()
            ),
            personality = Personality(
                traits = emptyMap(),
                description = description.ifEmpty { null }
            )
        )

        characterBook.saveProfile(newProfile)
        Timber.i("[UnifiedSocial] ✅ 创建新角色档案: $personName (isMaster=$isMasterPerson)")

        // 4. 创建与小光的关系
        val relationType = when {
            isMasterPerson -> RelationType.MASTER
            initialRelationType == "friend" -> RelationType.FRIEND
            initialRelationType == "family" -> RelationType.FAMILY
            else -> RelationType.OTHER
        }

        val relationship = Relationship(
            fromCharacterId = "xiaoguang_main",
            toCharacterId = characterId,
            relationType = relationType,
            intimacyLevel = if (isMasterPerson) 1.0f else 0.5f,  // ⭐ 主人锁定满值
            trustLevel = if (isMasterPerson) 1.0f else 0.5f,
            description = if (isMasterPerson) "小光最重要的人" else null,
            isMasterRelationship = isMasterPerson  // ⭐ 主人关系标记
        )

        characterBook.saveRelationship(relationship)
        Timber.i("[UnifiedSocial] ✅ 创建关系: xiaoguang_main -> $characterId (${relationType.displayName})")

        // 5. 返回UnifiedSocialRelation
        return UnifiedSocialRelation.fromCharacterProfile(
            profile = newProfile,
            characterBook = characterBook
        )
    }

    /**
     * 根据平台ID获取关系
     */
    suspend fun getRelationByPlatformId(platformId: String): UnifiedSocialRelation? {
        // 优先从CharacterBook查询
        val profile = characterBook.getProfileByPlatformId(platformId)
        if (profile != null) {
            return UnifiedSocialRelation.fromCharacterProfile(profile, characterBook)
        }

        // 旧系统没有直接的platformId查询，返回null
        return null
    }

    /**
     * 更新好感度
     *
     * 完全基于CharacterBook系统
     * ⭐ 主人规则：如果是主人，好感度永远锁定在100/1.0，不可修改
     *
     * @param personName 人物名称
     * @param delta 变化值
     * @param reason 原因
     * @return 更新后的好感度
     */
    suspend fun updateAffection(
        personName: String,
        delta: Int,
        reason: String
    ): Int {
        // ⭐ 检查是否是主人
        val isMasterPerson = isMaster(personName)

        if (isMasterPerson) {
            Timber.d("[UnifiedSocial] ⭐ 主人好感度锁定100: $personName")
            return 100  // 主人永远100
        }

        // 1. 获取或创建档案
        val characterProfile = characterBook.getProfileByName(personName)
            ?: run {
                // 档案不存在，先创建
                Timber.d("[UnifiedSocial] 档案不存在，自动创建: $personName")
                getOrCreateRelation(personName)
                characterBook.getProfileByName(personName)
            }

        if (characterProfile == null) {
            Timber.w("[UnifiedSocial] 无法创建档案: $personName")
            return 50
        }

        // 2. 获取关系
        var relationship = characterBook.getRelationship(characterProfile.basicInfo.characterId, "xiaoguang")
        if (relationship == null) {
            // ⭐ 关系不存在，创建默认关系（根据档案的 isMaster 标志决定关系类型）
            val relationType = if (characterProfile.basicInfo.isMaster) {
                RelationType.MASTER
            } else {
                RelationType.OTHER
            }
            relationship = Relationship(
                fromCharacterId = "xiaoguang_main",
                toCharacterId = characterProfile.basicInfo.characterId,
                relationType = relationType,
                intimacyLevel = if (characterProfile.basicInfo.isMaster) 1.0f else 0.5f
            )
            characterBook.saveRelationship(relationship)
            Timber.d("[UnifiedSocial] 创建默认关系: $personName (type=$relationType, isMaster=${characterProfile.basicInfo.isMaster})")
        }

        // ⚠️ 再次检查主人关系（双重保险）
        if (relationship.isMasterRelationship) {
            Timber.d("[UnifiedSocial] ⭐ 主人关系好感度锁定: $personName")
            return 100
        }

        // 3. 计算新的亲密度
        var currentLevel = (relationship.intimacyLevel * 100).toInt()
        val newLevel = (currentLevel + delta).coerceIn(0, 100)
        val newIntimacyLevel = newLevel / 100f

        // 4. 创建交互记录
        val interactionRecord = InteractionRecord(
            timestamp = System.currentTimeMillis(),
            interactionType = "affection_change",
            content = reason,
            emotionalImpact = delta / 10f  // 转换为-1~1的影响
        )

        // 5. 更新关系
        val updatedRelationship = relationship.copy(
            intimacyLevel = newIntimacyLevel,
            interactionCount = relationship.interactionCount + 1,
            lastInteractionAt = System.currentTimeMillis()
        ).recordInteraction(interactionRecord)

        characterBook.saveRelationship(updatedRelationship)

        Timber.d("[UnifiedSocial] ✅ 更新好感度: $personName $currentLevel -> $newLevel (原因: $reason)")
        return newLevel
    }

    /**
     * 记录互动
     *
     * 完全基于CharacterBook系统
     */
    suspend fun recordInteraction(personName: String) {
        // 1. 获取或创建档案
        val characterProfile = characterBook.getProfileByName(personName)
            ?: run {
                Timber.d("[UnifiedSocial] 档案不存在，自动创建: $personName")
                getOrCreateRelation(personName)
                characterBook.getProfileByName(personName)
            }

        if (characterProfile == null) {
            Timber.w("[UnifiedSocial] 无法创建档案: $personName")
            return
        }

        // 2. 获取或创建关系
        var relationship = characterBook.getRelationship(characterProfile.basicInfo.characterId, "xiaoguang")
        if (relationship == null) {
            // ⭐ 关系不存在，创建默认关系（根据档案的 isMaster 标志决定关系类型）
            val relationType = if (characterProfile.basicInfo.isMaster) {
                RelationType.MASTER
            } else {
                RelationType.OTHER
            }
            relationship = Relationship(
                fromCharacterId = "xiaoguang_main",
                toCharacterId = characterProfile.basicInfo.characterId,
                relationType = relationType,
                intimacyLevel = if (characterProfile.basicInfo.isMaster) 1.0f else 0.5f
            )
            Timber.d("[UnifiedSocial] 创建默认关系: $personName (type=$relationType, isMaster=${characterProfile.basicInfo.isMaster})")
        }

        // 3. 更新互动计数和时间
        val updatedRelationship = relationship.copy(
            interactionCount = relationship.interactionCount + 1,
            lastInteractionAt = System.currentTimeMillis()
        )
        characterBook.saveRelationship(updatedRelationship)

        Timber.d("[UnifiedSocial] ✅ 记录互动: $personName (总计: ${updatedRelationship.interactionCount})")
    }

    /**
     * 更新描述
     *
     * 完全基于CharacterBook系统
     */
    suspend fun updateDescription(personName: String, description: String) {
        // 1. 获取或创建档案
        val characterProfile = characterBook.getProfileByName(personName)
            ?: run {
                Timber.d("[UnifiedSocial] 档案不存在，自动创建: $personName")
                getOrCreateRelation(personName)
                characterBook.getProfileByName(personName)
            }

        if (characterProfile == null) {
            Timber.w("[UnifiedSocial] 无法创建档案: $personName")
            return
        }

        // 2. 更新描述
        val updatedProfile = characterProfile.copy(
            personality = characterProfile.personality.copy(
                description = description
            )
        )
        characterBook.saveProfile(updatedProfile)

        Timber.d("[UnifiedSocial] ✅ 更新描述: $personName")
    }

    /**
     * 添加记忆关联
     *
     * 完全基于CharacterBook系统
     */
    suspend fun addRelatedMemory(
        personName: String,
        memoryContent: String,
        importance: Float = 0.5f,
        category: MemoryCategory = MemoryCategory.EPISODIC
    ) {
        // 1. 获取或创建档案
        val characterProfile = characterBook.getProfileByName(personName)
            ?: run {
                Timber.d("[UnifiedSocial] 档案不存在，自动创建: $personName")
                getOrCreateRelation(personName)
                characterBook.getProfileByName(personName)
            }

        if (characterProfile == null) {
            Timber.w("[UnifiedSocial] 无法创建档案: $personName")
            return
        }

        // 2. 添加记忆到CharacterBook
        val memory = CharacterMemory(
            memoryId = "mem_${System.currentTimeMillis()}",
            characterId = characterProfile.basicInfo.characterId,
            category = category,
            content = memoryContent,
            importance = importance,
            createdAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis()
        )
        characterBook.addMemory(memory)
        Timber.d("[UnifiedSocial] ✅ 添加记忆: $personName (${memory.memoryId})")
    }

    /**
     * 获取所有关系
     *
     * 完全基于CharacterBook系统
     */
    suspend fun getAllRelations(): List<UnifiedSocialRelation> {
        val profiles = characterBook.getAllProfiles()
        return profiles.map { UnifiedSocialRelation.fromCharacterProfile(it, characterBook) }
    }

    /**
     * 获取增强的社交关系（用于心流系统）
     *
     * 完全基于CharacterBook系统
     * 包含态度、关系层级等信息
     */
    suspend fun getEnhancedRelation(
        personName: String,
        context: String = ""
    ): EnhancedSocialRelation? {
        // 1. 获取或创建UnifiedSocialRelation
        val unifiedRelation = getOrCreateRelation(personName)

        // 2. 计算最近好感度变化（从Relationship的interactionHistory）
        val characterProfile = characterBook.getProfileByName(personName)
        val recentDelta = if (characterProfile != null) {
            val relationship = characterBook.getRelationship(characterProfile.basicInfo.characterId, "xiaoguang")
            // 从最近的交互记录计算变化
            val recentInteractions = relationship?.interactionHistory?.takeLast(5) ?: emptyList()
            val avgImpact = recentInteractions.mapNotNull { record -> record.emotionalImpact }.average()
            (avgImpact * 10).toInt()  // 转换为-10~10的整数
        } else {
            0
        }

        // 3. 使用新系统的fromUnifiedRelation创建EnhancedSocialRelation
        return EnhancedSocialRelation.fromUnifiedRelation(
            unifiedRelation = unifiedRelation,
            recentAffectionDelta = recentDelta,
            context = context
        )
    }

    /**
     * 搜索关系
     *
     * 完全基于CharacterBook系统
     */
    suspend fun searchRelations(query: String): List<UnifiedSocialRelation> {
        val profiles = characterBook.searchProfiles(query)
        return profiles.map { UnifiedSocialRelation.fromCharacterProfile(it, characterBook) }
    }

    /**
     * 获取好感度变化历史
     *
     * 完全基于CharacterBook系统
     * 从Relationship的interactionRecords中提取好感度变化
     *
     * @param personName 人物名称
     * @return 好感度变化历史列表（转换为旧格式以保持兼容性）
     */
    suspend fun getAffectionHistory(personName: String): List<com.xiaoguang.assistant.data.local.database.entity.AffectionChange> {
        return try {
            val characterProfile = characterBook.getProfileByName(personName)
            if (characterProfile == null) {
                Timber.d("[UnifiedSocial] 未找到档案: $personName")
                return emptyList()
            }

            val relationship = characterBook.getRelationship(characterProfile.basicInfo.characterId, "xiaoguang")
            if (relationship == null) {
                Timber.d("[UnifiedSocial] 未找到关系: $personName")
                return emptyList()
            }

            // 从交互记录中提取好感度变化
            val affectionChanges = relationship.interactionHistory
                .filter { record -> record.interactionType == "affection_change" }
                .map { record ->
                    val delta = (record.emotionalImpact ?: 0f * 10).toInt()  // 转换回整数变化量
                    com.xiaoguang.assistant.data.local.database.entity.AffectionChange(
                        characterId = characterProfile.basicInfo.characterId,
                        oldValue = 50,  // 简化处理，无法从单条记录推断原值
                        newValue = (relationship.intimacyLevel * 100).toInt(),
                        reason = record.content ?: "互动",
                        timestamp = record.timestamp
                    )
                }

            affectionChanges
        } catch (e: Exception) {
            Timber.w(e, "[UnifiedSocial] 获取好感度历史失败: $personName")
            emptyList()
        }
    }
}

/**
 * 统一社交关系
 *
 * 融合新旧两套系统的数据结构
 */
data class UnifiedSocialRelation(
    val characterId: String,
    val personName: String,
    val platformId: String,
    val relationshipType: String,
    val affectionLevel: Int,  // 0-100
    val description: String,
    val interactionCount: Int,
    val lastInteractionAt: Long,
    val personalityTraits: List<String> = emptyList(),
    val preferences: List<String> = emptyList(),
    val isMaster: Boolean = false  // ⭐ 主人标识
) {
    companion object {
        suspend fun fromCharacterProfile(
            profile: CharacterProfile,
            characterBook: CharacterBook
        ): UnifiedSocialRelation {
            // 查询与小光的关系
            val relationship = characterBook.getRelationship(profile.basicInfo.characterId, "xiaoguang")

            return UnifiedSocialRelation(
                characterId = profile.basicInfo.characterId,
                personName = profile.basicInfo.name,
                platformId = profile.basicInfo.platformId ?: "",
                relationshipType = relationship?.relationType?.displayName ?: "未知",
                affectionLevel = if (profile.basicInfo.isMaster) {
                    100  // ⭐ 主人锁定100
                } else {
                    ((relationship?.actualIntimacyLevel ?: 0.5f) * 100).toInt()
                },
                description = profile.personality.description ?: "",
                interactionCount = relationship?.interactionCount ?: 0,
                lastInteractionAt = relationship?.lastInteractionAt ?: System.currentTimeMillis(),
                personalityTraits = profile.personality.traits.keys.toList(),
                preferences = profile.preferences.interests,
                isMaster = profile.basicInfo.isMaster  // ⭐ 主人标识
            )
        }

    }
}

/**
 * 同步结果
 */
data class SyncResult(
    val total: Int,
    val success: Int,
    val errors: List<String>
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

