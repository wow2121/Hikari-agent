package com.xiaoguang.assistant.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xiaoguang.assistant.data.local.database.dao.VoiceprintDao
import com.xiaoguang.assistant.data.local.database.entity.VoiceprintEntity

/**
 * 声纹元数据数据库
 * 存储声纹的基本信息，特征向量存储在ChromaDB中
 *
 * Version 2 变更：
 * - 移除 features 字段（特征向量迁移到ChromaDB）
 * - PrimaryKey 从 id(Long) 改为 voiceprintId(String)
 * - 添加 displayName, confidence, isStranger, lastRecognized 字段
 * - 重命名 lastUpdated -> updatedAt
 */
@Database(
    entities = [VoiceprintEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VoiceprintDatabase : RoomDatabase() {

    abstract fun voiceprintDao(): VoiceprintDao

    companion object {
        @Volatile
        private var INSTANCE: VoiceprintDatabase? = null

        /**
         * 数据库迁移：Version 1 -> 2
         * 破坏性迁移：删除旧表，创建新表
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 删除旧表（旧数据已废弃，声纹特征向量迁移到ChromaDB）
                database.execSQL("DROP TABLE IF EXISTS voiceprints")

                // 创建新表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS voiceprints (
                        voiceprintId TEXT PRIMARY KEY NOT NULL,
                        personId TEXT NOT NULL,
                        personName TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        sampleCount INTEGER NOT NULL DEFAULT 1,
                        confidence REAL NOT NULL DEFAULT 0.0,
                        isMaster INTEGER NOT NULL DEFAULT 0,
                        isStranger INTEGER NOT NULL DEFAULT 0,
                        lastRecognized INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 创建索引加速查询
                database.execSQL("CREATE INDEX IF NOT EXISTS index_voiceprints_personId ON voiceprints(personId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_voiceprints_personName ON voiceprints(personName)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_voiceprints_isStranger ON voiceprints(isStranger)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_voiceprints_isMaster ON voiceprints(isMaster)")
            }
        }

        fun getInstance(context: Context): VoiceprintDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VoiceprintDatabase::class.java,
                    "voiceprint_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
