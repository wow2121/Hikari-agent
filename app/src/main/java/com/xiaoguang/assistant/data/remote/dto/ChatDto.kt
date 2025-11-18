package com.xiaoguang.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("model")
    val model: String = "Pro/deepseek-ai/DeepSeek-V3.2-Exp",
    @SerializedName("messages")
    val messages: List<ChatMessage>,
    @SerializedName("stream")
    val stream: Boolean = false,
    @SerializedName("temperature")
    val temperature: Float = 0.7f,
    @SerializedName("max_tokens")
    val maxTokens: Int = 2000,
    @SerializedName("top_p")
    val topP: Float = 1.0f,
    @SerializedName("tools")
    val tools: List<Tool>? = null,
    @SerializedName("response_format")
    val responseFormat: Map<String, String>? = null
)

data class ChatMessage(
    @SerializedName("role")
    val role: String, // "system", "user", "assistant", "tool"
    @SerializedName("content")
    val content: String? = null,
    @SerializedName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerializedName("tool_call_id")
    val toolCallId: String? = null
)

data class ChatResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("object")
    val objectType: String,
    @SerializedName("created")
    val created: Long,
    @SerializedName("model")
    val model: String,
    @SerializedName("choices")
    val choices: List<Choice>,
    @SerializedName("usage")
    val usage: Usage?
)

data class Choice(
    @SerializedName("index")
    val index: Int,
    @SerializedName("message")
    val message: ChatMessage?,
    @SerializedName("delta")
    val delta: ChatMessage?, // For streaming responses
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

// DTOs for event extraction
data class EventExtractionResponse(
    @SerializedName("has_event")
    val hasEvent: Boolean,
    @SerializedName("title")
    val title: String = "",
    @SerializedName("datetime")
    val datetime: String = "",
    @SerializedName("duration_minutes")
    val durationMinutes: Int = 60,
    @SerializedName("location")
    val location: String = "",
    @SerializedName("description")
    val description: String = "",
    @SerializedName("confidence")
    val confidence: Float = 0.0f,
    @SerializedName("participants")
    val participants: List<String> = emptyList()
)

// DTOs for entity extraction
data class EntityExtractionResponse(
    @SerializedName("entities")
    val entities: List<ExtractedEntity>
)

data class ExtractedEntity(
    @SerializedName("type")
    val type: String, // "person", "event", "location", "topic"
    @SerializedName("name")
    val name: String,
    @SerializedName("attributes")
    val attributes: Map<String, String> = emptyMap(),
    @SerializedName("relationships")
    val relationships: List<EntityRelationship> = emptyList()
)

data class EntityRelationship(
    @SerializedName("target")
    val target: String,
    @SerializedName("type")
    val type: String
)

// Function Calling 相关DTOs
data class Tool(
    @SerializedName("type")
    val type: String = "function",
    @SerializedName("function")
    val function: FunctionDefinition
)

data class FunctionDefinition(
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("parameters")
    val parameters: Map<String, Any>
)

data class ToolCall(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String = "function",
    @SerializedName("function")
    val function: FunctionCall
)

data class FunctionCall(
    @SerializedName("name")
    val name: String,
    @SerializedName("arguments")
    val arguments: String  // JSON string
)
