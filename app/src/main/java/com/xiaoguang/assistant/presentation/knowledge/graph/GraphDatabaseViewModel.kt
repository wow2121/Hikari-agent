package com.xiaoguang.assistant.presentation.knowledge.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.knowledge.graph.RelationshipGraphService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 图数据库管理ViewModel
 */
@HiltViewModel
class GraphDatabaseViewModel @Inject constructor(
    private val graphService: RelationshipGraphService
) : ViewModel() {

    private val _uiState = MutableStateFlow(GraphDatabaseUiState())
    val uiState: StateFlow<GraphDatabaseUiState> = _uiState.asStateFlow()

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
                val isConnected = graphService.isAvailable()

                // 如果连接成功，获取统计信息
                val stats = if (isConnected) {
                    getGraphStats()
                } else {
                    GraphStats()
                }

                _uiState.value = _uiState.value.copy(
                    isConnected = isConnected,
                    nodeCount = stats.nodeCount,
                    relationshipCount = stats.relationshipCount,
                    characterNodeCount = stats.characterNodeCount,
                    eventNodeCount = stats.eventNodeCount,
                    memoryNodeCount = stats.memoryNodeCount,
                    nodeTypes = stats.nodeTypes,
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 状态检查完成"
                )
            } catch (e: Exception) {
                Timber.e(e, "[GraphDB] 状态检查失败")
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 初始化Schema
     */
    fun initializeSchema() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = graphService.initialize()
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        logs = _uiState.value.logs + "[${currentTime()}] Schema初始化成功"
                    )
                    checkStatus()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 初始化失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[GraphDB] 初始化失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 创建索引
     */
    fun createIndexes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // 调用 initialize() 会创建所有必要的索引
                val result = graphService.initialize()

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 索引创建完成"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 索引创建失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[GraphDB] 创建索引失败")
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
                // 注意：这是危险操作
                // RelationshipGraphService 目前没有提供清空方法
                // 这里只是重新初始化 schema
                val result = graphService.initialize()

                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] Schema已重新初始化（数据清空功能待实现）"
                    )
                    checkStatus()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        logs = _uiState.value.logs + "[${currentTime()}] 操作失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[GraphDB] 清空数据失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logs = _uiState.value.logs + "[${currentTime()}] 错误: ${e.message}"
                )
            }
        }
    }

    private suspend fun getGraphStats(): GraphStats {
        return try {
            // 调用真实的 graphService.getStats() 方法
            val serviceStatsResult = graphService.getStats()

            if (serviceStatsResult.isSuccess) {
                val serviceStats = serviceStatsResult.getOrThrow()

                // 将服务层的 GraphStats 映射到 ViewModel 的 GraphStats
                GraphStats(
                    nodeCount = serviceStats.totalNodes,
                    relationshipCount = serviceStats.totalRelationships,
                    // 注意：目前 RelationshipGraphService 不提供按类型的节点计数
                    // 这些字段需要后续通过 Cypher 查询来获取
                    characterNodeCount = 0,
                    eventNodeCount = 0,
                    memoryNodeCount = 0,
                    nodeTypes = mapOf("Total" to serviceStats.totalNodes)
                )
            } else {
                Timber.w("[GraphDB] 获取统计信息失败: ${serviceStatsResult.exceptionOrNull()?.message}")
                GraphStats()
            }
        } catch (e: Exception) {
            Timber.w(e, "[GraphDB] 获取统计信息失败")
            GraphStats()
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
data class GraphDatabaseUiState(
    val isConnected: Boolean = false,
    val serverUrl: String = "bolt://localhost:7687",
    val database: String = "neo4j",
    val version: String? = null,
    val nodeCount: Int = 0,
    val relationshipCount: Int = 0,
    val characterNodeCount: Int = 0,
    val eventNodeCount: Int = 0,
    val memoryNodeCount: Int = 0,
    val nodeTypes: Map<String, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val logs: List<String> = emptyList()
)

/**
 * 图统计信息
 */
data class GraphStats(
    val nodeCount: Int = 0,
    val relationshipCount: Int = 0,
    val characterNodeCount: Int = 0,
    val eventNodeCount: Int = 0,
    val memoryNodeCount: Int = 0,
    val nodeTypes: Map<String, Int> = emptyMap()
)
