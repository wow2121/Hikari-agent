package com.xiaoguang.assistant.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiaoguang.assistant.data.local.dao.ScheduleDao
import com.xiaoguang.assistant.data.local.entity.ScheduleEntity

/**
 * 日程数据库
 */
@Database(
    entities = [ScheduleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ScheduleDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: ScheduleDatabase? = null

        fun getInstance(context: Context): ScheduleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    "schedule_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
