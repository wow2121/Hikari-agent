package com.xiaoguang.assistant.domain.knowledge.vector

import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.EmbeddingRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 向量嵌入生成服务
 * 使用 SiliconFlow 的 Qwen Embedding 模型生成文本向量
 */
@Singleton
class VectorEmbeddingService @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI
) {

    /**
     * 从BuildConfig读取API Key
     * 确保在local.properties中配置了SILICON_FLOW_API_KEY
     */
    private val apiKey: String
        get() {
            val key = BuildConfig.SILICON_FLOW_API_KEY
            if (key.isBlank()) {
                Timber.e("[VectorEmbedding] API Key未配置，请在local.properties中设置SILICON_FLOW_API_KEY")
                throw IllegalStateException("Silicon Flow API Key未配置")
            }
            return key
        }

    /**
     * 生成单个文本的向量嵌入
     *
     * @param text 要嵌入的文本
     * @param model 使用的模型，默认使用 0.6B 版本（1024维）
     * @return 向量嵌入（FloatArray）
     */
    suspend fun generateEmbedding(
        text: String,
        model: String = SiliconFlowAPI.EMBEDDING_MODEL_0_6B
    ): Result<FloatArray> {
        return try {
            val request = EmbeddingRequest(
                model = model,
                input = text,
                encodingFormat = "float"
            )

            val response = siliconFlowAPI.createEmbeddings(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val embedding = response.body()!!.data.firstOrNull()?.embedding
                if (embedding != null) {
                    Result.success(embedding.toFloatArray())
                } else {
                    Result.failure(Exception("未返回嵌入向量"))
                }
            } else {
                Result.failure(Exception("API调用失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "[VectorEmbedding] 生成嵌入失败")
            Result.failure(e)
        }
    }

    /**
     * 批量生成文本的向量嵌入
     *
     * @param texts 要嵌入的文本列表
     * @param model 使用的模型
     * @return 向量嵌入列表
     */
    suspend fun generateEmbeddings(
        texts: List<String>,
        model: String = SiliconFlowAPI.EMBEDDING_MODEL_0_6B
    ): Result<List<FloatArray>> {
        if (texts.isEmpty()) {
            return Result.success(emptyList())
        }

        return try {
            // 分批处理（避免单次请求过大）
            val batchSize = 10
            val allEmbeddings = mutableListOf<FloatArray>()

            texts.chunked(batchSize).forEach { batch ->
                // 对于批量，需要多次调用单个请求（SiliconFlow API可能不支持批量）
                // 或者根据API文档调整
                batch.forEach { text ->
                    val result = generateEmbedding(text, model)
                    if (result.isSuccess) {
                        allEmbeddings.add(result.getOrThrow())
                    } else {
                        return Result.failure(result.exceptionOrNull() ?: Exception("生成嵌入失败"))
                    }
                }
            }

            Result.success(allEmbeddings)
        } catch (e: Exception) {
            Timber.e(e, "[VectorEmbedding] 批量生成嵌入失败")
            Result.failure(e)
        }
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vec1 向量1
     * @param vec2 向量2
     * @return 相似度 [-1, 1]，值越大越相似
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        require(vec1.size == vec2.size) { "向量维度不匹配" }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
        return if (denominator > 0f) {
            dotProduct / denominator
        } else {
            0f
        }
    }

    /**
     * 获取推荐的嵌入维度
     */
    fun getEmbeddingDimension(model: String = SiliconFlowAPI.EMBEDDING_MODEL_0_6B): Int {
        return when (model) {
            SiliconFlowAPI.EMBEDDING_MODEL_0_6B -> 1024
            SiliconFlowAPI.EMBEDDING_MODEL_4B -> 2560
            SiliconFlowAPI.EMBEDDING_MODEL_8B -> 4096
            else -> 1024  // 默认
        }
    }
}
