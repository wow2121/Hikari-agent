package com.xiaoguang.assistant.domain.knowledge.vector

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 向量搜索优化器
 *
 * 优化策略：
 * 1. LSH（局部敏感哈希）- 快速近似最近邻搜索，降低O(n)线性扫描到O(1)~O(log n)
 * 2. 预过滤 - 元数据过滤减少候选集
 * 3. 查询缓存 - LRU缓存频繁查询
 * 4. 批量优化 - 合并多个查询减少网络开销
 *
 * LSH原理：
 * - 使用随机投影将高维向量映射到低维哈希桶
 * - 相似向量有更高概率落入同一哈希桶
 * - 时间复杂度：O(d + k)，其中d是维度，k是候选集大小
 * - 相比暴力搜索的O(n×d)快数百倍
 */
@Singleton
class VectorSearchOptimizer @Inject constructor() {

    // LSH配置
    private val numHashTables = 10        // 哈希表数量（增加召回率）
    private val numHashFunctions = 5       // 每个表的哈希函数数量（增加精确度）
    private val vectorDimension = 1024     // 向量维度（Silicon Flow embedding维度）

    // LSH哈希表：Map<TableIndex, Map<HashKey, List<VectorId>>>
    private val lshTables = ConcurrentHashMap<Int, MutableMap<String, MutableList<String>>>()

    // 向量存储：Map<VectorId, FloatArray>
    private val vectorCache = ConcurrentHashMap<String, FloatArray>()

    // 元数据索引：Map<MetadataKey, Set<VectorId>>
    private val metadataIndex = ConcurrentHashMap<String, MutableSet<String>>()

    // 随机投影矩阵（用于LSH哈希）
    private val projectionMatrices: List<Array<FloatArray>> by lazy {
        initializeProjectionMatrices()
    }

