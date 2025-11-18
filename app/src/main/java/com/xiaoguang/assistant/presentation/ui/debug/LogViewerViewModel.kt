package com.xiaoguang.assistant.presentation.ui.debug

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.core.logging.LogCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 日志级别
 */
enum class LogLevel {
    DEBUG, INFO, WARN, ERROR, ALL
}

/**
 * 日志条目
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Long
)

/**
 * 日志查看器UI状态
 */
data class LogViewerUiState(
    val logs: List<LogEntry> = emptyList(),
    val selectedLevel: LogLevel = LogLevel.ALL,
    val searchQuery: String = "",
    val isAutoScroll: Boolean = true
)

/**
 * 日志查看器ViewModel
 *
 * 功能：
 * - 从 MemoryLogTree 读取真实的应用日志
 * - 自动过滤系统无用日志（如 SurfaceComposerClient）
 * - 支持按级别和关键词过滤
 * - 每2秒自动刷新日志
 */
@HiltViewModel
class LogViewerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    init {
        loadRealLogs()
        startAutoRefresh()
    }

    /**
     * 从 LogCollector 加载真实日志
     */
    private fun loadRealLogs() {
        val realLogs = LogCollector.getLogs().map { item ->
            LogEntry(
                level = priorityToLogLevel(item.priority),
                tag = item.tag ?: "Unknown",
                message = item.message,
                timestamp = item.timestamp
            )
        }

        _uiState.value = _uiState.value.copy(logs = realLogs)
    }

    /**
     * 自动刷新日志（每2秒）
     */
    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive && _uiState.value.isAutoScroll) {
                delay(2000)
                if (_uiState.value.isAutoScroll) {
                    loadRealLogs()
                }
            }
        }
    }

    /**
     * 将Android日志优先级转换为LogLevel
     */
    private fun priorityToLogLevel(priority: Int): LogLevel {
        return when (priority) {
            Log.VERBOSE -> LogLevel.DEBUG
            Log.DEBUG -> LogLevel.DEBUG
            Log.INFO -> LogLevel.INFO
            Log.WARN -> LogLevel.WARN
            Log.ERROR -> LogLevel.ERROR
            Log.ASSERT -> LogLevel.ERROR
            else -> LogLevel.DEBUG
        }
    }

    /**
     * 设置日志级别筛选
     */
    fun setLogLevel(level: LogLevel) {
        _uiState.value = _uiState.value.copy(selectedLevel = level)
    }

    /**
     * 设置搜索查询
     */
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    /**
     * 切换自动滚动
     */
    fun toggleAutoScroll(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoScroll = enabled)
    }

    /**
     * 清除日志
     */
    fun clearLogs() {
        LogCollector.clear()
        loadRealLogs()
    }

    /**
     * 刷新日志
     */
    fun refreshLogs() {
        loadRealLogs()
    }

    /**
     * 获取筛选后的日志
     */
    fun getFilteredLogs(): List<LogEntry> {
        var filtered = _uiState.value.logs

        // 按级别筛选
        if (_uiState.value.selectedLevel != LogLevel.ALL) {
            filtered = filtered.filter { it.level == _uiState.value.selectedLevel }
        }

        // 按搜索查询筛选
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.message.contains(query, ignoreCase = true) ||
                it.tag.contains(query, ignoreCase = true)
            }
        }

        return filtered
    }
}
