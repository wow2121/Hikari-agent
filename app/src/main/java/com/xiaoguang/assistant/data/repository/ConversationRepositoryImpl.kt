package com.xiaoguang.assistant.data.repository

import com.xiaoguang.assistant.data.local.realm.entities.ConversationEntity
import com.xiaoguang.assistant.data.local.realm.entities.toDomainModel
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.repository.ConversationRepository
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ConversationRepositoryImpl @Inject constructor(
    private val realm: Realm
) : ConversationRepository {

    override suspend fun addMessage(message: Message) {
        realm.write {
            val entity = ConversationEntity().apply {
                messageId = message.id
                role = message.role.name.lowercase()
                content = message.content
                timestamp = message.timestamp
                speakerId = message.speakerId
                speakerName = message.speakerName
            }
            copyToRealm(entity)
        }
    }

    override suspend fun getMessages(limit: Int): List<Message> {
        return realm.query<ConversationEntity>()
            .sort("timestamp", Sort.ASCENDING)
            .limit(limit)
            .find()
            .map { it.toDomainModel() }
    }

    override fun observeMessages(): Flow<List<Message>> {
        return realm.query<ConversationEntity>()
            .sort("timestamp", Sort.ASCENDING)
            .asFlow()
            .map { results ->
                results.list.map { it.toDomainModel() }
            }
    }

    override suspend fun getRecentMessages(count: Int): List<Message> {
        return realm.query<ConversationEntity>()
            .sort("timestamp", Sort.DESCENDING)
            .limit(count)
            .find()
            .reversed()
            .map { it.toDomainModel() }
    }

    override suspend fun deleteAllMessages() {
        realm.write {
            val messages = query<ConversationEntity>().find()
            delete(messages)
        }
    }
}
