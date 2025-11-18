package com.xiaoguang.assistant.util

import android.util.Log
import timber.log.Timber

/**
 * 过滤频繁日志的Timber Tree
 *
 * 功能：
 * 1. 限制相同日志的输出频率（防止日志爆炸）
 * 2. 过滤掉过于频繁的Verbose级别日志
 * 3. 允许重要日志正常输出
 */
class FilteredDebugTree : Timber.DebugTree() {

    companion object {
        private const val MIN_LOG_INTERVAL_MS = 1000L  // 相同日志最小间隔1秒
        private const val MAX_CACHE_SIZE = 100         // 缓存大小
    }

    // 记录每个日志的最后输出时间
    private val lastLogTime = mutableMapOf<String, Long>()

    // 过滤掉的系统标签
    private val filteredTags = setOf(
        "SurfaceComposerClient",
        "OpenGLRenderer",
        "BufferQueueProducer",
        "GraphicBuffer",
        "BufferQueue",
        "Choreographer"
    )

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // ⚠️ 过滤掉Verbose级别日志（太多了）
        if (priority == Log.VERBOSE) {
            return
        }

        // ⚠️ 过滤掉系统渲染日志
        if (tag != null && filteredTags.contains(tag)) {
            return
        }

        // ⚠️ 过滤掉特定的系统消息
        if (message.contains("Transaction::setDataspace") ||
            message.contains("setBufferCount") ||
            message.contains("queueBuffer") ||
            message.contains("dequeueBuffer")
        ) {
            return
        }

        // 生成日志的唯一键（tag + message的前50个字符）
        val logKey = "${tag}_${message.take(50)}"

        val currentTime = System.currentTimeMillis()
        val lastTime = lastLogTime[logKey]

        // 如果是首次记录或距离上次超过阈值，则允许输出
        if (lastTime == null || (currentTime - lastTime) >= MIN_LOG_INTERVAL_MS) {
            // 更新最后输出时间
            lastLogTime[logKey] = currentTime

            // 清理旧缓存
            if (lastLogTime.size > MAX_CACHE_SIZE) {
                // 移除最早的一半
                val sortedEntries = lastLogTime.entries.sortedBy { it.value }
                sortedEntries.take(MAX_CACHE_SIZE / 2).forEach {
                    lastLogTime.remove(it.key)
                }
            }

            // 输出日志
            super.log(priority, tag, message, t)
        }
        // 否则，静默丢弃（防止日志爆炸）
    }
}
