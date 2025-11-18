package com.xiaoguang.assistant.data.repository

import com.xiaoguang.assistant.domain.knowledge.models.*
import com.xiaoguang.assistant.domain.knowledge.vector.ChromaVectorStore
import com.xiaoguang.assistant.domain.knowledge.vector.VectorSearchResult
import com.xiaoguang.assistant.data.local.realm.entities.PersonEntity
import com.xiaoguang.assistant.data.local.realm.entities.AttributeEmbedded
import com.xiaoguang.assistant.data.local.database.MemoryDatabase
import com.xiaoguang.assistant.data.local.database.entity.MemoryConsolidationEntity
import com.xiaoguang.assistant.data.local.database.entity.ConsolidationStatisticsEntity
import com.xiaoguang.assistant.data.local.database.entity.EvaluationThresholdEntity
import com.xiaoguang.assistant.data.local.database.entity.DecisionPatternAnalysisEntity
import io.realm.kotlin.Realm
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.RealmResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Character Book数据仓库
 * 提供Character Book的数据访问接口
 *
 * ⚠️ 重构说明：
 * - 保留Repository接口层不变
 * - 底层实现从Room改为ChromaDB（记忆）+ Realm（角色档案）
 * - 上层业务代码（CharacterBook）无需修改
 */
