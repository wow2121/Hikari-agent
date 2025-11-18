package com.xiaoguang.assistant.domain.mcp

import com.google.gson.JsonObject

/**
 * MCP工具定义
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val handler: suspend (JsonObject) -> McpToolResult
)

/**
 * MCP工具调用结果
 */
data class McpToolResult(
    val success: Boolean,
    val content: String,
    val data: Any? = null
)

/**
 * MCP工具调用请求
 */
data class McpToolCall(
    val name: String,
    val arguments: JsonObject
)
