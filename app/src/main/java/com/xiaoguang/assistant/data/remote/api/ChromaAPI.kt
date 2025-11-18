package com.xiaoguang.assistant.data.remote.api

import com.xiaoguang.assistant.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Chroma向量数据库 HTTP API (v2)
 *
 * Chroma默认运行在 http://localhost:8000
 * 需要独立部署Chroma服务器
 *
 * 注意：ChromaDB v2 使用多租户架构
 * 默认: tenant=default_tenant, database=default_database
 */
interface ChromaAPI {

    /**
     * 创建集合
     */
    @POST("api/v2/tenants/{tenant}/databases/{database}/collections")
    @Headers("Content-Type: application/json")
    suspend fun createCollection(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Body request: ChromaCreateCollectionRequest
    ): Response<ChromaCollectionResponse>

    /**
     * 获取集合信息
     */
    @GET("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}")
    suspend fun getCollection(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String
    ): Response<ChromaCollectionResponse>

    /**
     * 删除集合
     */
    @DELETE("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}")
    suspend fun deleteCollection(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String
    ): Response<Unit>

    /**
     * 添加文档到集合
     */
    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/add")
    @Headers("Content-Type: application/json")
    suspend fun addDocuments(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String,
        @Body request: ChromaAddDocumentsRequest
    ): Response<Unit>

    /**
     * 查询相似文档（也可用于元数据过滤）
     */
    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/query")
    @Headers("Content-Type: application/json")
    suspend fun query(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String,
        @Body request: ChromaQueryRequest
    ): Response<ChromaQueryResponse>

    /**
     * 更新文档
     */
    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/update")
    @Headers("Content-Type: application/json")
    suspend fun updateDocuments(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String,
        @Body request: ChromaUpdateDocumentsRequest
    ): Response<Unit>

    /**
     * 删除文档
     */
    @POST("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/delete")
    @Headers("Content-Type: application/json")
    suspend fun deleteDocuments(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String,
        @Body request: ChromaDeleteDocumentsRequest
    ): Response<Unit>

    /**
     * 获取集合文档数量
     */
    @GET("api/v2/tenants/{tenant}/databases/{database}/collections/{collection_id}/count")
    suspend fun getCount(
        @Path("tenant") tenant: String,
        @Path("database") database: String,
        @Path("collection_id") collectionId: String
    ): Response<ChromaCollectionStats>

    companion object {
        /**
         * Chroma默认运行地址
         * 可在配置中修改为远程服务器地址
         */
        const val DEFAULT_BASE_URL = "http://localhost:8000/"

        /**
         * 默认租户和数据库
         */
        const val DEFAULT_TENANT = "default_tenant"
        const val DEFAULT_DATABASE = "default_database"

        /**
         * 预定义的集合名称
         */
        const val COLLECTION_WORLD_BOOK = "xiaoguang_world_book"
        const val COLLECTION_CHARACTER_MEMORIES = "xiaoguang_character_memories"
        const val COLLECTION_CONVERSATIONS = "xiaoguang_conversations"
        const val COLLECTION_RELATIONSHIP_CONTEXTS = "xiaoguang_relationship_contexts"  // 第三方关系语义检索
    }
}
