package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.database.entity.RelationshipEventEntity

/**
 * 关系事件DAO
 */
@Dao
interface RelationshipEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: RelationshipEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<RelationshipEventEntity>)

    @Update
    suspend fun updateEvent(event: RelationshipEventEntity)

    @Delete
    suspend fun deleteEvent(event: RelationshipEventEntity)

    @Query("SELECT * FROM relationship_events WHERE id = :eventId")
    suspend fun getEventById(eventId: Long): RelationshipEventEntity?

    @Query("SELECT * FROM relationship_events WHERE personName = :personName ORDER BY timestamp DESC")
    suspend fun getEventsByPerson(personName: String): List<RelationshipEventEntity>

    @Query("SELECT * FROM relationship_events WHERE personName = :personName AND importance >= 4 ORDER BY timestamp DESC")
    suspend fun getImportantEvents(personName: String): List<RelationshipEventEntity>

    @Query("SELECT * FROM relationship_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<RelationshipEventEntity>

    @Query("SELECT * FROM relationship_events WHERE eventType = :eventType ORDER BY timestamp DESC")
    suspend fun getEventsByType(eventType: String): List<RelationshipEventEntity>

    @Query("DELETE FROM relationship_events WHERE personName = :personName")
    suspend fun deleteEventsByPerson(personName: String)

    @Query("DELETE FROM relationship_events WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldEvents(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM relationship_events WHERE personName = :personName")
    suspend fun getEventCount(personName: String): Int
}
