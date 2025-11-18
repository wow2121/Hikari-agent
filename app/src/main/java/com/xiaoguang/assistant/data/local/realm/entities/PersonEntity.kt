package com.xiaoguang.assistant.data.local.realm.entities

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class PersonEntity : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var personId: String = ""
    var name: String = ""
    var attributes: RealmList<AttributeEmbedded> = realmListOf()
    var relationships: RealmList<RelationshipEmbedded> = realmListOf()
    var mentions: RealmList<ConversationMentionEmbedded> = realmListOf()
    var createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = System.currentTimeMillis()
}

class AttributeEmbedded : RealmObject {
    var key: String = ""
    var value: String = ""
}

class RelationshipEmbedded : RealmObject {
    var targetEntityId: String = ""
    var targetEntityName: String = ""
    var relationType: String = "" // "knows", "works_with", "family", "classmate", "teacher"
    var strength: Float = 0.5f
    var context: String = ""
}

class ConversationMentionEmbedded : RealmObject {
    var conversationId: String = ""
    var timestamp: Long = System.currentTimeMillis()
    var context: String = ""
    var sentiment: String = "neutral" // "positive", "neutral", "negative"
}
