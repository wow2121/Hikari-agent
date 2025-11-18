package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.database.entity.PersonTagEntity

/**
 * 人物标签DAO
 */
@Dao
interface PersonTagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: PersonTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<PersonTagEntity>)

    @Update
    suspend fun updateTag(tag: PersonTagEntity)

    @Delete
    suspend fun deleteTag(tag: PersonTagEntity)

    @Query("SELECT * FROM person_tags WHERE id = :tagId")
    suspend fun getTagById(tagId: Long): PersonTagEntity?

    @Query("SELECT * FROM person_tags WHERE personName = :personName ORDER BY confidence DESC, lastUpdated DESC")
    suspend fun getTagsByPerson(personName: String): List<PersonTagEntity>

    @Query("SELECT * FROM person_tags WHERE personName = :personName AND confidence >= :minConfidence ORDER BY confidence DESC")
    suspend fun getHighConfidenceTags(personName: String, minConfidence: Float = 0.7f): List<PersonTagEntity>

    @Query("SELECT * FROM person_tags WHERE personName = :personName AND tag = :tag")
    suspend fun getTagByName(personName: String, tag: String): PersonTagEntity?

    @Query("SELECT * FROM person_tags WHERE personName = :personName AND source = :source")
    suspend fun getTagsBySource(personName: String, source: String): List<PersonTagEntity>

    @Query("DELETE FROM person_tags WHERE personName = :personName")
    suspend fun deleteTagsByPerson(personName: String)

    @Query("DELETE FROM person_tags WHERE confidence < :minConfidence")
    suspend fun deleteLowConfidenceTags(minConfidence: Float)

    @Query("DELETE FROM person_tags WHERE personName = :personName AND confidence < :minConfidence")
    suspend fun deleteLowConfidenceTagsForPerson(personName: String, minConfidence: Float)

    @Query("SELECT COUNT(*) FROM person_tags WHERE personName = :personName")
    suspend fun getTagCount(personName: String): Int

    @Query("UPDATE person_tags SET confidence = :newConfidence, lastUpdated = :timestamp WHERE id = :tagId")
    suspend fun updateConfidence(tagId: Long, newConfidence: Float, timestamp: Long)
}
