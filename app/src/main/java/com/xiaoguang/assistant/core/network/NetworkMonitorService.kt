package com.xiaoguang.assistant.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络监控服务
 *
 * 负责：
 * 1. 记录所有 HTTP 请求和响应
 * 2. 提供网络请求历史查询
 * 3. 统计网络请求性能指标
 */
@Singleton
class NetworkMonitorService @Inject constructor() {

    // 最大记录数量（避免内存溢出）
    private val maxRecordCount = 100

    // 请求记录队列
    private val requestRecords = ConcurrentLinkedQueue<NetworkRequestRecord>()

    // 请求列表状态流
    private val _requests = MutableStateFlow<List<NetworkRequestRecord>>(emptyList())
    val requests: StateFlow<List<NetworkRequestRecord>> = _requests.asStateFlow()

    /**
     * 记录网络请求
     */
    fun recordRequest(record: NetworkRequestRecord) {
        requestRecords.offer(record)

        // 限制队列大小
        while (requestRecords.size > maxRecordCount) {
            requestRecords.poll()
        }

        // 更新状态流
        _requests.value = requestRecords.toList()

        Timber.d(
            "[NetworkMonitor] ${record.method} ${record.url} - ${record.statusCode} (${record.duration}ms)"
        )
    }

    /**
     * 获取所有请求记录
     */
    fun getAllRecords(): List<NetworkRequestRecord> {
        return requestRecords.toList()
    }

    /**
     * 按方法筛选
     */
    fun getRecordsByMethod(method: String): List<NetworkRequestRecord> {
        return requestRecords.filter { it.method == method }
    }

    /**
     * 获取失败的请求
     */
    fun getFailedRecords(): List<NetworkRequestRecord> {
        return requestRecords.filter { it.statusCode >= 400 }
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): NetworkStatistics {
        val records = requestRecords.toList()

        if (records.isEmpty()) {
            return NetworkStatistics()
        }

        val totalRequests = records.size
        val successCount = records.count { it.statusCode in 200..299 }
        val errorCount = records.count { it.statusCode >= 400 }
        val averageDuration = records.map { it.duration }.average().toLong()
        val totalDataSent = records.sumOf { it.requestBodySize }
        val totalDataReceived = records.sumOf { it.responseBodySize }

        return NetworkStatistics(
            totalRequests = totalRequests,
            successCount = successCount,
            errorCount = errorCount,
            averageDuration = averageDuration,
            totalDataSent = totalDataSent,
            totalDataReceived = totalDataReceived
        )
    }

    /**
     * 清除所有记录
     */
    fun clearRecords() {
        requestRecords.clear()
        _requests.value = emptyList()
        Timber.i("[NetworkMonitor] 已清除所有网络请求记录")
    }
}

/**
 * 网络请求记录
 */
data class NetworkRequestRecord(
    val id: String,
    val url: String,
    val method: String,
    val statusCode: Int,
    val duration: Long,                // 毫秒
    val timestamp: Long,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val requestBodySize: Long = 0,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val responseBodySize: Long = 0,
    val errorMessage: String? = null
)

/**
 * 网络统计信息
 */
data class NetworkStatistics(
    val totalRequests: Int = 0,
    val successCount: Int = 0,
    val errorCount: Int = 0,
    val averageDuration: Long = 0,
    val totalDataSent: Long = 0,
    val totalDataReceived: Long = 0
)
