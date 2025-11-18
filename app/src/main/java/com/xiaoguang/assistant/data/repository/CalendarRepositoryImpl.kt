package com.xiaoguang.assistant.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.xiaoguang.assistant.domain.model.CalendarEvent
import com.xiaoguang.assistant.domain.repository.CalendarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日历仓库实现
 * 使用Android CalendarContract API
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CalendarRepository {

    companion object {
        private val EVENT_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
    }

    override suspend fun getAllEvents(): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                EVENT_PROJECTION,
                null,
                null,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    events.add(cursorToEvent(it))
                }
            }
        } catch (e: SecurityException) {
            // 没有日历权限
        }

        events
    }

    override suspend fun getEventsBetween(
        startTime: Long,
        endTime: Long
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<CalendarEvent>()

        try {
            val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?)"
            val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                EVENT_PROJECTION,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                while (it.moveToNext()) {
                    events.add(cursorToEvent(it))
                }
            }
        } catch (e: SecurityException) {
            // 没有日历权限
        }

        events
    }

    /**
     * 观察日历事件变化
     * 使用ContentObserver实现实时监听，避免轮询消耗电池
     */
    override fun observeEvents(): Flow<List<CalendarEvent>> = callbackFlow {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // 日历数据变化时，发送最新数据
                scope.launch {
                    trySend(getAllEvents())
                }
            }
        }

        // 注册ContentObserver，监听日历事件变化
        context.contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,  // notifyForDescendants - 监听所有子URI的变化
            observer
        )

        // 立即发送当前数据
        trySend(getAllEvents())

        // 等待Flow被关闭
        awaitClose {
            // 取消注册ContentObserver
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    override suspend fun getEventById(eventId: Long): CalendarEvent? = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val cursor = context.contentResolver.query(
                uri,
                EVENT_PROJECTION,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    return@withContext cursorToEvent(it)
                }
            }
        } catch (e: SecurityException) {
            // 没有日历权限
        }

        null
    }

    override suspend fun createEvent(event: CalendarEvent): Long = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId())
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                values
            )

            uri?.let { ContentUris.parseId(it) } ?: -1L
        } catch (e: SecurityException) {
            -1L
        }
    }

    override suspend fun updateEvent(event: CalendarEvent): Unit = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
            }

            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
            context.contentResolver.update(uri, values, null, null)
        } catch (e: SecurityException) {
            // 没有日历权限
        }
    }

    override suspend fun deleteEvent(eventId: Long): Unit = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            // 没有日历权限
        }
    }

    override suspend fun createEventFromTodo(
        title: String,
        description: String,
        startTime: Long,
        durationMinutes: Int
    ): Long {
        val endTime = startTime + (durationMinutes * 60 * 1000)

        val event = CalendarEvent(
            title = title,
            description = description,
            startTime = startTime,
            endTime = endTime
        )

        return createEvent(event)
    }

    /**
     * 获取默认日历ID
     */
    private fun getDefaultCalendarId(): Long {
        try {
            val projection = arrayOf(CalendarContract.Calendars._ID)
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(0)
                }
            }
        } catch (e: SecurityException) {
            // 没有日历权限
        }

        return 1L // 默认返回1
    }

    /**
     * Cursor转CalendarEvent
     */
    private fun cursorToEvent(cursor: android.database.Cursor): CalendarEvent {
        return CalendarEvent(
            id = cursor.getLong(0),
            title = cursor.getString(1) ?: "",
            description = cursor.getString(2) ?: "",
            startTime = cursor.getLong(3),
            endTime = cursor.getLong(4),
            location = cursor.getString(5) ?: ""
        )
    }
}
