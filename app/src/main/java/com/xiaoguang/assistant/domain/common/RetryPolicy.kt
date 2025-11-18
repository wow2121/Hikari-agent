package com.xiaoguang.assistant.domain.common

import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.pow

/**
 * 重试策略
 * 为Neo4j和ChromaDB等外部服务提供自动重试机制
 *
 * 重试策略：
 * - 指数退避（Exponential Backoff）
 * - 可配置最大重试次数
 * - 可配置基础延迟时间
 * - 支持异常过滤（只重试特定异常）
 */
class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 10000,
    val backoffMultiplier: Double = 2.0,
    val retryableExceptions: List<Class<out Exception>> = listOf(
        java.net.ConnectException::class.java,
        java.net.SocketTimeoutException::class.java,
        java.io.IOException::class.java
    )
) {

    /**
     * 执行带重试的操作
     *
     * @param operation 要执行的操作
     * @param operationName 操作名称（用于日志）
     * @return 操作结果
     */
    suspend fun <T> execute(
        operationName: String,
        operation: suspend () -> T
    ): Result<T> {
        var lastException: Exception? = null
        var currentDelay = initialDelayMs

        repeat(maxRetries + 1) { attempt ->
            try {
                val result = operation()
                if (attempt > 0) {
                    Timber.i("[RetryPolicy] $operationName 在第${attempt + 1}次尝试成功")
                }
                return Result.success(result)

            } catch (e: Exception) {
                lastException = e

                // 检查是否是可重试的异常
                val isRetryable = retryableExceptions.any { it.isInstance(e) }

                if (!isRetryable) {
                    Timber.w(e, "[RetryPolicy] $operationName 遇到不可重试异常")
                    return Result.failure(e)
                }

                if (attempt < maxRetries) {
                    Timber.w(
                        "[RetryPolicy] $operationName 失败（第${attempt + 1}次尝试），" +
                                "${currentDelay}ms 后重试: ${e.message}"
                    )
                    delay(currentDelay)

                    // 指数退避
                    currentDelay = (currentDelay * backoffMultiplier).toLong()
                        .coerceAtMost(maxDelayMs)
                } else {
                    Timber.e(e, "[RetryPolicy] $operationName 重试${maxRetries}次后仍然失败")
                }
            }
        }

        return Result.failure(
            lastException ?: Exception("$operationName 失败但无异常信息")
        )
    }

    /**
     * 执行带重试的操作（Result版本）
     * 适用于已经返回Result的操作
     */
    suspend fun <T> executeResult(
        operationName: String,
        operation: suspend () -> Result<T>
    ): Result<T> {
        var lastResult: Result<T>? = null
        var currentDelay = initialDelayMs

        repeat(maxRetries + 1) { attempt ->
            try {
                val result = operation()

                if (result.isSuccess) {
                    if (attempt > 0) {
                        Timber.i("[RetryPolicy] $operationName 在第${attempt + 1}次尝试成功")
                    }
                    return result
                }

                // Result失败
                lastResult = result
                val exception = result.exceptionOrNull()

                // 检查是否是可重试的异常
                val isRetryable = exception != null &&
                        retryableExceptions.any { it.isInstance(exception) }

                if (!isRetryable) {
                    Timber.w("[RetryPolicy] $operationName 遇到不可重试错误: ${exception?.message}")
                    return result
                }

                if (attempt < maxRetries) {
                    Timber.w(
                        "[RetryPolicy] $operationName 失败（第${attempt + 1}次尝试），" +
                                "${currentDelay}ms 后重试: ${exception?.message}"
                    )
                    delay(currentDelay)

                    // 指数退避
                    currentDelay = (currentDelay * backoffMultiplier).toLong()
                        .coerceAtMost(maxDelayMs)
                } else {
                    Timber.e("[RetryPolicy] $operationName 重试${maxRetries}次后仍然失败")
                }

            } catch (e: Exception) {
                // 操作抛出异常
                val isRetryable = retryableExceptions.any { it.isInstance(e) }

                if (!isRetryable || attempt >= maxRetries) {
                    Timber.e(e, "[RetryPolicy] $operationName 失败")
                    return Result.failure(e)
                }

                Timber.w("[RetryPolicy] $operationName 异常，${currentDelay}ms 后重试")
                delay(currentDelay)
                currentDelay = (currentDelay * backoffMultiplier).toLong()
                    .coerceAtMost(maxDelayMs)
            }
        }

        return lastResult ?: Result.failure(Exception("$operationName 失败"))
    }

    companion object {
        /**
         * 默认重试策略（用于Neo4j/ChromaDB）
         */
        val DEFAULT = RetryPolicy(
            maxRetries = 3,
            initialDelayMs = 1000,
            maxDelayMs = 10000,
            backoffMultiplier = 2.0
        )

        /**
         * 快速重试策略（用于轻量级操作）
         */
        val FAST = RetryPolicy(
            maxRetries = 2,
            initialDelayMs = 500,
            maxDelayMs = 2000,
            backoffMultiplier = 1.5
        )

        /**
         * 持久重试策略（用于关键操作）
         */
        val PERSISTENT = RetryPolicy(
            maxRetries = 5,
            initialDelayMs = 2000,
            maxDelayMs = 30000,
            backoffMultiplier = 2.5
        )
    }
}

/**
 * 重试扩展函数
 */
suspend fun <T> retryWithPolicy(
    policy: RetryPolicy = RetryPolicy.DEFAULT,
    operationName: String,
    operation: suspend () -> T
): Result<T> {
    return policy.execute(operationName, operation)
}

suspend fun <T> retryResultWithPolicy(
    policy: RetryPolicy = RetryPolicy.DEFAULT,
    operationName: String,
    operation: suspend () -> Result<T>
): Result<T> {
    return policy.executeResult(operationName, operation)
}
