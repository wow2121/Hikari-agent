package com.xiaoguang.assistant.domain.repository

import com.xiaoguang.assistant.domain.model.CalendarEvent
import kotlinx.coroutines.flow.Flow

/**
 * 日历仓库接口
 */
interface CalendarRepository {
    /**
     * 获取所有日历事件
     */
    suspend fun getAllEvents(): List<CalendarEvent>

    /**
     * 获取指定日期范围的事件
     */
    suspend fun getEventsBetween(startTime: Long, endTime: Long): List<CalendarEvent>

    /**
     * 观察日历事件变化
     */
    fun observeEvents(): Flow<List<CalendarEvent>>

    /**
     * 根据ID获取事件
     */
    suspend fun getEventById(eventId: Long): CalendarEvent?

    /**
     * 创建日历事件
     */
    suspend fun createEvent(event: CalendarEvent): Long

    /**
     * 更新日历事件
     */
    suspend fun updateEvent(event: CalendarEvent)

    /**
     * 删除日历事件
     */
    suspend fun deleteEvent(eventId: Long)

    /**
     * 从待办创建日历事件
     */
    suspend fun createEventFromTodo(
        title: String,
        description: String,
        startTime: Long,
        durationMinutes: Int = 60
    ): Long
}
