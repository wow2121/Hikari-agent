package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.EmbeddingRequest
import com.xiaoguang.assistant.domain.model.BatchEmbeddingResult
import com.xiaoguang.assistant.domain.model.TextEmbedding
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生成文本Embedding的用例
 * 支持单个文本和批量文本的向量化
 */
@Singleton
class GenerateEmbeddingUseCase @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val appPreferences: AppPreferences
) {

    /**
     * Embedding缓存（LRU策略）
     * 限制最大缓存500条，避免内存泄漏
     */
    private val embeddingCache = object : LinkedHashMap<String, TextEmbedding>(
        100,        // 初始容量
        0.75f,      // 负载因子
        true        // accessOrder = true，按访问顺序排序（LRU）
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, TextEmbedding>): Boolean {
            return size > 500  // 超过500条时移除最旧的条目
        }
    }

    /**
     * 生成单个文本的embedding
     */
    suspend fun generateEmbedding(
        text: String,
        useCache: Boolean = true
    ): Result<TextEmbedding> {
        try {
            // 检查缓存
            if (useCache && embeddingCache.containsKey(text)) {
                Timber.d("从缓存返回embedding: ${text.take(50)}")
                return Result.success(embeddingCache[text]!!)
            }

            // 获取配置
            val useLocalModel = appPreferences.useLocalEmbedding.first()
            if (useLocalModel) {
                // TODO: 实现本地模型支持(Sprint 6)
                Timber.w("本地embedding模型尚未实现,切换到在线API")
            }

            val model = appPreferences.embeddingModel.first()
            val dimension = appPreferences.embeddingDimension.first()
            val apiKey = BuildConfig.SILICON_FLOW_API_KEY

            if (apiKey.isBlank()) {
                return Result.failure(IllegalStateException("API Key未配置"))
            }

            // 调用API
            val request = EmbeddingRequest(
                model = model,
                input = text,
                dimension = dimension
            )

            val response = siliconFlowAPI.createEmbeddings(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Timber.e("Embedding API失败: ${response.code()} - $errorBody")
                return Result.failure(
                    Exception("Embedding生成失败: ${response.code()} - ${response.message()}")
                )
            }

            val embeddingResponse = response.body()
                ?: return Result.failure(Exception("Embedding响应为空"))

            if (embeddingResponse.data.isEmpty()) {
                return Result.failure(Exception("Embedding数据为空"))
            }

            val embeddingData = embeddingResponse.data[0]
            val textEmbedding = TextEmbedding(
                text = text,
                vector = embeddingData.embedding,
                dimension = embeddingData.embedding.size,
                model = embeddingResponse.model
            )

            // 存入缓存
            if (useCache) {
                embeddingCache[text] = textEmbedding
            }

            Timber.d("成功生成embedding: ${text.take(50)}, 维度: ${embeddingData.embedding.size}")
            return Result.success(textEmbedding)

        } catch (e: Exception) {
            Timber.e(e, "生成embedding异常: ${text.take(50)}")
            return Result.failure(e)
        }
    }

    /**
     * 批量生成多个文本的embedding
     * 支持自动分批,避免单次请求过大
     */
    suspend fun generateBatchEmbeddings(
        texts: List<String>,
        batchSize: Int = 20,
        useCache: Boolean = true
    ): Result<BatchEmbeddingResult> {
        try {
            if (texts.isEmpty()) {
                return Result.success(BatchEmbeddingResult(emptyList(), 0))
            }

            val allEmbeddings = mutableListOf<TextEmbedding>()
            var totalTokens = 0

            // 分批处理
            texts.chunked(batchSize).forEach { batch ->
                // 过滤已缓存的文本
                val uncachedTexts = if (useCache) {
                    batch.filter { !embeddingCache.containsKey(it) }
                } else {
                    batch
                }

                // 添加缓存的embedding
                if (useCache) {
                    batch.filter { embeddingCache.containsKey(it) }
                        .forEach { allEmbeddings.add(embeddingCache[it]!!) }
                }

                if (uncachedTexts.isEmpty()) {
                    Timber.d("批次中所有文本都已缓存")
                    return@forEach
                }

                // 获取配置
                val model = appPreferences.embeddingModel.first()
                val dimension = appPreferences.embeddingDimension.first()
                val apiKey = BuildConfig.SILICON_FLOW_API_KEY

                if (apiKey.isBlank()) {
                    return Result.failure(IllegalStateException("API Key未配置"))
                }

                // 批量调用API
                val request = EmbeddingRequest(
                    model = model,
                    input = uncachedTexts,
                    dimension = dimension
                )

                val response = siliconFlowAPI.createEmbeddings(
                    authorization = "Bearer $apiKey",
                    request = request
                )

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    Timber.e("批量Embedding API失败: ${response.code()} - $errorBody")
                    return Result.failure(
                        Exception("批量Embedding生成失败: ${response.code()}")
                    )
                }

                val embeddingResponse = response.body()
                    ?: return Result.failure(Exception("Embedding响应为空"))

                // 处理结果
                embeddingResponse.data.forEachIndexed { index, embeddingData ->
                    val textEmbedding = TextEmbedding(
                        text = uncachedTexts[index],
                        vector = embeddingData.embedding,
                        dimension = embeddingData.embedding.size,
                        model = embeddingResponse.model
                    )
                    allEmbeddings.add(textEmbedding)

                    // 存入缓存
                    if (useCache) {
                        embeddingCache[uncachedTexts[index]] = textEmbedding
                    }
                }

                totalTokens += embeddingResponse.usage.totalTokens
                Timber.d("批量生成embedding成功: ${uncachedTexts.size}个文本")
            }

            return Result.success(
                BatchEmbeddingResult(
                    embeddings = allEmbeddings,
                    totalTokens = totalTokens
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "批量生成embedding异常")
            return Result.failure(e)
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        embeddingCache.clear()
        Timber.d("Embedding缓存已清除")
    }

    /**
     * 获取缓存统计
     */
    fun getCacheStats(): Pair<Int, Long> {
        val count = embeddingCache.size
        val memoryBytes = embeddingCache.values.sumOf { it.vector.size * 4L } // 每个float 4字节
        return count to memoryBytes
    }
}
