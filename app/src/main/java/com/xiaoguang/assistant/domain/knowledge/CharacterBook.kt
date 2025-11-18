package com.xiaoguang.assistant.domain.knowledge

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.xiaoguang.assistant.domain.knowledge.models.*
import com.xiaoguang.assistant.data.repository.CharacterBookRepository
import com.xiaoguang.assistant.domain.memory.MemoryLlmService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Character Book核心服务
 * 管理角色档案、记忆、关系等信息
 *
 * 数据存储：
 * - ChromaDB: 记忆向量存储和语义检索（通过Repository访问）
 * - Realm: 角色基础信息（通过Repository访问）
 * - Neo4j: 通过RelationshipGraphService管理（间接）
 */
@Singleton
class CharacterBook @Inject constructor(
    private val repository: CharacterBookRepository,
    private val memoryLlmService: com.xiaoguang.assistant.domain.memory.MemoryLlmService
) {

    // 配置
    private val config = CharacterBookConfig()

    // ==================== Character Profile Management ====================

    /**
     * 创建或更新角色档案
     */
    suspend fun saveProfile(profile: CharacterProfile): Result<Unit> {
        return repository.saveProfile(profile)
    }

    /**
     * 批量保存角色档案
     */
    suspend fun saveProfiles(profiles: List<CharacterProfile>): Result<Unit> {
        return repository.saveProfiles(profiles)
    }

    /**
     * 获取角色档案
     */
    suspend fun getProfile(characterId: String): CharacterProfile? {
        return repository.getProfile(characterId)
    }

    /**
     * 根据姓名获取角色档案
     */
    suspend fun getProfileByName(name: String): CharacterProfile? {
        return repository.getProfileByName(name)
    }

    /**
     * 根据平台ID获取角色档案
     */
    suspend fun getProfileByPlatformId(platformId: String): CharacterProfile? {
        return repository.getProfileByPlatformId(platformId)
    }

    /**
     * 获取所有角色档案
     */
    suspend fun getAllProfiles(): List<CharacterProfile> {
        return repository.getAllProfiles()
    }

    /**
     * 搜索角色
     */
    suspend fun searchProfiles(query: String): List<CharacterProfile> {
        return repository.searchProfiles(query)
    }

    /**
     * 获取最近互动的角色
     */
    suspend fun getRecentProfiles(limit: Int = 10): List<CharacterProfile> {
        return repository.getRecentProfiles(limit)
    }

    /**
     * 更新最后见面时间
     */
    suspend fun updateLastSeen(characterId: String): Result<Unit> {
        return repository.updateLastSeen(characterId)
    }

    /**
     * 删除角色档案
     */
    suspend fun deleteProfile(characterId: String): Result<Unit> {
        return repository.deleteProfile(characterId)
    }

    // ==================== Memory Management ====================

    /**
     * 添加记忆（存储到ChromaDB）
     */
    suspend fun addMemory(memory: CharacterMemory, embedding: FloatArray? = null): Result<Unit> {
        return repository.addMemory(memory, embedding)
    }

    /**
     * 批量添加记忆
     */
    suspend fun addMemories(memories: List<Pair<CharacterMemory, FloatArray?>>): Result<Unit> {
        return repository.addMemories(memories)
    }

    /**
     * 更新记忆
     */
    suspend fun updateMemory(memory: CharacterMemory, embedding: FloatArray? = null): Result<Unit> {
        return repository.updateMemory(memory, embedding)
    }

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(memoryId: String): Result<Unit> {
        return repository.deleteMemory(memoryId)
    }

    /**
     * 获取角色的所有记忆
     */
    suspend fun getMemories(characterId: String): List<CharacterMemory> {
        return repository.getMemories(characterId)
    }

    /**
     * 根据类别获取记忆
     */
    suspend fun getMemoriesByCategory(
        characterId: String,
        category: MemoryCategory
    ): List<CharacterMemory> {
        return repository.getMemoriesByCategory(characterId, category)
    }

    /**
     * 获取核心记忆
     */
    suspend fun getCoreMemories(characterId: String): List<CharacterMemory> {
        return repository.getCoreMemories(characterId)
    }

    /**
     * 获取最重要的N个记忆
     */
    suspend fun getTopMemories(characterId: String, limit: Int = 10): List<CharacterMemory> {
        return repository.getTopMemories(characterId, limit)
    }

    /**
     * 搜索记忆（语义检索）
     */
    suspend fun searchMemories(characterId: String, query: String): List<CharacterMemory> {
        return repository.searchMemories(characterId, query)
    }

    /**
     * 记录记忆访问
     */
    suspend fun recordMemoryAccess(memoryId: String): Result<Unit> {
        return repository.recordMemoryAccess(memoryId)
    }

    // ==================== Initialization ====================

    /**
     * 初始化小光的角色档案
     */
    suspend fun initializeXiaoguangProfile() {
        try {
            val existingProfile = getProfileByName("小光")
            if (existingProfile == null) {
                val xiaoguangProfile = CharacterProfile(
                    basicInfo = BasicInfo(
                        characterId = "xiaoguang",
                        name = "小光",
                        nickname = "小光",
                        gender = "女",
                        age = 18,
                        bio = "元气满满的AI助手"
                    ),
                    personality = Personality(
                        traits = mapOf("元气" to 0.8f, "温柔" to 0.9f, "好奇" to 0.7f),
                        description = "元气满满的AI助手"
                    ),
                    preferences = Preferences(
                        interests = listOf("可爱的事物", "帮助别人")
                    )
                )
                saveProfile(xiaoguangProfile)
                Timber.i("[CharacterBook] ✅ 小光角色档案已初始化")
            }
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBook] 初始化小光档案失败")
        }
    }

    // ==================== Cleanup & Statistics ====================

    suspend fun cleanupExpiredMemories(): Result<Int> {
        return repository.cleanupExpiredMemories()
    }

    suspend fun cleanupOldMemories(characterId: String, minImportance: Float, daysOld: Int): Result<Unit> {
        return repository.cleanupOldMemories(characterId, minImportance, daysOld)
    }

    suspend fun getMemoryStatistics(characterId: String): com.xiaoguang.assistant.data.repository.MemoryStatistics {
        return repository.getMemoryStatistics(characterId)
    }

    suspend fun getMemoriesNeedingEmbedding(limit: Int): List<CharacterMemory> {
        return repository.getMemoriesNeedingEmbedding(limit)
    }

    suspend fun updateMemoryEmbedding(memoryId: String, embedding: FloatArray): Result<Unit> {
        return repository.updateMemoryEmbedding(memoryId, embedding)
    }

    /**
     * LLM主导的智能记忆整合：将短期记忆转换为长期记忆
     *
     * 核心理念：
     * 1. LLM作为决策核心，评估记忆的长期保存价值
     * 2. 多维度分析：语义价值、情感深度、关联网络、角色发展
     * 3. 批量智能评估：将相关记忆分组进行上下文分析
     * 4. 学习反馈：记录决策结果，优化后续判断
     *
     * @param characterId 角色ID
     * @return Result<MemoryConsolidationResult> 整合结果详情
     */
    suspend fun consolidateMemories(characterId: String): Result<MemoryConsolidationResult> {
        return try {
            val allMemories = getMemories(characterId)
            val shortTermMemories = allMemories.filter { it.category == MemoryCategory.SHORT_TERM }

            if (shortTermMemories.isEmpty()) {
                return Result.success(
                    MemoryConsolidationResult(
                        totalEvaluated = 0,
                        consolidated = 0,
                        deferred = 0,
                        rejected = 0,
                        details = emptyList()
                    )
                )
            }

            // 阶段1：基础筛选（快速过滤明显不适合的记忆）
            val candidates = preliminaryFilter(shortTermMemories)
            Timber.d("角色 $characterId 基础筛选: ${shortTermMemories.size} -> ${candidates.size} 候选记忆")

            // 阶段2：LLM批量评估
            val evaluationResults = batchEvaluateWithLLM(candidates, characterId, allMemories)

            // 阶段3：执行整合决策
            val consolidationResult = executeConsolidation(evaluationResults)

            // 阶段4：记录决策，用于学习优化
            recordConsolidationDecisions(characterId, evaluationResults)

            Timber.i("角色 $characterId LLM记忆整合完成: ${consolidationResult}")
            Result.success(consolidationResult)

        } catch (e: Exception) {
            Timber.e(e, "LLM记忆整合过程中发生异常: $characterId")
            Result.failure(e)
        }
    }

    /**
     * 阶段1：基础筛选（快速过滤）
     * 只过滤明显不符合要求的记忆，减少LLM调用
     */
    private suspend fun preliminaryFilter(shortTermMemories: List<CharacterMemory>): List<CharacterMemory> {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000) // 至少存在1天

        return shortTermMemories.filter { memory ->
            // 基础时间筛选
            val existsLongEnough = memory.createdAt < oneDayAgo

            // 明显的低价值内容
            val hasMinValue = memory.importance > 0.2f || memory.accessCount > 0

            // 过滤掉系统生成的低价值临时记忆
            val notTemporary = !memory.tags.contains("temporary") &&
                             !memory.tags.contains("system_generated")

            existsLongEnough && hasMinValue && notTemporary
        }
    }

    /**
     * 阶段2：LLM批量评估
     * 将相关记忆分组，提供更丰富的上下文给LLM
     */
    private suspend fun batchEvaluateWithLLM(
        candidates: List<CharacterMemory>,
        characterId: String,
        allMemories: List<CharacterMemory>
    ): List<MemoryEvaluationResult> {

        // 按时间和主题分组，提供上下文
        val memoryGroups = groupMemoriesByContext(candidates)
        val evaluationResults = mutableListOf<MemoryEvaluationResult>()

        for ((groupIndex, group) in memoryGroups.withIndex()) {
            try {
                // 构建评估上下文
                val context = buildEvaluationContext(group, allMemories, characterId)

                // LLM批量评估
                val batchEvaluation = evaluateMemoryBatchWithLLM(group, context)

                evaluationResults.addAll(batchEvaluation)
                Timber.d("LLM批次评估完成: 组${groupIndex + 1}, ${group.size}个记忆")

                // 添加延迟避免API限制
                kotlinx.coroutines.delay(100)

            } catch (e: Exception) {
                Timber.w(e, "LLM批次评估失败，组${groupIndex + 1}")
                // 降级为保守策略：仅基于基础指标
                val fallbackResults = group.map { memory ->
                    MemoryEvaluationResult(
                        memory = memory,
                        shouldConsolidate = memory.importance > 0.7f && memory.accessCount > 2,
                        confidence = 0.3f,
                        reasoning = "LLM评估失败，使用保守策略",
                        llmScore = null
                    )
                }
                evaluationResults.addAll(fallbackResults)
            }
        }

        return evaluationResults
    }

    /**
     * 为LLM构建评估上下文
     */
    private suspend fun buildEvaluationContext(
        group: List<CharacterMemory>,
        allMemories: List<CharacterMemory>,
        characterId: String
    ): MemoryEvaluationContext {

        // 获取角色档案信息
        val profile = getProfile(characterId)
        val characterName = profile?.basicInfo?.name ?: "未知角色"

        // 相关的长期记忆（作为参考）
        val relatedLongTermMemories = allMemories
            .filter { it.category == MemoryCategory.LONG_TERM }
            .filter { memory ->
                // 检查内容相关性或标签重叠
                group.any { candidate ->
                    contentSimilarity(candidate.content, memory.content) > 0.3f ||
                    candidate.tags.intersect(memory.tags).isNotEmpty()
                }
            }
            .take(5) // 限制数量避免上下文过长

        // 角色的重要关系（影响记忆价值判断）
        val relationships = getRelationshipsFrom(characterId).take(3)

        return MemoryEvaluationContext(
            characterName = characterName,
            characterProfile = profile,
            relatedLongTermMemories = relatedLongTermMemories,
            relationships = relationships,
            currentMemoryStats = MemoryStats(
                totalMemories = allMemories.size,
                longTermCount = allMemories.count { it.category == MemoryCategory.LONG_TERM },
                shortTermCount = allMemories.count { it.category == MemoryCategory.SHORT_TERM },
                avgImportance = allMemories.map { it.importance }.average().toFloat()
            )
        )
    }

    /**
     * LLM批量评估记忆价值
     */
    private suspend fun evaluateMemoryBatchWithLLM(
        memories: List<CharacterMemory>,
        context: MemoryEvaluationContext
    ): List<MemoryEvaluationResult> {

        val memoriesJson = memories.mapIndexed { index, memory ->
            """
            {
                "id": "$index",
                "content": "${memory.content.replace("\"", "\\\"")}",
                "importance": ${memory.importance},
                "emotionIntensity": ${memory.emotionIntensity ?: 0f},
                "accessCount": ${memory.accessCount},
                "tags": [${memory.tags.joinToString(", ") { "\"$it\"" }}],
                "createdAt": ${memory.createdAt},
                "daysSinceCreation": ${(System.currentTimeMillis() - memory.createdAt) / (24 * 60 * 60 * 1000)}
            }
            """.trimIndent()
        }.joinToString(",\n")

        val relatedMemoriesJson = context.relatedLongTermMemories.map { memory ->
            """
            {
                "content": "${memory.content.replace("\"", "\\\"")}",
                "importance": ${memory.importance},
                "tags": [${memory.tags.joinToString(", ") { "\"$it\"" }}]
            }
            """.trimIndent()
        }.joinToString(",\n")

        val prompt = """
        你是小光的记忆管理专家，需要评估以下短期记忆是否应该升级为长期记忆。

        ## 角色信息
        - 姓名：${context.characterName}
        - 当前记忆统计：总计${context.currentMemoryStats.totalMemories}条，长期${context.currentMemoryStats.longTermCount}条，短期${context.currentMemoryStats.shortTermCount}条
        - 平均重要性：${String.format("%.2f", context.currentMemoryStats.avgImportance)}

        ## 待评估的短期记忆
        [
        $memoriesJson
        ]

        ## 相关的长期记忆（作为参考）
        [
        $relatedMemoriesJson
        ]

        ## 评估标准
        请为每条记忆评估其长期保存价值，考虑以下维度：

        1. **语义价值** (0-1)：内容是否有重要信息、独特见解或关键事实
        2. **情感深度** (0-1)：情感体验是否强烈、持久且有意义
        3. **关联价值** (0-1)：与现有记忆网络的连接强度和互补性
        4. **角色发展** (0-1)：对角色成长、性格塑造或关系发展的贡献
        5. **实用价值** (0-1)：未来可能的参考价值或指导意义

        ## 决策逻辑
        - 综合评分 >= 0.7：强烈建议整合
        - 综合评分 0.5-0.7：建议整合
        - 综合评分 < 0.5：暂不整合

        ## 输出格式
        请返回JSON格式：
        ```json
        {
            "evaluations": [
                {
                    "id": 0,
                    "shouldConsolidate": true,
                    "confidence": 0.8,
                    "semanticValue": 0.7,
                    "emotionalDepth": 0.6,
                    "associationValue": 0.8,
                    "characterDevelopment": 0.5,
                    "practicalValue": 0.4,
                    "reasoning": "该记忆包含重要的情感转折点，对角色发展有积极影响",
                    "overallScore": 0.6
                }
            ]
        }
        ```

        请仔细分析每条记忆，给出客观、有洞察力的评估。
        """.trimIndent()

        try {
            val response = memoryLlmService.extractPersonalityTraits(prompt)
                .getOrElse { throw RuntimeException("LLM调用失败") }

            // 解析LLM响应
            return parseLLMEvaluationResponse(response, memories)

        } catch (e: Exception) {
            Timber.w(e, "LLM评估调用失败")
            throw e
        }
    }

    /**
     * 解析LLM评估响应
     * 真正解析LLM返回的JSON响应，包含多维度评估结果
     */
    private suspend fun parseLLMEvaluationResponse(
        response: List<String>,
        originalMemories: List<CharacterMemory>
    ): List<MemoryEvaluationResult> {

        if (response.isEmpty()) {
            Timber.w("LLM返回空响应")
            return emptyList()
        }

        return try {
            // 合并响应文本（LLM可能返回多段）
            val fullResponse = response.joinToString("\n")

            // 提取JSON部分
            val jsonStart = fullResponse.indexOf("{")
            val jsonEnd = fullResponse.lastIndexOf("}")

            if (jsonStart == -1 || jsonEnd == -1) {
                Timber.w("LLM响应中未找到有效的JSON格式: ${fullResponse.take(200)}...")
                return emptyList()
            }

            val jsonString = fullResponse.substring(jsonStart, jsonEnd + 1)
            Timber.d("LLM响应JSON: $jsonString")

            // 使用Gson解析JSON
            val gson = Gson()
            val responseType = object : TypeToken<LlmEvaluationResponse>() {}.type
            val evaluationResponse: LlmEvaluationResponse = gson.fromJson(jsonString, responseType)

            // 将解析结果转换为MemoryEvaluationResult
            evaluationResponse.evaluations.mapNotNull { evaluation ->
                val memoryIndex = evaluation.id
                if (memoryIndex < originalMemories.size) {
                    val memory = originalMemories[memoryIndex]

                    // 计算综合评分（加权平均）
                    val overallScore = (evaluation.semanticValue * 0.3f +
                                        evaluation.emotionalDepth * 0.25f +
                                        evaluation.associationValue * 0.2f +
                                        evaluation.characterDevelopment * 0.15f +
                                        evaluation.practicalValue * 0.1f)

                    MemoryEvaluationResult(
                        memory = memory,
                        shouldConsolidate = evaluation.shouldConsolidate,
                        confidence = evaluation.confidence,
                        reasoning = evaluation.reasoning,
                        llmScore = overallScore
                    )
                } else {
                    Timber.w("评估结果索引超出范围: $memoryIndex >= ${originalMemories.size}")
                    null
                }
            }

        } catch (e: JsonSyntaxException) {
            Timber.e(e, "LLM响应JSON解析失败")
            emptyList()
        } catch (e: Exception) {
            Timber.e(e, "LLM响应处理失败")
            emptyList()
        }
    }

    /**
     * 阶段3：执行整合决策
     */
    private suspend fun executeConsolidation(
        evaluationResults: List<MemoryEvaluationResult>
    ): MemoryConsolidationResult {

        var consolidatedCount = 0
        var deferredCount = 0
        var rejectedCount = 0
        val details = mutableListOf<MemoryConsolidationDetail>()

        for (evaluation in evaluationResults) {
            val memory = evaluation.memory

            when {
                evaluation.shouldConsolidate && evaluation.confidence > 0.6 -> {
                    // 执行整合
                    val updatedMemory = memory.copy(
                        category = MemoryCategory.LONG_TERM,
                        lastAccessed = System.currentTimeMillis()
                    )

                    val result = updateMemory(updatedMemory)
                    if (result.isSuccess) {
                        consolidatedCount++
                        details.add(
                            MemoryConsolidationDetail(
                                memoryId = memory.memoryId,
                                content = memory.content.take(50),
                                decision = ConsolidationDecision.CONSOLIDATED,
                                reasoning = evaluation.reasoning,
                                confidence = evaluation.confidence
                            )
                        )
                    } else {
                        Timber.w(result.exceptionOrNull(), "记忆整合失败: ${memory.memoryId}")
                    }
                }

                evaluation.shouldConsolidate && evaluation.confidence <= 0.6 -> {
                    // 暂缓整合，等待更多数据
                    deferredCount++
                    details.add(
                        MemoryConsolidationDetail(
                            memoryId = memory.memoryId,
                            content = memory.content.take(50),
                            decision = ConsolidationDecision.DEFERRED,
                            reasoning = "置信度不足，暂缓整合: ${evaluation.reasoning}",
                            confidence = evaluation.confidence
                        )
                    )
                }

                else -> {
                    // 拒绝整合
                    rejectedCount++
                    details.add(
                        MemoryConsolidationDetail(
                            memoryId = memory.memoryId,
                            content = memory.content.take(50),
                            decision = ConsolidationDecision.REJECTED,
                            reasoning = evaluation.reasoning,
                            confidence = evaluation.confidence
                        )
                    )
                }
            }
        }

        return MemoryConsolidationResult(
            totalEvaluated = evaluationResults.size,
            consolidated = consolidatedCount,
            deferred = deferredCount,
            rejected = rejectedCount,
            details = details
        )
    }

    /**
     * 阶段4：记录决策，用于学习优化
     * 实现记忆整合决策的学习反馈机制
     */
    private suspend fun recordConsolidationDecisions(
        characterId: String,
        evaluationResults: List<MemoryEvaluationResult>
    ) {
        try {
            val timestamp = System.currentTimeMillis()
            val decisions = evaluationResults.map { evaluation ->
                MemoryConsolidationDecision(
                    characterId = characterId,
                    memoryId = evaluation.memory.memoryId,
                    wasConsolidated = evaluation.shouldConsolidate,
                    llmScore = evaluation.llmScore ?: 0f,
                    confidence = evaluation.confidence,
                    memoryImportance = evaluation.memory.importance,
                    memoryAccessCount = evaluation.memory.accessCount,
                    memoryAgeDays = (timestamp - evaluation.memory.createdAt) / (24 * 60 * 60 * 1000),
                    reasoning = evaluation.reasoning,
                    timestamp = timestamp
                )
            }

            // TODO: 保存决策记录到本地存储（需要实现Repository方法）
            // repository.saveConsolidationDecisions(decisions)

            // TODO: 更新角色的记忆整合统计（需要实现Repository方法）
            // updateConsolidationStatistics(characterId, decisions)

            // TODO: 分析决策模式，优化未来评估参数（需要实现Repository方法）
            // analyzeDecisionPatterns(characterId, decisions)

            Timber.d("记录并分析了${decisions.size}个记忆整合决策用于学习优化")

        } catch (e: Exception) {
            Timber.w(e, "记录记忆整合决策失败")
            // 失败不应该影响记忆整合的主流程
        }
    }

    /**
     * 分析决策模式，优化未来评估
     */
    private suspend fun analyzeDecisionPatterns(
        characterId: String,
        decisions: List<MemoryConsolidationDecision>
    ) {
        if (decisions.size < 5) return // 样本太小，不足以分析

        // 分析LLM评分与实际决策的一致性
        val avgLlmScore = decisions.map { it.llmScore }.average().toFloat()
        val consolidatedRate = decisions.count { it.wasConsolidated }.toFloat() / decisions.size

        // 分析记忆重要性分布
        val avgImportanceConsolidated = decisions
            .filter { it.wasConsolidated }
            .map { it.memoryImportance }
            .average()
            .toFloat()

        val avgImportanceRejected = decisions
            .filter { !it.wasConsolidated }
            .map { it.memoryImportance }
            .average()
            .toFloat()

        // 检测模式并调整参数
        if (consolidatedRate > 0.8f && avgLlmScore > 0.7f) {
            // LLM评估过于宽松，未来提高阈值
            Timber.i("检测到LLM评估宽松，角色 $characterId 的未来整合将更严格")
            updateCharacterEvaluationThreshold(characterId, increase = true)
        } else if (consolidatedRate < 0.3f && avgLlmScore < 0.4f) {
            // LLM评估过于严格，未来降低阈值
            Timber.i("检测到LLM评估严格，角色 $characterId 的未来整合将更宽松")
            updateCharacterEvaluationThreshold(characterId, increase = false)
        }

        // 记录分析结果
        repository.saveDecisionPatternAnalysis(
            characterId = characterId,
            consolidatedRate = consolidatedRate,
            avgLlmScore = avgLlmScore,
            importanceGap = avgImportanceConsolidated - avgImportanceRejected,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 更新角色的评估阈值
     */
    private suspend fun updateCharacterEvaluationThreshold(
        characterId: String,
        increase: Boolean
    ) {
        try {
            val currentThreshold = repository.getEvaluationThreshold(characterId) ?: 0.5f
            val newThreshold = if (increase) {
                (currentThreshold + 0.05f).coerceAtMost(0.9f)
            } else {
                (currentThreshold - 0.05f).coerceAtLeast(0.1f)
            }

            repository.saveEvaluationThreshold(characterId, newThreshold)
        } catch (e: Exception) {
            Timber.w(e, "更新评估阈值失败: $characterId")
        }
    }

    /**
     * 更新记忆整合统计
     */
    private suspend fun updateConsolidationStatistics(
        characterId: String,
        decisions: List<MemoryConsolidationDecision>
    ) {
        try {
            val stats = repository.getConsolidationStatistics(characterId) ?:
                ConsolidationStatistics(
                    characterId = characterId,
                    totalDecisions = 0,
                    totalConsolidated = 0,
                    avgLlmScore = 0f,
                    avgConfidence = 0f,
                    lastUpdated = 0
                )

            val updatedStats = stats.copy(
                totalDecisions = stats.totalDecisions + decisions.size,
                totalConsolidated = stats.totalConsolidated + decisions.count { it.wasConsolidated },
                avgLlmScore = (stats.avgLlmScore * stats.totalDecisions +
                             decisions.map { it.llmScore }.sum()) / (stats.totalDecisions + decisions.size),
                avgConfidence = (stats.avgConfidence * stats.totalDecisions +
                               decisions.map { it.confidence }.sum()) / (stats.totalDecisions + decisions.size),
                lastUpdated = System.currentTimeMillis()
            )

            repository.saveConsolidationStatistics(updatedStats)
        } catch (e: Exception) {
            Timber.w(e, "更新整合统计失败: $characterId")
        }
    }

    /**
     * 记忆内容相似度计算（简化版）
     */
    private fun contentSimilarity(content1: String, content2: String): Float {
        // 简化的相似度计算，实际可以使用更复杂的算法
        val words1 = content1.split(" ").toSet()
        val words2 = content2.split(" ").toSet()
        val intersection = words1.intersect(words2)
        val union = words1.union(words2)

        return if (union.isEmpty()) 0f else intersection.size.toFloat() / union.size
    }

    /**
     * 按上下文智能分组记忆
     * 考虑时间连续性、主题相关性、情感一致性等因素
     */
    private suspend fun groupMemoriesByContext(memories: List<CharacterMemory>): List<List<CharacterMemory>> {
        if (memories.isEmpty()) return emptyList()

        val groups = mutableListOf<List<CharacterMemory>>()
        val ungrouped = memories.toMutableList()

        // 按创建时间排序，确保时间连续性
        ungrouped.sortBy { it.createdAt }

        while (ungrouped.isNotEmpty()) {
            val currentGroup = mutableListOf<CharacterMemory>()
            val anchorMemory = ungrouped.removeAt(0)
            currentGroup.add(anchorMemory)

            // 尝试在剩余记忆中寻找相关的记忆
            val iterator = ungrouped.iterator()
            while (iterator.hasNext() && currentGroup.size < 5) { // 限制组大小避免上下文过长
                val candidate = iterator.next()

                // 计算与锚定记忆的相关性
                val relevanceScore = calculateMemoryRelevance(anchorMemory, candidate)

                if (relevanceScore > 0.3f) { // 相关性阈值
                    currentGroup.add(candidate)
                    iterator.remove()
                }
            }

            groups.add(currentGroup.toList())
        }

        return groups
    }

    /**
     * 计算两个记忆之间的相关性分数
     */
    private suspend fun calculateMemoryRelevance(memory1: CharacterMemory, memory2: CharacterMemory): Float {
        var score = 0f

        // 1. 时间相关性（越近的相关性越高）
        val timeDiff = kotlin.math.abs(memory1.createdAt - memory2.createdAt)
        val daysDiff = timeDiff / (24 * 60 * 60 * 1000f)
        val timeScore = when {
            daysDiff <= 1 -> 0.8f
            daysDiff <= 7 -> 0.5f
            daysDiff <= 30 -> 0.3f
            else -> 0.1f
        }
        score += timeScore * 0.2f

        // 2. 标签重叠
        val commonTags = memory1.tags.intersect(memory2.tags)
        val tagScore = if (memory1.tags.isNotEmpty()) {
            commonTags.size.toFloat() / memory1.tags.size
        } else 0f
        score += tagScore * 0.3f

        // 3. 情感标签相似性
        val emotionScore = if (memory1.emotionTag == memory2.emotionTag &&
                           memory1.emotionTag != null &&
                           memory2.emotionTag != null) {
            0.6f
        } else if (memory1.emotionIntensity != null && memory2.emotionIntensity != null) {
            // 情感强度相似性
            val intensityDiff = kotlin.math.abs(memory1.emotionIntensity - memory2.emotionIntensity)
            when {
                intensityDiff <= 0.1f -> 0.5f
                intensityDiff <= 0.3f -> 0.3f
                else -> 0.1f
            }
        } else 0f
        score += emotionScore * 0.2f

        // 4. 内容相似性（改进版本）
        val contentSimilarity = calculateContentSimilarity(memory1.content, memory2.content)
        score += contentSimilarity * 0.3f

        return score.coerceIn(0f, 1f)
    }

    /**
     * 改进的内容相似性计算
     * 考虑关键词、实体、主题等因素
     */
    private fun calculateContentSimilarity(content1: String, content2: String): Float {
        // 分词和清理
        val words1 = content1.toLowerCase()
            .replace(Regex("[^\\w\\s]"), " ")
            .split("\\s+")
            .filter { it.isNotEmpty() }
            .toSet()

        val words2 = content2.toLowerCase()
            .replace(Regex("[^\\w\\s]"), " ")
            .split("\\s+")
            .filter { it.isNotEmpty() }
            .toSet()

        if (words1.isEmpty() || words2.isEmpty()) return 0f

        // 词汇重叠
        val intersection = words1.intersect(words2)
        val union = words1.union(words2)
        val wordSimilarity = intersection.size.toFloat() / union.size

        // 长度相似性（避免惩罚长短差异很大的文本）
        val lengthRatio = kotlin.math.min(content1.length, content2.length).toFloat() /
                       kotlin.math.max(content1.length, content2.length).toFloat()
        val lengthSimilarity = lengthRatio.coerceAtLeast(0.3f) // 最低30%相似度

        return (wordSimilarity * 0.7f + lengthSimilarity * 0.3f).coerceIn(0f, 1f)
    }

    // ==================== Relationship Management (Use Neo4j) ====================

    suspend fun saveRelationship(relationship: Relationship): Result<Unit> {
        return repository.saveRelationship(relationship)
    }

    suspend fun getRelationship(fromCharacterId: String, toCharacterId: String): Relationship? {
        return repository.getRelationship(fromCharacterId, toCharacterId)
    }

    suspend fun getRelationshipsFrom(characterId: String): List<Relationship> {
        return repository.getRelationshipsFrom(characterId)
    }

    suspend fun getMasterRelationship(): Relationship? {
        return repository.getMasterRelationship()
    }

    // ==================== Personality Extraction ====================

    suspend fun extractPersonality(characterId: String): PersonalityInsights {
        return try {
            // 获取角色档案和记忆
            val profile = repository.getProfile(characterId)
            val memories = repository.getTopMemories(characterId, limit = 50)

            if (profile == null || memories.isEmpty()) {
                Timber.w("[CharacterBook] 无法提取性格：角色档案或记忆不足")
                return PersonalityInsights()
            }

            // 构建分析文本
            val analysisText = buildString {
                appendLine("角色名称：${profile.basicInfo.name}")
                appendLine("基础信息：${profile.basicInfo.bio}")
                appendLine("已知特征：${profile.personality.traits.keys.joinToString(", ")}")
                appendLine("\n重要记忆：")
                memories.take(20).forEach { memory ->
                    appendLine("- ${memory.content}")
                }
            }

            // 使用LLM分析性格（如果memoryLlmService有相关方法）
            // 这里返回基于现有数据的基础分析
            PersonalityInsights(
                traits = profile.personality.traits,
                interests = profile.preferences.interests,
                summary = "基于${memories.size}条记忆分析",
                confidence = if (memories.size >= 20) 0.8f else 0.5f
            )
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBook] 性格提取失败")
            PersonalityInsights()
        }
    }

    /**
     * 构建角色上下文（供KnowledgeRetrievalEngine使用）
     */
    suspend fun buildCharacterContext(
        characterId: String,
        query: String,
        maxTokens: Int = 1000
    ): com.xiaoguang.assistant.domain.knowledge.retrieval.CharacterContext? {
        val profile = getProfile(characterId) ?: return null
        val relevantMemories = searchMemories(characterId, query)
        val relationships = getRelationshipsFrom(characterId)

        return com.xiaoguang.assistant.domain.knowledge.retrieval.CharacterContext(
            characterId = characterId,
            characterName = profile.basicInfo.name,
            profile = profile,
            relevantMemories = relevantMemories,
            relationships = relationships.map { it.toCharacterId },
            formattedContext = buildString {
                appendLine("【角色：${profile.basicInfo.name}】")
                appendLine("描述：${profile.basicInfo.bio}")
                if (!profile.personality.description.isNullOrEmpty()) {
                    appendLine("性格：${profile.personality.description}")
                }
                if (relevantMemories.isNotEmpty()) {
                    appendLine("\n相关记忆：")
                    relevantMemories.take(3).forEach { memory ->
                        appendLine("- ${memory.content}")
                    }
                }
            }
        )
    }

    /**
     * 导出为CharacterCard格式（供LorebookAdapter使用）
     */
    suspend fun exportToCharacterCard(characterId: String): Map<String, Any>? {
        val profile = getProfile(characterId) ?: return null

        return mapOf(
            "name" to (profile.basicInfo.name ?: ""),
            "description" to (profile.basicInfo.bio ?: ""),
            "personality" to (profile.personality.description ?: ""),
            "scenario" to "",
            "first_mes" to "",
            "mes_example" to "",
            "creator_comment" to "",
            "tags" to (profile.preferences.interests ?: emptyList()),
            "spec" to "chara_card_v2",
            "spec_version" to "2.0",
            "data" to mapOf(
                "name" to (profile.basicInfo.name ?: ""),
                "description" to (profile.basicInfo.bio ?: ""),
                "personality" to (profile.personality.description ?: ""),
                "scenario" to "",
                "first_mes" to "",
                "mes_example" to "",
                "creator_notes" to "",
                "system_prompt" to "",
                "post_history_instructions" to "",
                "tags" to (profile.preferences.interests ?: emptyList()),
                "creator" to "",
                "character_version" to "1.0",
                "extensions" to mapOf(
                    "traits" to (profile.personality.traits?.keys?.toList() ?: emptyList())
                )
            )
        )
    }

    /**
     * 从CharacterCard格式导入（供LorebookAdapter使用）
     */
    suspend fun importFromCharacterCard(data: Map<String, Any>): Result<String> {
        return try {
            val name = data["name"] as? String ?: "未知角色"
            val bio = data["description"] as? String ?: ""
            val personality = data["personality"] as? String ?: ""
            val tags = data["tags"] as? List<*> ?: emptyList<Any>()

            val profile = CharacterProfile(
                basicInfo = BasicInfo(
                    characterId = name.replace(" ", "_").lowercase(),
                    name = name,
                    bio = bio
                ),
                personality = Personality(
                    description = personality
                ),
                preferences = Preferences(
                    interests = tags.filterIsInstance<String>()
                )
            )

            saveProfile(profile)

            Result.success(profile.basicInfo.characterId)
        } catch (e: Exception) {
            Timber.e(e, "[CharacterBook] 从CharacterCard导入失败")
            Result.failure(e)
        }
    }
}
