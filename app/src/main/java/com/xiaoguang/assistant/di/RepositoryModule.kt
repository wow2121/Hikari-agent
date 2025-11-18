package com.xiaoguang.assistant.di

import com.xiaoguang.assistant.data.repository.CalendarRepositoryImpl
import com.xiaoguang.assistant.data.repository.ConversationRepositoryImpl
import com.xiaoguang.assistant.data.repository.EmbeddingRepositoryImpl
import com.xiaoguang.assistant.data.repository.TodoRepositoryImpl
import com.xiaoguang.assistant.domain.repository.CalendarRepository
import com.xiaoguang.assistant.domain.repository.ConversationRepository
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import com.xiaoguang.assistant.domain.repository.TodoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl
    ): ConversationRepository

    @Binds
    @Singleton
    abstract fun bindTodoRepository(
        impl: TodoRepositoryImpl
    ): TodoRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(
        impl: CalendarRepositoryImpl
    ): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(
        impl: EmbeddingRepositoryImpl
    ): EmbeddingRepository
}
