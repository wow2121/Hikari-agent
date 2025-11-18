package com.xiaoguang.assistant.presentation.knowledge.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.relationship.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Phase 6: 关系图谱可视化ViewModel (v2.2 - 纯Neo4j架构)
 * 管理关系图谱的展示、交互和数据
 *
 * v2.2架构变更：
 * - 移除Neo4jSyncService（不再需要Room-Neo4j同步）
 * - 数据直接从Neo4j读取，无需同步操作
 */
@HiltViewModel
class RelationshipGraphViewModel @Inject constructor(
    private val relationshipAggregator: RelationshipAggregator,
    private val relationshipInferenceEngine: RelationshipInferenceEngine,
    private val thirdPartyEventDetector: ThirdPartyEventDetector
) : ViewModel() {

    // UI状态
    private val _uiState = MutableStateFlow<RelationshipGraphUiState>(RelationshipGraphUiState.Loading)
    val uiState: StateFlow<RelationshipGraphUiState> = _uiState.asStateFlow()

    // 当前选中的人物
    private val _selectedPerson = MutableStateFlow<String?>(null)
    val selectedPerson: StateFlow<String?> = _selectedPerson.asStateFlow()

    // 图谱深度
    private val _graphDepth = MutableStateFlow(2)
    val graphDepth: StateFlow<Int> = _graphDepth.asStateFlow()

    // 推理结果
    private val _inferredRelations = MutableStateFlow<List<InferredRelation>>(emptyList())
    val inferredRelations: StateFlow<List<InferredRelation>> = _inferredRelations.asStateFlow()

    // 事件历史
    private val _eventHistory = MutableStateFlow<List<EventRecord>>(emptyList())
    val eventHistory: StateFlow<List<EventRecord>> = _eventHistory.asStateFlow()

    init {
        Timber.d("[RelationshipGraphViewModel] 初始化 (v2.2 纯Neo4j架构)")
    }

    /**
     * 加载关系图谱
     */
    fun loadRelationshipGraph(personName: String, depth: Int = 2) {
        viewModelScope.launch {
            try {
                _uiState.value = RelationshipGraphUiState.Loading
                _selectedPerson.value = personName
                _graphDepth.value = depth

                Timber.d("[RelationshipGraphViewModel] 加载关系图谱: $personName (深度: $depth)")

                // 1. 获取完整关系视图
                val completeView = relationshipAggregator.getCompleteRelationships(personName)

                // 2. 获取关系图谱
                val graph = relationshipAggregator.getRelationshipGraph(personName, depth)

                // 3. 加载事件历史
                loadEventHistory(personName)

                _uiState.value = RelationshipGraphUiState.Success(
                    completeView = completeView,
                    graph = graph
                )

            } catch (e: Exception) {
                Timber.e(e, "[RelationshipGraphViewModel] 加载关系图谱失败")
                _uiState.value = RelationshipGraphUiState.Error(e.message ?: "加载失败")
            }
        }
    }

    /**
     * 搜索人物
     */
    fun searchPeople(query: String) {
        viewModelScope.launch {
            try {
                _uiState.value = RelationshipGraphUiState.Loading

                val results = relationshipAggregator.searchPeople(query)

                _uiState.value = RelationshipGraphUiState.SearchResults(results)

            } catch (e: Exception) {
                Timber.e(e, "[RelationshipGraphViewModel] 搜索失败")
                _uiState.value = RelationshipGraphUiState.Error(e.message ?: "搜索失败")
            }
        }
    }

    /**
     * 查找两人之间的关系路径
     */
    fun findRelationshipPath(personA: String, personB: String, maxDepth: Int = 3) {
        viewModelScope.launch {
            try {
                _uiState.value = RelationshipGraphUiState.Loading

                val path = relationshipAggregator.getRelationshipPath(personA, personB, maxDepth)

                _uiState.value = RelationshipGraphUiState.PathFound(path)

            } catch (e: Exception) {
                Timber.e(e, "[RelationshipGraphViewModel] 查找路径失败")
                _uiState.value = RelationshipGraphUiState.Error(e.message ?: "查找失败")
            }
        }
    }

    /**
     * 执行关系推理
     */
    fun performInference(personName: String? = null) {
        viewModelScope.launch {
            try {
                Timber.d("[RelationshipGraphViewModel] 执行关系推理: $personName")

                val inferences = relationshipInferenceEngine.performInference(personName)
                _inferredRelations.value = inferences

                Timber.d("[RelationshipGraphViewModel] 推理完成，发现 ${inferences.size} 个潜在关系")

            } catch (e: Exception) {
                Timber.e(e, "[RelationshipGraphViewModel] 推理失败")
            }
        }
    }

    /**
     * 应用推理结果
     */
    fun applyInferences(threshold: Float = 0.5f) {
        viewModelScope.launch {
            try {
                val inferences = _inferredRelations.value
                val appliedCount = relationshipInferenceEngine.applyInferences(inferences, threshold)

                Timber.d("[RelationshipGraphViewModel] 应用推理结果: $appliedCount/${inferences.size}")

                // 重新加载图谱
                _selectedPerson.value?.let { person ->
                    loadRelationshipGraph(person, _graphDepth.value)
                }

                // 清空推理列表
                _inferredRelations.value = emptyList()

            } catch (e: Exception) {
                Timber.e(e, "[RelationshipGraphViewModel] 应用推理失败")
            }
        }
    }

    /**
     * 加载事件历史
     */
    fun loadEventHistory(personName: String? = null, limit: Int = 50) {
        viewModelScope.launch {
            try {
                val events = thirdPartyEventDetector.getEventHistory(personName, limit)
                _eventHistory.value = events

                Timber.d("[RelationshipGraphViewModel] 加载事件历史: ${events.size} 个事件")

            } catch (e: Exception) {
                Timber.e(e, "[RelationshipGraphViewModel] 加载事件历史失败")
            }
        }
    }

    // ==================== v2.2架构: 已移除同步相关方法 ====================
    // 纯Neo4j架构下，数据直接存储在Neo4j中，无需同步操作
    // 已移除的方法:
    // - syncToNeo4j() - 不再需要同步
    // - loadSyncStatus() - 不再需要同步状态
    // - toggleNeo4jSync() - 不再需要切换同步

    /**
     * 设置图谱深度
     */
    fun setGraphDepth(depth: Int) {
        _graphDepth.value = depth.coerceIn(1, 4)

        // 重新加载图谱
        _selectedPerson.value?.let { person ->
            loadRelationshipGraph(person, _graphDepth.value)
        }
    }

    /**
     * 清空推理结果
     */
    fun clearInferences() {
        _inferredRelations.value = emptyList()
    }
}

/**
 * UI状态
 */
sealed class RelationshipGraphUiState {
    object Loading : RelationshipGraphUiState()

    data class Success(
        val completeView: CompleteRelationshipView,
        val graph: RelationshipGraph
    ) : RelationshipGraphUiState()

    data class SearchResults(
        val results: List<PersonSearchResult>
    ) : RelationshipGraphUiState()

    data class PathFound(
        val path: RelationshipPath
    ) : RelationshipGraphUiState()

    data class Error(
        val message: String
    ) : RelationshipGraphUiState()
}
