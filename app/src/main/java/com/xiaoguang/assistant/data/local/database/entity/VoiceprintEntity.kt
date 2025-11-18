package com.xiaoguang.assistant.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 声纹元数据实体
 * 存储声纹的基本信息，特征向量存储在ChromaDB中
 *
 * 架构说明：
 * - 声纹特征向量（128维）存储在 ChromaDB collection "xiaoguang_voiceprints" 中
 * - 本实体仅存储元数据，用于快速查询和本地缓存
 */
@Entity(tableName = "voiceprints")
data class VoiceprintEntity(
    @PrimaryKey
    val voiceprintId: String,  // 声纹ID（UUID），与ChromaDB中的document ID对应
    val personId: String,  // 人物ID（对应CharacterProfile.basicInfo.characterId）
    val personName: String,  // 人物名称
    val displayName: String,  // 显示名称（可能是临时名称，如"陌生人_1234"）
    val sampleCount: Int = 1,  // 声纹样本数量
    val confidence: Float = 0f,  // 识别置信度（0.0-1.0）
    val isMaster: Boolean = false,  // 是否是主人
    val isStranger: Boolean = false,  // 是否是陌生人（未命名）
    val lastRecognized: Long = System.currentTimeMillis(),  // 最后识别时间
    val createdAt: Long = System.currentTimeMillis(),  // 创建时间
    val updatedAt: Long = System.currentTimeMillis()  // 更新时间
)
