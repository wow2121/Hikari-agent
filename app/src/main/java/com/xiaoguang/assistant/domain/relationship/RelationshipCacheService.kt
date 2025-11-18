package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.data.local.database.entity.RelationshipNetworkEntity
import com.xiaoguang.assistant.domain.common.LoadingLruCache
import com.xiaoguang.assistant.domain.common.LruCache
import com.xiaoguang.assistant.domain.usecase.RelationshipNetworkManagementUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 关系查询缓存服务 (v2.2 - 纯Neo4j架构)
 * 使用LRU缓存优化关系网络查询性能
 *
 * v2.2架构变更：
 * - 使用RelationshipNetworkManagementUseCase代替DAO
 * - UseCase内部直接调用Neo4j，缓存层保持不变
 *
 * 缓存策略：
 * 1. 单个关系缓存 - 缓存两人之间的关系
 * 2. 人物关系列表缓存 - 缓存某人的所有关系
 * 3. 查询结果缓存 - 缓存复杂查询结果
 * 4. TTL机制 - 5分钟过期，确保数据新鲜度
 */
@Singleton
class RelationshipCacheService @Inject constructor(
    private val relationshipUseCase: RelationshipNetworkManagementUseCase
) {

    // ==================== 缓存实例 ====================

    /**
     * 单个关系缓存
     * Key: "personA|personB" (规范化后)
     * Value: RelationshipNetworkEntity
     */
    private val relationCache = LoadingLruCache<String, RelationshipNetworkEntity?>(
        maxSize = 500,
        ttlMs = 5 * 60 * 1000,  // 5分钟过期
        name = "RelationCache"
    ) { key ->
        val (personA, personB) = key.split("|")
        relationshipUseCase.getRelationBetween(personA, personB)
    }

    /**
     * 人物关系列表缓存
     * Key: personName
     * Value: List<RelationshipNetworkEntity>
     */
    private val personRelationsCache = LoadingLruCache<String, List<RelationshipNetworkEntity>>(
        maxSize = 200,
        ttlMs = 5 * 60 * 1000,
        name = "PersonRelationsCache"
    ) { personName ->
        relationshipUseCase.getPersonRelations(personName)
    }

    /**
     * 路径查询缓存
     * Key: "personA->personB"
     * Value: List<RelationshipPath>
     */
    private val pathCache = LruCache<String, List<RelationshipPath>>(
        maxSize = 100,
        ttlMs = 10 * 60 * 1000,  // 10分钟过期
        name = "PathCache"
    )

    /**
     * 图查询结果缓存
     * Key: queryHash
     * Value: Any (generic result)
     */
    private val queryCache = LruCache<String, Any>(
        maxSize = 50,
        ttlMs = 3 * 60 * 1000,  // 3分钟过期
        name = "QueryCache"
    )

    // ==================== 缓存操作 ====================

    /**
     * 获取两人之间的关系（带缓存）
     */
    suspend fun getRelationBetween(personA: String, personB: String): RelationshipNetworkEntity? {
        val key = buildRelationKey(personA, personB)
        return relationCache.get(key)
    }

    /**
     * 获取某人的所有关系（带缓存）
     */
    suspend fun getPersonRelations(personName: String): List<RelationshipNetworkEntity> {
        return personRelationsCache.get(personName) ?: emptyList()
    }

    /**
     * 获取路径查询结果（带缓存）
     */
    fun getPathQueryResult(personA: String, personB: String): List<RelationshipPath>? {
        val key = "$personA->$personB"
        return pathCache.get(key)
    }

    /**
     * 缓存路径查询结果
     */
    fun cachePathQueryResult(personA: String, personB: String, paths: List<RelationshipPath>) {
        val key = "$personA->$personB"
        pathCache.put(key, paths)
    }

    /**
     * 获取通用查询结果（带缓存）
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getQueryResult(queryKey: String): T? {
        return queryCache.get(queryKey) as? T
    }

    /**
     * 缓存通用查询结果
     */
    fun cacheQueryResult(queryKey: String, result: Any) {
        queryCache.put(queryKey, result)
    }

    // ==================== 缓存失效 ====================

    /**
     * 使某个关系的缓存失效
     */
    fun invalidateRelation(personA: String, personB: String) {
        val key = buildRelationKey(personA, personB)
        relationCache.invalidate(key)

        // 同时使相关人物的关系列表缓存失效
        personRelationsCache.invalidate(personA)
        personRelationsCache.invalidate(personB)

        Timber.d("[RelationshipCache] 关系缓存已失效: $personA - $personB")
    }

    /**
     * 使某人的所有关系缓存失效
     */
    fun invalidatePersonRelations(personName: String) {
        personRelationsCache.invalidate(personName)
        Timber.d("[RelationshipCache] 人物关系缓存已失效: $personName")
    }

    /**
     * 使路径查询缓存失效
     */
    fun invalidatePathQuery(personA: String, personB: String) {
        val key = "$personA->$personB"
        pathCache.remove(key)

        // 反向路径也失效
        val reverseKey = "$personB->$personA"
        pathCache.remove(reverseKey)
    }

    /**
     * 清空所有缓存
     */
    fun invalidateAll() {
        relationCache.invalidateAll()
        personRelationsCache.invalidateAll()
        pathCache.clear()
        queryCache.clear()
        Timber.i("[RelationshipCache] 所有缓存已清空")
    }

    // ==================== 缓存统计 ====================

    /**
     * 打印缓存统计信息
     */
    fun logCacheStats() {
        Timber.i("[RelationshipCache] ==================== 缓存统计 ====================")
        relationCache.logStats()
        personRelationsCache.logStats()
        pathCache.logStats()
        queryCache.logStats()
    }

    /**
     * 获取汇总统计信息
     */
    fun getCombinedStats(): CombinedCacheStats {
        val relationStats = relationCache.getStats()
        val personStats = personRelationsCache.getStats()
        val pathStats = pathCache.getStats()
        val queryStats = queryCache.getStats()

        val totalHits = relationStats.hitCount + personStats.hitCount + pathStats.hitCount + queryStats.hitCount
        val totalMisses = relationStats.missCount + personStats.missCount + pathStats.missCount + queryStats.missCount
        val overallHitRate = if (totalHits + totalMisses > 0) {
            totalHits.toDouble() / (totalHits + totalMisses)
        } else {
            0.0
        }

        return CombinedCacheStats(
            totalSize = relationStats.size + personStats.size + pathStats.size + queryStats.size,
            totalMaxSize = relationStats.maxSize + personStats.maxSize + pathStats.maxSize + queryStats.maxSize,
            overallHitRate = overallHitRate,
            relationCacheHitRate = relationStats.hitRate,
            personCacheHitRate = personStats.hitRate,
            pathCacheHitRate = pathStats.hitRate,
            queryCacheHitRate = queryStats.hitRate
        )
    }

    // ==================== 缓存维护 ====================

    /**
     * 清理过期条目
     */
    fun cleanupExpired(): Int {
        var totalCleaned = 0
        totalCleaned += pathCache.cleanupExpired()
        totalCleaned += queryCache.cleanupExpired()

        if (totalCleaned > 0) {
            Timber.d("[RelationshipCache] 清理了${totalCleaned}个过期条目")
        }

        return totalCleaned
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建关系缓存key（规范化）
     */
    private fun buildRelationKey(personA: String, personB: String): String {
        val (normalizedA, normalizedB) = RelationshipNetworkEntity.normalize(personA, personB)
        return "$normalizedA|$normalizedB"
    }

    /**
     * 汇总缓存统计
     */
    data class CombinedCacheStats(
        val totalSize: Int,
        val totalMaxSize: Int,
        val overallHitRate: Double,
        val relationCacheHitRate: Double,
        val personCacheHitRate: Double,
        val pathCacheHitRate: Double,
        val queryCacheHitRate: Double
    )
}
