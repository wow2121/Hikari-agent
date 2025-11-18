package com.xiaoguang.assistant.presentation.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.MemoryQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 个人资料页面 ViewModel
 *
 * 功能：
 * - 聚合各种统计数据
 * - 显示真实的运行天数、对话次数、记忆数量
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val unifiedMemorySystem: UnifiedMemorySystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences("xiaoguang_profile", Context.MODE_PRIVATE)

    init {
        loadStatistics()
    }

    /**
     * 加载统计数据
     */
    private fun loadStatistics() {
        viewModelScope.launch {
            try {
                // 1. 计算运行天数
                val runningDays = calculateRunningDays()

                // 2. 获取对话次数（从 Realm 或其他存储）
                val conversationCount = getConversationCount()

                // 3. 获取记忆数量
                val memoryCount = getMemoryCount()

                _uiState.value = ProfileUiState(
                    runningDays = runningDays,
                    conversationCount = conversationCount,
                    memoryCount = memoryCount,
                    isLoading = false
                )

                Timber.i("[ProfileViewModel] 统计数据: $runningDays 天, $conversationCount 次对话, $memoryCount 条记忆")
            } catch (e: Exception) {
                Timber.e(e, "[ProfileViewModel] 加载统计数据失败")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * 计算运行天数
     */
    private fun calculateRunningDays(): Int {
        val firstLaunchTime = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)

        if (firstLaunchTime == 0L) {
            // 首次启动，记录时间
            val currentTime = System.currentTimeMillis()
            prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, currentTime).apply()
            return 1
        }

        // 计算天数
        val daysSinceFirstLaunch = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - firstLaunchTime
        )

        return (daysSinceFirstLaunch + 1).toInt()  // 至少显示1天
    }

    /**
     * 获取对话次数
     *
     * TODO: 目前使用 SharedPreferences 估算
     * 未来可以从 ConversationRepository 或 Realm 获取真实数据
     */
    private fun getConversationCount(): Int {
        // 简单估算：每次对话后递增计数
        return prefs.getInt(KEY_CONVERSATION_COUNT, 0)
    }

    /**
     * 获取记忆数量
     */
    private suspend fun getMemoryCount(): Int {
        return try {
            val memories = unifiedMemorySystem.queryMemories(
                MemoryQuery(limit = 10000)  // 查询所有记忆
            )
            memories.size
        } catch (e: Exception) {
            Timber.w(e, "[ProfileViewModel] 获取记忆数量失败")
            0
        }
    }

    /**
     * 递增对话次数（供外部调用）
     */
    fun incrementConversationCount() {
        viewModelScope.launch {
            val currentCount = prefs.getInt(KEY_CONVERSATION_COUNT, 0)
            prefs.edit().putInt(KEY_CONVERSATION_COUNT, currentCount + 1).apply()
            loadStatistics()  // 刷新统计
        }
    }

    companion object {
        private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
        private const val KEY_CONVERSATION_COUNT = "conversation_count"
    }
}

/**
 * Profile UI 状态
 */
data class ProfileUiState(
    val runningDays: Int = 1,
    val conversationCount: Int = 0,
    val memoryCount: Int = 0,
    val isLoading: Boolean = true
)
