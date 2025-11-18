package com.xiaoguang.assistant.presentation.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 记忆条目（UI展示）
 */
data class MemoryItem(
    val id: String,
    val content: String,
    val category: MemoryCategory,
    val timestamp: Long,
    val importance: Float,
    val tags: List<String>,
    val relatedPeople: List<String>
)

/**
 * 记忆浏览UI状态
 */
data class MemoryBrowserUiState(
    val memories: List<MemoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedCategory: MemoryCategory? = null,
    val sortBy: MemorySortBy = MemorySortBy.TIME_DESC
)

/**
 * 记忆排序方式
 */
enum class MemorySortBy {
    TIME_DESC,      // 时间倒序
    TIME_ASC,       // 时间正序
    IMPORTANCE_DESC, // 重要性倒序
    IMPORTANCE_ASC  // 重要性正序
}

/**
 * 记忆浏览页面ViewModel
 */
@HiltViewModel
class MemoryBrowserViewModel @Inject constructor(
    private val memorySystem: UnifiedMemorySystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryBrowserUiState())
    val uiState: StateFlow<MemoryBrowserUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
    }

    /**
     * 加载记忆列表
     */
    fun loadMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 查询所有记忆
                val query = MemoryQuery(
                    limit = 100,
                    categories = if (_uiState.value.selectedCategory != null) {
                        listOf(_uiState.value.selectedCategory!!)
                    } else {
                        null
                    }
                )

                val rankedMemories = memorySystem.queryMemories(query)

                // 转换为UI模型
                val memoryItems = rankedMemories.map { ranked ->
                    MemoryItem(
                        id = ranked.memory.id,
                        content = ranked.memory.content,
                        category = ranked.memory.category,
                        timestamp = ranked.memory.timestamp,
                        importance = ranked.memory.importance,
                        tags = ranked.memory.tags,
                        relatedPeople = ranked.memory.relatedCharacters
                    )
                }

                // 应用排序
                val sorted = sortMemories(memoryItems, _uiState.value.sortBy)

                _uiState.update {
                    it.copy(
                        memories = sorted,
                        isLoading = false
                    )
                }

                Timber.d("已加载 ${memoryItems.size} 条记忆")
            } catch (e: Exception) {
                Timber.e(e, "加载记忆失败")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    /**
     * 搜索记忆
     */
    fun searchMemories(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            loadMemories()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val memoryQuery = MemoryQuery(
                    semantic = query,
                    limit = 50
                )

                val rankedMemories = memorySystem.queryMemories(memoryQuery)

                val memoryItems = rankedMemories.map { ranked ->
                    MemoryItem(
                        id = ranked.memory.id,
                        content = ranked.memory.content,
                        category = ranked.memory.category,
                        timestamp = ranked.memory.timestamp,
                        importance = ranked.memory.importance,
                        tags = ranked.memory.tags,
                        relatedPeople = ranked.memory.relatedCharacters
                    )
                }

                _uiState.update {
                    it.copy(
                        memories = memoryItems,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "搜索记忆失败")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "搜索失败"
                    )
                }
            }
        }
    }

    /**
     * 设置分类筛选
     */
    fun setCategory(category: MemoryCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadMemories()
    }

    /**
     * 设置排序方式
     */
    fun setSortBy(sortBy: MemorySortBy) {
        val currentMemories = _uiState.value.memories
        val sorted = sortMemories(currentMemories, sortBy)
        _uiState.update {
            it.copy(
                memories = sorted,
                sortBy = sortBy
            )
        }
    }

    /**
     * 排序记忆
     */
    private fun sortMemories(memories: List<MemoryItem>, sortBy: MemorySortBy): List<MemoryItem> {
        return when (sortBy) {
            MemorySortBy.TIME_DESC -> memories.sortedByDescending { it.timestamp }
            MemorySortBy.TIME_ASC -> memories.sortedBy { it.timestamp }
            MemorySortBy.IMPORTANCE_DESC -> memories.sortedByDescending { it.importance }
            MemorySortBy.IMPORTANCE_ASC -> memories.sortedBy { it.importance }
        }
    }
}
