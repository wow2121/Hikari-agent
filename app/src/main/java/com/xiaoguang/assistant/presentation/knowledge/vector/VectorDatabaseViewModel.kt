package com.xiaoguang.assistant.presentation.knowledge.vector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 向量数据库管理ViewModel
 */
@HiltViewModel
class VectorDatabaseViewModel @Inject constructor(
    private val chromaVectorStore: ChromaVectorStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(VectorDatabaseUiState())
    val uiState: StateFlow<VectorDatabaseUiState> = _uiState.asStateFlow()

    init {
        checkStatus()
    }

    /**
     * 检查连接状态
     */
    fun checkStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val isConnected = chromaVectorStore.isAvailable()
                val collections = if (isConnected) {
                    listOf(
                        CollectionInfo("world_book", getCollectionCount("world_book"), "世界书知识库"),
                        CollectionInfo("character_memories", getCollectionCount("character_memories"), "角色记忆库"),
                        CollectionInfo("conversations", getCollectionCount("conversations"), "对话历史")
                    )
                } else {
                    emptyList()
                }

                _uiState.value = _uiState.value.copy(
                    isConnected = isConnected,
                    collections = collections,
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 状态检查完成"
                )
            } catch (e: Exception) {
                Timber.e(e, "[VectorDB] 状态检查失败")
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 初始化集合
     */
    fun initializeCollections() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = chromaVectorStore.initializeCollections()
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        logs = _uiState.value.logs + "[${currentTime()}] 集合初始化成功"
                    )
                    checkStatus()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 初始化失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[VectorDB] 初始化失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 清空所有数据
     */
    fun clearAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 清空所有集合的数据（删除后重新创建）
                val result = chromaVectorStore.initializeCollections()

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 数据清空完成，集合已重新初始化"
                    )
                    checkStatus()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 清空失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[VectorDB] 清空数据失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 错误: ${e.message}"
                )
            }
        }
    }

    private suspend fun getCollectionCount(collectionName: String): Int {
        return try {
            // 映射显示名称到实际集合名称
            val actualCollectionName = when (collectionName) {
                "world_book" -> com.xiaoguang.assistant.data.remote.api.ChromaAPI.COLLECTION_WORLD_BOOK
                "character_memories" -> com.xiaoguang.assistant.data.remote.api.ChromaAPI.COLLECTION_CHARACTER_MEMORIES
                "conversations" -> com.xiaoguang.assistant.data.remote.api.ChromaAPI.COLLECTION_CONVERSATIONS
                else -> return 0
            }

            // 调用 ChromaVectorStore 的通用计数方法
            val countResult = chromaVectorStore.getDocumentCount(actualCollectionName)
            countResult.getOrNull() ?: 0
        } catch (e: Exception) {
            Timber.w(e, "[VectorDB] 获取集合 $collectionName 数量失败")
            0
        }
    }

    private fun currentTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
            .format(java.util.Date())
    }
}

/**
 * UI状态
 */
data class VectorDatabaseUiState(
    val isConnected: Boolean = false,
    val serverUrl: String = "http://localhost:8000",
    val version: String? = null,
    val collections: List<CollectionInfo> = emptyList(),
    val isLoading: Boolean = false,
    val logs: List<String> = emptyList()
)

/**
 * 集合信息
 */
data class CollectionInfo(
    val name: String,
    val documentCount: Int,
    val description: String
)