@Singleton
class CharacterBookRepository @Inject constructor(
    private val chromaVectorStore: ChromaVectorStore,
    private val realm: Realm,
    private val memoryDatabase: MemoryDatabase
) {

    companion object {
        private const val MEMORY_COLLECTION = "xiaoguang_character_memories"
        private const val MEMORY_RETENTION_DAYS = 90  // 记忆保留天数
    }

    // ==================== Character Profile Management ====================

    /**
     * 保存角色档案
     */
    suspend fun saveProfile(profile: CharacterProfile): Result<Unit> {
        return try {
            realm.write {
                val existingPerson = query<PersonEntity>("personId == $0", profile.basicInfo.characterId).first().find()

                if (existingPerson != null) {
                    // 更新现有
                    existingPerson.apply {
                        name = profile.basicInfo.name
                        updatedAt = System.currentTimeMillis()

                        // 清空并重建attributes
                        attributes.clear()
                        addAttribute("characterId", profile.basicInfo.characterId)
                        addAttribute("nickname", profile.basicInfo.nickname ?: profile.basicInfo.name)
                        profile.basicInfo.age?.let { addAttribute("age", it.toString()) }
                        profile.basicInfo.gender?.let { addAttribute("gender", it) }
                        addAttribute("bio", profile.basicInfo.bio ?: "")
                        addAttribute("traits", profile.personality.traits.entries.joinToString(",") { "${it.key}: ${it.value}" })
                        if (profile.preferences.interests.isNotEmpty()) {
                            addAttribute("interests", profile.preferences.interests.joinToString(","))
                        }
                        addAttribute("platformId", profile.basicInfo.characterId)
                        addAttribute("lastSeenTime", System.currentTimeMillis().toString())

                        // 声纹相关字段
                        profile.basicInfo.voiceprintId?.let { addAttribute("voiceprintId", it) }
                        addAttribute("isStranger", profile.basicInfo.isStranger.toString())
                        if (profile.basicInfo.aliases.isNotEmpty()) {
                            addAttribute("aliases", profile.basicInfo.aliases.joinToString(","))
                        }
                        // 元数据
                        profile.basicInfo.metadata.forEach { (key, value) ->
                            addAttribute("metadata_$key", value)
                        }
                    }
                } else {
                    // 创建新的
                    copyToRealm(PersonEntity().apply {
                        personId = profile.basicInfo.characterId
                        name = profile.basicInfo.name
                        createdAt = System.currentTimeMillis()
                        updatedAt = System.currentTimeMillis()

                        // 添加attributes
                        addAttribute("characterId", profile.basicInfo.characterId)
                        addAttribute("nickname", profile.basicInfo.nickname ?: profile.basicInfo.name)
                        profile.basicInfo.age?.let { addAttribute("age", it.toString()) }
                        profile.basicInfo.gender?.let { addAttribute("gender", it) }
                        addAttribute("bio", profile.basicInfo.bio ?: "")
                        addAttribute("traits", profile.personality.traits.entries.joinToString(",") { "${it.key}: ${it.value}" })
                        if (profile.preferences.interests.isNotEmpty()) {
                            addAttribute("interests", profile.preferences.interests.joinToString(","))
                        }
                        addAttribute("platformId", profile.basicInfo.characterId)
                        addAttribute("lastSeenTime", System.currentTimeMillis().toString())

                        // 声纹相关字段
                        profile.basicInfo.voiceprintId?.let { addAttribute("voiceprintId", it) }
                        addAttribute("isStranger", profile.basicInfo.isStranger.toString())
                        if (profile.basicInfo.aliases.isNotEmpty()) {
                            addAttribute("aliases", profile.basicInfo.aliases.joinToString(","))
                        }
                        // 元数据
                        profile.basicInfo.metadata.forEach { (key, value) ->
                            addAttribute("metadata_$key", value)
                        }
                    })
                }
            }
            Timber.d("[CharacterBookRepository] 保存角色档案成功: ${profile.basicInfo.name}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 保存角色档案失败")
            Result.failure(e)
        }
    }

    /**
     * 批量保存角色档案
     */
    suspend fun saveProfiles(profiles: List<CharacterProfile>): Result<Unit> {
        return try {
            profiles.forEach { profile ->
                val result = saveProfile(profile)
                if (result.isFailure) return result
            }
            Timber.d("[CharacterBookRepository] 批量保存${profiles.size}个角色档案成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 批量保存角色档案失败")
            Result.failure(e)
        }
    }

    /**
     * 根据ID获取角色档案
     */
    suspend fun getProfile(characterId: String): CharacterProfile? {
        return try {
            val person = realm.query<PersonEntity>("personId == $0", characterId).first().find()
            person?.toCharacterProfile()
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取角色档案失败: $characterId")
            null
        }
    }

    /**
     * 根据姓名获取角色档案
     */
    suspend fun getProfileByName(name: String): CharacterProfile? {
        return try {
            val person = realm.query<PersonEntity>("name == $0", name).first().find()
            person?.toCharacterProfile()
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 根据姓名获取角色档案失败: $name")
            null
        }
    }

    /**
     * 根据平台ID获取角色档案
     */
    suspend fun getProfileByPlatformId(platformId: String): CharacterProfile? {
        return try {
            // 通过attributes中的platformId查找
            val allPersons = realm.query<PersonEntity>().find()
            val person = allPersons.firstOrNull { person ->
                person.attributes.any { it.key == "platformId" && it.value == platformId }
            }
            person?.toCharacterProfile()
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 根据平台ID获取角色档案失败: $platformId")
            null
        }
    }

    /**
     * 获取所有角色档案
     */
    suspend fun getAllProfiles(): List<CharacterProfile> {
        return try {
            val persons: RealmResults<PersonEntity> = realm.query<PersonEntity>().find()
            persons.mapNotNull { it.toCharacterProfile() }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取所有角色档案失败")
            emptyList()
        }
    }

    /**
     * 搜索角色
     */
    suspend fun searchProfiles(query: String): List<CharacterProfile> {
        return try {
            val persons = realm.query<PersonEntity>("name CONTAINS[c] $0", query).find()
            // 也搜索attributes中的bio
            val allPersons = realm.query<PersonEntity>().find()
            val additionalResults = allPersons.filter { person ->
                person.attributes.any {
                    it.key == "bio" && it.value.contains(query, ignoreCase = true)
                }
            }
            (persons + additionalResults).distinct().mapNotNull { it.toCharacterProfile() }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 搜索角色失败")
            emptyList()
        }
    }

    /**
     * 获取最近互动的角色
     */
    suspend fun getRecentProfiles(limit: Int = 10): List<CharacterProfile> {
        return try {
            val allPersons = realm.query<PersonEntity>().find()
            allPersons
                .sortedByDescending { person ->
                    person.attributes.firstOrNull { it.key == "lastSeenTime" }?.value?.toLongOrNull() ?: 0L
                }
                .take(limit)
                .mapNotNull { it.toCharacterProfile() }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取最近互动角色失败")
            getAllProfiles().take(limit)
        }
    }

    /**
     * 更新最后见面时间
     */
    suspend fun updateLastSeen(characterId: String): Result<Unit> {
        return try {
            realm.write {
                val person = query<PersonEntity>("personId == $0", characterId).first().find()
                person?.let {
                    it.updatedAt = System.currentTimeMillis()
                    val lastSeenAttr = it.attributes.firstOrNull { attr -> attr.key == "lastSeenTime" }
                    if (lastSeenAttr != null) {
                        lastSeenAttr.value = System.currentTimeMillis().toString()
                    } else {
                        it.addAttribute("lastSeenTime", System.currentTimeMillis().toString())
                    }
                }
            }
            Timber.d("[CharacterBookRepository] 更新最后见面时间成功: $characterId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 更新最后见面时间失败")
            Result.failure(e)
        }
    }

    /**
     * 删除角色档案
     */
    suspend fun deleteProfile(characterId: String): Result<Unit> {
        return try {
            realm.write {
                val person = query<PersonEntity>("personId == $0", characterId).first().find()
                person?.let { delete(it) }
            }
            Timber.d("[CharacterBookRepository] 删除角色档案成功: $characterId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 删除角色档案失败")
            Result.failure(e)
        }
    }

    // ==================== Memory Management ====================

    /**
     * 添加记忆
     */
    suspend fun addMemory(memory: CharacterMemory, embedding: FloatArray? = null): Result<Unit> {
        return try {
            chromaVectorStore.addCharacterMemory(
                memoryId = memory.memoryId,
                content = memory.content,
                metadata = memory.toMetadata()
            )
            Timber.d("[CharacterBookRepository] 添加记忆成功: ${memory.memoryId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 添加记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 批量添加记忆
     */
    suspend fun addMemories(memories: List<Pair<CharacterMemory, FloatArray?>>): Result<Unit> {
        return try {
            memories.forEach { (memory, embedding) ->
                val result = addMemory(memory, embedding)
                if (result.isFailure) return result
            }
            Timber.d("[CharacterBookRepository] 批量添加${memories.size}个记忆成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 批量添加记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 更新记忆
     */
    suspend fun updateMemory(memory: CharacterMemory, embedding: FloatArray? = null): Result<Unit> {
        return try {
            chromaVectorStore.updateDocument(
                collectionName = MEMORY_COLLECTION,
                documentId = memory.memoryId,
                content = memory.content,
                metadata = memory.toMetadata()
            )
            Timber.d("[CharacterBookRepository] 更新记忆成功: ${memory.memoryId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 更新记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        return try {
            chromaVectorStore.deleteDocument(MEMORY_COLLECTION, memoryId)
            Timber.d("[CharacterBookRepository] 删除记忆成功: $memoryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 删除记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 获取角色的所有记忆
     */
    suspend fun getMemories(characterId: String): List<CharacterMemory> {
        return try {
            val result = chromaVectorStore.getDocumentsByMetadata(
                collectionName = MEMORY_COLLECTION,
                where = mapOf("characterId" to characterId),
                limit = 10000
            )
            result.getOrNull()
                ?.map { it.toCharacterMemory() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取角色记忆失败: $characterId")
            emptyList()
        }
    }

    /**
     * 根据类别获取记忆
     */
    suspend fun getMemoriesByCategory(
        characterId: String,
        category: MemoryCategory
    ): List<CharacterMemory> {
        return try {
            val result = chromaVectorStore.getDocumentsByMetadata(
                collectionName = MEMORY_COLLECTION,
                where = mapOf(
                    "characterId" to characterId,
                    "category" to category.name
                ),
                limit = 10000
            )
            result.getOrNull()
                ?.map { it.toCharacterMemory() }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取分类记忆失败: $characterId, $category")
            emptyList()
        }
    }

    /**
     * 获取核心记忆（高重要性）
     */
    suspend fun getCoreMemories(characterId: String): List<CharacterMemory> {
        return try {
            val allMemories = getMemories(characterId)
            allMemories.filter { it.importance >= 8 }
                .sortedByDescending { it.importance }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取核心记忆失败")
            emptyList()
        }
    }

    /**
     * 获取最重要的N个记忆
     */
    suspend fun getTopMemories(characterId: String, limit: Int = 10): List<CharacterMemory> {
        return try {
            val allMemories = getMemories(characterId)
            allMemories.sortedByDescending { it.importance }.take(limit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取TopN记忆失败")
            emptyList()
        }
    }

    /**
     * 搜索记忆（语义检索）
     */
    suspend fun searchMemories(characterId: String, query: String, topK: Int = 10): List<CharacterMemory> {
        return try {
            val result = chromaVectorStore.searchCharacterMemories(
                query = query,
                characterId = characterId,
                nResults = topK
            )
            val searchResults = result.getOrNull() ?: emptyList()
            searchResults.map { searchResult ->
                CharacterMemory(
                    memoryId = searchResult.id,
                    characterId = characterId,
                    category = MemoryCategory.EPISODIC, // 默认类别
                    content = searchResult.content,
                    importance = (searchResult.metadata["importance"] as? Float) ?: 0.5f,
                    emotionalValence = (searchResult.metadata["emotionalValence"] as? Float) ?: 0f,
                    emotionTag = searchResult.metadata["emotionTag"] as? String,
                    emotionIntensity = searchResult.metadata["emotionIntensity"] as? Float,
                    tags = (searchResult.metadata["tags"] as? String)?.split(",")?.map { it.trim() } ?: emptyList(),
                    accessCount = (searchResult.metadata["accessCount"] as? Int) ?: 0,
                    lastAccessed = (searchResult.metadata["lastAccessed"] as? Long) ?: System.currentTimeMillis(),
                    createdAt = (searchResult.metadata["createdAt"] as? Long) ?: System.currentTimeMillis(),
                    expiresAt = searchResult.metadata["expiresAt"] as? Long
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 搜索记忆失败")
            emptyList()
        }
    }

    /**
     * 记录记忆访问
     */
    suspend fun recordMemoryAccess(memoryId: String): Result<Unit> {
        return try {
            // 获取现有记忆
            val allMemoriesResult = chromaVectorStore.getAllDocuments(MEMORY_COLLECTION, limit = 10000)
            val memory = allMemoriesResult.getOrNull()?.firstOrNull { it.id == memoryId }
                ?: return Result.failure(Exception("记忆不存在: $memoryId"))

            // 更新访问次数和最后访问时间
            val metadata = memory.metadata.toMutableMap()
            val accessCount = (metadata["accessCount"] as? Number)?.toInt() ?: 0
            metadata["accessCount"] = accessCount + 1
            metadata["lastAccessTime"] = System.currentTimeMillis()

            chromaVectorStore.updateDocument(
                collectionName = MEMORY_COLLECTION,
                documentId = memoryId,
                content = memory.content,
                metadata = metadata
            )
            Timber.d("[CharacterBookRepository] 记录记忆访问成功: $memoryId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 记录记忆访问失败")
            Result.failure(e)
        }
    }

    /**
     * 清理过期记忆
     */
    suspend fun cleanupExpiredMemories(): Result<Int> {
        return try {
            val expirationTime = System.currentTimeMillis() - (MEMORY_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            val allMemoriesResult = chromaVectorStore.getAllDocuments(MEMORY_COLLECTION, limit = 10000)
            val expiredMemories = allMemoriesResult.getOrNull()?.filter { doc ->
                val timestamp = (doc.metadata["timestamp"] as? Number)?.toLong() ?: Long.MAX_VALUE
                val importance = (doc.metadata["importance"] as? Number)?.toInt() ?: 10
                timestamp < expirationTime && importance < 5  // 只删除低重要性的旧记忆
            } ?: emptyList()

            expiredMemories.forEach { doc ->
                chromaVectorStore.deleteDocument(MEMORY_COLLECTION, doc.id)
            }

            Timber.i("[CharacterBookRepository] 清理过期记忆${expiredMemories.size}条")
            Result.success(expiredMemories.size)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 清理过期记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 清理旧记忆
     */
    suspend fun cleanupOldMemories(characterId: String, minImportance: Float, daysOld: Int): Result<Unit> {
        return try {
            val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
            val memories = getMemories(characterId)

            val memoriesToDelete = memories.filter { memory ->
                memory.importance < minImportance && memory.createdAt < cutoffTime
            }

            memoriesToDelete.forEach { memory ->
                chromaVectorStore.deleteDocument(MEMORY_COLLECTION, memory.memoryId)
            }

            Timber.i("[CharacterBookRepository] 清理${memoriesToDelete.size}条旧记忆（characterId=$characterId, minImportance=$minImportance, daysOld=$daysOld）")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 清理旧记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 获取记忆统计信息
     */
    suspend fun getMemoryStatistics(characterId: String): MemoryStatistics {
        return try {
            val memories = getMemories(characterId)
            MemoryStatistics(
                totalMemories = memories.size,
                categoryDistribution = memories
                    .groupBy { it.category.name }
                    .mapValues { it.value.size }
            )
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取记忆统计失败")
            MemoryStatistics()
        }
    }

    /**
     * 获取需要embedding的记忆
     */
    suspend fun getMemoriesNeedingEmbedding(limit: Int): List<CharacterMemory> {
        // ChromaDB自动处理embedding，此方法返回空列表
        return emptyList()
    }

    /**
     * 更新记忆embedding
     */
    suspend fun updateMemoryEmbedding(memoryId: String, embedding: FloatArray): Result<Unit> {
        // ChromaDB自动处理embedding，此方法直接返回成功
        return Result.success(Unit)
    }

    // ==================== Observation Methods ====================

    /**
     * 观察角色档案变化（用于UI）
     */
    fun observeProfile(characterId: String): Flow<CharacterProfile?> = flow {
        // Realm不支持实时监听单个对象，返回静态数据
        emit(getProfile(characterId))
    }

    /**
     * 观察所有角色档案变化
     */
    fun observeAllProfiles(): Flow<List<CharacterProfile>> = flow {
        // Realm不支持实时监听，返回静态数据
        emit(getAllProfiles())
    }

    /**
     * 观察角色记忆变化
     */
    fun observeMemories(characterId: String): Flow<List<CharacterMemory>> = flow {
        // ChromaDB不支持实时监听，返回静态数据
        emit(getMemories(characterId))
    }

    // ==================== Relationship Management (应使用Neo4j) ====================

    /**
     * 保存关系（应使用Neo4j）
     */
    suspend fun saveRelationship(relationship: Relationship): Result<Unit> {
        // 暂存到Realm的relationships字段
        return try {
            realm.write {
                val person = query<PersonEntity>("personId == $0", relationship.fromCharacterId).first().find()
                person?.let {
                    // 检查关系是否已存在
                    val existingRel = it.relationships.firstOrNull { rel ->
                        rel.targetEntityId == relationship.toCharacterId && rel.relationType == relationship.relationType.name
                    }

                    if (existingRel != null) {
                        existingRel.relationType = relationship.relationType.name
                        existingRel.strength = relationship.getStrength()
                    } else {
                        it.relationships.add(com.xiaoguang.assistant.data.local.realm.entities.RelationshipEmbedded().apply {
                            targetEntityId = relationship.toCharacterId
                            targetEntityName = relationship.toCharacterId
                            relationType = relationship.relationType.name
                            strength = relationship.getStrength()
                            context = ""
                        })
                    }
                }
            }
            Timber.d("[CharacterBookRepository] 保存关系成功（暂存Realm）")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 保存关系失败")
            Result.failure(e)
        }
    }

    /**
     * 获取关系（应使用Neo4j）
     */
    suspend fun getRelationship(fromCharacterId: String, toCharacterId: String): Relationship? {
        return try {
            val person = realm.query<PersonEntity>("personId == $0", fromCharacterId).first().find()
            val relEmbedded = person?.relationships?.firstOrNull { it.targetEntityId == toCharacterId }

            relEmbedded?.let {
                try {
                    val relationType = RelationType.valueOf(it.relationType.uppercase())
                    Relationship(
                        fromCharacterId = fromCharacterId,
                        toCharacterId = it.targetEntityId,
                        relationType = relationType,
                        intimacyLevel = it.strength,
                        trustLevel = it.strength
                    )
                } catch (e: Exception) {
                    // 如果解析失败，使用默认值
                    Relationship(
                        fromCharacterId = fromCharacterId,
                        toCharacterId = it.targetEntityId,
                        relationType = RelationType.OTHER,
                        intimacyLevel = it.strength,
                        trustLevel = it.strength
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取关系失败")
            null
        }
    }

    /**
     * 获取角色的所有关系（应使用Neo4j）
     */
    suspend fun getRelationshipsFrom(characterId: String): List<Relationship> {
        return try {
            val person = realm.query<PersonEntity>("personId == $0", characterId).first().find()
            person?.relationships?.map { relEmbedded ->
                try {
                    val relationType = RelationType.valueOf(relEmbedded.relationType.uppercase())
                    Relationship(
                        fromCharacterId = characterId,
                        toCharacterId = relEmbedded.targetEntityId,
                        relationType = relationType,
                        intimacyLevel = relEmbedded.strength,
                        trustLevel = relEmbedded.strength
                    )
                } catch (e: Exception) {
                    Relationship(
                        fromCharacterId = characterId,
                        toCharacterId = relEmbedded.targetEntityId,
                        relationType = RelationType.OTHER,
                        intimacyLevel = relEmbedded.strength,
                        trustLevel = relEmbedded.strength
                    )
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取关系列表失败")
            emptyList()
        }
    }

    /**
     * 获取主人关系（应使用Neo4j）
     */
    suspend fun getMasterRelationship(): Relationship? {
        // 查找所有角色中标记为master的关系
        return try {
            val allPersons = realm.query<PersonEntity>().find()
            for (person in allPersons) {
                val masterRel = person.relationships.firstOrNull {
                    it.relationType == RelationType.MASTER.name
                }
                if (masterRel != null) {
                    return try {
                        val relationType = RelationType.valueOf(masterRel.relationType.uppercase())
                        Relationship(
                            fromCharacterId = person.personId,
                            toCharacterId = masterRel.targetEntityId,
                            relationType = relationType,
                            intimacyLevel = masterRel.strength,
                            trustLevel = masterRel.strength,
                            isMasterRelationship = true
                        )
                    } catch (e: Exception) {
                        Relationship(
                            fromCharacterId = person.personId,
                            toCharacterId = masterRel.targetEntityId,
                            relationType = RelationType.OTHER,
                            intimacyLevel = masterRel.strength,
                            trustLevel = masterRel.strength,
                            isMasterRelationship = true
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取主人关系失败")
            null
        }
    }

    // ==================== Memory Consolidation Methods ====================

    /**
     * 保存记忆整合决策记录
     */
    suspend fun saveConsolidationDecision(decision: MemoryConsolidationDecision): Result<Unit> {
        return try {
            val entity = MemoryConsolidationEntity(
                characterId = decision.characterId,
                memoryId = decision.memoryId,
                wasConsolidated = decision.wasConsolidated,
                llmScore = decision.llmScore,
                confidence = decision.confidence,
                memoryImportance = decision.memoryImportance,
                memoryAccessCount = decision.memoryAccessCount,
                memoryAgeDays = decision.memoryAgeDays,
                reasoning = decision.reasoning,
                timestamp = decision.timestamp
            )
            memoryDatabase.memoryConsolidationDao().insertConsolidationDecision(entity)
            Timber.d("[CharacterBookRepository] 保存整合决策成功: ${decision.memoryId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 保存整合决策失败")
            Result.failure(e)
        }
    }

    /**
     * 保存决策模式分析结果
     */
    suspend fun saveDecisionPatternAnalysis(
        characterId: String,
        consolidatedRate: Float,
        avgLlmScore: Float,
        importanceGap: Float,
        timestamp: Long
    ): Result<Unit> {
        return try {
            val entity = DecisionPatternAnalysisEntity(
                characterId = characterId,
                consolidatedRate = consolidatedRate,
                avgLlmScore = avgLlmScore,
                importanceGap = importanceGap,
                timestamp = timestamp
            )
            memoryDatabase.memoryConsolidationDao().insertDecisionPatternAnalysis(entity)
            Timber.d("[CharacterBookRepository] 保存决策模式分析成功: characterId=$characterId, consolidatedRate=$consolidatedRate")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 保存决策模式分析失败")
            Result.failure(e)
        }
    }

    /**
     * 获取评估阈值
     */
    suspend fun getEvaluationThreshold(characterId: String): Float? {
        return try {
            memoryDatabase.memoryConsolidationDao().getEvaluationThreshold(characterId)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取评估阈值失败: $characterId")
            null
        }
    }

    /**
     * 保存评估阈值
     */
    suspend fun saveEvaluationThreshold(characterId: String, threshold: Float): Result<Unit> {
        return try {
            val entity = EvaluationThresholdEntity(
                characterId = characterId,
                threshold = threshold,
                lastUpdated = System.currentTimeMillis()
            )
            memoryDatabase.memoryConsolidationDao().insertOrUpdateEvaluationThreshold(entity)
            Timber.d("[CharacterBookRepository] 保存评估阈值成功: characterId=$characterId, threshold=$threshold")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 保存评估阈值失败")
            Result.failure(e)
        }
    }

    /**
     * 获取整合统计信息
     */
    suspend fun getConsolidationStatistics(characterId: String): ConsolidationStatistics? {
        return try {
            val entity = memoryDatabase.memoryConsolidationDao().getConsolidationStatistics(characterId)
            entity?.let {
                ConsolidationStatistics(
                    characterId = it.characterId,
                    totalDecisions = it.totalDecisions,
                    totalConsolidated = it.totalConsolidated,
                    avgLlmScore = it.avgLlmScore,
                    avgConfidence = it.avgConfidence,
                    lastUpdated = it.lastUpdated
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取整合统计失败: $characterId")
            null
        }
    }

    /**
     * 保存整合统计信息
     */
    suspend fun saveConsolidationStatistics(statistics: ConsolidationStatistics): Result<Unit> {
        return try {
            val entity = ConsolidationStatisticsEntity(
                characterId = statistics.characterId,
                totalDecisions = statistics.totalDecisions,
                totalConsolidated = statistics.totalConsolidated,
                avgLlmScore = statistics.avgLlmScore,
                avgConfidence = statistics.avgConfidence,
                lastUpdated = statistics.lastUpdated
            )
            memoryDatabase.memoryConsolidationDao().insertOrUpdateConsolidationStatistics(entity)
            Timber.d("[CharacterBookRepository] 保存整合统计成功: characterId=${statistics.characterId}, totalDecisions=${statistics.totalDecisions}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 保存整合统计失败")
            Result.failure(e)
        }
    }

    /**
     * 获取角色的最近整合决策
     */
    suspend fun getRecentConsolidationDecisions(characterId: String, limit: Int = 100): List<MemoryConsolidationDecision> {
        return try {
            val entities = memoryDatabase.memoryConsolidationDao().getRecentDecisions(characterId, limit)
            entities.map { entity ->
                MemoryConsolidationDecision(
                    characterId = entity.characterId,
                    memoryId = entity.memoryId,
                    wasConsolidated = entity.wasConsolidated,
                    llmScore = entity.llmScore,
                    confidence = entity.confidence,
                    memoryImportance = entity.memoryImportance,
                    memoryAccessCount = entity.memoryAccessCount,
                    memoryAgeDays = entity.memoryAgeDays,
                    reasoning = entity.reasoning,
                    timestamp = entity.timestamp
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 获取最近整合决策失败: $characterId")
            emptyList()
        }
    }

    /**
     * 清理过期的整合决策数据
     */
    suspend fun cleanupConsolidationData(characterId: String?, cutoffTime: Long): Result<Int> {
        return try {
            val dao = memoryDatabase.memoryConsolidationDao()
            var deletedCount = 0

            if (characterId != null) {
                dao.deleteOldDecisions(characterId, cutoffTime)
                dao.deleteOldPatternAnalyses(characterId, cutoffTime)
            } else {
                dao.cleanupOldDecisions(cutoffTime)
                dao.cleanupOldAnalyses(cutoffTime)
            }

            Timber.i("[CharacterBookRepository] 清理整合数据完成: characterId=$characterId")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBookRepository] 清理整合数据失败")
            Result.failure(e)
        }
    }

    // ==================== Helper Methods ====================

    /**
     * PersonEntity添加attribute的扩展方法
     */
    private fun PersonEntity.addAttribute(key: String, value: String) {
        attributes.add(AttributeEmbedded().apply {
            this.key = key
            this.value = value
        })
    }

    /**
     * PersonEntity转CharacterProfile
     */
    private fun PersonEntity.toCharacterProfile(): CharacterProfile {
        val attrs = attributes.associate { it.key to it.value }

        // 提取元数据（所有以 "metadata_" 开头的属性）
        val metadata = attrs.filterKeys { it.startsWith("metadata_") }
            .mapKeys { it.key.removePrefix("metadata_") }

        return CharacterProfile(
            basicInfo = BasicInfo(
                characterId = personId,
                name = name,
                aliases = attrs["aliases"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                nickname = attrs["nickname"],
                gender = attrs["gender"],
                age = attrs["age"]?.toIntOrNull(),
                bio = attrs["bio"] ?: "",
                voiceprintId = attrs["voiceprintId"],
                isStranger = attrs["isStranger"]?.toBoolean() ?: false,
                createdAt = createdAt,
                lastSeenAt = attrs["lastSeenTime"]?.toLongOrNull() ?: createdAt,
                metadata = metadata
            ),
            personality = Personality(
                traits = attrs["traits"]?.split(",")?.filter { it.isNotBlank() }?.associate { it to 0.5f } ?: emptyMap(),
                description = attrs["bio"] ?: ""
            ),
            preferences = Preferences(
                interests = attrs["interests"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            )
        )
    }

    /**
     * CharacterMemory转Metadata
     */
    private fun CharacterMemory.toMetadata(): Map<String, Any> = mapOf(
        "characterId" to characterId,
        "category" to category.name,
        "importance" to importance,
        "timestamp" to createdAt,
        "accessCount" to 0,
        "lastAccessTime" to System.currentTimeMillis()
    )

    /**
     * VectorSearchResult转CharacterMemory
     */
    private fun VectorSearchResult.toCharacterMemory(): CharacterMemory {
        val meta = metadata
        return CharacterMemory(
            memoryId = id,
            characterId = meta["characterId"] as? String ?: "",
            content = content,
            category = MemoryCategory.valueOf(meta["category"] as? String ?: "GENERAL"),
            importance = (meta["importance"] as? Number)?.toFloat() ?: 0.5f,
            createdAt = (meta["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

/**
 * 记忆统计信息
 */
data class MemoryStatistics(
    val totalMemories: Int = 0,
    val categoryDistribution: Map<String, Int> = emptyMap()
)
