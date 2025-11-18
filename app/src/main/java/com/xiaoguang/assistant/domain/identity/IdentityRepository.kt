package com.xiaoguang.assistant.domain.identity

import com.xiaoguang.assistant.data.local.database.dao.IdentityDao
import com.xiaoguang.assistant.data.local.database.entity.IdentityEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 身份数据仓库
 * 负责 Identity 与数据库之间的转换
 */
@Singleton
class IdentityRepository @Inject constructor(
    private val identityDao: IdentityDao
) {

    suspend fun saveIdentity(identity: Identity) {
        identityDao.insertOrUpdate(identity.toEntity())
    }

    suspend fun getIdentity(canonicalId: String): Identity? {
        return identityDao.getById(canonicalId)?.toDomain()
    }

    suspend fun getAllIdentities(): List<Identity> {
        return identityDao.getAll().map { it.toDomain() }
    }

    suspend fun deleteIdentity(canonicalId: String) {
        identityDao.deleteById(canonicalId)
    }

    // ==================== 转换方法 ====================

    private fun Identity.toEntity(): IdentityEntity {
        return IdentityEntity(
            canonicalId = canonicalId,
            characterId = characterId,
            personIdentifier = personIdentifier,
            displayName = displayName,
            aliases = aliases.joinToString(","),  // 转换为逗号分隔的字符串
            isMaster = isMaster,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun IdentityEntity.toDomain(): Identity {
        return Identity(
            canonicalId = canonicalId,
            characterId = characterId,
            personIdentifier = personIdentifier,
            displayName = displayName,
            aliases = if (aliases.isBlank()) emptySet() else aliases.split(",").toSet(),
            isMaster = isMaster,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
