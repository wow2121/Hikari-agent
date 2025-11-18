package com.xiaoguang.assistant.presentation.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.KnowledgeSystemInitializer
import com.xiaoguang.assistant.domain.knowledge.WorldBook
import com.xiaoguang.assistant.domain.knowledge.adapter.LorebookAdapter
import com.xiaoguang.assistant.domain.knowledge.models.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * 知识管理ViewModel
 * 为UI提供知识系统的管理接口
 */
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val worldBook: WorldBook,
    private val characterBook: CharacterBook,
    private val lorebookAdapter: LorebookAdapter,
    private val initializer: KnowledgeSystemInitializer,
    private val chromaVectorStore: com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore,
    private val graphService: com.xiaoguang.assistant.domain.knowledge.graph.RelationshipGraphService
) : ViewModel() {

    // ==================== State ====================

    private val _worldEntries = MutableStateFlow<List<WorldEntry>>(emptyList())
    val worldEntries: StateFlow<List<WorldEntry>> = _worldEntries.asStateFlow()

    private val _characterProfiles = MutableStateFlow<List<CharacterProfile>>(emptyList())
    val characterProfiles: StateFlow<List<CharacterProfile>> = _characterProfiles.asStateFlow()

    private val _systemStats = MutableStateFlow<com.xiaoguang.assistant.domain.knowledge.SystemStats?>(null)
    val systemStats: StateFlow<com.xiaoguang.assistant.domain.knowledge.SystemStats?> = _systemStats.asStateFlow()

    private val _uiState = MutableStateFlow<KnowledgeUiState>(KnowledgeUiState.Idle)
    val uiState: StateFlow<KnowledgeUiState> = _uiState.asStateFlow()

    init {
        // 初始化时加载数据
        loadData()
    }

    // ==================== World Book Operations ====================

    /**
     * 加载所有数据
     */
    fun loadData() {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                // 加载World Book条目
                val entries = worldBook.getAllEntries()
                _worldEntries.value = entries

                // 加载Character profiles
                val profiles = characterBook.getAllProfiles()
                _characterProfiles.value = profiles

                // 加载系统统计
                val stats = initializer.getSystemStats()
                _systemStats.value = stats

                _uiState.value = KnowledgeUiState.Success("加载完成")
                Timber.d("[KnowledgeVM] 加载完成: ${entries.size}个条目, ${profiles.size}个角色")

            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 加载失败")
                _uiState.value = KnowledgeUiState.Error("加载失败: ${e.message}")
            }
        }
    }

    /**
     * 添加World Book条目
     */
    fun addWorldEntry(entry: WorldEntry) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = worldBook.addEntry(entry)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("添加成功")
                    loadData()  // 重新加载
                } else {
                    _uiState.value = KnowledgeUiState.Error("添加失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 添加条目失败")
                _uiState.value = KnowledgeUiState.Error("添加失败: ${e.message}")
            }
        }
    }

    /**
     * 更新World Book条目
     */
    fun updateWorldEntry(entry: WorldEntry) {
        viewModelScope.launch {
            try {
                val result = worldBook.updateEntry(entry)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("更新成功")
                    loadData()
                } else {
                    _uiState.value = KnowledgeUiState.Error("更新失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 更新条目失败")
                _uiState.value = KnowledgeUiState.Error("更新失败: ${e.message}")
            }
        }
    }

    /**
     * 删除World Book条目
     */
    fun deleteWorldEntry(entryId: String) {
        viewModelScope.launch {
            try {
                val result = worldBook.deleteEntry(entryId)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("删除成功")
                    loadData()
                } else {
                    _uiState.value = KnowledgeUiState.Error("删除失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 删除条目失败")
                _uiState.value = KnowledgeUiState.Error("删除失败: ${e.message}")
            }
        }
    }

    /**
     * 搜索World Book条目
     */
    fun searchWorldEntries(query: String) {
        viewModelScope.launch {
            try {
                val results = worldBook.searchEntries(query)
                _worldEntries.value = results
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 搜索失败")
                _uiState.value = KnowledgeUiState.Error("搜索失败: ${e.message}")
            }
        }
    }

    // ==================== Character Book Operations ====================

    /**
     * 添加角色档案
     */
    fun addCharacterProfile(profile: CharacterProfile) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = characterBook.saveProfile(profile)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("添加成功")
                    loadData()
                } else {
                    _uiState.value = KnowledgeUiState.Error("添加失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 添加角色失败")
                _uiState.value = KnowledgeUiState.Error("添加失败: ${e.message}")
            }
        }
    }

    /**
     * 删除角色档案
     */
    fun deleteCharacterProfile(characterId: String) {
        viewModelScope.launch {
            try {
                val result = characterBook.deleteProfile(characterId)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("删除成功")
                    loadData()
                } else {
                    _uiState.value = KnowledgeUiState.Error("删除失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 删除角色失败")
                _uiState.value = KnowledgeUiState.Error("删除失败: ${e.message}")
            }
        }
    }

    /**
     * 获取角色的记忆
     */
    fun getCharacterMemories(characterId: String): Flow<List<CharacterMemory>> {
        return flow {
            try {
                val memories = characterBook.getMemories(characterId)
                emit(memories)
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 获取记忆失败")
                emit(emptyList())
            }
        }
    }

    /**
     * 添加角色记忆
     */
    fun addCharacterMemory(memory: CharacterMemory) {
        viewModelScope.launch {
            try {
                val result = characterBook.addMemory(memory)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("添加记忆成功")
                } else {
                    _uiState.value = KnowledgeUiState.Error("添加记忆失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 添加记忆失败")
                _uiState.value = KnowledgeUiState.Error("添加记忆失败: ${e.message}")
            }
        }
    }

    // ==================== Import/Export ====================

    /**
     * 导出World Book到文件
     */
    fun exportWorldBook(file: File) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = lorebookAdapter.exportWorldBookToFile(file)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("导出成功: ${file.name}")
                } else {
                    _uiState.value = KnowledgeUiState.Error("导出失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 导出失败")
                _uiState.value = KnowledgeUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    /**
     * 从文件导入World Book
     */
    fun importWorldBook(file: File) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = lorebookAdapter.importWorldBookFromFile(file)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("导入成功: ${result.getOrNull()}个条目")
                    loadData()
                } else {
                    _uiState.value = KnowledgeUiState.Error("导入失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 导入失败")
                _uiState.value = KnowledgeUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    /**
     * 导出Character Card
     */
    fun exportCharacterCard(characterId: String, file: File) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = lorebookAdapter.exportCharacterToFile(characterId, file)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("导出成功: ${file.name}")
                } else {
                    _uiState.value = KnowledgeUiState.Error("导出失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 导出角色卡失败")
                _uiState.value = KnowledgeUiState.Error("导出失败: ${e.message}")
            }
        }
    }

    /**
     * 导入Character Card
     */
    fun importCharacterCard(file: File) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = lorebookAdapter.importCharacterFromFile(file)
                if (result.isSuccess) {
                    _uiState.value = KnowledgeUiState.Success("导入成功")
                    loadData()
                } else {
                    _uiState.value = KnowledgeUiState.Error("导入失败")
                }
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 导入角色卡失败")
                _uiState.value = KnowledgeUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    // ==================== Migration ====================

    /**
     * 执行数据迁移（已废弃 - 旧系统已删除）
     */
    fun performMigration() {
        viewModelScope.launch {
            Timber.w("[KnowledgeVM] 迁移功能已废弃，旧系统已完全删除")
            _uiState.value = KnowledgeUiState.Success("系统已是最新版本，无需迁移")
            loadData()
        }
    }

    /**
     * 触发数据迁移（别名，已废弃）
     */
    fun triggerMigration() = performMigration()

    /**
     * 检查迁移状态（已废弃 - 旧系统已删除）
     */
    fun checkMigrationStatus() {
        Timber.w("[KnowledgeVM] 迁移检查功能已废弃，旧系统已完全删除")
        // 不再需要迁移，什么都不做
    }

    // ==================== Utility ====================

    /**
     * 重置UI状态
     */
    fun resetUiState() {
        _uiState.value = KnowledgeUiState.Idle
    }

    /**
     * 初始化默认数据
     */
    fun initializeDefaults() {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                worldBook.initializeDefaultEntries()
                characterBook.initializeXiaoguangProfile()

                _uiState.value = KnowledgeUiState.Success("初始化成功")
                loadData()
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 初始化失败")
                _uiState.value = KnowledgeUiState.Error("初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 重新初始化系统
     */
    fun reinitializeSystem() {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                initializer.reinitialize()

                _uiState.value = KnowledgeUiState.Success("重新初始化成功")
                loadData()
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 重新初始化失败")
                _uiState.value = KnowledgeUiState.Error("重新初始化失败: ${e.message}")
            }
        }
    }

    /**
     * 导出所有数据为Lorebook格式
     */
    suspend fun exportAsLorebook(): String? {
        return try {
            val json = lorebookAdapter.exportWorldBookToLorebook()
            Timber.d("[KnowledgeVM] Lorebook导出成功，长度: ${json.length}")
            json
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeVM] 导出Lorebook失败")
            null
        }
    }

    /**
     * 从JSON导入Lorebook
     */
    fun importFromLorebook(json: String) {
        viewModelScope.launch {
            try {
                _uiState.value = KnowledgeUiState.Loading

                val result = lorebookAdapter.importWorldBookFromLorebook(json)
                if (result.isSuccess) {
                    val count = result.getOrDefault(0)
                    Timber.i("[KnowledgeVM] 导入了 $count 个条目")
                    _uiState.value = KnowledgeUiState.Success("成功导入 $count 个条目")
                } else {
                    throw result.exceptionOrNull() ?: Exception("导入失败")
                }
                loadData()
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 导入Lorebook失败")
                _uiState.value = KnowledgeUiState.Error("导入失败: ${e.message}")
            }
        }
    }

    /**
     * 导出World Book条目为JSON
     */
    fun exportWorldBookAsJson(): String? {
        return try {
            val entries = _worldEntries.value
            val json = com.google.gson.Gson().toJson(entries)
            Timber.d("[KnowledgeVM] World Book导出成功: ${entries.size} 个条目")
            json
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeVM] 导出World Book失败")
            null
        }
    }

    /**
     * 导出Character Book为JSON
     */
    fun exportCharacterBookAsJson(): String? {
        return try {
            val profiles = _characterProfiles.value
            val json = com.google.gson.Gson().toJson(profiles)
            Timber.d("[KnowledgeVM] Character Book导出成功: ${profiles.size} 个角色")
            json
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeVM] 导出Character Book失败")
            null
        }
    }

    /**
     * 导出单个角色为JSON
     */
    fun exportCharacterProfileAsJson(profile: CharacterProfile): String? {
        return try {
            val json = com.google.gson.Gson().toJson(profile)
            Timber.d("[KnowledgeVM] 角色导出成功: ${profile.basicInfo.name}")
            json
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeVM] 导出角色失败: ${profile.basicInfo.name}")
            null
        }
    }

    /**
     * 更新角色档案
     */
    fun updateCharacterProfile(profile: CharacterProfile) {
        viewModelScope.launch {
            try {
                characterBook.saveProfile(profile)
                Timber.i("[KnowledgeVM] 角色档案已更新: ${profile.basicInfo.name}")
                loadData()
            } catch (e: Exception) {
                Timber.e(e, "[KnowledgeVM] 更新角色档案失败")
            }
        }
    }

    /**
     * 检查Chroma向量数据库是否可用
     */
    fun isChromaAvailable(): Boolean {
        return try {
            // 注意：这是一个同步方法调用异步检查，实际应该在协程中
            // 这里返回false是安全的默认值
            false
        } catch (e: Exception) {
            Timber.w(e, "[KnowledgeViewModel] Chroma可用性检查失败")
            false
        }
    }

    /**
     * 异步检查Chroma是否可用
     */
    suspend fun checkChromaAvailable(): Boolean {
        return try {
            chromaVectorStore.isAvailable()
        } catch (e: Exception) {
            Timber.w(e, "[KnowledgeViewModel] Chroma可用性检查失败")
            false
        }
    }

    /**
     * 检查Neo4j图数据库是否可用
     */
    fun isNeo4jAvailable(): Boolean {
        return try {
            // 注意：这是一个同步方法调用异步检查，实际应该在协程中
            // 这里返回false是安全的默认值
            false
        } catch (e: Exception) {
            Timber.w(e, "[KnowledgeViewModel] Neo4j可用性检查失败")
            false
        }
    }

    /**
     * 异步检查Neo4j是否可用
     */
    suspend fun checkNeo4jAvailable(): Boolean {
        return try {
            graphService.isAvailable()
        } catch (e: Exception) {
            Timber.w(e, "[KnowledgeViewModel] Neo4j可用性检查失败")
            false
        }
    }

    /**
     * 获取所有角色的关系总数
     */
    suspend fun getTotalRelationshipCount(): Int {
        return try {
            val profiles = characterBook.getAllProfiles()
            profiles.sumOf { profile ->
                characterBook.getRelationshipsFrom(profile.basicInfo.characterId).size
            }
        } catch (e: Exception) {
            Timber.e(e, "[KnowledgeViewModel] 获取关系总数失败")
            0
        }
    }
}

/**
 * UI状态
 */
sealed class KnowledgeUiState {
    object Idle : KnowledgeUiState()
    object Loading : KnowledgeUiState()
    data class Success(val message: String) : KnowledgeUiState()
    data class Error(val message: String) : KnowledgeUiState()
    data class MigrationNeeded(val message: String) : KnowledgeUiState()
}
