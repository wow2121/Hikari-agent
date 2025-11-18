package com.xiaoguang.assistant.presentation.screens.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.state.XiaoguangCoreState
import com.xiaoguang.assistant.domain.state.XiaoguangCoreStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 心流状态页面 ViewModel
 *
 * 功能：
 * - 订阅 XiaoguangCoreStateManager 的状态流
 * - 实时更新心流状态数据
 * - 提供给 UI 层展示
 */
@HiltViewModel
class FlowStatusViewModel @Inject constructor(
    private val coreStateManager: XiaoguangCoreStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<XiaoguangCoreState?>(null)
    val uiState: StateFlow<XiaoguangCoreState?> = _uiState.asStateFlow()

    init {
        // 订阅核心状态流
        viewModelScope.launch {
            coreStateManager.coreState.collect { coreState ->
                _uiState.value = coreState
            }
        }
    }
}
