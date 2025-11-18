package com.xiaoguang.assistant.domain.voiceprint

import com.xiaoguang.assistant.data.remote.api.ChromaAPI
import com.xiaoguang.assistant.data.remote.dto.*
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * 声纹管理器
 *
 * 职责：
 * - 声纹特征提取（从音频样本）
 * - 声纹注册和存储（ChromaDB）
 * - 声纹识别和匹配
 * - 声纹档案管理（CRUD）
 * - 与IdentityRegistry集成
 *
 * 特性：
 * - 支持多样本平均提高准确率
 * - 自动陌生人检测
 * - 主人声纹特殊保护
 * - 向量相似度匹配
 */
@Singleton
class VoiceprintManager @Inject constructor(
    private val chromaAPI: ChromaAPI,
    private val featureExtractor: VoiceprintFeatureExtractor
) {

    companion object {
        private const val COLLECTION_NAME = "voiceprints"
        private const val SIMILARITY_THRESHOLD = 0.75f  // 相似度阈值
        private const val MIN_SAMPLES = 3                // 最少样本数
        private const val MAX_RESULTS = 5                // 最多返回结果数
    }

    /**
     * 初始化声纹集合
     */
    suspend fun initialize(): Result<Unit> {
        return try {
            val request = ChromaCreateCollectionRequest(
                name = COLLECTION_NAME,
                metadata = mapOf(
                    "description" to "Voiceprint profiles for speaker identification",
                    "type" to "voiceprint",
                    "features" to "similarity_search,speaker_identification"
                ),
                getOrCreate = true
            )

            chromaAPI.createCollection(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                request = request
            )
            Timber.i("[VoiceprintManager] 声纹集合初始化完成")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 初始化失败")
            Result.failure(e)
        }
    }

    /**
     * 注册新声纹
     *
     * @param request 注册请求，包含音频样本和人物信息
     * @return 注册成功的声纹档案
     */
    suspend fun registerVoiceprint(request: VoiceprintRegistrationRequest): Result<VoiceprintProfile> {
        return try {
            // 1. 验证样本数量
            if (request.audioSamples.size < MIN_SAMPLES) {
                return Result.failure(IllegalArgumentException(
                    "需要至少 $MIN_SAMPLES 个音频样本，当前只有 ${request.audioSamples.size} 个"
                ))
            }

            // 2. 提取每个样本的特征向量
            val features = request.audioSamples.map { audioData ->
                featureExtractor.extractFeature(audioData, request.sampleRate)
            }

            // 3. 计算平均特征向量（提高鲁棒性）
            val avgFeature = averageVectors(features)

            // 4. 计算置信度（基于样本一致性）
            val confidence = calculateConfidence(features)

            // 5. 创建声纹档案
            val voiceprintId = UUID.randomUUID().toString()
            val personId = request.personId ?: "person_${UUID.randomUUID()}"
            val displayName = request.personName ?: generateStrangerName()

            val profile = VoiceprintProfile(
                voiceprintId = voiceprintId,
                personId = personId,
                personName = request.personName,
                displayName = displayName,
                isMaster = request.isMaster,
                isStranger = request.personName.isNullOrBlank(),
                featureVector = avgFeature,
                sampleCount = request.audioSamples.size,
                confidence = confidence,
                metadata = request.metadata
            )

            // 6. 存储到ChromaDB
            saveToChroma(profile)

            Timber.i("[VoiceprintManager] 声纹注册成功: ${profile.displayName} (置信度: $confidence)")
            Result.success(profile)

        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 声纹注册失败")
            Result.failure(e)
        }
    }

    /**
     * 识别说话人
     *
     * @param audioData PCM音频数据
     * @param sampleRate 采样率
     * @return 识别结果
     */
    suspend fun identifySpeaker(
        audioData: ByteArray,
        sampleRate: Int = 16000
    ): Result<VoiceprintIdentificationResult> {
        return try {
            // 1. 提取特征
            val queryFeature = featureExtractor.extractFeature(audioData, sampleRate)

            // 2. 在ChromaDB中查询最相似的声纹
            val queryResult = queryChroma(queryFeature, nResults = MAX_RESULTS)

            // 3. 解析结果
            if (queryResult.isEmpty()) {
                // 未匹配到任何声纹
                val tempSpeakerId = "stranger_${System.currentTimeMillis()}"
                return Result.success(VoiceprintIdentificationResult(
                    matched = false,
                    profile = null,
                    similarity = 0f,
                    confidence = 0f,
                    speakerId = tempSpeakerId
                ))
            }

            // 4. 获取最相似的结果
            val topResult = queryResult.first()
            val similarity = topResult.distance?.let { 1f - it } ?: 0f

            // 5. 判断是否超过阈值
            if (similarity >= SIMILARITY_THRESHOLD) {
                // 匹配成功
                val profile = parseProfileFromMetadata(topResult)
                Result.success(VoiceprintIdentificationResult(
                    matched = true,
                    profile = profile,
                    similarity = similarity,
                    confidence = similarity,
                    speakerId = profile.personId
                ))
            } else {
                // 相似度不足，视为陌生人
                val tempSpeakerId = "stranger_${System.currentTimeMillis()}"
                Result.success(VoiceprintIdentificationResult(
                    matched = false,
                    profile = null,
                    similarity = similarity,
                    confidence = 0f,
                    speakerId = tempSpeakerId
                ))
            }

        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 说话人识别失败")
            Result.failure(e)
        }
    }

    /**
     * 获取主人声纹档案
     */
    suspend fun getMasterProfile(): VoiceprintProfile? {
        return try {
            // 获取集合UUID
            val collectionUuid = getCollectionId(COLLECTION_NAME)
            if (collectionUuid == null) {
                Timber.w("[VoiceprintManager] 集合不存在: $COLLECTION_NAME")
                return null
            }

            val queryRequest = ChromaQueryRequest(
                queryEmbeddings = listOf(List(128) { 0f }),  // 空查询向量
                nResults = 100,
                where = mapOf("isMaster" to true),
                include = listOf("metadatas", "embeddings")  // ✅ 包含 embeddings
            )

            val response = chromaAPI.query(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,  // 使用 UUID
                request = queryRequest
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.ids?.firstOrNull()?.isNotEmpty() == true) {
                    val metadata = body.metadatas?.firstOrNull()?.firstOrNull()
                    metadata?.let { parseProfileFromMetadata(QueryResultItem(
                        id = body.ids.first().first(),
                        embedding = body.embeddings?.firstOrNull()?.firstOrNull(),  // ✅ 恢复使用 embeddings
                        metadata = it,
                        distance = null
                    )) }
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 获取主人档案失败")
            null
        }
    }

    /**
     * 更新声纹名称（陌生人被识别后）
     */
    suspend fun updatePersonName(
        voiceprintId: String,
        newName: String
    ): Result<Unit> {
        return try {
            // 1. 获取原档案
            val profile = getProfileById(voiceprintId)
                ?: return Result.failure(IllegalArgumentException("声纹ID不存在: $voiceprintId"))

            // 2. 更新档案
            val updatedProfile = profile.copy(
                personName = newName,
                displayName = newName,
                isStranger = false,
                updatedAt = System.currentTimeMillis()
            )

            // 3. 更新到ChromaDB
            updateInChroma(updatedProfile)

            Timber.i("[VoiceprintManager] 声纹名称已更新: $voiceprintId -> $newName")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 更新声纹名称失败")
            Result.failure(e)
        }
    }

    /**
     * 删除声纹档案
     */
    suspend fun deleteVoiceprint(voiceprintId: String): Result<Unit> {
        return try {
            // 获取集合UUID
            val collectionUuid = getCollectionId(COLLECTION_NAME)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: $COLLECTION_NAME"))
            }

            val request = ChromaDeleteDocumentsRequest(ids = listOf(voiceprintId))
            chromaAPI.deleteDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,  // 使用 UUID
                request = request
            )

            Timber.i("[VoiceprintManager] 声纹已删除: $voiceprintId")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 删除声纹失败")
            Result.failure(e)
        }
    }

    /**
     * 获取所有声纹档案
     */
    suspend fun getAllProfiles(): List<VoiceprintProfile> {
        return try {
            // 获取集合UUID
            val collectionUuid = getCollectionId(COLLECTION_NAME)
            if (collectionUuid == null) {
                Timber.w("[VoiceprintManager] 集合不存在: $COLLECTION_NAME")
                return emptyList()
            }

            val queryRequest = ChromaQueryRequest(
                queryEmbeddings = listOf(List(128) { 0f }),  // 空查询向量
                nResults = 1000,
                include = listOf("metadatas", "embeddings")  // ✅ 包含 embeddings
            )

            val response = chromaAPI.query(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,  // 使用 UUID
                request = queryRequest
            )

            if (response.isSuccessful) {
                val body = response.body()
                body?.ids?.firstOrNull()?.mapIndexedNotNull { index, id ->
                    val metadata = body.metadatas?.firstOrNull()?.getOrNull(index)
                    val embedding = body.embeddings?.firstOrNull()?.getOrNull(index)  // ✅ 恢复使用 embeddings

                    metadata?.let {
                        parseProfileFromMetadata(QueryResultItem(
                            id = id,
                            embedding = embedding,
                            metadata = it,
                            distance = null
                        ))
                    }
                } ?: emptyList()
            } else {
                emptyList()
            }

        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 获取所有档案失败")
            emptyList()
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 获取集合ID（通过名称）
     * ChromaDB v2 API: POST 操作需要使用 UUID，GET 可以使用名称
     */
    private suspend fun getCollectionId(collectionName: String): String? {
        return try {
            val response = chromaAPI.getCollection(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionName  // GET 支持使用名称
            )
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.id
            } else {
                Timber.w("[VoiceprintManager] 获取集合ID失败: $collectionName - ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "[VoiceprintManager] 获取集合ID异常: $collectionName")
            null
        }
    }

    /**
     * 保存到ChromaDB
     */
    private suspend fun saveToChroma(profile: VoiceprintProfile) {
        // 获取集合UUID
        val collectionUuid = getCollectionId(COLLECTION_NAME)
            ?: throw Exception("集合不存在: $COLLECTION_NAME")

        val request = ChromaAddDocumentsRequest(
            ids = listOf(profile.voiceprintId),
            embeddings = listOf(profile.featureVector.toList()),
            documents = listOf(profile.displayName),  // 使用 displayName 作为文档内容
            metadatas = listOf(mapOf(
                "personId" to profile.personId,
                "personName" to (profile.personName ?: ""),
                "displayName" to profile.displayName,
                "isMaster" to profile.isMaster.toString(),
                "isStranger" to profile.isStranger.toString(),
                "sampleCount" to profile.sampleCount.toString(),
                "confidence" to profile.confidence.toString(),
                "createdAt" to profile.createdAt.toString(),
                "updatedAt" to profile.updatedAt.toString()
            ) + profile.metadata)
        )

        chromaAPI.addDocuments(
            tenant = ChromaAPI.DEFAULT_TENANT,
            database = ChromaAPI.DEFAULT_DATABASE,
            collectionId = collectionUuid,  // 使用 UUID
            request = request
        )
    }

    /**
     * 在ChromaDB中查询
     */
    private suspend fun queryChroma(
        queryVector: FloatArray,
        nResults: Int
    ): List<QueryResultItem> {
        // 获取集合UUID
        val collectionUuid = getCollectionId(COLLECTION_NAME)
        if (collectionUuid == null) {
            Timber.w("[VoiceprintManager] 集合不存在: $COLLECTION_NAME")
            return emptyList()
        }

        val request = ChromaQueryRequest(
            queryEmbeddings = listOf(queryVector.toList()),
            nResults = nResults,
            include = listOf("metadatas", "embeddings", "distances")  // ✅ 包含所有字段
        )

        val response = chromaAPI.query(
            tenant = ChromaAPI.DEFAULT_TENANT,
            database = ChromaAPI.DEFAULT_DATABASE,
            collectionId = collectionUuid,  // 使用 UUID
            request = request
        )

        // 解析结果
        val results = mutableListOf<QueryResultItem>()
        val body = response.body()
        body?.ids?.firstOrNull()?.forEachIndexed { index, id ->
            val metadata = body.metadatas?.firstOrNull()?.getOrNull(index)
            val embedding = body.embeddings?.firstOrNull()?.getOrNull(index)  // ✅ 恢复使用 embeddings
            val distance = body.distances?.firstOrNull()?.getOrNull(index)

            if (metadata != null) {
                results.add(QueryResultItem(
                    id = id,
                    embedding = embedding,
                    metadata = metadata,
                    distance = distance
                ))
            }
        }

        return results
    }

    /**
     * 更新ChromaDB中的数据
     */
    private suspend fun updateInChroma(profile: VoiceprintProfile) {
        // ChromaDB的更新策略：先删除再添加
        deleteVoiceprint(profile.voiceprintId)
        saveToChroma(profile)
    }

    /**
     * 根据ID获取档案
     */
    private suspend fun getProfileById(voiceprintId: String): VoiceprintProfile? {
        return try {
            // 获取集合UUID
            val collectionUuid = getCollectionId(COLLECTION_NAME)
            if (collectionUuid == null) {
                Timber.w("[VoiceprintManager] 集合不存在: $COLLECTION_NAME")
                return null
            }

            val queryRequest = ChromaQueryRequest(
                queryEmbeddings = listOf(List(128) { 0f }),  // 空查询向量
                nResults = 100,
                where = mapOf("id" to voiceprintId),
                include = listOf("metadatas", "embeddings")  // ✅ 包含 embeddings
            )

            val response = chromaAPI.query(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,  // 使用 UUID
                request = queryRequest
            )

            val body = response.body()
            body?.ids?.firstOrNull()?.firstOrNull()?.let { id ->
                if (id == voiceprintId) {
                    val metadata = body.metadatas?.firstOrNull()?.firstOrNull()
                    val embedding = body.embeddings?.firstOrNull()?.firstOrNull()  // ✅ 恢复使用 embeddings

                    metadata?.let {
                        parseProfileFromMetadata(QueryResultItem(
                            id = id,
                            embedding = embedding,
                            metadata = it,
                            distance = null
                        ))
                    }
                } else null
            }
        } catch (e: Exception) {
            Timber.e(e, "[VoiceprintManager] 获取档案失败: $voiceprintId")
            null
        }
    }

    /**
     * 从元数据解析档案
     */
    private fun parseProfileFromMetadata(item: QueryResultItem): VoiceprintProfile {
        val metadata = item.metadata
        return VoiceprintProfile(
            voiceprintId = item.id,
            personId = metadata["personId"]?.toString() ?: item.id,
            personName = metadata["personName"]?.toString()?.takeIf { it.isNotBlank() },
            displayName = metadata["displayName"]?.toString() ?: "未命名",
            isMaster = metadata["isMaster"]?.toString()?.toBoolean() ?: false,
            isStranger = metadata["isStranger"]?.toString()?.toBoolean() ?: false,
            featureVector = item.embedding?.toFloatArray() ?: FloatArray(0),
            sampleCount = metadata["sampleCount"]?.toString()?.toIntOrNull() ?: 0,
            confidence = metadata["confidence"]?.toString()?.toFloatOrNull() ?: 0f,
            createdAt = metadata["createdAt"]?.toString()?.toLongOrNull() ?: 0L,
            updatedAt = metadata["updatedAt"]?.toString()?.toLongOrNull() ?: 0L,
            metadata = metadata.filterKeys {
                it !in setOf("personId", "personName", "displayName", "isMaster",
                    "isStranger", "sampleCount", "confidence", "createdAt", "updatedAt")
            }.mapValues { it.value.toString() }
        )
    }

    /**
     * 计算多个向量的平均值
     */
    private fun averageVectors(vectors: List<FloatArray>): FloatArray {
        if (vectors.isEmpty()) return FloatArray(0)

        val dimension = vectors.first().size
        val avgVector = FloatArray(dimension)

        for (vector in vectors) {
            for (i in vector.indices) {
                avgVector[i] += vector[i]
            }
        }

        for (i in avgVector.indices) {
            avgVector[i] /= vectors.size.toFloat()
        }

        return avgVector
    }

    /**
     * 计算置信度（基于样本间的一致性）
     */
    private fun calculateConfidence(features: List<FloatArray>): Float {
        if (features.size < 2) return 0.5f

        // 计算样本间的平均余弦相似度
        var totalSimilarity = 0f
        var count = 0

        for (i in features.indices) {
            for (j in (i + 1) until features.size) {
                totalSimilarity += cosineSimilarity(features[i], features[j])
                count++
            }
        }

        return if (count > 0) totalSimilarity / count else 0.5f
    }

    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * 生成陌生人临时名称
     */
    private fun generateStrangerName(): String {
        val timestamp = System.currentTimeMillis() % 10000
        return "陌生人_$timestamp"
    }
}

/**
 * 查询结果项（内部使用）
 */
private data class QueryResultItem(
    val id: String,
    val embedding: List<Float>?,
    val metadata: Map<String, Any>,
    val distance: Float?
)
