package com.xiaoguang.assistant.core.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络监控拦截器
 *
 * 拦截所有 OkHttp 请求，记录到 NetworkMonitorService
 */
@Singleton
class NetworkMonitorInterceptor @Inject constructor(
    private val networkMonitorService: NetworkMonitorService
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        // 记录请求信息
        val requestUrl = request.url.toString()
        val requestMethod = request.method
        val requestHeaders = request.headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }
        val requestBody = try {
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8().take(10000) // 限制大小，避免内存问题
            }
        } catch (e: Exception) {
            Timber.w(e, "[NetworkMonitor] 读取请求体失败")
            null
        }
        val requestBodySize = request.body?.contentLength() ?: 0L

        var response: Response? = null
        var errorMessage: String? = null

        try {
            // 执行请求
            response = chain.proceed(request)

            // 记录响应信息
            val statusCode = response.code
            val responseHeaders = response.headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }

            // 读取响应体（需要小心处理，避免消耗响应流）
            val responseBodyString = try {
                if (response.body != null) {
                    val source = response.body!!.source()
                    source.request(Long.MAX_VALUE) // 请求整个响应体
                    val buffer = source.buffer
                    buffer.clone().readUtf8().take(10000) // 限制大小
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e, "[NetworkMonitor] 读取响应体失败")
                null
            }

            val responseBodySize = response.body?.contentLength() ?: 0L
            val duration = System.currentTimeMillis() - startTime

            // 记录到监控服务
            val record = NetworkRequestRecord(
                id = requestId,
                url = requestUrl,
                method = requestMethod,
                statusCode = statusCode,
                duration = duration,
                timestamp = startTime,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                requestBodySize = requestBodySize,
                responseHeaders = responseHeaders,
                responseBody = responseBodyString,
                responseBodySize = responseBodySize
            )

            networkMonitorService.recordRequest(record)

            return response

        } catch (e: IOException) {
            // 网络异常
            errorMessage = e.message ?: "网络请求失败"
            val duration = System.currentTimeMillis() - startTime

            // 记录失败请求
            val record = NetworkRequestRecord(
                id = requestId,
                url = requestUrl,
                method = requestMethod,
                statusCode = 0,
                duration = duration,
                timestamp = startTime,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                requestBodySize = requestBodySize,
                errorMessage = errorMessage
            )

            networkMonitorService.recordRequest(record)

            // 重新抛出异常
            throw e

        } catch (e: Exception) {
            // 其他异常
            errorMessage = e.message ?: "未知错误"
            val duration = System.currentTimeMillis() - startTime

            val record = NetworkRequestRecord(
                id = requestId,
                url = requestUrl,
                method = requestMethod,
                statusCode = 0,
                duration = duration,
                timestamp = startTime,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                requestBodySize = requestBodySize,
                errorMessage = errorMessage
            )

            networkMonitorService.recordRequest(record)

            // 重新抛出异常
            throw e
        }
    }
}
