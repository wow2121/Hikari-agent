package com.xiaoguang.assistant.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.xiaoguang.assistant.data.local.database.dao.RelationshipEventDao
import com.xiaoguang.assistant.data.local.database.dao.PersonTagDao
import com.xiaoguang.assistant.data.local.database.dao.IdentityDao
import com.xiaoguang.assistant.data.local.database.entity.RelationshipEventEntity
import com.xiaoguang.assistant.data.local.database.entity.PersonTagEntity
import com.xiaoguang.assistant.data.local.database.entity.IdentityEntity

/**
 * 社交关系数据库（辅助功能） v2.2
 * 存储：
 * 1. 关系历史事件
 * 2. 人物动态标签
 * 3. 身份标识符映射
 *
 * v2.2架构变更：
 * - 移除RelationshipNetworkEntity（迁移至纯Neo4j架构）
 * - CharacterBook和WorldBook已迁移至ChromaDB+Neo4j+Realm
 */
@Database(
    entities = [
        RelationshipEventEntity::class,
        PersonTagEntity::class,
        IdentityEntity::class
    ],
    version = 12,  // ⭐ v2.2: 升级到v12（移除RelationshipNetworkEntity，迁移至纯Neo4j）
    exportSchema = false
)
abstract class SocialDatabase : RoomDatabase() {

    abstract fun relationshipEventDao(): RelationshipEventDao
    abstract fun personTagDao(): PersonTagDao
    // v2.2: relationshipNetworkDao已移除（纯Neo4j架构）
    abstract fun identityDao(): IdentityDao

    companion object {
        @Volatile
        private var INSTANCE: SocialDatabase? = null

        fun getInstance(context: Context): SocialDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SocialDatabase::class.java,
                    "social_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
