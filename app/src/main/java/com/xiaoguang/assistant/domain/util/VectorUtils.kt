package com.xiaoguang.assistant.domain.util

import kotlin.math.sqrt

/**
 * 向量计算工具类
 */
object VectorUtils {

    /**
     * 计算两个向量的余弦相似度
     * 范围: [-1, 1]，值越大越相似
     * 对于归一化向量，余弦相似度等于点积
     *
     * @param vec1 第一个向量
     * @param vec2 第二个向量
     * @return 余弦相似度值，如果向量维度不匹配或为空返回null
     */
    fun cosineSimilarity(vec1: List<Float>, vec2: List<Float>): Float? {
        if (vec1.isEmpty() || vec2.isEmpty() || vec1.size != vec2.size) {
            return null
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            val v1 = vec1[i].toDouble()
            val v2 = vec2[i].toDouble()
            dotProduct += v1 * v2
            norm1 += v1 * v1
            norm2 += v2 * v2
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        if (denominator == 0.0) {
            return null
        }

        return (dotProduct / denominator).toFloat()
    }

    /**
     * 计算欧氏距离
     * 距离越小越相似
     *
     * @param vec1 第一个向量
     * @param vec2 第二个向量
     * @return 欧氏距离，如果向量维度不匹配或为空返回null
     */
    fun euclideanDistance(vec1: List<Float>, vec2: List<Float>): Float? {
        if (vec1.isEmpty() || vec2.isEmpty() || vec1.size != vec2.size) {
            return null
        }

        var sumSquaredDiff = 0.0
        for (i in vec1.indices) {
            val diff = vec1[i] - vec2[i]
            sumSquaredDiff += diff * diff
        }

        return sqrt(sumSquaredDiff).toFloat()
    }

    /**
     * 向量归一化(L2范数)
     *
     * @param vector 输入向量
     * @return 归一化后的向量
     */
    fun normalize(vector: List<Float>): List<Float> {
        if (vector.isEmpty()) return emptyList()

        var norm = 0.0
        for (v in vector) {
            norm += v * v
        }
        norm = sqrt(norm)

        if (norm == 0.0) return vector

        return vector.map { (it / norm).toFloat() }
    }

    /**
     * 批量计算查询向量与候选向量的相似度
     *
     * @param queryVector 查询向量
     * @param candidateVectors 候选向量列表
     * @return 相似度分数列表(与候选向量列表对应)
     */
    fun batchCosineSimilarity(
        queryVector: List<Float>,
        candidateVectors: List<List<Float>>
    ): List<Float> {
        return candidateVectors.map { candidate ->
            cosineSimilarity(queryVector, candidate) ?: 0f
        }
    }

    /**
     * 找出最相似的K个向量
     *
     * @param queryVector 查询向量
     * @param candidateVectors 候选向量列表
     * @param k 返回的数量
     * @return 最相似的K个向量的索引及其相似度分数
     */
    fun findTopKSimilar(
        queryVector: List<Float>,
        candidateVectors: List<List<Float>>,
        k: Int
    ): List<Pair<Int, Float>> {
        val similarities = candidateVectors.mapIndexed { index, candidate ->
            val similarity = cosineSimilarity(queryVector, candidate) ?: 0f
            index to similarity
        }

        return similarities
            .sortedByDescending { it.second }
            .take(k.coerceAtMost(similarities.size))
    }

    /**
     * 向量加法
     */
    fun add(vec1: List<Float>, vec2: List<Float>): List<Float>? {
        if (vec1.size != vec2.size) return null
        return vec1.mapIndexed { index, v -> v + vec2[index] }
    }

    /**
     * 向量减法
     */
    fun subtract(vec1: List<Float>, vec2: List<Float>): List<Float>? {
        if (vec1.size != vec2.size) return null
        return vec1.mapIndexed { index, v -> v - vec2[index] }
    }

    /**
     * 标量乘法
     */
    fun scale(vector: List<Float>, scalar: Float): List<Float> {
        return vector.map { it * scalar }
    }

    /**
     * 计算向量的L2范数
     */
    fun l2Norm(vector: List<Float>): Float {
        var sum = 0.0
        for (v in vector) {
            sum += v * v
        }
        return sqrt(sum).toFloat()
    }

    /**
     * 点积
     */
    fun dotProduct(vec1: List<Float>, vec2: List<Float>): Float? {
        if (vec1.size != vec2.size) return null
        var sum = 0.0
        for (i in vec1.indices) {
            sum += vec1[i] * vec2[i]
        }
        return sum.toFloat()
    }

    /**
     * 向量维度检查
     */
    fun isDimensionCompatible(vectors: List<List<Float>>): Boolean {
        if (vectors.isEmpty()) return true
        val dimension = vectors[0].size
        return vectors.all { it.size == dimension }
    }

    /**
     * 计算向量集合的质心(平均向量)
     */
    fun centroid(vectors: List<List<Float>>): List<Float>? {
        if (vectors.isEmpty()) return null
        if (!isDimensionCompatible(vectors)) return null

        val dimension = vectors[0].size
        val sum = FloatArray(dimension) { 0f }

        for (vector in vectors) {
            for (i in vector.indices) {
                sum[i] += vector[i]
            }
        }

        val count = vectors.size.toFloat()
        return sum.map { it / count }
    }
}
