package com.xiaoguang.assistant.presentation.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.core.network.NetworkMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 网络请求数据
 */
data class NetworkRequest(
    val id: String,
    val url: String,
    val method: String,
    val statusCode: Int,
    val duration: Long,
    val timestamp: Long,
    val requestBody: String?,
    val responseBody: String?
)

/**
 * 网络调试UI状态
 */
data class NetworkDebugUiState(
    val requests: List<NetworkRequest> = emptyList(),
    val selectedRequest: NetworkRequest? = null,
    val filterMethod: String? = null,
    val totalRequests: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val averageDuration: Long = 0
)

/**
 * 网络调试ViewModel
 *
 * 使用 NetworkMonitorService 获取真实的网络请求记录
 */
@HiltViewModel
class NetworkDebugViewModel @Inject constructor(
    private val networkMonitorService: NetworkMonitorService
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkDebugUiState())
    val uiState: StateFlow<NetworkDebugUiState> = _uiState.asStateFlow()

    init {
        // 订阅网络监控服务的请求流
        viewModelScope.launch {
            networkMonitorService.requests.collect { records ->
                updateUiState(records.map { record ->
                    NetworkRequest(
                        id = record.id,
                        url = record.url,
                        method = record.method,
                        statusCode = record.statusCode,
                        duration = record.duration,
                        timestamp = record.timestamp,
                        requestBody = record.requestBody,
                        responseBody = record.responseBody
                    )
                })
            }
        }
    }

    /**
     * 更新 UI 状态
     */
    private fun updateUiState(requests: List<NetworkRequest>) {
        val filteredRequests = if (_uiState.value.filterMethod != null) {
            requests.filter { it.method == _uiState.value.filterMethod }
        } else {
            requests
        }

        val successCount = requests.count { it.statusCode in 200..299 }
        val errorCount = requests.count { it.statusCode >= 400 || it.statusCode == 0 }
        val avgDuration = if (requests.isNotEmpty()) {
            requests.sumOf { it.duration } / requests.size
        } else 0L

        _uiState.value = _uiState.value.copy(
            requests = filteredRequests,
            totalRequests = requests.size,
            successCount = successCount,
            errorCount = errorCount,
            averageDuration = avgDuration
        )
    }

    /**
     * 选择请求查看详情
     */
    fun selectRequest(request: NetworkRequest?) {
        _uiState.value = _uiState.value.copy(selectedRequest = request)
    }

    /**
     * 按方法筛选
     */
    fun filterByMethod(method: String?) {
        _uiState.value = _uiState.value.copy(filterMethod = method)

        // 重新应用筛选
        val allRecords = networkMonitorService.getAllRecords()
        updateUiState(allRecords.map { record ->
            NetworkRequest(
                id = record.id,
                url = record.url,
                method = record.method,
                statusCode = record.statusCode,
                duration = record.duration,
                timestamp = record.timestamp,
                requestBody = record.requestBody,
                responseBody = record.responseBody
            )
        })
    }

    /**
     * 清除历史
     */
    fun clearHistory() {
        networkMonitorService.clearRecords()
        _uiState.value = NetworkDebugUiState()
    }

    /**
     * 重新加载
     */
    fun reload() {
        val allRecords = networkMonitorService.getAllRecords()
        updateUiState(allRecords.map { record ->
            NetworkRequest(
                id = record.id,
                url = record.url,
                method = record.method,
                statusCode = record.statusCode,
                duration = record.duration,
                timestamp = record.timestamp,
                requestBody = record.requestBody,
                responseBody = record.responseBody
            )
        })
    }
}
