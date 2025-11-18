package com.xiaoguang.assistant.domain.knowledge.graph

import android.util.Base64
import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.Neo4jAPI
import com.xiaoguang.assistant.data.remote.dto.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Neo4j客户端
 * 封装Neo4j HTTP API，提供Cypher查询执行
 */
@Singleton
class Neo4jClient @Inject constructor(
    private val neo4jAPI: Neo4jAPI
) {

    /**
     * 从配置读取Neo4j连接信息
     * 默认值可在BuildConfig或local.properties中配置
     */
    private val username = BuildConfig.NEO4J_USERNAME.takeIf { it.isNotBlank() } ?: Neo4jAPI.DEFAULT_USERNAME
    private val password = BuildConfig.NEO4J_PASSWORD.takeIf { it.isNotBlank() } ?: "neo4j"  // 默认密码
    private val database = BuildConfig.NEO4J_DATABASE.takeIf { it.isNotBlank() } ?: Neo4jAPI.DEFAULT_DATABASE

    private val authHeader: String
        get() {
            val credentials = "$username:$password"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            return "Basic $encoded"
        }

    init {
        // 验证配置
        if (password == "neo4j" || password.isBlank()) {
            Timber.w("[Neo4j] 使用默认密码或密码未配置，建议在local.properties中设置NEO4J_PASSWORD")
        }
    }

    /**
     * 执行单个Cypher查询
     *
     * @param cypher Cypher查询语句
     * @param parameters 查询参数
     * @return 查询结果
     */
    suspend fun executeQuery(
        cypher: String,
        parameters: Map<String, Any>? = null
    ): Result<QueryResult> {
        return try {
            val statement = CypherStatement(
                statement = cypher,
                parameters = parameters,
                resultDataContents = listOf("row", "graph"),
                includeStats = true
            )

            val request = Neo4jQueryRequest(statements = listOf(statement))

            val response = neo4jAPI.executeQuery(
                database = database,
                authorization = authHeader,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.errors.isNotEmpty()) {
                    val error = body.errors.first()
                    Timber.e("[Neo4jClient] Cypher错误: ${error.code} - ${error.message}")
                    return Result.failure(Exception("Cypher错误: ${error.message}"))
                }

                val result = body.results.firstOrNull()
                    ?: return Result.failure(Exception("查询无结果"))

                Result.success(result)
            } else {
                Result.failure(Exception("API调用失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[Neo4jClient] 查询执行失败（可选服务）")
            Result.failure(e)
        }
    }

    /**
     * 执行批量Cypher查询
     */
    suspend fun executeBatch(
        queries: List<Pair<String, Map<String, Any>?>>
    ): Result<List<QueryResult>> {
        return try {
            val statements = queries.map { (cypher, params) ->
                CypherStatement(
                    statement = cypher,
                    parameters = params,
                    resultDataContents = listOf("row"),
                    includeStats = true
                )
            }

            val request = Neo4jQueryRequest(statements = statements)

            val response = neo4jAPI.executeQuery(
                database = database,
                authorization = authHeader,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.errors.isNotEmpty()) {
                    val error = body.errors.first()
                    return Result.failure(Exception("批量查询错误: ${error.message}"))
                }

                Result.success(body.results)
            } else {
                Result.failure(Exception("批量查询失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[Neo4jClient] 批量查询失败（可选服务）")
            Result.failure(e)
        }
    }

    /**
     * 检查连接状态
     */
    suspend fun checkConnection(): Boolean {
        return try {
            val result = executeQuery("RETURN 1")
            result.isSuccess
        } catch (e: Exception) {
            Timber.w(e, "[Neo4jClient] 连接检查失败（可选服务）")
            false
        }
    }

    /**
     * 创建索引
     */
    suspend fun createIndex(label: String, property: String): Result<Unit> {
        val cypher = "CREATE INDEX IF NOT EXISTS FOR (n:$label) ON (n.$property)"
        return executeQuery(cypher).map { }
    }

    /**
     * 创建约束（唯一性）
     */
    suspend fun createConstraint(label: String, property: String): Result<Unit> {
        val cypher = """
            CREATE CONSTRAINT IF NOT EXISTS
            FOR (n:$label)
            REQUIRE n.$property IS UNIQUE
        """.trimIndent()
        return executeQuery(cypher).map { }
    }

    /**
     * 清空数据库（谨慎使用！）
     */
    suspend fun clearDatabase(): Result<Unit> {
        val cypher = "MATCH (n) DETACH DELETE n"
        return executeQuery(cypher).map { }
    }

    /**
     * 获取数据库统计信息
     */
    suspend fun getStats(): Result<Map<String, Any>> {
        val cypher = """
            MATCH (n)
            OPTIONAL MATCH ()-[r]->()
            RETURN
                count(DISTINCT n) as nodeCount,
                count(DISTINCT r) as relationshipCount,
                count(DISTINCT labels(n)) as labelCount
        """.trimIndent()

        return try {
            val result = executeQuery(cypher).getOrThrow()
            val row = result.data.firstOrNull()?.row

            if (row != null && row.size >= 3) {
                Result.success(
                    mapOf<String, Any>(
                        "nodeCount" to ((row[0] as? Number)?.toInt() ?: 0),
                        "relationshipCount" to ((row[1] as? Number)?.toInt() ?: 0),
                        "labelCount" to ((row[2] as? Number)?.toInt() ?: 0)
                    )
                )
            } else {
                Result.failure(Exception("统计数据格式错误"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[Neo4jClient] 获取统计失败（可选服务）")
            Result.failure(e)
        }
    }
}
