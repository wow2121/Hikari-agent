package com.xiaoguang.assistant.domain.repository

import com.xiaoguang.assistant.domain.model.TodoItem
import com.xiaoguang.assistant.domain.model.TodoPriority
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    /**
     * 添加待办事项
     */
    suspend fun addTodo(todo: TodoItem)

    /**
     * 获取所有待办事项
     */
    suspend fun getAllTodos(): List<TodoItem>

    /**
     * 观察待办事项变化
     */
    fun observeTodos(): Flow<List<TodoItem>>

    /**
     * 获取未完成的待办
     */
    suspend fun getIncompleteTodos(): List<TodoItem>

    /**
     * 获取自动创建的待办（AI提取的）
     */
    suspend fun getAutoCreatedTodos(): List<TodoItem>

    /**
     * 根据ID获取待办
     */
    suspend fun getTodoById(id: String): TodoItem?

    /**
     * 更新待办事项
     */
    suspend fun updateTodo(todo: TodoItem)

    /**
     * 标记为完成/未完成
     */
    suspend fun markAsCompleted(id: String, completed: Boolean)

    /**
     * 标记已添加到日历
     */
    suspend fun markAddedToCalendar(id: String, calendarEventId: Long)

    /**
     * 删除待办
     */
    suspend fun deleteTodo(id: String)

    /**
     * 删除所有已完成的待办
     */
    suspend fun deleteCompletedTodos()

    /**
     * 删除所有待办
     */
    suspend fun deleteAllTodos()
}
