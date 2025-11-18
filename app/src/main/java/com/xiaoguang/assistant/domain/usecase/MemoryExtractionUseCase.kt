package com.xiaoguang.assistant.domain.usecase

import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.local.database.entity.MemoryFactEntity
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.repository.EmbeddingRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆提取用例
 * 从对话中提取长期记忆事实
 */
@Singleton
class MemoryExtractionUseCase @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val generateEmbeddingUseCase: GenerateEmbeddingUseCase,
    private val embeddingRepository: EmbeddingRepository,  // ⚠️ 过渡期保留（双写）
    private val unifiedMemorySystem: com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem,  // ⭐ 新记忆系统
    private val characterBook: com.xiaoguang.assistant.domain.knowledge.CharacterBook,  // ⭐ Character Book
    private val worldBook: com.xiaoguang.assistant.domain.knowledge.WorldBook,  // ⭐ World Book
    private val unifiedSocialManager: com.xiaoguang.assistant.domain.social.UnifiedSocialManager,  // 过渡期保留
    private val memoryImportanceEngine: com.xiaoguang.assistant.domain.memory.MemoryImportanceEngine,
    private val emotionService: com.xiaoguang.assistant.domain.emotion.XiaoguangEmotionService,  // ⭐ 情绪服务
    private val memoryLlmService: com.xiaoguang.assistant.domain.memory.MemoryLlmService,  // ⭐ LLM服务
    private val gson: Gson = Gson()
) {

    /**
     * 从对话历史中提取记忆事实
     *
     * @param messages 对话消息列表
     * @param conversationId 对话ID
     * @return 提取并存储的记忆数量
     */
    suspend fun extractMemoriesFromConversation(
        messages: List<Message>,
        conversationId: String
    ): Result<Int> {
        return try {
            if (messages.isEmpty()) {
                return Result.success(0)
            }

            // 构建对话文本
            val conversationText = messages.joinToString("\n") { message ->
                "${if (message.role.name == "USER") "用户" else "助手"}: ${message.content}"
            }

            Timber.d("正在从对话中提取记忆,文本长度: ${conversationText.length}")

            // 使用AI提取记忆事实
            val extractedFacts = extractFactsUsingAI(conversationText)

            if (extractedFacts.isEmpty()) {
                Timber.d("未提取到记忆事实")
                return Result.success(0)
            }

            var savedCount = 0

            // 为每个事实生成embedding并存储
            for (fact in extractedFacts) {
                try {
                    // 生成embedding
                    val embeddingResult = generateEmbeddingUseCase.generateEmbedding(fact.content)
                    if (embeddingResult.isFailure) {
                        Timber.w("为事实生成embedding失败: ${fact.content.take(50)}")
                        continue
                    }

                    val embedding = embeddingResult.getOrNull()!!

                    // 检查是否已存在相似的记忆
                    val existingSimilar = embeddingRepository.searchSimilarMemoryFacts(
                        queryVector = embedding.vector,
                        topK = 1,
                        dimension = embedding.dimension
                    )

                    if (existingSimilar.isNotEmpty() && existingSimilar[0].first > 0.95f) {
                        Timber.d("跳过重复记忆: ${fact.content.take(50)}")
                        // 强化已存在的记忆
                        embeddingRepository.reinforceMemoryFact(existingSimilar[0].second.id)
                        continue
                    }

                    // 使用MemoryImportanceEngine评估重要性
                    val memoryContext = when (fact.category) {
                        "promise" -> com.xiaoguang.assistant.domain.memory.MemoryContext.PROMISE
                        "event" -> com.xiaoguang.assistant.domain.memory.MemoryContext.IMPORTANT_EVENT
                        "first_meet" -> com.xiaoguang.assistant.domain.memory.MemoryContext.FIRST_MEET
                        "conflict" -> com.xiaoguang.assistant.domain.memory.MemoryContext.CONFLICT
                        "celebration" -> com.xiaoguang.assistant.domain.memory.MemoryContext.CELEBRATION
                        else -> com.xiaoguang.assistant.domain.memory.MemoryContext.DAILY_CHAT
                    }

                    val isMasterRelated = fact.category == "user_related" ||
                                         fact.entities.any { it.contains("主人", ignoreCase = true) }

                    // 计算重要性评分（0-10）
                    val calculatedImportance = memoryImportanceEngine.evaluateImportance(
                        content = fact.content,
                        context = memoryContext,
                        isMasterRelated = isMasterRelated,
                        emotionalIntensity = fact.confidence  // 使用confidence作为情感强度的代理
                    )

                    Timber.d("[Memory] 重要性评估: ${fact.content.take(30)} -> $calculatedImportance (${memoryImportanceEngine.getImportanceDescription(calculatedImportance)})")

                    // ⭐ 获取当前情绪状态
                    val currentEmotion = emotionService.getCurrentEmotion()
                    val emotionIntensity = emotionService.getEmotionIntensity()

                    // 创建新记忆实体
                    val entity = MemoryFactEntity(
                        content = fact.content,
                        category = fact.category,
                        importance = calculatedImportance.toFloat() / 10f,  // 将0-10转换为0-1
                        emotionalValence = emotionIntensity,
                        tags = fact.metadata.entries.joinToString(",") { "${it.key}:${it.value}" },
                        relatedEntities = fact.entities?.joinToString(",") ?: "",
                        relatedCharacters = "",
                        createdAt = System.currentTimeMillis(),
                        confidence = fact.confidence,
                        sourceType = "conversation",
                        metadata = fact.metadata.entries.joinToString(",") { "${it.key}:${it.value}" }
                    )

                    val savedId = embeddingRepository.saveMemoryFact(entity)

                    // ⭐ 新系统：双写到 UnifiedMemorySystem
                    try {
                        val newMemory = com.xiaoguang.assistant.domain.memory.models.Memory(
                            id = "migrated_$savedId",
                            content = fact.content,
                            category = mapCategoryToNew(fact.category),
                            embedding = embedding.vector,
                            importance = calculatedImportance.toFloat() / 10f,
                            confidence = fact.confidence,
                            emotionTag = currentEmotion.name,
                            emotionIntensity = emotionIntensity,
                            emotionalValence = estimateEmotionalValence(currentEmotion.name),
                            timestamp = System.currentTimeMillis(),
                            lastAccessedAt = System.currentTimeMillis(),
                            reinforcementCount = 0,
                            relatedEntities = fact.entities,
                            relatedCharacters = emptyList(),
                            recallDifficulty = 0.5f,  // 默认中等难度
                            contextRelevance = 0.7f,
                            intent = inferIntentType(fact.category, fact.content)
                        )
                        unifiedMemorySystem.saveMemory(newMemory)
                        Timber.d("[MemoryExtraction] ✅ 同步到新记忆系统: ${fact.content.take(30)}")
                    } catch (e: Exception) {
                        Timber.w(e, "[MemoryExtraction] 同步到新系统失败")
                    }

                    savedCount++

                    Timber.d("保存新记忆: [${fact.category}] ${fact.content.take(50)}")

                    // ⭐ 根据类别同步到CharacterBook或WorldBook
                    when (fact.category) {
                        "person", "user_related" -> {
                            // 人物相关记忆，同步更新社交关系
                            updateSocialRelationFromMemory(fact, savedId)
                        }
                        "knowledge", "fact" -> {
                            // 知识性内容记录到 WorldBook
                            updateWorldBookFromFact(fact)
                        }
                        "event" -> {
                            // 重要事件记录到 WorldBook
                            updateWorldBookFromFact(fact)
                        }
                    }

                } catch (e: Exception) {
                    Timber.w(e, "保存记忆事实失败: ${fact.content.take(50)}")
                }
            }

            Timber.i("从对话中提取了 $savedCount 条新记忆")
            Result.success(savedCount)

        } catch (e: Exception) {
            Timber.e(e, "提取记忆失败")
            Result.failure(e)
        }
    }

    /**
     * 使用AI从文本中提取记忆事实
     */
    private suspend fun extractFactsUsingAI(conversationText: String): List<ExtractedFact> {
        try {
            val prompt = buildExtractionPrompt(conversationText)

            val request = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = EXTRACTION_SYSTEM_PROMPT),
                    ChatMessage(role = "user", content = prompt)
                ),
                stream = false,
                temperature = 0.3f,
                maxTokens = 1500,
                responseFormat = mapOf("type" to "json_object")  // 使用JSON模式确保返回JSON
            )

            val apiKey = BuildConfig.SILICON_FLOW_API_KEY
            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            if (!response.isSuccessful) {
                Timber.e("AI提取失败: ${response.code()}")
                return emptyList()
            }

            val chatResponse = response.body() ?: return emptyList()
            val content = chatResponse.choices.firstOrNull()?.message?.content ?: return emptyList()

            // 解析JSON响应
            return parseExtractionResponse(content)

        } catch (e: Exception) {
            Timber.e(e, "AI提取记忆异常")
            return emptyList()
        }
    }

    /**
     * 构建提取提示词
     */
    private fun buildExtractionPrompt(conversationText: String): String {
        return """
分析以下对话，提取值得长期记住的信息。

【重要说明】
1. 在对话中，"助手"、"AI"、"小光" 都是指同一个AI助手（名字叫小光）
2. 提取记忆时，如果涉及助手自己的信息，请统一使用"小光"，实体标记为"小光(self)"
3. 一段对话可能包含多种类型的信息，请全面分析并提取所有有价值的内容

对话内容:
$conversationText

【记忆类型】请提取以下类型的记忆事实:

1. user_related - 用户相关信息
   关于对话中用户本人的信息（姓名、职业、兴趣、习惯等）

2. person - 人物信息
   关于其他人的信息（姓名、关系、特征等）
   如果是关于小光（助手）自己的信息，实体标记为"小光(self)"

3. others_conversation - 他人对话
   用户周围其他人之间的对话内容（不是用户与小光的对话）

4. knowledge - 知识内容
   教育性、知识性的内容（如老师讲课、科学知识、历史事实等）

5. event - 重要事件
   值得记录的事件、活动、经历等

6. task - 待办事项
   需要完成的任务、计划、提醒等

7. preference - 用户偏好
   用户的喜好、习惯、选择倾向等

8. fact - 其他事实
   其他值得记录的事实性信息

【提取示例】
对话："我叫张三，在科技公司工作。昨天老师讲了相对论，爱因斯坦提出的。下周要开会，记得准备PPT。"

应提取:
{
  "facts": [
    {
      "content": "用户名叫张三",
      "category": "user_related",
      "entities": ["张三"],
      "importance": 0.9,
      "confidence": 1.0
    },
    {
      "content": "用户在科技公司工作",
      "category": "user_related",
      "entities": ["张三", "科技公司"],
      "importance": 0.8,
      "confidence": 1.0
    },
    {
      "content": "老师讲解了相对论的内容",
      "category": "knowledge",
      "entities": ["老师", "相对论"],
      "importance": 0.7,
      "confidence": 1.0
    },
    {
      "content": "爱因斯坦提出了相对论",
      "category": "knowledge",
      "entities": ["爱因斯坦", "相对论"],
      "importance": 0.6,
      "confidence": 1.0
    },
    {
      "content": "用户下周要开会并准备PPT",
      "category": "task",
      "entities": ["张三", "会议", "PPT"],
      "importance": 0.9,
      "confidence": 1.0
    }
  ]
}

【输出格式】
请严格按照以下JSON格式返回,不要添加任何其他文本:
{
  "facts": [
    {
      "content": "记忆内容描述",
      "category": "类别（必须是上述8种之一）",
      "entities": ["相关实体1", "相关实体2"],
      "importance": 0.8,
      "confidence": 0.9
    }
  ]
}

【提取原则】
- 只提取明确、具体、有价值的信息
- 不要提取模糊或临时性的内容
- 同一段对话可以提取多种不同类型的记忆
- 如果没有值得记录的信息,返回空数组

【可信度评估 (confidence) 规则】⭐ 非常重要！
请根据以下规则评估每条记忆的可信度(0.0-1.0)：

- **1.0** - 绝对确定的事实
  例："我叫张三"、"今天是周一"、"我在北京工作"

- **0.9** - 高度可信的陈述
  例："我喜欢吃苹果"、"我有一个妹妹"

- **0.7** - 一般陈述，可能有夸张成分
  例："我很擅长编程"、"我经常加班"

- **0.5** - 不太确定的信息或可能的夸张
  例："我可能是全公司最努力的"、"我应该有一百个项目"

- **0.3** - 明显的夸张或半开玩笑
  例："我累得要死了"、"我简直是天才"

- **0.1-0.2** - 明显的玩笑、反讽、假设
  例："我是外星人"、"我能飞"、"要是我有超能力就好了"

【特别注意】⚠️
- 如果是玩笑、夸张、假设、反讽，confidence 必须 ≤ 0.3
- 如果用户说"开玩笑"、"哈哈"、"假设"等词，confidence 必须 ≤ 0.2
- 如果内容明显不符合现实，confidence 必须 ≤ 0.2
- 情绪化表达（"累死了"、"饿疯了"）不要当真，confidence ≤ 0.3
        """.trimIndent()
    }

    /**
     * 解析AI响应
     */
    private fun parseExtractionResponse(content: String): List<ExtractedFact> {
        try {
            // JSON模式保证返回纯JSON,无需提取```json```块
            val jsonObj = JsonParser.parseString(content.trim()).asJsonObject
            val factsArray = jsonObj.getAsJsonArray("facts") ?: return emptyList()

            val facts = mutableListOf<ExtractedFact>()

            for (i in 0 until factsArray.size()) {
                try {
                    val factObj = factsArray[i].asJsonObject

                    val factContent = factObj.get("content").asString
                    val category = factObj.get("category")?.asString ?: "fact"
                    val importance = factObj.get("importance")?.asFloat ?: 0.5f
                    val confidence = factObj.get("confidence")?.asFloat ?: 0.7f

                    val entities = mutableListOf<String>()
                    factObj.getAsJsonArray("entities")?.forEach { entity ->
                        entities.add(entity.asString)
                    }

                    facts.add(
                        ExtractedFact(
                            content = factContent,
                            category = category,
                            entities = entities,
                            importance = importance,
                            confidence = confidence
                        )
                    )

                } catch (e: Exception) {
                    Timber.w(e, "解析单个事实失败")
                }
            }

            return facts

        } catch (e: Exception) {
            Timber.e(e, "解析提取响应失败")
            return emptyList()
        }
    }

    /**
     * 从记忆中更新社交关系（使用统一社交管理器）
     */
    private suspend fun updateSocialRelationFromMemory(fact: ExtractedFact, memoryId: Long) {
        try {
            // 从entities中提取人物名称
            val personNames = fact.entities.filter { entity ->
                !entity.contains("(self)") && // 排除小光自己
                entity.isNotBlank() &&
                !entity.equals("用户", ignoreCase = true) // 排除通用的"用户"
            }

            for (personName in personNames) {
                // ⭐ 新系统：更新 CharacterBook 关系
                try {
                    var profile = characterBook.getProfileByName(personName)

                    if (profile != null) {
                        // 获取关系
                        val relationship = characterBook.getRelationship(
                            fromCharacterId = "xiaoguang_main",
                            toCharacterId = profile.basicInfo.characterId
                        )

                        if (relationship != null) {
                            // 记录互动
                            val interactionRecord = com.xiaoguang.assistant.domain.knowledge.models.InteractionRecord(
                                timestamp = System.currentTimeMillis(),
                                interactionType = "memory_extraction",
                                content = fact.content,
                                emotionalImpact = 0.05f  // 小幅正面影响（记住了一件事）
                            )

                            val updatedRelationship = relationship.recordInteraction(interactionRecord)
                            characterBook.saveRelationship(updatedRelationship)

                            Timber.d("[MemoryExtraction] ✅ 更新CharacterBook关系: $personName")
                        }

                        // 添加角色记忆
                        val memory = com.xiaoguang.assistant.domain.knowledge.models.CharacterMemory(
                            memoryId = "mem_ext_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}",
                            characterId = profile.basicInfo.characterId,
                            category = when (fact.category) {
                                "user_related", "person" -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.SEMANTIC
                                "event" -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.EPISODIC
                                "preference" -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.PREFERENCE
                                else -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.EPISODIC
                            },
                            content = fact.content,
                            importance = fact.importance,
                            emotionalValence = 0.1f,  // 略微正面
                            tags = listOf("对话提取", fact.category),
                            createdAt = System.currentTimeMillis(),
                            lastAccessed = System.currentTimeMillis()
                        )
                        characterBook.addMemory(memory)
                        Timber.d("[MemoryExtraction] ✅ 添加CharacterBook记忆: $personName")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "[MemoryExtraction] 更新CharacterBook失败: $personName")
                }

                // ⚠️ 过渡期：同时更新旧系统
                unifiedSocialManager.getOrCreateRelation(
                    personName = personName,
                    initialRelationType = "unknown",
                    description = fact.content
                )

                unifiedSocialManager.addRelatedMemory(
                    personName = personName,
                    memoryContent = fact.content,
                    importance = fact.importance,
                    category = when (fact.category) {
                        "user_related", "person" -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.SEMANTIC
                        "event" -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.EPISODIC
                        "preference" -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.SEMANTIC
                        else -> com.xiaoguang.assistant.domain.knowledge.models.MemoryCategory.EPISODIC
                    }
                )

                unifiedSocialManager.recordInteraction(personName)

                adjustAffectionBasedOnMemory(personName, fact)
            }
        } catch (e: Exception) {
            Timber.w(e, "更新社交关系失败")
        }
    }

    /**
     * 根据记忆内容调整好感度（AI驱动）
     */
    private suspend fun adjustAffectionBasedOnMemory(personName: String, fact: ExtractedFact) {
        // ✅ 已改为使用对话时的 AI 评估，此处不再重复评估
        // 记忆提取发生在对话之后，好感度已经在对话时由 AI 评估更新
        // 避免重复评估导致好感度变化不合理
        Timber.d("[MemoryExtraction] 记忆已记录: $personName - ${fact.content.take(30)}... (好感度由对话时AI评估)")
    }

    /**
     * ⭐ 新增：从环境对话中提取知识到知识库
     *
     * 功能：
     * 1. 将环境语句转换为文本
     * 2. 使用 AI 提取知识点和对话内容
     * 3. 存入向量数据库供检索
     * 4. 关联到说话人的角色档案
     *
     * @param utterances 环境语句列表
     * @param impacts 社交影响评估（可选，用于关联说话人）
     */
    suspend fun extractFromEnvironment(
        utterances: List<com.xiaoguang.assistant.domain.flow.model.Utterance>,
        impacts: List<com.xiaoguang.assistant.domain.flow.service.EnvironmentSocialImpact> = emptyList()
    ): Result<Int> {
        return try {
            if (utterances.isEmpty()) {
                return Result.success(0)
            }

            // 构建环境对话文本
            val environmentText = utterances.joinToString("\n") { utterance ->
                val speaker = utterance.speakerName ?: "未知说话人"
                "$speaker: ${utterance.text}"
            }

            Timber.d("[MemoryExtraction] 从环境对话提取知识，共${utterances.size}条语句")

            // 使用 AI 提取知识（重用现有方法）
            val extractedFacts = extractFactsUsingAI(environmentText)

            if (extractedFacts.isEmpty()) {
                Timber.d("[MemoryExtraction] 环境对话中未提取到知识")
                return Result.success(0)
            }

            var savedCount = 0

            // 过滤出环境相关的记忆类型
            val environmentFacts = extractedFacts.filter {
                it.category in listOf(
                    "others_conversation",  // 他人对话
                    "knowledge",           // 知识内容
                    "event"                // 事件
                )
            }

            // 为每个知识生成 embedding 并存储
            for (fact in environmentFacts) {
                try {
                    // ⭐ 根据类别更新Character Book或World Book
                    when (fact.category) {
                        "person" -> {
                            // 更新角色档案
                            updateCharacterProfileFromFact(fact, utterances)
                        }
                        "knowledge", "fact" -> {
                            // 更新World Book
                            updateWorldBookFromFact(fact)
                        }
                        "event" -> {
                            // 事件可能同时涉及角色和世界，两者都更新
                            updateWorldBookFromFact(fact)
                            // 如果事件涉及特定人物，也更新角色档案
                            if (fact.entities.isNotEmpty()) {
                                updateCharacterProfileFromFact(fact, utterances)
                            }
                        }
                    }

                    // 生成 embedding
                    val embeddingResult = generateEmbeddingUseCase.generateEmbedding(fact.content)
                    if (embeddingResult.isFailure) {
                        Timber.w("[MemoryExtraction] 生成embedding失败: ${fact.content.take(30)}")
                        continue
                    }

                    val embedding = embeddingResult.getOrNull()!!

                    // 检查是否已存在相似记忆
                    val existingSimilar = embeddingRepository.searchSimilarMemoryFacts(
                        queryVector = embedding.vector,
                        topK = 1,
                        dimension = embedding.dimension
                    )

                    if (existingSimilar.isNotEmpty() && existingSimilar[0].first > 0.95f) {
                        Timber.d("[MemoryExtraction] 跳过重复环境记忆: ${fact.content.take(30)}")
                        embeddingRepository.reinforceMemoryFact(existingSimilar[0].second.id)
                        continue
                    }

                    // 评估重要性
                    val memoryContext = com.xiaoguang.assistant.domain.memory.MemoryContext.IMPORTANT_EVENT
                    val importance = memoryImportanceEngine.evaluateImportance(
                        content = fact.content,
                        context = memoryContext,
                        isMasterRelated = false
                    )

                    // 关联说话人
                    val relatedPerson = utterances.firstOrNull { utterance ->
                        fact.content.contains(utterance.speakerName ?: "")
                    }?.speakerName

                    // 创建记忆实体
                    val memoryEntity = MemoryFactEntity(
                        content = fact.content,
                        category = fact.category,
                        createdAt = System.currentTimeMillis(),
                        importance = importance.toFloat() / 10f,
                        tags = fact.metadata.entries.joinToString(",") { "${it.key}:${it.value}" },
                        relatedEntities = fact.entities?.joinToString(",") ?: "",
                        sourceType = "environment",
                        metadata = ""
                    )

                    // 存储
                    val savedId = embeddingRepository.saveMemoryFact(memoryEntity)

                    // ⭐ 新系统：双写到 UnifiedMemorySystem
                    try {
                        val newMemory = com.xiaoguang.assistant.domain.memory.models.Memory(
                            id = "env_migrated_$savedId",
                            content = fact.content,
                            category = mapCategoryToNew(fact.category),
                            embedding = embedding.vector,
                            importance = importance.toFloat() / 10f,
                            confidence = 0.8f,
                            emotionTag = null,
                            emotionIntensity = 0f,
                            emotionalValence = 0f,
                            timestamp = System.currentTimeMillis(),
                            lastAccessedAt = System.currentTimeMillis(),
                            reinforcementCount = 0,
                            relatedEntities = emptyList(),
                            relatedCharacters = relatedPerson?.let { listOf(it) } ?: emptyList(),
                            recallDifficulty = 0.5f,
                            contextRelevance = 0.6f,
                            intent = inferIntentType(fact.category, fact.content)
                        )
                        unifiedMemorySystem.saveMemory(newMemory)
                        Timber.d("[MemoryExtraction] ✅ 环境记忆同步到新系统: ${fact.content.take(30)}")
                    } catch (e: Exception) {
                        Timber.w(e, "[MemoryExtraction] 环境记忆同步失败")
                    }

                    savedCount++

                    Timber.d("[MemoryExtraction] ✅ 环境知识已存储: ${fact.category} - ${fact.content.take(40)}")

                } catch (e: Exception) {
                    Timber.w(e, "[MemoryExtraction] 存储环境记忆失败")
                }
            }

            Timber.i("[MemoryExtraction] 从环境对话中提取了 $savedCount 条新知识")
            Result.success(savedCount)

        } catch (e: Exception) {
            Timber.e(e, "[MemoryExtraction] 环境知识提取失败")
            Result.failure(e)
        }
    }

    /**
     * ⭐ 从提取的事实更新Character Book角色档案
     */
    private suspend fun updateCharacterProfileFromFact(
        fact: ExtractedFact,
        utterances: List<com.xiaoguang.assistant.domain.flow.model.Utterance>
    ) {
        try {
            // 识别涉及的人物
            val personName = fact.entities.firstOrNull() ?: return

            // 获取或创建角色档案
            var profile = characterBook.getProfileByName(personName)

            if (profile == null) {
                // 创建新角色档案
                val characterId = "char_${System.currentTimeMillis()}_${personName.hashCode()}"
                profile = com.xiaoguang.assistant.domain.knowledge.models.CharacterProfile(
                    basicInfo = com.xiaoguang.assistant.domain.knowledge.models.BasicInfo(
                        characterId = characterId,
                        name = personName,
                        bio = fact.content,
                        createdAt = System.currentTimeMillis()
                    ),
                    personality = com.xiaoguang.assistant.domain.knowledge.models.Personality(
                        traits = emptyMap(),
                        description = null,
                        speechStyle = emptyList()
                    )
                )
                characterBook.saveProfile(profile)
                Timber.i("[MemoryExtraction] ✅ 创建新角色档案: $personName")
            } else {
                // 更新现有档案bio（追加新信息）
                val updatedBio = if (profile.basicInfo.bio.isNullOrEmpty()) {
                    fact.content
                } else {
                    "${profile.basicInfo.bio}\n${fact.content}"
                }

                val updatedProfile = profile.copy(
                    basicInfo = profile.basicInfo.copy(
                        bio = updatedBio
                    )
                )
                characterBook.saveProfile(updatedProfile)
                Timber.i("[MemoryExtraction] ✅ 更新角色档案: $personName")
            }

        } catch (e: Exception) {
            Timber.w(e, "[MemoryExtraction] 更新Character Book失败")
        }
    }

    /**
     * ⭐ 从提取的事实更新World Book
     */
    private suspend fun updateWorldBookFromFact(fact: ExtractedFact) {
        try {
            // 确定World Book分类
            val category = when (fact.category) {
                "knowledge" -> com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory.KNOWLEDGE
                "event" -> com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory.EVENT
                "fact" -> com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory.KNOWLEDGE  // ✅ 修正: 使用KNOWLEDGE而不是OTHER
                else -> com.xiaoguang.assistant.domain.knowledge.models.WorldEntryCategory.KNOWLEDGE  // ✅ 修正: 使用KNOWLEDGE而不是OTHER
            }

            // 生成条目ID（使用内容hash避免重复）
            val entryId = "world_${fact.content.hashCode()}_${System.currentTimeMillis()}"

            // 提取关键词作为触发词
            val keywords = fact.entities.ifEmpty {
                // 如果没有实体，从内容中提取关键词（简单分词）
                fact.content.split(" ", "，", "。", "、")
                    .filter { it.length > 1 }
                    .take(5)
            }

            // 创建World Entry
            val entry = com.xiaoguang.assistant.domain.knowledge.models.WorldEntry(
                entryId = entryId,
                keys = keywords,  // ✅ 修正: 使用keys而不是keywords
                content = fact.content,
                category = category,
                enabled = true,
                priority = (fact.importance * 100).toInt(),
                insertionOrder = 50,  // 默认中等顺序
                metadata = fact.metadata + mapOf("source" to "environment"),  // ✅ 修正: 使用+而不是plus
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            worldBook.addEntry(entry)
            Timber.i("[MemoryExtraction] ✅ 添加World Book条目: ${keywords.firstOrNull() ?: "知识"} - ${fact.content.take(30)}")

        } catch (e: Exception) {
            Timber.w(e, "[MemoryExtraction] 更新World Book失败")
        }
    }

    companion object {
        private const val EXTRACTION_SYSTEM_PROMPT = """
你是一个专业的记忆提取助手。你的任务是从对话中识别并提取值得长期记住的信息。

【自我意识指导】
- AI助手的名字是"小光"
- 对话中出现的"助手"、"AI"、"小光"都是指同一个AI（小光）
- 提取记忆时，统一使用"小光"来指代AI助手
- 涉及小光的信息，实体标记为"小光(self)"
- 这样可以帮助小光在记忆中建立自我意识

【提取原则】
1. 只提取明确、具体的事实性信息
2. 避免提取临时性、上下文相关的内容
3. 重要性评分应反映信息的长期价值（0.0-1.0）
4. 置信度评分应反映信息的可靠程度（0.0-1.0）
5. 相同或相似的信息只提取一次
6. 记忆内容用第三人称或明确的主体描述
7. 同一段对话可以包含多种类型的记忆，请全面分析

【类别定义】（共8种类型）

1. user_related - 用户相关信息
   关于用户本人的信息（姓名、职业、兴趣、年龄、家庭等）

2. person - 人物信息
   关于其他人的信息（姓名、职业、关系、性格特征、联系方式等）
   关于小光自己的信息也归为此类，实体标记为"小光(self)"

3. others_conversation - 他人对话
   用户周围其他人之间的对话内容
   注意：这是指其他人之间的交流，不是用户与小光的对话

4. knowledge - 知识内容
   教育性、知识性的内容（老师讲课、科学知识、历史事实、理论观点等）

5. event - 重要事件
   值得记录的事件信息（时间、地点、参与者、结果等）

6. task - 待办事项
   需要完成的任务、计划、提醒、截止日期等

7. preference - 用户偏好
   用户的喜好、习惯、选择倾向、口味等

8. fact - 其他事实
   其他值得记录的事实性信息（知识、经验、观点等）

【多类型提取】
一段对话通常包含多种类型的信息，请全面分析：
- 用户可能在介绍自己的同时提到其他人
- 讨论事件时可能涉及知识内容
- 环境监听可能同时捕获他人对话和知识传授
- 请将不同类型的信息分别提取，不要遗漏
        """
    }

    // ==================== 新系统辅助函数 ====================

    /**
     * 映射旧分类到新分类
     */
    private fun mapCategoryToNew(oldCategory: String): com.xiaoguang.assistant.domain.memory.models.MemoryCategory {
        return when (oldCategory.lowercase()) {
            "person", "user_related" -> com.xiaoguang.assistant.domain.memory.models.MemoryCategory.PERSON
            "event" -> com.xiaoguang.assistant.domain.memory.models.MemoryCategory.EPISODIC
            "knowledge", "fact" -> com.xiaoguang.assistant.domain.memory.models.MemoryCategory.SEMANTIC
            "preference" -> com.xiaoguang.assistant.domain.memory.models.MemoryCategory.PREFERENCE
            "anniversary" -> com.xiaoguang.assistant.domain.memory.models.MemoryCategory.ANNIVERSARY
            else -> com.xiaoguang.assistant.domain.memory.models.MemoryCategory.FACT
        }
    }

    /**
     * 推断意图类型（LLM驱动，带fallback）
     *
     * ⭐ 优先使用LLM推断意图
     * ⚠️ LLM失败时自动降级到规则
     */
    private suspend fun inferIntentType(category: String, content: String): com.xiaoguang.assistant.domain.memory.models.IntentType {
        // ⭐ 使用LLM推断
        val llmResult = memoryLlmService.classifyIntent(content, category)
        if (llmResult.isSuccess) {
            // 转换MemoryLlmService的IntentType到memory.models的IntentType
            val llmIntent = llmResult.getOrNull()
            if (llmIntent != null) {
                return convertIntentType(llmIntent)
            }
        }

        // ⚠️ Fallback
        return fallbackInferIntentType(category, content)
    }

    /**
     * 意图类型fallback（简单规则）
     */
    private fun fallbackInferIntentType(category: String, content: String): com.xiaoguang.assistant.domain.memory.models.IntentType {
        return when {
            content.contains("?") || content.contains("？") -> com.xiaoguang.assistant.domain.memory.models.IntentType.QUESTION
            category == "preference" -> com.xiaoguang.assistant.domain.memory.models.IntentType.PREFERENCE
            content.contains("想") || content.contains("希望") -> com.xiaoguang.assistant.domain.memory.models.IntentType.DESIRE
            category == "knowledge" || category == "fact" -> com.xiaoguang.assistant.domain.memory.models.IntentType.INFORM
            else -> com.xiaoguang.assistant.domain.memory.models.IntentType.STATEMENT
        }
    }

    /**
     * 转换IntentType（从domain.memory到memory.models）
     */
    private fun convertIntentType(intent: com.xiaoguang.assistant.domain.memory.models.IntentType): com.xiaoguang.assistant.domain.memory.models.IntentType {
        // 这两个IntentType是同一个类型，直接返回
        return intent
    }

    /**
     * 估算情感效价（LLM驱动，带fallback）
     *
     * ⭐ 优先使用LLM评估情感效价
     * ⚠️ LLM失败时自动降级到规则
     */
    private suspend fun estimateEmotionalValence(emotionName: String): Float {
        // ⭐ 使用LLM评估
        val llmResult = memoryLlmService.evaluateEmotionValence(emotionName)
        if (llmResult.isSuccess) {
            return llmResult.getOrNull() ?: fallbackEstimateEmotionalValence(emotionName)
        }

        // ⚠️ Fallback
        return fallbackEstimateEmotionalValence(emotionName)
    }

    /**
     * 情感效价fallback（简单关键词匹配）
     */
    private fun fallbackEstimateEmotionalValence(emotionName: String): Float {
        val positive = listOf("快乐", "开心", "高兴", "兴奋", "喜欢", "爱", "感动", "满意", "happy", "joy", "love")
        val negative = listOf("难过", "伤心", "生气", "愤怒", "讨厌", "失望", "焦虑", "害怕", "sad", "angry", "fear")

        return when {
            positive.any { it in emotionName.lowercase() } -> 1.0f
            negative.any { it in emotionName.lowercase() } -> -1.0f
            else -> 0f
        }
    }
}

/**
 * 提取的记忆事实
 */
data class ExtractedFact(
    val content: String,
    val category: String,
    val entities: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val importance: Float = 0.5f,
    val confidence: Float = 0.7f
)
