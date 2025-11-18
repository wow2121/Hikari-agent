package com.xiaoguang.assistant.core.logging

/**
 * 日志收集器 - 全局单例，提供访问内存日志
 */
object LogCollector {

    private var memoryLogTree: MemoryLogTree? = null

    /**
     * 安装内存日志树（由Application调用）
     */
    fun install(tree: MemoryLogTree) {
        memoryLogTree = tree
    }

    /**
     * 获取所有日志
     */
    fun getLogs(): List<MemoryLogTree.LogItem> {
        return memoryLogTree?.getLogs() ?: emptyList()
    }

    /**
     * 清空日志
     */
    fun clear() {
        memoryLogTree?.clear()
    }
}
