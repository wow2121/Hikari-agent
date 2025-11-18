package com.xiaoguang.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Neo4j图数据库请求/响应数据类
 * 使用HTTP Transactional Cypher endpoint
 */

/**
 * Cypher查询请求
 */
data class Neo4jQueryRequest(
    @SerializedName("statements")
    val statements: List<CypherStatement>
)

/**
 * Cypher语句
 */
data class CypherStatement(
    @SerializedName("statement")
    val statement: String,

    @SerializedName("parameters")
    val parameters: Map<String, Any>? = null,

    @SerializedName("resultDataContents")
    val resultDataContents: List<String> = listOf("row", "graph"),

    @SerializedName("includeStats")
    val includeStats: Boolean = false
)

/**
 * Neo4j查询响应
 */
data class Neo4jQueryResponse(
    @SerializedName("results")
    val results: List<QueryResult>,

    @SerializedName("errors")
    val errors: List<Neo4jError>
)

/**
 * 查询结果
 */
data class QueryResult(
    @SerializedName("columns")
    val columns: List<String>,

    @SerializedName("data")
    val data: List<ResultData>,

    @SerializedName("stats")
    val stats: QueryStats? = null
)

/**
 * 结果数据
 */
data class ResultData(
    @SerializedName("row")
    val row: List<Any?>? = null,

    @SerializedName("graph")
    val graph: GraphData? = null
)

/**
 * 图数据
 */
data class GraphData(
    @SerializedName("nodes")
    val nodes: List<GraphNode>,

    @SerializedName("relationships")
    val relationships: List<GraphRelationship>
)

/**
 * 图节点
 */
data class GraphNode(
    @SerializedName("id")
    val id: String,

    @SerializedName("labels")
    val labels: List<String>,

    @SerializedName("properties")
    val properties: Map<String, Any>
)

/**
 * 图关系
 */
data class GraphRelationship(
    @SerializedName("id")
    val id: String,

    @SerializedName("type")
    val type: String,

    @SerializedName("startNode")
    val startNode: String,

    @SerializedName("endNode")
    val endNode: String,

    @SerializedName("properties")
    val properties: Map<String, Any>
)

/**
 * 查询统计
 */
data class QueryStats(
    @SerializedName("nodes_created")
    val nodesCreated: Int = 0,

    @SerializedName("nodes_deleted")
    val nodesDeleted: Int = 0,

    @SerializedName("relationships_created")
    val relationshipsCreated: Int = 0,

    @SerializedName("relationships_deleted")
    val relationshipsDeleted: Int = 0,

    @SerializedName("properties_set")
    val propertiesSet: Int = 0
)

/**
 * Neo4j错误
 */
data class Neo4jError(
    @SerializedName("code")
    val code: String,

    @SerializedName("message")
    val message: String
)
