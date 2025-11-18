package com.xiaoguang.assistant.data.repository

import com.xiaoguang.assistant.data.local.realm.entities.TaskEntity
import com.xiaoguang.assistant.domain.model.TodoItem
import com.xiaoguang.assistant.domain.model.toDomainModel
import com.xiaoguang.assistant.domain.repository.TodoRepository
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TodoRepositoryImpl @Inject constructor(
    private val realm: Realm
) : TodoRepository {

    override suspend fun addTodo(todo: TodoItem) {
        realm.write {
            val entity = TaskEntity().apply {
                taskId = todo.id
                title = todo.title
                description = todo.description
                dueDate = todo.dueDate
                isCompleted = todo.isCompleted
                priority = todo.priority.value
                relatedPersonId = todo.relatedPersonId
                sourceConversationId = todo.sourceConversationId
                sourceText = todo.sourceText
                confidence = todo.confidence
                isAutoCreated = todo.isAutoCreated
                addedToCalendar = todo.addedToCalendar
                calendarEventId = todo.calendarEventId
                createdAt = todo.createdAt
                updatedAt = System.currentTimeMillis()
            }
            copyToRealm(entity)
        }
    }

    override suspend fun getAllTodos(): List<TodoItem> {
        return realm.query<TaskEntity>()
            .find()
            .map { it.toDomainModel() }
    }

    override fun observeTodos(): Flow<List<TodoItem>> {
        return realm.query<TaskEntity>()
            .asFlow()
            .map { results ->
                results.list.map { it.toDomainModel() }
            }
    }

    override suspend fun getIncompleteTodos(): List<TodoItem> {
        return realm.query<TaskEntity>("isCompleted == false")
            .find()
            .map { it.toDomainModel() }
    }

    override suspend fun getAutoCreatedTodos(): List<TodoItem> {
        return realm.query<TaskEntity>("isAutoCreated == true")
            .find()
            .map { it.toDomainModel() }
    }

    override suspend fun getTodoById(id: String): TodoItem? {
        return realm.query<TaskEntity>("taskId == $0", id)
            .first()
            .find()
            ?.toDomainModel()
    }

    override suspend fun updateTodo(todo: TodoItem) {
        realm.write {
            val entity = query<TaskEntity>("taskId == $0", todo.id)
                .first()
                .find()

            entity?.apply {
                title = todo.title
                description = todo.description
                dueDate = todo.dueDate
                isCompleted = todo.isCompleted
                priority = todo.priority.value
                relatedPersonId = todo.relatedPersonId
                addedToCalendar = todo.addedToCalendar
                calendarEventId = todo.calendarEventId
                updatedAt = System.currentTimeMillis()
            }
        }
    }

    override suspend fun markAsCompleted(id: String, completed: Boolean) {
        realm.write {
            val entity = query<TaskEntity>("taskId == $0", id)
                .first()
                .find()

            entity?.apply {
                isCompleted = completed
                updatedAt = System.currentTimeMillis()
            }
        }
    }

    override suspend fun markAddedToCalendar(id: String, calendarEventId: Long) {
        realm.write {
            val entity = query<TaskEntity>("taskId == $0", id)
                .first()
                .find()

            entity?.apply {
                addedToCalendar = true
                this.calendarEventId = calendarEventId
                updatedAt = System.currentTimeMillis()
            }
        }
    }

    override suspend fun deleteTodo(id: String) {
        realm.write {
            val entity = query<TaskEntity>("taskId == $0", id)
                .first()
                .find()

            entity?.let { delete(it) }
        }
    }

    override suspend fun deleteCompletedTodos() {
        realm.write {
            val completed = query<TaskEntity>("isCompleted == true").find()
            delete(completed)
        }
    }

    override suspend fun deleteAllTodos() {
        realm.write {
            val all = query<TaskEntity>().find()
            delete(all)
        }
    }
}
