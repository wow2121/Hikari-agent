package com.xiaoguang.assistant.data.repository

import com.xiaoguang.assistant.data.local.dao.ScheduleDao
import com.xiaoguang.assistant.data.local.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日程仓库
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val scheduleDao: ScheduleDao
) {

    /**
     * 获取所有日程
     */
    fun getAllSchedules(): Flow<List<ScheduleEntity>> {
        return scheduleDao.getAllSchedules()
    }

    /**
     * 获取未完成的日程
     */
    fun getPendingSchedules(): Flow<List<ScheduleEntity>> {
        return scheduleDao.getPendingSchedules()
    }

    /**
     * 获取已完成的日程
     */
    fun getCompletedSchedules(): Flow<List<ScheduleEntity>> {
        return scheduleDao.getCompletedSchedules()
    }

    /**
     * 根据ID获取日程
     */
    suspend fun getScheduleById(id: String): ScheduleEntity? {
        return scheduleDao.getScheduleById(id)
    }

    /**
     * 创建日程
     */
    suspend fun createSchedule(
        title: String,
        description: String? = null,
        dueTime: Long,
        priority: String = "NORMAL",
        type: String = "TODO"
    ): Result<String> {
        return try {
            val id = UUID.randomUUID().toString()
            val schedule = ScheduleEntity(
                id = id,
                title = title,
                description = description,
                dueTime = dueTime,
                priority = priority,
                type = type
            )
            scheduleDao.insertSchedule(schedule)
            Timber.i("[ScheduleRepository] 创建日程成功: $title")
            Result.success(id)
        } catch (e: Exception) {
            Timber.e(e, "[ScheduleRepository] 创建日程失败")
            Result.failure(e)
        }
    }

    /**
     * 更新日程
     */
    suspend fun updateSchedule(schedule: ScheduleEntity): Result<Unit> {
        return try {
            scheduleDao.updateSchedule(schedule)
            Timber.i("[ScheduleRepository] 更新日程成功: ${schedule.title}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[ScheduleRepository] 更新日程失败")
            Result.failure(e)
        }
    }

    /**
     * 删除日程
     */
    suspend fun deleteSchedule(schedule: ScheduleEntity): Result<Unit> {
        return try {
            scheduleDao.deleteSchedule(schedule)
            Timber.i("[ScheduleRepository] 删除日程成功: ${schedule.title}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[ScheduleRepository] 删除日程失败")
            Result.failure(e)
        }
    }

    /**
     * 切换日程完成状态
     */
    suspend fun toggleScheduleComplete(id: String, isCompleted: Boolean): Result<Unit> {
        return try {
            if (isCompleted) {
                scheduleDao.markAsCompleted(id)
            } else {
                scheduleDao.markAsIncomplete(id)
            }
            Timber.i("[ScheduleRepository] 切换日程状态成功: $id -> $isCompleted")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[ScheduleRepository] 切换日程状态失败")
            Result.failure(e)
        }
    }

    /**
     * 删除所有日程
     */
    suspend fun deleteAllSchedules(): Result<Unit> {
        return try {
            scheduleDao.deleteAllSchedules()
            Timber.i("[ScheduleRepository] 删除所有日程成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[ScheduleRepository] 删除所有日程失败")
            Result.failure(e)
        }
    }
}
