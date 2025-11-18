package com.xiaoguang.assistant.presentation.screens.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.flow.HeartFlowCoordinator
import com.xiaoguang.assistant.domain.interaction.InteractionPhraseGenerator
import com.xiaoguang.assistant.domain.state.XiaoguangCoreState
import com.xiaoguang.assistant.domain.state.XiaoguangCoreStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 小光中心页面ViewModel
 *
 * 负责：
 * - 聚合核心状态
 * - 提供UI状态
 * - 处理用户交互
 * - 每天生成个性化互动语句
 */
@HiltViewModel
class XiaoguangCenterViewModel @Inject constructor(
    private val coreStateManager: XiaoguangCoreStateManager,
    private val heartFlowCoordinator: HeartFlowCoordinator,
    private val phraseGenerator: InteractionPhraseGenerator
) : ViewModel() {

    /**
     * 核心状态流
     */
    val coreState: StateFlow<XiaoguangCoreState> = coreStateManager.coreState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = XiaoguangCoreState()
        )

    /**
     * 详细思考内容状态
     */
    private val _showThoughtDetail = MutableStateFlow(false)
    val showThoughtDetail: StateFlow<Boolean> = _showThoughtDetail.asStateFlow()

    /**
     * 互动语句列表（从 LLM 生成或存储中加载）
     */
    private val _interactionPhrases = MutableStateFlow<List<String>>(emptyList())

    init {
        // 初始化时加载或生成互动语句
        loadOrGeneratePhrases()
    }

    /**
     * 加载或生成互动语句
     */
    private fun loadOrGeneratePhrases() {
        viewModelScope.launch {
            try {
                // 先尝试从存储加载
                val storedPhrases = phraseGenerator.getCurrentPhrases()
                _interactionPhrases.value = storedPhrases

                // 检查是否需要重新生成（每天一次）
                if (phraseGenerator.shouldRegenerate()) {
                    Timber.i("[XiaoguangCenter] 开始生成今日互动语句...")

                    val result = phraseGenerator.generateDailyPhrases()

                    if (result.isSuccess) {
                        val newPhrases = result.getOrNull() ?: emptyList()
                        _interactionPhrases.value = newPhrases
                        Timber.i("[XiaoguangCenter] 成功生成 ${newPhrases.size} 条新的互动语句")
                    } else {
                        Timber.w("[XiaoguangCenter] 生成失败，使用已存储的语句")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[XiaoguangCenter] 加载互动语句失败")
            }
        }
    }

    /**
     * 触摸小光头像 - 触发可爱的互动反应（从 LLM 生成的语句中随机选择）
     */
    fun onAvatarTapped() {
        try {
            val phrases = _interactionPhrases.value

            if (phrases.isEmpty()) {
                Timber.w("[XiaoguangCenter] 互动语句为空，使用默认语句")
                heartFlowCoordinator.triggerManualSpeak("诶？主人叫我吗？")
                return
            }

            // 随机选择一句LLM生成的可爱的话
            val phrase = phrases.random()

            // 通过心流系统触发发言
            heartFlowCoordinator.triggerManualSpeak(phrase)

            Timber.i("[XiaoguangCenter] 头像被点击，触发互动: $phrase")
        } catch (e: Exception) {
            Timber.e(e, "[XiaoguangCenter] 触发互动反应失败")
        }
    }

    /**
     * 查看当前想法详情 - 展开详细的思考内容
     */
    fun onThoughtClicked() {
        try {
            _showThoughtDetail.value = true
            Timber.i("[XiaoguangCenter] 展开想法详情")
        } catch (e: Exception) {
            Timber.e(e, "[XiaoguangCenter] 展开想法详情失败")
        }
    }

    /**
     * 关闭想法详情对话框
     */
    fun dismissThoughtDetail() {
        _showThoughtDetail.value = false
    }

    /**
     * 开始对话 - 准备开始对话
     */
    fun onStartConversation() {
        try {
            Timber.i("[XiaoguangCenter] 准备开始对话")
            // 导航逻辑由 Screen 层处理，这里可以做一些准备工作
            // 例如：确保心流系统正在运行
            if (!heartFlowCoordinator.isRunning.value) {
                heartFlowCoordinator.start()
            }
        } catch (e: Exception) {
            Timber.e(e, "[XiaoguangCenter] 准备对话失败")
        }
    }
}
