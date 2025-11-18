package com.xiaoguang.assistant.data.local.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

/**
 * 日程 DAO
 */
@Dao
interface ScheduleDao {

    /**
     * 查询所有日程（按截止时间排序）
     */
    @Query("SELECT * FROM schedule ORDER BY dueTime ASC")
    fun getAllSchedules(): Flow<List<ScheduleEntity>>

    /**
     * 查询未完成的日程
     */
    @Query("SELECT * FROM schedule WHERE isCompleted = 0 ORDER BY dueTime ASC")
    fun getPendingSchedules(): Flow<List<ScheduleEntity>>

    /**
     * 查询已完成的日程
     */
    @Query("SELECT * FROM schedule WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedSchedules(): Flow<List<ScheduleEntity>>

    /**
     * 根据ID查询单个日程
     */
    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun getScheduleById(id: String): ScheduleEntity?

    /**
     * 插入日程
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    /**
     * 更新日程
     */
    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    /**
     * 删除日程
     */
    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    /**
     * 删除所有日程
     */
    @Query("DELETE FROM schedule")
    suspend fun deleteAllSchedules()

    /**
     * 标记日程为完成
     */
    @Query("UPDATE schedule SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markAsCompleted(id: String, completedAt: Long = System.currentTimeMillis())

    /**
     * 标记日程为未完成
     */
    @Query("UPDATE schedule SET isCompleted = 0, completedAt = NULL WHERE id = :id")
    suspend fun markAsIncomplete(id: String)
}
