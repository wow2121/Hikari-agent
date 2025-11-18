package com.xiaoguang.assistant.domain.repository

import com.xiaoguang.assistant.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    suspend fun addMessage(message: Message)
    suspend fun getMessages(limit: Int = 50): List<Message>
    fun observeMessages(): Flow<List<Message>>
    suspend fun getRecentMessages(count: Int): List<Message>
    suspend fun deleteAllMessages()
}
