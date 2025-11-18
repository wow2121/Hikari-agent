package com.xiaoguang.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Chroma向量数据库请求/响应数据类
 */

/**
 * 创建集合请求
 */
data class ChromaCreateCollectionRequest(
    @SerializedName("name")
    val name: String,

    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null,

    @SerializedName("get_or_create")
    val getOrCreate: Boolean = true
)

/**
 * 添加文档请求
 */
data class ChromaAddDocumentsRequest(
    @SerializedName("ids")
    val ids: List<String>,

    @SerializedName("embeddings")
    val embeddings: List<List<Float>>,

    @SerializedName("documents")
    val documents: List<String>,

    @SerializedName("metadatas")
    val metadatas: List<Map<String, Any>>? = null
)

/**
 * 查询请求
 */
data class ChromaQueryRequest(
    @SerializedName("query_embeddings")
    val queryEmbeddings: List<List<Float>>,

    @SerializedName("n_results")
    val nResults: Int = 10,

    @SerializedName("where")
    val where: Map<String, Any>? = null,

    @SerializedName("include")
    val include: List<String> = listOf("documents", "metadatas", "distances")
)

/**
 * 集合响应
 */
data class ChromaCollectionResponse(
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)

/**
 * 查询结果响应
 */
data class ChromaQueryResponse(
    @SerializedName("ids")
    val ids: List<List<String>>,

    @SerializedName("distances")
    val distances: List<List<Float>>?,

    @SerializedName("embeddings")
    val embeddings: List<List<List<Float>>>?,

    @SerializedName("documents")
    val documents: List<List<String>>?,

    @SerializedName("metadatas")
    val metadatas: List<List<Map<String, Any>>>?
)

/**
 * 更新文档请求
 */
data class ChromaUpdateDocumentsRequest(
    @SerializedName("ids")
    val ids: List<String>,

    @SerializedName("embeddings")
    val embeddings: List<List<Float>>? = null,

    @SerializedName("documents")
    val documents: List<String>? = null,

    @SerializedName("metadatas")
    val metadatas: List<Map<String, Any>>? = null
)

/**
 * 删除文档请求
 */
data class ChromaDeleteDocumentsRequest(
    @SerializedName("ids")
    val ids: List<String>? = null,

    @SerializedName("where")
    val where: Map<String, Any>? = null
)

/**
 * 获取集合统计
 */
data class ChromaCollectionStats(
    @SerializedName("count")
    val count: Int,

    @SerializedName("name")
    val name: String,

    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null
)
