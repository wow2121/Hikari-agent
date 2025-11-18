package com.xiaoguang.assistant.domain.relationship

import com.xiaoguang.assistant.domain.flow.service.FlowLlmService
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.WorldBook
import com.xiaoguang.assistant.domain.usecase.RelationshipNetworkManagementUseCase
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 3: 关系推理引擎
 * 基于已知信息智能推断隐含关系
 *
 * 推理类型：
 * 1. 传递性推理：A是B的朋友，B是C的朋友 → A和C可能认识
 * 2. 层级推理：A是某公司员工，B是该公司老板 → A和B是上下级关系
 * 3. 家庭关系推理：A是B的父亲，B是C的儿子 → A是C的爷爷
 * 4. 社交圈推理：A、B、C经常一起出现 → 他们是同一个朋友圈
 * 5. 角色关系推理：从WorldBook背景知识推断职业/身份关系
 */
@Singleton
class RelationshipInferenceEngine @Inject constructor(
    private val characterBook: CharacterBook,
    private val worldBook: WorldBook,
    private val relationshipNetworkUseCase: RelationshipNetworkManagementUseCase,
    private val flowLlmService: FlowLlmService,
    private val memoryLlmService: com.xiaoguang.assistant.domain.memory.MemoryLlmService
) {

    /**
     * 执行完整的关系推理
     * @param triggerPerson 触发推理的人物（可选）
     * @return 推理出的新关系列表
     */
    suspend fun performInference(triggerPerson: String? = null): List<InferredRelation> {
        Timber.d("[RelationshipInferenceEngine] 开始关系推理${triggerPerson?.let { ": $it" } ?: ""}")

        val inferredRelations = mutableListOf<InferredRelation>()

        try {
            // 1. 传递性推理
            inferredRelations.addAll(inferTransitiveRelations(triggerPerson))

            // 2. 家庭关系推理
            inferredRelations.addAll(inferFamilyRelations(triggerPerson))

            // 3. 职业层级推理
            inferredRelations.addAll(inferHierarchyRelations(triggerPerson))

            // 4. 社交圈推理
            inferredRelations.addAll(inferCommunityRelations(triggerPerson))

            // 5. 基于WorldBook的背景推理
            inferredRelations.addAll(inferFromWorldKnowledge(triggerPerson))

            // ==================== 新增推理类型 ====================

            // 6. 时序推理（基于事件顺序）
            inferredRelations.addAll(inferTemporalRelations(triggerPerson))

            // 7. 地理推理（基于地点共现）
            inferredRelations.addAll(inferGeographicRelations(triggerPerson))

            // 8. LLM语义推理（深度分析）
            inferredRelations.addAll(inferWithLLMSemanticAnalysis(triggerPerson))

            Timber.d("[RelationshipInferenceEngine] 推理完成（含新增推理），发现 ${inferredRelations.size} 个潜在关系")

            return inferredRelations

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 关系推理失败")
            return emptyList()
        }
    }

    /**
     * 传递性推理：朋友的朋友
     * A-B-C，如果A和C之间没有直接关系，推断他们可能认识
     */
    private suspend fun inferTransitiveRelations(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            // 获取所有人物
            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                listOf(triggerPerson)
            } else {
                allProfiles.map { it.basicInfo.name }
            }

            for (personA in peopleToCheck) {
                // 获取A的所有朋友
                val aFriends = relationshipNetworkUseCase.getPersonRelations(personA)
                    .map { if (it.personA == personA) it.personB else it.personA }

                // 对于A的每个朋友B，检查B的朋友C
                for (personB in aFriends) {
                    val bFriends = relationshipNetworkUseCase.getPersonRelations(personB)
                        .map { if (it.personA == personB) it.personB else it.personA }

                    for (personC in bFriends) {
                        // 如果A和C没有直接关系，推断他们可能认识
                        if (personC != personA) {
                            val existingRelation = relationshipNetworkUseCase.getRelationBetween(personA, personC)
                            if (existingRelation == null) {
                                inferred.add(InferredRelation(
                                    personA = personA,
                                    personB = personC,
                                    inferredType = "可能认识",
                                    confidence = 0.4f,
                                    reasoning = "$personA 和 $personC 都认识 $personB，他们可能相互认识",
                                    inferenceMethod = "传递性推理",
                                    evidenceChain = listOf(personA, personB, personC)
                                ))
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 传递性推理失败")
        }

        return inferred
    }

    /**
     * 家庭关系推理
     * 基于已知的家庭关系推断其他家庭成员
     */
    private suspend fun inferFamilyRelations(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            // 家庭关系推理规则
            val familyRules = mapOf(
                // 父子 + 父子 → 爷孙
                Pair("父子", "父子") to "爷孙",
                Pair("父女", "父子") to "爷孙",
                Pair("父子", "父女") to "爷孙",
                // 母子 + 母子 → 祖孙
                Pair("母子", "母子") to "祖孙",
                Pair("母女", "母子") to "祖孙",
                // 夫妻 + 父子 → 父子/母子
                Pair("夫妻", "父子") to "母子",
                Pair("夫妻", "父女") to "母女",
                // 兄弟 + 父子 → 叔侄
                Pair("兄弟", "父子") to "叔侄",
                Pair("兄弟", "父女") to "叔侄",
                // 姐妹 + 母子 → 姨侄
                Pair("姐妹", "母子") to "姨侄",
                Pair("姐妹", "母女") to "姨侄"
            )

            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                listOf(triggerPerson)
            } else {
                allProfiles.map { it.basicInfo.name }
            }

            for (personA in peopleToCheck) {
                val aRelations = relationshipNetworkUseCase.getPersonRelations(personA)

                for (relAB in aRelations) {
                    val personB = if (relAB.personA == personA) relAB.personB else relAB.personA
                    val bRelations = relationshipNetworkUseCase.getPersonRelations(personB)

                    for (relBC in bRelations) {
                        val personC = if (relBC.personA == personB) relBC.personB else relBC.personA

                        if (personC != personA) {
                            // 检查是否符合推理规则
                            val relationPair = Pair(relAB.relationType, relBC.relationType)
                            val inferredType = familyRules[relationPair]

                            if (inferredType != null) {
                                val existingRelation = relationshipNetworkUseCase.getRelationBetween(personA, personC)
                                if (existingRelation == null) {
                                    inferred.add(InferredRelation(
                                        personA = personA,
                                        personB = personC,
                                        inferredType = inferredType,
                                        confidence = 0.7f,
                                        reasoning = "$personA 是 $personB 的 ${relAB.relationType}，$personB 是 $personC 的 ${relBC.relationType}，因此 $personA 和 $personC 是 $inferredType",
                                        inferenceMethod = "家庭关系推理",
                                        evidenceChain = listOf(personA, personB, personC)
                                    ))
                                }
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 家庭关系推理失败")
        }

        return inferred
    }

    /**
     * 职业层级推理
     * 基于职位、公司等信息推断上下级关系
     */
    private suspend fun inferHierarchyRelations(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                allProfiles.filter { it.basicInfo.name == triggerPerson }
            } else {
                allProfiles
            }

            // 职位层级表
            val hierarchyLevels = mapOf(
                "CEO" to 5,
                "总经理" to 5,
                "老板" to 5,
                "副总" to 4,
                "总监" to 4,
                "经理" to 3,
                "主管" to 2,
                "员工" to 1,
                "实习生" to 0
            )

            for (profileA in peopleToCheck) {
                val personA = profileA.basicInfo.name
                val bioA = profileA.basicInfo.bio ?: ""

                // 从bio中提取职位和公司
                val (jobA, companyA) = extractJobAndCompany(bioA)

                if (jobA != null && companyA != null) {
                    // 查找同公司的其他人
                    for (profileB in allProfiles) {
                        if (profileB.basicInfo.characterId == profileA.basicInfo.characterId) continue

                        val personB = profileB.basicInfo.name
                        val bioB = profileB.basicInfo.bio ?: ""
                        val (jobB, companyB) = extractJobAndCompany(bioB)

                        if (jobB != null && companyB == companyA) {
                            // 同公司，比较职位层级
                            val levelA = hierarchyLevels[jobA] ?: 1
                            val levelB = hierarchyLevels[jobB] ?: 1

                            val existingRelation = relationshipNetworkUseCase.getRelationBetween(personA, personB)

                            if (existingRelation == null && levelA != levelB) {
                                val relationType = if (levelA > levelB) "上下级" else "下属"
                                val superior = if (levelA > levelB) personA else personB
                                val subordinate = if (levelA > levelB) personB else personA

                                inferred.add(InferredRelation(
                                    personA = superior,
                                    personB = subordinate,
                                    inferredType = relationType,
                                    confidence = 0.6f,
                                    reasoning = "$superior 是 $companyA 的 ${if (levelA > levelB) jobA else jobB}，$subordinate 是 ${if (levelA > levelB) jobB else jobA}，存在上下级关系",
                                    inferenceMethod = "职业层级推理",
                                    evidenceChain = listOf(personA, personB)
                                ))
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 职业层级推理失败")
        }

        return inferred
    }

    /**
     * 社交圈推理
     * 基于共同出现频率推断关系
     */
    private suspend fun inferCommunityRelations(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            // 获取所有人的记忆，统计共同出现
            val allProfiles = characterBook.getAllProfiles()
            val coOccurrenceMap = mutableMapOf<Pair<String, String>, Int>()

            for (profile in allProfiles) {
                val memories = characterBook.getMemories(profile.basicInfo.characterId)

                for (memory in memories) {
                    // 从记忆内容中提取提到的人名
                    val mentionedPeople = allProfiles
                        .map { it.basicInfo.name }
                        .filter { name ->
                            name != profile.basicInfo.name && memory.content.contains(name)
                        }

                    // 统计共同出现
                    for (i in mentionedPeople.indices) {
                        for (j in i + 1 until mentionedPeople.size) {
                            val personA = mentionedPeople[i]
                            val personB = mentionedPeople[j]
                            val pair = if (personA < personB) {
                                Pair(personA, personB)
                            } else {
                                Pair(personB, personA)
                            }
                            coOccurrenceMap[pair] = (coOccurrenceMap[pair] ?: 0) + 1
                        }
                    }
                }
            }

            // 对于共同出现3次以上的人物对，推断关系
            for ((pair, count) in coOccurrenceMap) {
                if (count >= 3) {
                    val (personA, personB) = pair
                    val existingRelation = relationshipNetworkUseCase.getRelationBetween(personA, personB)

                    if (existingRelation == null) {
                        val confidence = minOf(0.3f + count * 0.1f, 0.8f)
                        inferred.add(InferredRelation(
                            personA = personA,
                            personB = personB,
                            inferredType = "同一社交圈",
                            confidence = confidence,
                            reasoning = "$personA 和 $personB 在记忆中共同出现 $count 次，可能属于同一社交圈",
                            inferenceMethod = "社交圈推理",
                            evidenceChain = listOf(personA, personB),
                            metadata = mapOf("co_occurrence_count" to count.toString())
                        ))
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 社交圈推理失败")
        }

        return inferred
    }

    /**
     * 基于WorldBook推理
     * 从世界观知识中推断背景关系
     */
    private suspend fun inferFromWorldKnowledge(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                listOf(triggerPerson)
            } else {
                allProfiles.map { it.basicInfo.name }
            }

            for (personA in peopleToCheck) {
                // 搜索相关的WorldBook条目
                val entries = worldBook.searchEntries(personA)

                for (entry in entries) {
                    // 从条目内容中提取其他人名
                    val mentionedPeople = allProfiles
                        .map { it.basicInfo.name }
                        .filter { name ->
                            name != personA && entry.content.contains(name)
                        }

                    for (personB in mentionedPeople) {
                        val existingRelation = relationshipNetworkUseCase.getRelationBetween(personA, personB)

                        if (existingRelation == null) {
                            // 使用LLM分析具体关系类型
                            val inferredType = analyzeRelationshipFromText(personA, personB, entry.content)

                            if (inferredType != null) {
                                inferred.add(InferredRelation(
                                    personA = personA,
                                    personB = personB,
                                    inferredType = inferredType,
                                    confidence = 0.5f,
                                    reasoning = "从世界观知识「${entry.content.take(50)}...」推断出的关系",
                                    inferenceMethod = "WorldBook推理",
                                    evidenceChain = listOf(personA, personB),
                                    metadata = mapOf("world_entry_id" to entry.entryId)
                                ))
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] WorldBook推理失败")
        }

        return inferred
    }

    /**
     * 应用推理结果（将推理出的关系保存到数据库）
     * @param threshold 置信度阈值，只保存置信度高于此值的关系
     */
    suspend fun applyInferences(
        inferences: List<InferredRelation>,
        threshold: Float = 0.5f
    ): Int {
        var appliedCount = 0

        for (inference in inferences) {
            if (inference.confidence >= threshold) {
                try {
                    relationshipNetworkUseCase.recordRelation(
                        personA = inference.personA,
                        personB = inference.personB,
                        relationType = inference.inferredType,
                        description = inference.reasoning,
                        confidence = inference.confidence,
                        source = "ai_inferred_${inference.inferenceMethod}"
                    )
                    appliedCount++
                } catch (e: Exception) {
                    Timber.e(e, "[RelationshipInferenceEngine] 应用推理失败: ${inference.personA} - ${inference.personB}")
                }
            }
        }

        Timber.d("[RelationshipInferenceEngine] 应用推理结果: $appliedCount/${inferences.size}")
        return appliedCount
    }

    /**
     * 辅助方法：从文本中提取职位和公司
     */
    private fun extractJobAndCompany(bio: String): Pair<String?, String?> {
        // 简单正则匹配："XXX公司的XXX"
        val pattern = Regex("(\\S+)公司的(\\S+)")
        val match = pattern.find(bio)

        return if (match != null) {
            val company = match.groupValues[1] + "公司"
            val job = match.groupValues[2]
            Pair(job, company)
        } else {
            Pair(null, null)
        }
    }

    /**
     * 辅助方法：使用LLM分析关系类型（LLM驱动，带fallback）
     *
     * ⭐ 优先使用LLM分析关系
     * ⚠️ LLM失败时自动降级到规则
     */
    private suspend fun analyzeRelationshipFromText(
        personA: String,
        personB: String,
        text: String
    ): String? {
        return try {
            // ⭐ 使用LLM分析
            val llmResult = memoryLlmService.analyzeRelationshipType(personA, personB, text)
            if (llmResult.isSuccess) {
                return llmResult.getOrNull()
            }

            // ⚠️ Fallback: 关键词匹配
            fallbackAnalyzeRelationship(text)
        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 分析关系失败")
            fallbackAnalyzeRelationship(text)
        }
    }

    /**
     * 关系分析fallback（关键词匹配）
     */
    private fun fallbackAnalyzeRelationship(text: String): String {
        return when {
            text.contains("朋友") -> "朋友"
            text.contains("同事") -> "同事"
            text.contains("家人") || text.contains("亲人") -> "家人"
            text.contains("上司") || text.contains("老板") -> "上下级"
            text.contains("邻居") -> "邻居"
            text.contains("同学") -> "同学"
            text.contains("师生") || text.contains("老师") || text.contains("学生") -> "师生"
            else -> "认识"
        }
    }

    // ==================== 新增推理方法 ====================

    /**
     * 时序推理：基于事件发生的时间顺序推断关系
     *
     * 示例逻辑：
     * - 如果A和B在同一时间段多次共同出现 → 可能是朋友/同事
     * - 如果A的关系在B之后建立 → 可能是通过B认识的
     * - 如果关系突然中断又恢复 → 可能经历了冲突和解
     */
    private suspend fun inferTemporalRelations(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            // 获取所有人物
            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                listOf(triggerPerson)
            } else {
                allProfiles.map { it.basicInfo.name }.take(20)  // 限制处理数量以提升性能
            }

            for (person in peopleToCheck) {
                // 获取该人物的所有关系（包括历史）
                val personRelations = relationshipNetworkUseCase.getPersonRelations(person)

                // 按时间分组关系
                val relationsByPeriod = personRelations.groupBy { relation ->
                    // 按月分组
                    val timestamp = relation.createdAt
                    timestamp / (30L * 24 * 60 * 60 * 1000)  // 转换为月份编号
                }

                // 分析同时期建立的关系
                relationsByPeriod.forEach { (period, relations) ->
                    if (relations.size >= 2) {
                        // 同时期认识多个人，他们可能互相认识
                        for (i in relations.indices) {
                            for (j in (i + 1) until relations.size) {
                                val personB = if (relations[i].personA == person) relations[i].personB else relations[i].personA
                                val personC = if (relations[j].personA == person) relations[j].personB else relations[j].personA

                                // 检查B和C之间是否有直接关系
                                val existingRelation = relationshipNetworkUseCase.getRelationBetween(personB, personC)
                                if (existingRelation == null) {
                                    inferred.add(
                                        InferredRelation(
                                            personA = personB,
                                            personB = personC,
                                            inferredType = "acquaintance",
                                            confidence = 0.5f,
                                            reasoning = "通过$person 在同一时期（${period}月）认识，可能互相认识",
                                            inferenceMethod = "temporal_concurrent",
                                            evidenceChain = listOf(
                                                "$person - $personB (${relations[i].relationType})",
                                                "$person - $personC (${relations[j].relationType})"
                                            ),
                                            metadata = mapOf(
                                                "period" to period.toString(),
                                                "throughPerson" to person
                                            )
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 分析关系建立的先后顺序
                val sortedRelations = personRelations.sortedBy { it.createdAt }
                for (i in 0 until (sortedRelations.size - 1)) {
                    val earlier = sortedRelations[i]
                    val later = sortedRelations[i + 1]

                    // 如果两个关系建立时间相近（7天内）
                    val timeDiff = later.createdAt - earlier.createdAt
                    if (timeDiff < 7 * 24 * 60 * 60 * 1000) {
                        val personB = if (earlier.personA == person) earlier.personB else earlier.personA
                        val personC = if (later.personA == person) later.personB else later.personA

                        // 推断可能是通过B认识的C
                        inferred.add(
                            InferredRelation(
                                personA = person,
                                personB = personC,
                                inferredType = "introduced_by",
                                confidence = 0.6f,
                                reasoning = "$person 可能通过 $personB 认识了 $personC（时间相近）",
                                inferenceMethod = "temporal_sequence",
                                evidenceChain = listOf(
                                    "先认识: $personB",
                                    "后认识: $personC"
                                ),
                                metadata = mapOf(
                                    "introducedBy" to personB,
                                    "timeDiffDays" to (timeDiff / (24 * 60 * 60 * 1000)).toString()
                                )
                            )
                        )
                    }
                }
            }

            Timber.d("[RelationshipInferenceEngine] 时序推理完成，发现${inferred.size}个关系")
            return inferred

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 时序推理失败")
            return emptyList()
        }
    }

    /**
     * 地理推理：基于地点共现推断关系
     *
     * 示例逻辑：
     * - 如果A和B多次在同一地点出现 → 可能是邻居/同事/常客
     * - 如果A、B、C在某地点聚会 → 可能是朋友圈
     * - 如果A和B的常驻地相同 → 可能是邻居
     */
    private suspend fun inferGeographicRelations(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            // 获取所有人物及其记忆
            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                listOf(triggerPerson)
            } else {
                allProfiles.map { it.basicInfo.name }.take(20)
            }

            // 提取每个人的地点信息
            val personLocationMap = mutableMapOf<String, MutableList<String>>()

            for (person in peopleToCheck) {
                val memories = characterBook.getMemories(person)
                val locations = mutableListOf<String>()

                // 从记忆中提取地点
                memories.forEach { memory ->
                    val content = memory.content.toLowerCase()

                    // 简单的地点提取（可以改进为NER）
                    val locationKeywords = listOf(
                        "公司", "办公室", "家", "咖啡厅", "餐厅", "酒吧",
                        "学校", "图书馆", "健身房", "公园", "商场", "医院"
                    )

                    locationKeywords.forEach { location ->
                        if (content.contains(location)) {
                            locations.add(location)
                        }
                    }
                }

                if (locations.isNotEmpty()) {
                    personLocationMap[person] = locations
                }
            }

            // 分析地点共现
            val people = personLocationMap.keys.toList()
            for (i in people.indices) {
                for (j in (i + 1) until people.size) {
                    val personA = people[i]
                    val personB = people[j]

                    val locationsA = personLocationMap[personA] ?: emptyList()
                    val locationsB = personLocationMap[personB] ?: emptyList()

                    // 计算共同出现的地点
                    val commonLocations = locationsA.intersect(locationsB.toSet())

                    if (commonLocations.size >= 2) {
                        // 检查是否已有关系
                        val existingRelation = relationshipNetworkUseCase.getRelationBetween(personA, personB)
                        if (existingRelation == null) {
                            // 根据共同地点类型推断关系
                            val inferredType = when {
                                commonLocations.contains("公司") || commonLocations.contains("办公室") -> "colleague"
                                commonLocations.contains("家") -> "neighbor"
                                commonLocations.contains("学校") -> "classmate"
                                else -> "acquaintance"
                            }

                            val confidence = (commonLocations.size / 5.0f).coerceIn(0.3f, 0.8f)

                            inferred.add(
                                InferredRelation(
                                    personA = personA,
                                    personB = personB,
                                    inferredType = inferredType,
                                    confidence = confidence,
                                    reasoning = "$personA 和 $personB 在${commonLocations.size}个地点共同出现：${commonLocations.joinToString(", ")}",
                                    inferenceMethod = "geographic_cooccurrence",
                                    evidenceChain = commonLocations.map { "共同地点: $it" },
                                    metadata = mapOf(
                                        "commonLocationCount" to commonLocations.size.toString(),
                                        "commonLocations" to commonLocations.joinToString(",")
                                    )
                                )
                            )
                        }
                    }
                }
            }

            Timber.d("[RelationshipInferenceEngine] 地理推理完成，发现${inferred.size}个关系")
            return inferred

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] 地理推理失败")
            return emptyList()
        }
    }

    /**
     * LLM语义推理：使用大语言模型进行深度关系分析
     *
     * 相比规则推理，LLM能够：
     * - 理解复杂的语义关系
     * - 识别隐含的情感关系
     * - 综合多种线索进行推断
     * - 处理歧义和不确定性
     */
    private suspend fun inferWithLLMSemanticAnalysis(triggerPerson: String?): List<InferredRelation> {
        val inferred = mutableListOf<InferredRelation>()

        try {
            // 获取所有人物
            val allProfiles = characterBook.getAllProfiles()
            val peopleToCheck = if (triggerPerson != null) {
                listOf(triggerPerson)
            } else {
                allProfiles.map { it.basicInfo.name }.take(10)  // LLM推理成本高，限制数量
            }

            peopleLoop@ for (person in peopleToCheck) {
                // 获取该人物的所有记忆和已知关系
                val memories = characterBook.getTopMemories(person, limit = 20)
                val existingRelations = relationshipNetworkUseCase.getPersonRelations(person)

                // 构建上下文
                val context = buildString {
                    appendLine("关于${person}的信息：")
                    appendLine()
                    appendLine("已知关系：")
                    existingRelations.forEach { rel ->
                        val other = if (rel.personA == person) rel.personB else rel.personA
                        appendLine("- ${other}：${rel.relationType}（${rel.description}）")
                    }
                    appendLine()
                    appendLine("重要记忆：")
                    memories.forEach { memory ->
                        appendLine("- ${memory.content}")
                    }
                }

                // 使用LLM分析潜在关系
                val prompt = """
                    基于以下信息，分析${person}可能认识但尚未记录的人物关系。

                    $context

                    请推断：
                    1. ${person}可能认识哪些人（从记忆中提及的人物）
                    2. 他们之间可能是什么关系
                    3. 推断的理由和置信度（0-1）

                    输出格式（JSON数组）：
                    [
                      {
                        "personB": "人物名",
                        "relationType": "关系类型",
                        "reasoning": "推理原因",
                        "confidence": 0.7
                      }
                    ]
                """.trimIndent()

                try {
                    // 调用LLM分析潜在关系
                    val llmResponse = memoryLlmService.analyzeRelationshipType(
                        personA = person,
                        personB = "", // 这里留空，让LLM从记忆中识别潜在关系对象
                        context = prompt
                    )

                    // 解析JSON响应（简化处理）
                    val responseText = llmResponse.getOrElse { error ->
                        Timber.e("LLM关系分析失败: $error")
                        null
                    } ?: continue
                    val inferredFromLLM = parseLLMRelationshipResponse(responseText)

                    inferredFromLLM.forEach { llmInferred ->
                        // 验证不与已知关系冲突
                        val existing = relationshipNetworkUseCase.getRelationBetween(person, llmInferred.personB)
                        if (existing == null) {
                            inferred.add(
                                InferredRelation(
                                    personA = person,
                                    personB = llmInferred.personB,
                                    inferredType = llmInferred.relationType,
                                    confidence = llmInferred.confidence,
                                    reasoning = llmInferred.reasoning,
                                    inferenceMethod = "llm_semantic_analysis",
                                    evidenceChain = listOf("LLM分析: ${llmInferred.reasoning}"),
                                    metadata = mapOf(
                                        "llmModel" to "memory_llm",
                                        "analysisDate" to System.currentTimeMillis().toString()
                                    )
                                )
                            )
                        }
                    }

                } catch (e: Exception) {
                    Timber.w(e, "[RelationshipInferenceEngine] LLM分析${person}失败")
                }
            }

            Timber.d("[RelationshipInferenceEngine] LLM语义推理完成，发现${inferred.size}个关系")
            return inferred

        } catch (e: Exception) {
            Timber.e(e, "[RelationshipInferenceEngine] LLM语义推理失败")
            return emptyList()
        }
    }

    /**
     * 解析LLM关系推理响应
     */
    private fun parseLLMRelationshipResponse(response: String): List<LLMInferredRelation> {
        return try {
            // 简化的JSON解析（实际应使用Gson/Moshi）
            // 这里假设LLM返回规范的JSON
            val results = mutableListOf<LLMInferredRelation>()

            // 提取JSON数组内容（简单正则）
            val jsonPattern = """\{\s*"personB"\s*:\s*"([^"]+)"\s*,\s*"relationType"\s*:\s*"([^"]+)"\s*,\s*"reasoning"\s*:\s*"([^"]+)"\s*,\s*"confidence"\s*:\s*([0-9.]+)\s*\}""".toRegex()

            jsonPattern.findAll(response).forEach { match ->
                val (personB, relationType, reasoning, confidenceStr) = match.destructured
                results.add(
                    LLMInferredRelation(
                        personB = personB,
                        relationType = relationType,
                        reasoning = reasoning,
                        confidence = confidenceStr.toFloatOrNull() ?: 0.5f
                    )
                )
            }

            results
        } catch (e: Exception) {
            Timber.w(e, "[RelationshipInferenceEngine] 解析LLM响应失败")
            emptyList()
        }
    }

    /**
     * LLM推理结果（内部使用）
     */
    private data class LLMInferredRelation(
        val personB: String,
        val relationType: String,
        val reasoning: String,
        val confidence: Float
    )
}

// ==================== 数据模型 ====================

/**
 * 推理出的关系
 */
data class InferredRelation(
    val personA: String,
    val personB: String,
    val inferredType: String,
    val confidence: Float,
    val reasoning: String,
    val inferenceMethod: String,
    val evidenceChain: List<String>,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)
