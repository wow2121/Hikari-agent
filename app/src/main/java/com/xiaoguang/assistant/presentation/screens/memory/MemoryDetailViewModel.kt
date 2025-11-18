package com.xiaoguang.assistant.presentation.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.MemoryQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 记忆详情UI状态
 */
data class MemoryDetailUiState(
    val memoryId: String = "",
    val content: String = "",
    val category: String = "日常记忆",
    val emotion: String? = null,
    val importance: Float = 0.5f,
    val confidence: Float = 0.8f,
    val timestamp: Long = 0L,
    val relatedPeople: List<String> = emptyList(),
    val relatedTopics: List<String> = emptyList(),
    val source: String = "对话系统",
    val accessCount: Int = 0,
    val lastAccessTime: Long? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * 记忆详情ViewModel
 */
@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    private val unifiedMemorySystem: UnifiedMemorySystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryDetailUiState())
    val uiState: StateFlow<MemoryDetailUiState> = _uiState.asStateFlow()

    /**
     * 加载记忆详情
     */
    fun loadMemoryDetail(memoryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // 从 UnifiedMemorySystem 查询记忆
                val results = unifiedMemorySystem.queryMemories(
                    MemoryQuery(
                        limit = 100  // 获取所有记忆，然后过滤
                    )
                )

                // 在结果中查找匹配的记忆ID
                val memory = results.find { it.memory.id == memoryId }?.memory

                if (memory != null) {
                    _uiState.value = MemoryDetailUiState(
                        memoryId = memory.id,
                        content = memory.content,
                        category = memory.category.name,
                        emotion = memory.emotionTag,
                        importance = memory.importance,
                        confidence = memory.confidence,
                        timestamp = memory.timestamp,
                        relatedPeople = memory.relatedCharacters,
                        relatedTopics = memory.tags,
                        source = memory.source ?: "未知",
                        accessCount = memory.accessCount,
                        lastAccessTime = memory.lastAccessedAt,
                        isLoading = false
                    )

                    Timber.d("加载记忆详情成功: $memoryId")
                } else {
                    // 如果没找到，显示错误
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未找到该记忆 (ID: $memoryId)"
                    )
                    Timber.w("未找到记忆: $memoryId")
                }

            } catch (e: Exception) {
                Timber.e(e, "加载记忆详情失败: $memoryId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
}