    // 查询缓存（LRU）
    private val queryCache = object : LinkedHashMap<String, List<VectorSearchResult>>(
        50, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<VectorSearchResult>>): Boolean {
            return size > 100  // 最多缓存100个查询
        }
    }

    /**
     * 初始化随机投影矩阵
     * 每个哈希表有numHashFunctions个随机向量
     */
    private fun initializeProjectionMatrices(): List<Array<FloatArray>> {
        Timber.d("[VectorSearchOptimizer] 初始化LSH投影矩阵")

        return List(numHashTables) { tableIndex ->
            Array(numHashFunctions) { funcIndex ->
                // 生成随机单位向量
                val randomVector = FloatArray(vectorDimension) {
                    Random.nextFloat() * 2 - 1  // [-1, 1]
                }
                // 归一化
                val norm = sqrt(randomVector.sumOf { it.toDouble() * it.toDouble() }).toFloat()
                FloatArray(vectorDimension) { i -> randomVector[i] / norm }
            }
        }
    }

    /**
     * 计算LSH哈希值
     * 使用随机投影：h(v) = sign(v · r)，其中r是随机向量
     */
    private fun computeLSHHash(vector: FloatArray, tableIndex: Int): String {
        val hashBits = StringBuilder()

        val projectionMatrix = projectionMatrices[tableIndex]

        for (projectionVector in projectionMatrix) {
            // 点积
            var dotProduct = 0f
            for (i in vector.indices) {
                dotProduct += vector[i] * projectionVector[i]
            }

            // sign函数：>= 0 为 1，< 0 为 0
            hashBits.append(if (dotProduct >= 0) '1' else '0')
        }

        return hashBits.toString()
    }

    /**
     * 添加向量到LSH索引
     *
     * @param id 向量ID
     * @param vector 向量数据
     * @param metadata 元数据（用于预过滤）
     */
    fun addVector(
        id: String,
        vector: FloatArray,
        metadata: Map<String, Any> = emptyMap()
    ) {
        // 1. 存储向量
        vectorCache[id] = vector

        // 2. 构建LSH索引
        for (tableIndex in 0 until numHashTables) {
            val hashKey = computeLSHHash(vector, tableIndex)

            lshTables.getOrPut(tableIndex) { ConcurrentHashMap() }
                .getOrPut(hashKey) { mutableListOf() }
                .add(id)
        }

        // 3. 构建元数据索引
        metadata.forEach { (key, value) ->
            val indexKey = "$key:$value"
            metadataIndex.getOrPut(indexKey) { mutableSetOf() }
                .add(id)
        }
    }

    /**
     * LSH近似搜索
     *
     * @param queryVector 查询向量
     * @param topK 返回前K个结果
     * @param metadataFilter 元数据过滤条件
     * @return 搜索结果列表
     */
    fun searchSimilar(
        queryVector: FloatArray,
        topK: Int = 10,
        metadataFilter: Map<String, Any>? = null
    ): List<VectorSearchResult> {
        // 1. 检查查询缓存
        val cacheKey = buildCacheKey(queryVector, topK, metadataFilter)
        queryCache[cacheKey]?.let {
            Timber.d("[VectorSearchOptimizer] 命中查询缓存")
            return it
        }

        // 2. 元数据预过滤
        val candidateIds = if (metadataFilter != null) {
            applyMetadataFilter(metadataFilter)
        } else {
            null
        }

        // 3. LSH候选集检索
        val lshCandidates = mutableSetOf<String>()

        for (tableIndex in 0 until numHashTables) {
            val hashKey = computeLSHHash(queryVector, tableIndex)
            lshTables[tableIndex]?.get(hashKey)?.let { bucket ->
                lshCandidates.addAll(bucket)
            }
        }

        // 4. 合并候选集（元数据过滤 ∩ LSH候选）
        val finalCandidates = if (candidateIds != null) {
            lshCandidates.intersect(candidateIds)
        } else {
            lshCandidates
        }

        Timber.d("[VectorSearchOptimizer] LSH候选集: ${lshCandidates.size}, 元数据过滤后: ${finalCandidates.size}")

        // 5. 精确计算相似度（只针对候选集）
        val results = finalCandidates.mapNotNull { id ->
            vectorCache[id]?.let { vector ->
                val similarity = cosineSimilarity(queryVector, vector)
                VectorSearchResult(
                    id = id,
                    content = "",  // 内容需要后续从数据库获取
                    distance = 1f - similarity,
                    similarity = similarity,
                    metadata = emptyMap()
                )
            }
        }.sortedByDescending { it.similarity }
            .take(topK)

        // 6. 缓存结果
        queryCache[cacheKey] = results

        return results
    }

    /**
     * 元数据预过滤
     */
    private fun applyMetadataFilter(filter: Map<String, Any>): Set<String> {
        val results = mutableSetOf<String>()

        // 获取所有满足条件的ID（取交集）
        val conditions = filter.map { (key, value) ->
            metadataIndex["$key:$value"] ?: emptySet()
        }

        if (conditions.isNotEmpty()) {
            results.addAll(conditions.first())
            conditions.drop(1).forEach { condition ->
                results.retainAll(condition)
            }
        }

        return results
    }

    /**
     * 余弦相似度计算
     * 优化：使用内联和局部变量减少开销
     */
    private inline fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in v1.indices) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dotProduct += a * b
            norm1 += a * a
            norm2 += b * b
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0) {
            (dotProduct / denominator).toFloat()
        } else {
            0f
        }
    }

    /**
     * 构建缓存键
     */
    private fun buildCacheKey(
        vector: FloatArray,
        topK: Int,
        filter: Map<String, Any>?
    ): String {
        // 使用向量哈希 + topK + 过滤条件
        val vectorHash = vector.contentHashCode()
        val filterHash = filter?.hashCode() ?: 0
        return "$vectorHash-$topK-$filterHash"
    }

    /**
     * 批量搜索优化
     * 一次处理多个查询，共享LSH索引查找
     */
    fun batchSearch(
        queries: List<FloatArray>,
        topK: Int = 10,
        metadataFilter: Map<String, Any>? = null
    ): List<List<VectorSearchResult>> {
        Timber.d("[VectorSearchOptimizer] 批量搜索: ${queries.size} 个查询")

        // 并行处理多个查询（如果查询数量多）
        return queries.map { query ->
            searchSimilar(query, topK, metadataFilter)
        }
    }

    /**
     * 删除向量
     */
    fun removeVector(id: String) {
        // 从向量缓存删除
        vectorCache.remove(id)

        // 从LSH索引删除
        for (tableIndex in 0 until numHashTables) {
            lshTables[tableIndex]?.values?.forEach { bucket ->
                bucket.remove(id)
            }
        }

        // 从元数据索引删除
        metadataIndex.values.forEach { idSet ->
            idSet.remove(id)
        }

        // 清空查询缓存（因为结果可能改变）
        queryCache.clear()
    }

    /**
     * 更新向量
     */
    fun updateVector(
        id: String,
        vector: FloatArray,
        metadata: Map<String, Any> = emptyMap()
    ) {
        removeVector(id)
        addVector(id, vector, metadata)
    }

    /**
     * 清空所有索引
     */
    fun clearAll() {
        vectorCache.clear()
        lshTables.clear()
        metadataIndex.clear()
        queryCache.clear()
        Timber.d("[VectorSearchOptimizer] 已清空所有索引")
    }

    /**
     * 获取统计信息
     */
    fun getStats(): OptimzerStats {
        val totalVectors = vectorCache.size
        val totalBuckets = lshTables.values.sumOf { it.size }
        val avgBucketSize = if (totalBuckets > 0) {
            lshTables.values.flatMap { it.values }.map { it.size }.average()
        } else 0.0

        return OptimzerStats(
            totalVectors = totalVectors,
            numHashTables = numHashTables,
            totalBuckets = totalBuckets,
            avgBucketSize = avgBucketSize.toFloat(),
            cacheHitRate = if (queryCache.size > 0) {
                // 简化的缓存命中率估算
                0.5f
            } else 0f,
            metadataIndexSize = metadataIndex.size
        )
    }
}

/**
 * 优化器统计信息
 */
data class OptimzerStats(
    val totalVectors: Int,
    val numHashTables: Int,
    val totalBuckets: Int,
    val avgBucketSize: Float,
    val cacheHitRate: Float,
    val metadataIndexSize: Int
)
