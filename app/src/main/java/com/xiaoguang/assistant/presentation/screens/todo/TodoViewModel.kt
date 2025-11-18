package com.xiaoguang.assistant.presentation.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 待办事项数据模型
 */
data class TodoItem(
    val id: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean,
    val priority: TodoPriority,
    val dueDate: Long,
    val createdAt: Long
)

/**
 * 待办优先级
 */
enum class TodoPriority(val label: String) {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低")
}

/**
 * 过滤器类型
 */
enum class TodoFilter(val label: String) {
    ALL("全部"),
    ACTIVE("未完成"),
    COMPLETED("已完成")
}

/**
 * Todo UI状态
 */
data class TodoUiState(
    val todos: List<TodoItem> = emptyList(),
    val selectedFilter: TodoFilter = TodoFilter.ALL,
    val isLoading: Boolean = false
)

/**
 * 待办事项ViewModel
 */
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState.asStateFlow()

    init {
        loadTodos()
    }

    /**
     * 加载待办事项（从 ScheduleRepository 获取类型为 TODO 的项目）
     */
    private fun loadTodos() {
        viewModelScope.launch {
            try {
                scheduleRepository.getAllSchedules().collect { schedules ->
                    // 只获取类型为 TODO 的项目
                    val todoItems = schedules
                        .filter { it.type == "TODO" }
                        .map { schedule ->
                            TodoItem(
                                id = schedule.id,
                                title = schedule.title,
                                description = schedule.description ?: "",
                                isCompleted = schedule.isCompleted,
                                priority = when (schedule.priority) {
                                    "HIGH", "URGENT" -> TodoPriority.HIGH
                                    "NORMAL" -> TodoPriority.MEDIUM
                                    else -> TodoPriority.LOW
                                },
                                dueDate = schedule.dueTime,
                                createdAt = schedule.createdAt
                            )
                        }

                    _uiState.update { it.copy(todos = todoItems, isLoading = false) }
                }
            } catch (e: Exception) {
                Timber.e(e, "[TodoViewModel] 加载待办事项失败")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 添加待办
     */
    fun addTodo(title: String, description: String, priority: TodoPriority) {
        viewModelScope.launch {
            val priorityStr = when (priority) {
                TodoPriority.HIGH -> "HIGH"
                TodoPriority.MEDIUM -> "NORMAL"
                TodoPriority.LOW -> "LOW"
            }

            val result = scheduleRepository.createSchedule(
                title = title,
                description = description,
                dueTime = System.currentTimeMillis() + 86400000 * 7, // 默认7天后
                priority = priorityStr,
                type = "TODO"
            )

            if (result.isFailure) {
                Timber.e(result.exceptionOrNull(), "[TodoViewModel] 添加待办失败")
            }
        }
    }

    /**
     * 切换完成状态
     */
    fun toggleComplete(id: String) {
        viewModelScope.launch {
            val todo = _uiState.value.todos.find { it.id == id }
            if (todo != null) {
                val result = scheduleRepository.toggleScheduleComplete(id, !todo.isCompleted)

                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "[TodoViewModel] 切换完成状态失败")
                }
            }
        }
    }

    /**
     * 删除待办
     */
    fun deleteTodo(id: String) {
        viewModelScope.launch {
            val scheduleEntity = scheduleRepository.getScheduleById(id)
            if (scheduleEntity != null) {
                val result = scheduleRepository.deleteSchedule(scheduleEntity)

                if (result.isFailure) {
                    Timber.e(result.exceptionOrNull(), "[TodoViewModel] 删除待办失败")
                }
            }
        }
    }

    /**
     * 设置过滤器
     */
    fun setFilter(filter: TodoFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    /**
     * 获取过滤后的待办
     */
    fun getFilteredTodos(): List<TodoItem> {
        val filter = _uiState.value.selectedFilter
        val todos = _uiState.value.todos

        return when (filter) {
            TodoFilter.ALL -> todos
            TodoFilter.ACTIVE -> todos.filter { !it.isCompleted }
            TodoFilter.COMPLETED -> todos.filter { it.isCompleted }
        }
    }
}
