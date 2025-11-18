package com.xiaoguang.assistant.presentation.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.data.local.entity.ScheduleEntity
import com.xiaoguang.assistant.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 日程 ViewModel
 */
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedules()
    }

    /**
     * 加载日程列表
     */
    private fun loadSchedules() {
        viewModelScope.launch {
            try {
                scheduleRepository.getAllSchedules().collect { entities ->
                    val scheduleItems = entities.map { it.toScheduleItem() }
                    _uiState.value = _uiState.value.copy(
                        schedules = scheduleItems,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[ScheduleViewModel] 加载日程失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "加载日程失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 添加日程
     */
    fun addSchedule(
        title: String,
        description: String? = null,
        dueTime: Long,
        priority: SchedulePriority = SchedulePriority.NORMAL,
        type: ScheduleType = ScheduleType.TODO
    ) {
        viewModelScope.launch {
            val result = scheduleRepository.createSchedule(
                title = title,
                description = description,
                dueTime = dueTime,
                priority = priority.name,
                type = type.name
            )

            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    error = "添加日程失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * 切换完成状态
     */
    fun toggleComplete(id: String, isCompleted: Boolean) {
        viewModelScope.launch {
            val result = scheduleRepository.toggleScheduleComplete(id, isCompleted)

            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    error = "更新状态失败: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    /**
     * 删除日程
     */
    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            val schedule = _uiState.value.schedules.find { it.id == id }
            if (schedule != null) {
                val entity = schedule.toEntity()
                val result = scheduleRepository.deleteSchedule(entity)

                if (result.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        error = "删除日程失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 日程 UI 状态
 */
data class ScheduleUiState(
    val schedules: List<ScheduleItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ScheduleEntity 转 ScheduleItem
 */
private fun ScheduleEntity.toScheduleItem(): ScheduleItem {
    return ScheduleItem(
        id = id,
        title = title,
        description = description,
        dueTime = dueTime,
        isCompleted = isCompleted,
        priority = SchedulePriority.valueOf(priority),
        type = ScheduleType.valueOf(type)
    )
}

/**
 * ScheduleItem 转 ScheduleEntity
 */
private fun ScheduleItem.toEntity(): ScheduleEntity {
    return ScheduleEntity(
        id = id,
        title = title,
        description = description,
        dueTime = dueTime,
        isCompleted = isCompleted,
        priority = priority.name,
        type = type.name
    )
}
