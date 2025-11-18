package com.xiaoguang.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Embedding API 请求
 */
data class EmbeddingRequest(
    @SerializedName("model")
    val model: String = "Qwen/Qwen3-Embedding-0.6B",
    @SerializedName("input")
    val input: Any, // 可以是单个字符串或字符串列表
    @SerializedName("encoding_format")
    val encodingFormat: String = "float",
    @SerializedName("dimension")
    val dimension: Int? = null // 可选,指定输出维度(如1024, 2560, 4096)
)

/**
 * Embedding API 响应
 */
data class EmbeddingResponse(
    @SerializedName("object")
    val objectType: String,
    @SerializedName("data")
    val data: List<EmbeddingData>,
    @SerializedName("model")
    val model: String,
    @SerializedName("usage")
    val usage: EmbeddingUsage
)

/**
 * 单个文本的Embedding数据
 */
data class EmbeddingData(
    @SerializedName("object")
    val objectType: String,
    @SerializedName("index")
    val index: Int,
    @SerializedName("embedding")
    val embedding: List<Float>
)

/**
 * Embedding API 使用统计
 */
data class EmbeddingUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)
