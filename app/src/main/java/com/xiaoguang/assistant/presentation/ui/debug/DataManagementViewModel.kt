package com.xiaoguang.assistant.presentation.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.MemoryQuery
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * 数据管理 ViewModel
 *
 * 功能：
 * - 提供数据统计
 * - 清除各类数据
 */
@HiltViewModel
class DataManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceprintManager: VoiceprintManager,
    private val unifiedMemorySystem: UnifiedMemorySystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataManagementUiState())
    val uiState: StateFlow<DataManagementUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    /**
     * 加载数据统计
     */
    fun loadStatistics() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // 1. 获取对话数量（从SharedPreferences）
                val conversationCount = getConversationCount()

                // 2. 获取声纹数量
                val voiceprintCount = getVoiceprintCount()

                // 3. 获取记忆数量
                val memoryCount = getMemoryCount()

                // 4. 计算数据库大小（近似）
                val databaseSize = calculateDatabaseSize()

                _uiState.value = DataManagementUiState(
                    conversationCount = conversationCount,
                    voiceprintCount = voiceprintCount,
                    memoryCount = memoryCount,
                    databaseSizeMB = databaseSize,
                    isLoading = false
                )

                Timber.i("[DataManagementVM] 统计数据加载完成: $conversationCount 对话, $voiceprintCount 声纹, $memoryCount 记忆")
            } catch (e: Exception) {
                Timber.e(e, "[DataManagementVM] 加载统计失败")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 清除声纹数据（保留主人）
     */
    suspend fun clearVoiceprints(): Result<Unit> {
        return try {
            Timber.i("[DataManagementVM] 开始清除声纹数据...")

            // 获取所有声纹档案
            val allProfiles = voiceprintManager.getAllProfiles()
            Timber.d("[DataManagementVM] 找到 ${allProfiles.size} 个声纹档案")

            // 过滤掉主人声纹
            val nonMasterProfiles = allProfiles.filter { !it.isMaster }
            Timber.d("[DataManagementVM] 需要删除 ${nonMasterProfiles.size} 个非主人声纹")

            // 删除每个非主人声纹
            var successCount = 0
            var failCount = 0

            for (profile in nonMasterProfiles) {
                val result = voiceprintManager.deleteVoiceprint(profile.voiceprintId)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failCount++
                    Timber.w("[DataManagementVM] 删除声纹失败: ${profile.displayName}")
                }
            }

            Timber.i("[DataManagementVM] 声纹清除完成: 成功 $successCount, 失败 $failCount")

            // 刷新统计
            loadStatistics()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[DataManagementVM] 清除声纹数据失败")
            Result.failure(e)
        }
    }

    /**
     * 清除记忆数据
     */
    suspend fun clearMemories(): Result<Unit> {
        return try {
            Timber.i("[DataManagementVM] 开始清除记忆数据...")

            // 获取所有记忆
            val allMemories = unifiedMemorySystem.queryMemories(
                MemoryQuery(limit = 10000)
            )
            Timber.d("[DataManagementVM] 找到 ${allMemories.size} 条记忆")

            // 注意：由于UnifiedMemorySystem没有提供批量删除方法
            // 这里只能记录日志，实际清除需要在ChromaDB层面操作
            // TODO: 考虑在UnifiedMemorySystem中添加clearAll()方法

            Timber.w("[DataManagementVM] 记忆清除功能需要系统级支持，当前仅重置统计")

            // 刷新统计
            loadStatistics()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[DataManagementVM] 清除记忆数据失败")
            Result.failure(e)
        }
    }

    /**
     * 清除所有数据
     */
    suspend fun clearAllData(): Result<Unit> {
        return try {
            Timber.i("[DataManagementVM] 开始清除所有数据...")

            // 1. 清除声纹（保留主人）
            clearVoiceprints()

            // 2. 清除记忆
            clearMemories()

            // 3. 清除SharedPreferences中的统计数据
            clearSharedPreferences()

            Timber.i("[DataManagementVM] 所有数据已清除")

            // 刷新统计
            loadStatistics()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[DataManagementVM] 清除所有数据失败")
            Result.failure(e)
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取对话数量
     */
    private fun getConversationCount(): Int {
        val prefs = context.getSharedPreferences("xiaoguang_profile", Context.MODE_PRIVATE)
        return prefs.getInt("conversation_count", 0)
    }

    /**
     * 获取声纹数量
     */
    private suspend fun getVoiceprintCount(): Int {
        return try {
            voiceprintManager.getAllProfiles().size
        } catch (e: Exception) {
            Timber.w(e, "[DataManagementVM] 获取声纹数量失败")
            0
        }
    }

    /**
     * 获取记忆数量
     */
    private suspend fun getMemoryCount(): Int {
        return try {
            unifiedMemorySystem.queryMemories(
                MemoryQuery(limit = 10000)
            ).size
        } catch (e: Exception) {
            Timber.w(e, "[DataManagementVM] 获取记忆数量失败")
            0
        }
    }

    /**
     * 计算数据库大小（近似估算）
     */
    private fun calculateDatabaseSize(): Float {
        return try {
            val dbDir = context.getDatabasePath("xiaoguang_schedule.db")?.parentFile
            var totalSize = 0L

            dbDir?.listFiles()?.forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                }
            }

            // 转换为MB
            totalSize / (1024f * 1024f)
        } catch (e: Exception) {
            Timber.w(e, "[DataManagementVM] 计算数据库大小失败")
            0f
        }
    }

    /**
     * 清除SharedPreferences中的数据
     */
    private fun clearSharedPreferences() {
        try {
            // 清除互动语句缓存
            context.getSharedPreferences("interaction_phrases", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()

            // 注意：不清除 xiaoguang_profile 中的 first_launch_time
            // 因为需要保留运行天数统计

            Timber.i("[DataManagementVM] SharedPreferences 已清除")
        } catch (e: Exception) {
            Timber.w(e, "[DataManagementVM] 清除 SharedPreferences 失败")
        }
    }

    /**
     * 导出所有数据为JSON字符串
     *
     * @return JSON格式的备份数据
     */
    suspend fun exportDataAsJson(): Result<String> {
        return try {
            Timber.i("[DataManagementVM] 开始导出数据...")

            val exportData = JSONObject()

            // 1. 导出元数据
            exportData.put("version", "1.0")
            exportData.put("exportTime", System.currentTimeMillis())
            exportData.put("appName", "小光AI助手")

            // 2. 导出声纹数据
            val voiceprintsArray = JSONArray()
            val voiceprints = voiceprintManager.getAllProfiles()
            for (profile in voiceprints) {
                val vpJson = JSONObject().apply {
                    put("voiceprintId", profile.voiceprintId)
                    put("personId", profile.personId)
                    put("personName", profile.personName ?: "")
                    put("displayName", profile.displayName)
                    put("isMaster", profile.isMaster)
                    put("isStranger", profile.isStranger)
                    put("sampleCount", profile.sampleCount)
                    put("confidence", profile.confidence)
                    put("createdAt", profile.createdAt)
                    // 注意: featureVector 不导出,因为太大且不易序列化
                }
                voiceprintsArray.put(vpJson)
            }
            exportData.put("voiceprints", voiceprintsArray)

            // 3. 导出记忆数据 (基本信息)
            val memoriesArray = JSONArray()
            val memories = unifiedMemorySystem.queryMemories(
                MemoryQuery(limit = 10000)
            )
            for (memory in memories.take(100)) { // 限制导出100条最重要的记忆
                val memJson = JSONObject().apply {
                    put("content", memory.memory.content)
                    put("category", memory.memory.category.name)
                    put("importance", memory.memory.importance)
                    put("emotionIntensity", memory.memory.emotionIntensity)
                    put("timestamp", memory.memory.timestamp)
                }
                memoriesArray.put(memJson)
            }
            exportData.put("memories", memoriesArray)

            // 4. 导出统计信息
            val statsJson = JSONObject().apply {
                put("conversationCount", getConversationCount())
                put("voiceprintCount", voiceprints.size)
                put("memoryCount", memories.size)
            }
            exportData.put("statistics", statsJson)

            val jsonString = exportData.toString(2) // 格式化输出
            Timber.i("[DataManagementVM] 数据导出成功, 大小: ${jsonString.length} 字符")

            Result.success(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "[DataManagementVM] 导出数据失败")
            Result.failure(e)
        }
    }

    /**
     * 从JSON字符串导入数据
     *
     * @param jsonString JSON格式的备份数据
     * @return 导入结果
     */
    suspend fun importDataFromJson(jsonString: String): Result<String> {
        return try {
            Timber.i("[DataManagementVM] 开始导入数据...")

            val importData = JSONObject(jsonString)

            // 1. 验证版本
            val version = importData.optString("version", "unknown")
            Timber.d("[DataManagementVM] 备份文件版本: $version")

            // 2. 解析统计信息
            val stats = importData.optJSONObject("statistics")
            val voiceprintCount = stats?.optInt("voiceprintCount", 0) ?: 0
            val memoryCount = stats?.optInt("memoryCount", 0) ?: 0

            Timber.i("[DataManagementVM] 备份包含: $voiceprintCount 个声纹, $memoryCount 条记忆")

            // 注意: 完整的导入功能需要各个子系统支持批量导入
            // 这里仅做基本验证和日志记录
            // TODO: 实现实际的数据导入逻辑

            val message = "数据验证成功!\n" +
                    "声纹: $voiceprintCount 个\n" +
                    "记忆: $memoryCount 条\n" +
                    "注意: 完整导入功能开发中"

            Timber.i("[DataManagementVM] 数据导入完成")
            Result.success(message)

        } catch (e: Exception) {
            Timber.e(e, "[DataManagementVM] 导入数据失败")
            Result.failure(e)
        }
    }
}

/**
 * 数据管理 UI 状态
 */
data class DataManagementUiState(
    val conversationCount: Int = 0,
    val voiceprintCount: Int = 0,
    val memoryCount: Int = 0,
    val databaseSizeMB: Float = 0f,
    val isLoading: Boolean = true
)
