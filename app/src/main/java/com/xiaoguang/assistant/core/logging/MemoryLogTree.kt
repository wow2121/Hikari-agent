package com.xiaoguang.assistant.core.logging

import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 内存日志树 - 收集应用日志到内存中供日志查看器使用
 *
 * 特点：
 * - 只保留最近的日志（默认1000条）
 * - 线程安全
 * - 过滤系统无用日志
 */
class MemoryLogTree(
    private val maxLogSize: Int = 1000
) : Timber.Tree() {

    private val logs = ConcurrentLinkedQueue<LogItem>()

    /**
     * 日志条目
     */
    data class LogItem(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 需要过滤的系统标签（这些日志对应用调试没有帮助）
     */
    private val systemTagsToFilter = setOf(
        "SurfaceComposerClient",
        "OpenGLRenderer",
        "BufferQueueProducer",
        "GraphicBuffer",
        "BufferQueue",
        "Choreographer",
        "ViewRootImpl",
        "InputTransport",
        "InputMethodManager",
        "InsetsController",
        "DecorView"
    )

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // 过滤掉系统无用日志
        if (tag != null && systemTagsToFilter.contains(tag)) {
            return
        }

        // 过滤掉 Transaction::setDataspace 等底层渲染日志
        if (message.contains("Transaction::setDataspace") ||
            message.contains("setBufferCount") ||
            message.contains("queueBuffer") ||
            message.contains("dequeueBuffer")
        ) {
            return
        }

        val logItem = LogItem(priority, tag, message, t)
        logs.offer(logItem)

        // 保持日志数量在限制内
        while (logs.size > maxLogSize) {
            logs.poll()
        }
    }

    /**
     * 获取所有日志
     */
    fun getLogs(): List<LogItem> {
        return logs.toList()
    }

    /**
     * 清空日志
     */
    fun clear() {
        logs.clear()
    }

    companion object {
        /**
         * 将 Timber 优先级转换为日志级别名称
         */
        fun priorityToLevelName(priority: Int): String {
            return when (priority) {
                android.util.Log.VERBOSE -> "VERBOSE"
                android.util.Log.DEBUG -> "DEBUG"
                android.util.Log.INFO -> "INFO"
                android.util.Log.WARN -> "WARN"
                android.util.Log.ERROR -> "ERROR"
                android.util.Log.ASSERT -> "ASSERT"
                else -> "UNKNOWN"
            }
        }
    }
}
