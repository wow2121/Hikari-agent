package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.database.entity.VoiceprintEntity

/**
 * 声纹元数据DAO
 * 用于快速查询声纹基本信息（特征向量存储在ChromaDB中）
 */
@Dao
interface VoiceprintDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(voiceprint: VoiceprintEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(voiceprints: List<VoiceprintEntity>)

    @Update
    suspend fun update(voiceprint: VoiceprintEntity)

    @Delete
    suspend fun delete(voiceprint: VoiceprintEntity)

    @Query("SELECT * FROM voiceprints WHERE voiceprintId = :voiceprintId")
    suspend fun getByVoiceprintId(voiceprintId: String): VoiceprintEntity?

    @Query("SELECT * FROM voiceprints WHERE personId = :personId")
    suspend fun getByPersonId(personId: String): VoiceprintEntity?

    @Query("SELECT * FROM voiceprints WHERE personName = :personName LIMIT 1")
    suspend fun getByPersonName(personName: String): VoiceprintEntity?

    @Query("SELECT * FROM voiceprints WHERE displayName = :displayName LIMIT 1")
    suspend fun getByDisplayName(displayName: String): VoiceprintEntity?

    @Query("SELECT * FROM voiceprints WHERE isMaster = 1 LIMIT 1")
    suspend fun getMasterVoiceprint(): VoiceprintEntity?

    @Query("SELECT * FROM voiceprints WHERE isStranger = 1 ORDER BY createdAt DESC")
    suspend fun getStrangers(): List<VoiceprintEntity>

    @Query("SELECT * FROM voiceprints ORDER BY lastRecognized DESC")
    suspend fun getAllVoiceprints(): List<VoiceprintEntity>

    @Query("SELECT COUNT(*) FROM voiceprints")
    suspend fun getCount(): Int

    @Query("DELETE FROM voiceprints WHERE personId = :personId")
    suspend fun deleteByPersonId(personId: String)

    @Query("DELETE FROM voiceprints WHERE voiceprintId = :voiceprintId")
    suspend fun deleteByVoiceprintId(voiceprintId: String)

    @Query("UPDATE voiceprints SET sampleCount = sampleCount + 1, updatedAt = :timestamp, lastRecognized = :timestamp WHERE voiceprintId = :voiceprintId")
    suspend fun incrementSampleCount(voiceprintId: String, timestamp: Long)

    @Query("UPDATE voiceprints SET personName = :newName, displayName = :newName, isStranger = 0, updatedAt = :timestamp WHERE voiceprintId = :voiceprintId")
    suspend fun updatePersonName(voiceprintId: String, newName: String, timestamp: Long)

    @Query("UPDATE voiceprints SET confidence = :confidence, updatedAt = :timestamp WHERE voiceprintId = :voiceprintId")
    suspend fun updateConfidence(voiceprintId: String, confidence: Float, timestamp: Long)

    @Query("UPDATE voiceprints SET lastRecognized = :timestamp, updatedAt = :timestamp WHERE voiceprintId = :voiceprintId")
    suspend fun updateLastRecognized(voiceprintId: String, timestamp: Long)

    @Query("UPDATE voiceprints SET isMaster = :isMaster, updatedAt = :timestamp WHERE personId = :personId")
    suspend fun setMaster(personId: String, isMaster: Boolean, timestamp: Long)
}
