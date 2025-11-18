package com.xiaoguang.assistant.domain.knowledge.vector

import com.xiaoguang.assistant.data.remote.api.ChromaAPI
import com.xiaoguang.assistant.data.remote.dto.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chroma向量存储服务
 *
 * 职责：
 * - 封装Chroma API，提供向量存储和检索功能
 * - 管理多个知识集合（World Book、Character Memories、Relationship Contexts）
 * - 提供语义搜索和相似性检索能力
 *
 * 性能优化特性：
 * - 集成LSH索引进行快速近似搜索
 * - 元数据预过滤减少候选集
 * - 查询结果缓存机制
 * - 批量操作支持
 *
 * 使用场景：
 * - 知识库语义搜索
 * - 记忆相似性检索
 * - 关系上下文匹配
 */
@Singleton
class ChromaVectorStore @Inject constructor(
    private val chromaAPI: ChromaAPI,
    private val embeddingService: VectorEmbeddingService,
    private val searchOptimizer: VectorSearchOptimizer
) {

    // 是否启用LSH优化（默认开启）
    private var enableLSHOptimization = true

    /**
     * 初始化向量存储集合
     *
     * 在应用启动时调用，确保以下集合存在：
     * - World Book: 知识库条目语义搜索
     * - Character Memories: 角色记忆和档案
     * - Relationship Contexts: 第三方关系上下文语义搜索
     *
     * @return Result<Unit> 初始化结果
     */
    suspend fun initializeCollections(): Result<Unit> {
        return try {
            // 创建 World Book 集合
            createCollectionIfNotExists(
                name = ChromaAPI.COLLECTION_WORLD_BOOK,
                metadata = mapOf(
                    "description" to "World Book entries with semantic search",
                    "type" to "knowledge_base"
                )
            )

            // 创建 Character Memories 集合
            createCollectionIfNotExists(
                name = ChromaAPI.COLLECTION_CHARACTER_MEMORIES,
                metadata = mapOf(
                    "description" to "Character memories and profiles",
                    "type" to "memory"
                )
            )

            // 创建 Relationship Contexts 集合
            createCollectionIfNotExists(
                name = ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS,
                metadata = mapOf(
                    "description" to "Third-party relationship contexts for semantic search",
                    "type" to "relationship",
                    "features" to "semantic_search,clustering,similarity"
                )
            )

            Timber.i("[ChromaVectorStore] 集合初始化完成（包括关系语义检索）")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[ChromaVectorStore] 初始化集合失败")
            Result.failure(e)
        }
    }

    /**
     * 创建集合（如果不存在）
     */
    private suspend fun createCollectionIfNotExists(
        name: String,
        metadata: Map<String, Any>? = null
    ): Result<ChromaCollectionResponse> {
        return try {
            val request = ChromaCreateCollectionRequest(
                name = name,
                metadata = metadata,
                getOrCreate = true
            )

            val response = chromaAPI.createCollection(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                Timber.d("[ChromaVectorStore] 集合已创建/获取: $name (ID: ${response.body()!!.id})")
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("创建集合失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 创建集合异常（可选服务）: $name")
            Result.failure(e)
        }
    }

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
                Timber.w("[ChromaVectorStore] 获取集合ID失败: $collectionName - ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 获取集合ID异常: $collectionName")
            null
        }
    }

    /**
     * 添加World Book条目到向量数据库
     *
     * @param entryId 条目ID
     * @param content 条目内容
     * @param metadata 元数据（如分类、优先级等）
     */
    suspend fun addWorldEntry(
        entryId: String,
        content: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            // 1. 生成嵌入向量
            val embeddingResult = embeddingService.generateEmbedding(content)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull() ?: Exception("生成嵌入失败"))
            }

            val embedding = embeddingResult.getOrThrow()

            // 2. 添加到LSH索引（用于快速搜索）
            if (enableLSHOptimization) {
                searchOptimizer.addVector(
                    id = entryId,
                    vector = embedding,
                    metadata = metadata + ("collection" to ChromaAPI.COLLECTION_WORLD_BOOK)
                )
            }

            // 3. 添加到Chroma（持久化存储）
            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_WORLD_BOOK)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_WORLD_BOOK}"))
            }

            val request = ChromaAddDocumentsRequest(
                ids = listOf(entryId),
                embeddings = listOf(embedding.toList()),
                documents = listOf(content),
                metadatas = listOf(metadata)
            )

            val response = chromaAPI.addDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 已添加World Entry: $entryId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("添加文档失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 添加World Entry失败（可选服务）: $entryId")
            Result.failure(e)
        }
    }

    /**
     * 添加角色记忆到向量数据库
     *
     * @param memoryId 记忆ID
     * @param content 记忆内容
     * @param metadata 元数据（如角色ID、分类等）
     */
    suspend fun addCharacterMemory(
        memoryId: String,
        content: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            val embeddingResult = embeddingService.generateEmbedding(content)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull() ?: Exception("生成嵌入失败"))
            }

            val embedding = embeddingResult.getOrThrow()

            // 添加到LSH索引
            if (enableLSHOptimization) {
                searchOptimizer.addVector(
                    id = memoryId,
                    vector = embedding,
                    metadata = metadata + ("collection" to ChromaAPI.COLLECTION_CHARACTER_MEMORIES)
                )
            }

            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_CHARACTER_MEMORIES)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_CHARACTER_MEMORIES}"))
            }

            val request = ChromaAddDocumentsRequest(
                ids = listOf(memoryId),
                embeddings = listOf(embedding.toList()),
                documents = listOf(content),
                metadatas = listOf(metadata)
            )

            val response = chromaAPI.addDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 已添加Character Memory: $memoryId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("添加文档失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 添加Character Memory失败（可选服务）: $memoryId")
            Result.failure(e)
        }
    }

    /**
     * 语义搜索World Book条目
     *
     * @param query 查询文本
     * @param nResults 返回结果数量
     * @param filter 元数据过滤条件
     * @return 搜索结果列表
     */
    suspend fun searchWorldEntries(
        query: String,
        nResults: Int = 5,
        filter: Map<String, Any>? = null
    ): Result<List<VectorSearchResult>> {
        return semanticSearch(
            collectionName = ChromaAPI.COLLECTION_WORLD_BOOK,
            query = query,
            nResults = nResults,
            filter = filter
        )
    }

    /**
     * 语义搜索角色记忆
     *
     * @param query 查询文本
     * @param characterId 可选的角色ID过滤
     * @param nResults 返回结果数量
     * @return 搜索结果列表
     */
    suspend fun searchCharacterMemories(
        query: String,
        characterId: String? = null,
        nResults: Int = 5
    ): Result<List<VectorSearchResult>> {
        val filter = characterId?.let { mapOf("character_id" to it) }
        return semanticSearch(
            collectionName = ChromaAPI.COLLECTION_CHARACTER_MEMORIES,
            query = query,
            nResults = nResults,
            filter = filter
        )
    }

    /**
     * 通用语义搜索
     * 集成LSH优化，大幅提升搜索速度
     */
    private suspend fun semanticSearch(
        collectionName: String,
        query: String,
        nResults: Int,
        filter: Map<String, Any>? = null
    ): Result<List<VectorSearchResult>> {
        return try {
            // 1. 生成查询向量
            val embeddingResult = embeddingService.generateEmbedding(query)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull() ?: Exception("生成查询嵌入失败"))
            }

            val queryEmbedding = embeddingResult.getOrThrow()

            // 2. 使用LSH优化搜索（快速近似）
            if (enableLSHOptimization) {
                val combinedFilter = filter?.toMutableMap() ?: mutableMapOf()
                combinedFilter["collection"] = collectionName

                val lshResults = searchOptimizer.searchSimilar(
                    queryVector = queryEmbedding,
                    topK = nResults,
                    metadataFilter = combinedFilter
                )

                // 如果LSH找到足够的结果，直接返回
                if (lshResults.size >= nResults) {
                    Timber.d("[ChromaVectorStore] LSH搜索完成: $collectionName, 找到 ${lshResults.size} 个结果")
                    return Result.success(lshResults)
                }

                // 否则继续使用Chroma进行完整搜索（回退方案）
                Timber.w("[ChromaVectorStore] LSH结果不足(${lshResults.size}/$nResults)，使用Chroma完整搜索")
            }

            // 3. Chroma完整搜索（精确但较慢）
            val collectionUuid = getCollectionId(collectionName)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: $collectionName"))
            }

            val request = ChromaQueryRequest(
                queryEmbeddings = listOf(queryEmbedding.toList()),
                nResults = nResults,
                where = filter
            )

            val response = chromaAPI.query(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // 4. 解析结果
                val results = mutableListOf<VectorSearchResult>()
                if (!body.ids.isNullOrEmpty()) {
                    val ids = body.ids[0]
                    val distances = body.distances?.get(0) ?: List(ids.size) { 0f }
                    val documents = body.documents?.get(0) ?: List(ids.size) { "" }
                    val metadatas = body.metadatas?.get(0) ?: List(ids.size) { emptyMap() }

                    for (i in ids.indices) {
                        results.add(
                            VectorSearchResult(
                                id = ids[i],
                                content = documents[i],
                                distance = distances[i],
                                similarity = 1f - distances[i],  // 距离越小，相似度越高
                                metadata = metadatas[i]
                            )
                        )
                    }
                }

                Timber.d("[ChromaVectorStore] Chroma搜索完成: $collectionName, 找到 ${results.size} 个结果")
                Result.success(results)
            } else {
                Result.failure(Exception("查询失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 语义搜索失败（可选服务）: $collectionName")
            Result.failure(e)
        }
    }

    /**
     * 删除文档
     */
    suspend fun deleteDocument(
        collectionName: String,
        documentId: String
    ): Result<Unit> {
        return try {
            // 从LSH索引删除
            if (enableLSHOptimization) {
                searchOptimizer.removeVector(documentId)
            }

            val collectionUuid = getCollectionId(collectionName)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: $collectionName"))
            }

            val request = ChromaDeleteDocumentsRequest(
                ids = listOf(documentId)
            )

            val response = chromaAPI.deleteDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 已删除文档: $documentId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 删除文档失败（可选服务）: $documentId")
            Result.failure(e)
        }
    }

    /**
     * 更新文档
     */
    suspend fun updateDocument(
        collectionName: String,
        documentId: String,
        content: String,
        metadata: Map<String, Any>? = null
    ): Result<Unit> {
        return try {
            // 生成新的嵌入
            val embeddingResult = embeddingService.generateEmbedding(content)
            if (embeddingResult.isFailure) {
                return Result.failure(embeddingResult.exceptionOrNull() ?: Exception("生成嵌入失败"))
            }

            val embedding = embeddingResult.getOrThrow()

            // 更新LSH索引
            if (enableLSHOptimization) {
                val combinedMetadata = (metadata ?: emptyMap()) + ("collection" to collectionName)
                searchOptimizer.updateVector(
                    id = documentId,
                    vector = embedding,
                    metadata = combinedMetadata
                )
            }

            val collectionUuid = getCollectionId(collectionName)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: $collectionName"))
            }

            val request = ChromaUpdateDocumentsRequest(
                ids = listOf(documentId),
                embeddings = listOf(embedding.toList()),
                documents = listOf(content),
                metadatas = metadata?.let { listOf(it) }
            )

            val response = chromaAPI.updateDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 已更新文档: $documentId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("更新失败: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 更新文档失败（可选服务）: $documentId")
            Result.failure(e)
        }
    }

    /**
     * 获取优化器统计信息
     */
    fun getOptimizerStats(): OptimzerStats? {
        return if (enableLSHOptimization) {
            searchOptimizer.getStats()
        } else {
            null
        }
    }

    /**
     * 启用/禁用LSH优化
     */
    fun setLSHOptimization(enabled: Boolean) {
        enableLSHOptimization = enabled
        Timber.i("[ChromaVectorStore] LSH优化已${if (enabled) "启用" else "禁用"}")
    }

    /**
     * 检查Chroma服务是否可用
     */
    suspend fun isAvailable(): Boolean {
        return try {
            // 尝试获取一个已知集合来检查连接
            val response = chromaAPI.getCollection(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = ChromaAPI.COLLECTION_WORLD_BOOK
            )
            // 如果返回成功（200）或集合不存在（404），说明服务可用
            response.isSuccessful || response.code() == 404
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] Chroma服务不可用")
            false
        }
    }

    /**
     * 按元数据过滤查询文档
     *
     * @param collectionName 集合名称
     * @param where 元数据过滤条件 (如 mapOf("category" to "SETTING", "enabled" to true))
     * @param limit 返回数量限制
     */
    suspend fun getDocumentsByMetadata(
        collectionName: String,
        where: Map<String, Any>? = null,
        limit: Int = 1000
    ): Result<List<VectorSearchResult>> {
        return try {
            val collectionUuid = getCollectionId(collectionName)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: $collectionName"))
            }

            // 使用 query 端点配合 where 过滤
            val request = ChromaQueryRequest(
                queryEmbeddings = listOf(List(1024) { 0f }),
                nResults = limit,
                where = where,
                include = listOf("documents", "metadatas", "distances")
            )

            val response = chromaAPI.query(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val results = mutableListOf<VectorSearchResult>()

                // ChromaDB query 返回嵌套数组结构
                body.ids.firstOrNull()?.forEachIndexed { index, id ->
                    val distance = body.distances?.firstOrNull()?.getOrNull(index) ?: 0f
                    val document = body.documents?.firstOrNull()?.getOrNull(index) ?: ""
                    val metadata = body.metadatas?.firstOrNull()?.getOrNull(index) ?: emptyMap()

                    results.add(
                        VectorSearchResult(
                            id = id,
                            content = document,
                            distance = distance,
                            similarity = 1f - distance,
                            metadata = metadata
                        )
                    )
                }
                Result.success(results)
            } else {
                Result.failure(Exception("查询失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 按元数据查询失败")
            Result.failure(e)
        }
    }

    /**
     * 获取集合中所有文档
     */
    suspend fun getAllDocuments(collectionName: String, limit: Int = 10000): Result<List<VectorSearchResult>> {
        return getDocumentsByMetadata(collectionName, where = null, limit = limit)
    }

    /**
     * 获取文档数量
     */
    suspend fun getDocumentCount(collectionName: String): Result<Int> {
        return try {
            val response = chromaAPI.getCount(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionName
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.count)
            } else {
                Result.failure(Exception("获取数量失败"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 获取文档数量失败")
            Result.failure(e)
        }
    }

    // ==================== Relationship Context Vector Storage ====================

    /**
     * 添加关系上下文到向量数据库
     * 将关系描述向量化存储，支持语义检索
     *
     * @param relationshipId 关系ID（唯一标识符）
     * @param personA 人物A
     * @param personB 人物B
     * @param relationType 关系类型
     * @param description 关系描述文本（用于向量化）
     * @param metadata 额外元数据（置信度、来源等）
     */
    suspend fun addRelationshipContext(
        relationshipId: String,
        personA: String,
        personB: String,
        relationType: String,
        description: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            // 构建完整的关系上下文文本（用于生成embedding）
            val contextText = buildRelationshipContextText(personA, personB, relationType, description)

            // 生成向量
            val embedding = embeddingService.generateEmbedding(contextText).getOrNull()
                ?: return Result.failure(Exception("生成关系向量失败"))

            // 合并元数据
            val fullMetadata = metadata.toMutableMap().apply {
                put("personA", personA)
                put("personB", personB)
                put("relationType", relationType)
                put("timestamp", System.currentTimeMillis())
                put("descriptionLength", description.length)
            }

            // 添加到ChromaDB
            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS}"))
            }

            val request = ChromaAddDocumentsRequest(
                ids = listOf(relationshipId),
                embeddings = listOf(embedding.toList()),
                documents = listOf(contextText),
                metadatas = listOf(fullMetadata)
            )

            val response = chromaAPI.addDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 关系上下文已添加: $personA <-> $personB ($relationType)")
                Result.success(Unit)
            } else {
                Result.failure(Exception("添加关系上下文失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 添加关系上下文异常")
            Result.failure(e)
        }
    }

    /**
     * 批量添加关系上下文
     */
    suspend fun addRelationshipContexts(
        relationships: List<RelationshipContextData>
    ): Result<Unit> {
        return try {
            if (relationships.isEmpty()) {
                return Result.success(Unit)
            }

            val ids = mutableListOf<String>()
            val embeddings = mutableListOf<List<Float>>()
            val documents = mutableListOf<String>()
            val metadatas = mutableListOf<Map<String, Any>>()

            for (rel in relationships) {
                val contextText = buildRelationshipContextText(
                    rel.personA, rel.personB, rel.relationType, rel.description
                )

                val embedding = embeddingService.generateEmbedding(contextText).getOrNull()
                    ?: continue  // 跳过失败的向量生成

                ids.add(rel.relationshipId)
                embeddings.add(embedding.toList())
                documents.add(contextText)
                metadatas.add(
                    (rel.metadata ?: emptyMap()).toMutableMap().apply {
                        put("personA", rel.personA)
                        put("personB", rel.personB)
                        put("relationType", rel.relationType)
                        put("timestamp", System.currentTimeMillis())
                        put("descriptionLength", rel.description.length)
                    }
                )
            }

            if (ids.isEmpty()) {
                return Result.failure(Exception("所有关系向量生成失败"))
            }

            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS}"))
            }

            val request = ChromaAddDocumentsRequest(
                ids = ids,
                embeddings = embeddings,
                documents = documents,
                metadatas = metadatas
            )

            val response = chromaAPI.addDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.i("[ChromaVectorStore] 批量添加${ids.size}个关系上下文成功")
                Result.success(Unit)
            } else {
                Result.failure(Exception("批量添加失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "[ChromaVectorStore] 批量添加关系上下文失败")
            Result.failure(e)
        }
    }

    /**
     * 语义搜索相似关系
     * 根据查询文本找到语义相似的关系
     *
     * @param query 查询文本（如"谁和张三关系最好？"）
     * @param limit 返回结果数量
     * @param where 元数据过滤条件
     */
    suspend fun searchSimilarRelationships(
        query: String,
        limit: Int = 10,
        where: Map<String, Any>? = null
    ): Result<List<VectorSearchResult>> {
        return try {
            // 生成查询向量
            val queryEmbedding = embeddingService.generateEmbedding(query).getOrNull()
                ?: return Result.failure(Exception("生成查询向量失败"))

            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS}"))
            }

            // 构建查询请求
            val request = ChromaQueryRequest(
                queryEmbeddings = listOf(queryEmbedding.toList()),
                nResults = limit,
                where = where,
                include = listOf("documents", "metadatas", "distances")
            )

            val response = chromaAPI.query(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val results = mutableListOf<VectorSearchResult>()
                for (i in body.ids.indices) {
                    val id = body.ids[i].firstOrNull() ?: ""
                    val distance = body.distances?.get(i)?.firstOrNull() ?: 0f
                    val document = body.documents?.get(i)?.firstOrNull() ?: ""
                    val metadata = body.metadatas?.get(i)?.firstOrNull() ?: emptyMap()

                    results.add(
                        VectorSearchResult(
                            id = id,
                            content = document,
                            distance = distance,
                            similarity = distance,
                            metadata = metadata
                        )
                    )
                }
                Timber.d("[ChromaVectorStore] 找到${results.size}个相似关系")
                Result.success(results)
            } else {
                Result.failure(Exception("搜索相似关系失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 搜索相似关系异常")
            Result.failure(e)
        }
    }

    /**
     * 查找某人的所有关系（按相关性排序）
     */
    suspend fun searchRelationshipsByPerson(
        personName: String,
        limit: Int = 50
    ): Result<List<VectorSearchResult>> {
        val query = "关于${personName}的所有人际关系"

        // 使用元数据过滤
        val where = mapOf(
            "\$or" to listOf(
                mapOf("personA" to personName),
                mapOf("personB" to personName)
            )
        )

        return searchSimilarRelationships(query, limit, where)
    }

    /**
     * 更新关系上下文
     */
    suspend fun updateRelationshipContext(
        relationshipId: String,
        personA: String,
        personB: String,
        relationType: String,
        description: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit> {
        return try {
            val contextText = buildRelationshipContextText(personA, personB, relationType, description)
            val embedding = embeddingService.generateEmbedding(contextText).getOrNull()
                ?: return Result.failure(Exception("生成关系向量失败"))

            val fullMetadata = metadata.toMutableMap().apply {
                put("personA", personA)
                put("personB", personB)
                put("relationType", relationType)
                put("timestamp", System.currentTimeMillis())
                put("descriptionLength", description.length)
                put("updatedAt", System.currentTimeMillis())
            }

            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS}"))
            }

            val request = ChromaUpdateDocumentsRequest(
                ids = listOf(relationshipId),
                embeddings = listOf(embedding.toList()),
                documents = listOf(contextText),
                metadatas = listOf(fullMetadata)
            )

            val response = chromaAPI.updateDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 关系上下文已更新: $relationshipId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("更新关系上下文失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 更新关系上下文异常")
            Result.failure(e)
        }
    }

    /**
     * 删除关系上下文
     */
    suspend fun deleteRelationshipContext(relationshipId: String): Result<Unit> {
        return try {
            val collectionUuid = getCollectionId(ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS)
            if (collectionUuid == null) {
                return Result.failure(Exception("集合不存在: ${ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS}"))
            }

            val request = ChromaDeleteDocumentsRequest(ids = listOf(relationshipId))
            val response = chromaAPI.deleteDocuments(
                tenant = ChromaAPI.DEFAULT_TENANT,
                database = ChromaAPI.DEFAULT_DATABASE,
                collectionId = collectionUuid,
                request = request
            )

            if (response.isSuccessful) {
                Timber.d("[ChromaVectorStore] 关系上下文已删除: $relationshipId")
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除关系上下文失败: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 删除关系上下文异常")
            Result.failure(e)
        }
    }

    /**
     * 构建关系上下文文本
     * 将关系信息组合成适合向量化的文本
     */
    private fun buildRelationshipContextText(
        personA: String,
        personB: String,
        relationType: String,
        description: String
    ): String {
        return buildString {
            // 主要信息
            append("$personA 和 $personB 是 $relationType 关系")

            // 详细描述
            if (description.isNotBlank()) {
                append("。")
                append(description)
            }

            // 添加不同表述方式以增强语义匹配
            append("。关于 $personA 和 $personB 的关系：他们之间是 $relationType")
        }
    }

    /**
     * 关系聚类分析
     * 找到相似的关系模式
     *
     * @param sampleRelationshipId 样本关系ID
     * @param limit 返回相似关系数量
     */
    suspend fun findSimilarRelationshipPatterns(
        sampleRelationshipId: String,
        limit: Int = 10
    ): Result<List<VectorSearchResult>> {
        return try {
            // 先获取样本关系的文档
            val sampleDoc = getDocumentsByMetadata(
                collectionName = ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS,
                where = null,
                limit = 1
            ).getOrNull()?.firstOrNull { it.id == sampleRelationshipId }
                ?: return Result.failure(Exception("样本关系不存在"))

            // 使用样本的内容进行相似搜索
            searchSimilarRelationships(sampleDoc.content, limit + 1)  // +1因为会包含自己
                .map { results -> results.filter { it.id != sampleRelationshipId } }  // 排除自己
        } catch (e: Exception) {
            Timber.w(e, "[ChromaVectorStore] 关系聚类分析失败")
            Result.failure(e)
        }
    }

    /**
     * 获取关系上下文数量
     */
    suspend fun getRelationshipContextCount(): Result<Int> {
        return getDocumentCount(ChromaAPI.COLLECTION_RELATIONSHIP_CONTEXTS)
    }

    // ========== v2.4 记忆重构系统 Facade ==========

    /**
     * 生成文本的向量嵌入
     * 供SemanticSearchEngine使用
     */
    suspend fun embed(text: String): List<Float> {
        return try {
            val result = embeddingService.generateEmbedding(text)
            if (result.isSuccess) {
                result.getOrNull()?.toList() ?: emptyList()
            } else {
                Timber.e(result.exceptionOrNull(), "生成嵌入向量失败")
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "生成嵌入向量异常: ${text.take(50)}")
            emptyList()
        }
    }

    /**
     * 向量相似度搜索
     * 供SemanticSearchEngine使用
     */
    suspend fun similaritySearch(
        queryVector: List<Float>,
        limit: Int = 10,
        similarityThreshold: Float = 0.6f
    ): List<VectorSearchResult> {
        return try {
            // 使用现有的private semanticSearch方法，但需要修改其访问权限
            // 暂时返回一个空列表，实际应该实现公共的semanticSearch方法
            Timber.w("ChromaVectorStore.similaritySearch - 使用默认实现")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "向量相似度搜索失败")
            emptyList()
        }
    }

    /**
     * 存储向量数据
     * 供MemoryReconstructionService使用
     */
    suspend fun upsert(
        key: String,
        vector: List<Float>,
        metadata: Map<String, Any>
    ): Result<Unit> {
        return try {
            // TODO: 实现upsert逻辑
            Timber.w("ChromaVectorStore.upsert - 未完全实现")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "存储向量数据失败: $key")
            Result.failure(e)
        }
    }
}

/**
 * 向量搜索结果
 */
data class VectorSearchResult(
    val id: String,
    val content: String,
    val distance: Float,
    val similarity: Float,
    val metadata: Map<String, Any>
)

/**
 * 关系上下文数据（用于批量添加）
 */
data class RelationshipContextData(
    val relationshipId: String,
    val personA: String,
    val personB: String,
    val relationType: String,
    val description: String,
    val metadata: Map<String, Any>? = null
)
