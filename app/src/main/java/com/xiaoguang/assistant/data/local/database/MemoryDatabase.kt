package com.xiaoguang.assistant.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.xiaoguang.assistant.data.local.database.dao.ConversationEmbeddingDao
import com.xiaoguang.assistant.data.local.database.dao.MemoryFactDao
import com.xiaoguang.assistant.data.local.database.dao.MemoryConsolidationDao
import com.xiaoguang.assistant.data.local.database.entity.ConversationEmbeddingEntity
import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import com.xiaoguang.assistant.data.local.database.entity.MemoryConsolidationEntity
import com.xiaoguang.assistant.data.local.database.entity.ConsolidationStatisticsEntity
import com.xiaoguang.assistant.data.local.database.entity.EvaluationThresholdEntity
import com.xiaoguang.assistant.data.local.database.entity.DecisionPatternAnalysisEntity

/**
 * 记忆数据库 v2.0
 *
 * 存储：
 * 1. ConversationEmbeddingEntity - 对话嵌入向量
 * 2. MemoryFactEntity - 记忆事实基础数据
 * 3. MemoryConsolidationEntity - 记忆整合决策记录
 * 4. ConsolidationStatisticsEntity - 整合统计
 * 5. EvaluationThresholdEntity - 评估阈值
 * 6. DecisionPatternAnalysisEntity - 决策模式分析
 *
 * v2.4架构：MemoryCore的核心存储
 * 配合ChromaDB（向量搜索）和Neo4j（关系图谱）使用
 */
@Database(
    entities = [
        ConversationEmbeddingEntity::class,
        MemoryFactEntity::class,
        MemoryConsolidationEntity::class,
        ConsolidationStatisticsEntity::class,
        EvaluationThresholdEntity::class,
        DecisionPatternAnalysisEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MemoryDatabase : RoomDatabase() {

    abstract fun conversationEmbeddingDao(): ConversationEmbeddingDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun memoryConsolidationDao(): MemoryConsolidationDao

    companion object {
        @Volatile
        private var INSTANCE: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "memory_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
