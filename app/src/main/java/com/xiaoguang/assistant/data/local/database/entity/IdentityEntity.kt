package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 身份标识符实体
 * 用于统一管理各种ID映射关系
 */
@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey
    val canonicalId: String,
    val characterId: String? = null,
    val personIdentifier: String? = null,
    val displayName: String,
    val aliases: String = "",  // 逗号分隔的别名列表
    val isMaster: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
