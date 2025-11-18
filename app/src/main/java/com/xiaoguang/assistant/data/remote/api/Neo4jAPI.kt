package com.xiaoguang.assistant.data.remote.api

import com.xiaoguang.assistant.data.remote.dto.Neo4jQueryRequest
import com.xiaoguang.assistant.data.remote.dto.Neo4jQueryResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * Neo4j图数据库 HTTP API
 *
 * Neo4j默认运行在 http://localhost:7474
 * 使用 HTTP Transactional Cypher endpoint
 */
interface Neo4jAPI {

    /**
     * 执行Cypher查询（事务性）
     *
     * @param authorization Basic Auth (username:password base64编码)
     * @param request 查询请求
     */
    @POST("db/{database}/tx/commit")
    @Headers("Content-Type: application/json")
    suspend fun executeQuery(
        @Path("database") database: String = "neo4j",
        @Header("Authorization") authorization: String,
        @Body request: Neo4jQueryRequest
    ): Response<Neo4jQueryResponse>

    /**
     * 开始事务
     */
    @POST("db/{database}/tx")
    @Headers("Content-Type: application/json")
    suspend fun beginTransaction(
        @Path("database") database: String = "neo4j",
        @Header("Authorization") authorization: String,
        @Body request: Neo4jQueryRequest
    ): Response<Neo4jQueryResponse>

    /**
     * 提交事务
     */
    @POST("db/{database}/tx/{transactionId}/commit")
    @Headers("Content-Type: application/json")
    suspend fun commitTransaction(
        @Path("database") database: String = "neo4j",
        @Path("transactionId") transactionId: String,
        @Header("Authorization") authorization: String,
        @Body request: Neo4jQueryRequest
    ): Response<Neo4jQueryResponse>

    /**
     * 回滚事务
     */
    @DELETE("db/{database}/tx/{transactionId}")
    suspend fun rollbackTransaction(
        @Path("database") database: String = "neo4j",
        @Path("transactionId") transactionId: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    companion object {
        /**
         * Neo4j默认运行地址
         * 可在配置中修改为远程服务器地址
         */
        const val DEFAULT_BASE_URL = "http://localhost:7474/"

        /**
         * 默认数据库名称
         */
        const val DEFAULT_DATABASE = "neo4j"

        /**
         * 默认用户名
         */
        const val DEFAULT_USERNAME = "neo4j"

        /**
         * 节点标签
         */
        const val LABEL_CHARACTER = "Character"
        const val LABEL_MEMORY = "Memory"
        const val LABEL_EVENT = "Event"
        const val LABEL_LOCATION = "Location"
        const val LABEL_CONCEPT = "Concept"

        /**
         * 关系类型
         */
        const val REL_KNOWS = "KNOWS"
        const val REL_MASTER = "MASTER"
        const val REL_FAMILY = "FAMILY"
        const val REL_FRIEND = "FRIEND"
        const val REL_COLLEAGUE = "COLLEAGUE"
        const val REL_RIVAL = "RIVAL"
        const val REL_LOVER = "LOVER"
        const val REL_REMEMBERS = "REMEMBERS"
        const val REL_PARTICIPATED_IN = "PARTICIPATED_IN"
        const val REL_LOCATED_AT = "LOCATED_AT"
        const val REL_RELATED_TO = "RELATED_TO"
    }
}
