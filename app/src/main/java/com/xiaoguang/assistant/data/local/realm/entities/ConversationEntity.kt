package com.xiaoguang.assistant.data.local.realm.entities

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class ConversationEntity : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var messageId: String = ""
    var role: String = "" // "system", "user", "assistant"
    var content: String = ""
    var timestamp: Long = System.currentTimeMillis()
    // 说话人信息（用于多人对话识别）
    var speakerId: String? = null       // 说话人唯一标识
    var speakerName: String? = null     // 说话人显示名称
}

fun ConversationEntity.toDomainModel() = com.xiaoguang.assistant.domain.model.Message(
    id = messageId,
    role = when (role) {
        "system" -> com.xiaoguang.assistant.domain.model.MessageRole.SYSTEM
        "user" -> com.xiaoguang.assistant.domain.model.MessageRole.USER
        "assistant" -> com.xiaoguang.assistant.domain.model.MessageRole.ASSISTANT
        else -> com.xiaoguang.assistant.domain.model.MessageRole.USER
    },
    content = content,
    timestamp = timestamp,
    speakerId = speakerId,
    speakerName = speakerName
)
