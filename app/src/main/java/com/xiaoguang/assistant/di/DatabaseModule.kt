package com.xiaoguang.assistant.di

import android.content.Context
import com.xiaoguang.assistant.data.local.database.SocialDatabase
import com.xiaoguang.assistant.data.local.database.MemoryDatabase
import com.xiaoguang.assistant.data.local.database.dao.PersonTagDao
import com.xiaoguang.assistant.data.local.database.dao.RelationshipEventDao
import com.xiaoguang.assistant.data.local.database.dao.IdentityDao
import com.xiaoguang.assistant.data.local.database.dao.ConversationEmbeddingDao
import com.xiaoguang.assistant.data.local.database.dao.MemoryFactDao
import com.xiaoguang.assistant.data.local.realm.entities.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // ========== Realm Configuration ==========

    @Provides
    @Singleton
    fun provideRealmConfiguration(): RealmConfiguration {
        return RealmConfiguration.Builder(
            schema = setOf(
                ConversationEntity::class,
                PersonEntity::class,
                EventEntity::class,
                TaskEntity::class,
                AttributeEmbedded::class,
                RelationshipEmbedded::class,
                ConversationMentionEmbedded::class
            )
        )
            .name("xiaoguang.realm")
            .schemaVersion(1)
            .build()
    }

    @Provides
    @Singleton
    fun provideRealm(configuration: RealmConfiguration): Realm {
        return Realm.open(configuration)
    }

    // ========== Room Database for Social System (Auxiliary Features) ==========

    @Provides
    @Singleton
    fun provideSocialDatabase(
        @ApplicationContext context: Context
    ): SocialDatabase {
        return SocialDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePersonTagDao(
        database: SocialDatabase
    ): PersonTagDao {
        return database.personTagDao()
    }

    @Provides
    @Singleton
    fun provideRelationshipEventDao(
        database: SocialDatabase
    ): RelationshipEventDao {
        return database.relationshipEventDao()
    }

    // v2.2架构: RelationshipNetworkDao已移除（纯Neo4j架构，不再使用Room）

    @Provides
    @Singleton
    fun provideIdentityDao(
        database: SocialDatabase
    ): IdentityDao {
        return database.identityDao()
    }

    // Voiceprint 相关依赖已移至 VoiceprintModule，避免重复绑定

    // ========== Room Database for Memory System (v2.4) ==========

    @Provides
    @Singleton
    fun provideMemoryDatabase(
        @ApplicationContext context: Context
    ): MemoryDatabase {
        return MemoryDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideConversationEmbeddingDao(
        database: MemoryDatabase
    ): ConversationEmbeddingDao {
        return database.conversationEmbeddingDao()
    }

    @Provides
    @Singleton
    fun provideMemoryFactDao(
        database: MemoryDatabase
    ): MemoryFactDao {
        return database.memoryFactDao()
    }

    // ========== Room Database for Schedule System ==========

    @Provides
    @Singleton
    fun provideScheduleDatabase(
        @ApplicationContext context: Context
    ): com.xiaoguang.assistant.data.local.database.ScheduleDatabase {
        return com.xiaoguang.assistant.data.local.database.ScheduleDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideScheduleDao(
        database: com.xiaoguang.assistant.data.local.database.ScheduleDatabase
    ): com.xiaoguang.assistant.data.local.dao.ScheduleDao {
        return database.scheduleDao()
    }

    // EmbeddingRepository 已移至 RepositoryModule，避免重复绑定
}
