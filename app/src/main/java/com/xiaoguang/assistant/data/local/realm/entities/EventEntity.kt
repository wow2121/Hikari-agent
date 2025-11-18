package com.xiaoguang.assistant.data.local.realm.entities

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class EventEntity : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var eventId: String = ""
    var title: String = ""
    var description: String = ""
    var startTime: Long = 0L
    var endTime: Long = 0L
    var location: String = ""
    var calendarEventId: Long = 0L // Reference to Android Calendar event
    var participants: RealmList<String> = realmListOf()
    var relatedConversations: RealmList<ConversationMentionEmbedded> = realmListOf()
    var isAutoCreated: Boolean = false
    var confidence: Float = 1.0f
    var createdAt: Long = System.currentTimeMillis()
}

class TaskEntity : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var taskId: String = ""
    var title: String = ""
    var description: String = ""
    var dueDate: Long = 0L
    var isCompleted: Boolean = false
    var priority: Int = 0 // 0=low, 1=medium, 2=high
    var relatedPersonId: String = "" // Who assigned this task
    var relatedEventId: String = "" // Related event
    var sourceConversationId: String = ""
    var sourceText: String = "" // Original conversation text
    var confidence: Float = 0.0f // AI extraction confidence (0.0 to 1.0)
    var isAutoCreated: Boolean = false // Created by AI or manually
    var addedToCalendar: Boolean = false // Has been added to calendar
    var calendarEventId: Long = 0L // Reference to calendar event if added
    var createdAt: Long = System.currentTimeMillis()
    var updatedAt: Long = System.currentTimeMillis()
}
