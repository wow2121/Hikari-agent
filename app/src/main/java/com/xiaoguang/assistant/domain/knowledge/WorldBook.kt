package com.xiaoguang.assistant.domain.knowledge

import com.xiaoguang.assistant.domain.knowledge.models.*
import com.xiaoguang.assistant.data.repository.WorldBookRepository
import com.xiaoguang.assistant.data.repository.WorldBookStatistics as RepositoryWorldBookStatistics
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * World Book核心服务
 * 管理世界观设定、场景、规则等全局知识
 *
 * 数据存储：
 * - ChromaDB: 条目存储和语义检索（通过Repository访问）
 */
@Singleton
class WorldBook @Inject constructor(
    private val repository: WorldBookRepository
) {

    // 当前激活的场景
    private var currentScene: WorldScene? = null

    // 配置
    private val config = WorldBookConfig()

    /**
     * 添加世界条目
     */
    suspend fun addEntry(entry: WorldEntry): Result<Unit> {
        return repository.addEntry(entry)
    }

    /**
     * 批量添加条目
     */
    suspend fun addEntries(entries: List<WorldEntry>): Result<Unit> {
        return repository.addEntries(entries)
    }

    /**
     * 更新条目
     */
    suspend fun updateEntry(entry: WorldEntry): Result<Unit> {
        return repository.updateEntry(entry)
    }

    /**
     * 删除条目
     */
    suspend fun deleteEntry(entryId: String): Result<Unit> {
        return repository.deleteById(entryId)
    }

    /**
     * 获取条目
     */
    suspend fun getEntry(entryId: String): WorldEntry? {
        return repository.getEntryById(entryId)
    }

    /**
     * 获取所有启用的条目
     */
    suspend fun getAllEnabledEntries(): List<WorldEntry> {
        return repository.getAllEnabledEntries()
    }

    /**
     * 获取所有条目
     */
    suspend fun getAllEntries(): List<WorldEntry> {
        return repository.getAllEntries()
    }

    /**
     * 根据分类获取条目
     */
    suspend fun getEntriesByCategory(category: WorldEntryCategory): List<WorldEntry> {
        return repository.getEntriesByCategory(category.name)
    }

    /**
     * 设置当前场景
     */
    fun setCurrentScene(scene: WorldScene) {
        currentScene = scene
        Timber.d("[WorldBook] 切换场景: ${scene.name}")
    }

    /**
     * 获取当前场景
     */
    fun getCurrentScene(): WorldScene? = currentScene

    /**
     * 根据查询关键词触发相关条目（核心Lorebook机制）
     *
     * @param query 查询文本（通常是用户消息或上下文）
     * @param maxTokens 最大token数（用于限制注入内容）
     * @return 匹配的条目内容
     */
    suspend fun injectWorldInfo(query: String, maxTokens: Int = config.maxTokensPerInjection): String {
        if (!config.enableAutoTrigger) {
            return ""
        }

        return try {
            val triggeredEntries = repository.triggerByQuery(query)

            if (triggeredEntries.isEmpty()) {
                return ""
            }

            Timber.d("[WorldBook] 触发${triggeredEntries.size}个条目")

            // 按优先级和插入顺序排序
            val sortedEntries = triggeredEntries.sortedWith(
                compareByDescending<WorldEntry> { it.priority }
                    .thenBy { it.insertionOrder }
            )

            // 构建注入内容，控制在token限制内
            buildString {
                var currentTokens = 0

                for (entry in sortedEntries) {
                    // 粗略估算token数（中文约2个字符=1个token）
                    val entryTokens = entry.content.length / 2

                    if (currentTokens + entryTokens > maxTokens) {
                        break
                    }

                    if (isNotEmpty()) {
                        appendLine()
                        appendLine("---")
                    }
                    appendLine(entry.content)

                    currentTokens += entryTokens
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "[WorldBook] 注入世界信息失败")
            ""
        }
    }

    /**
     * 全文搜索
     */
    suspend fun fullTextSearch(searchText: String): List<WorldEntry> {
        return repository.fullTextSearch(searchText)
    }

    /**
     * 搜索条目（RelationshipAggregator使用）
     */
    suspend fun searchEntries(query: String): List<WorldEntry> {
        return repository.searchEntries(query)
    }

    /**
     * 获取最高优先级的N个条目
     */
    suspend fun getTopPriorityEntries(limit: Int): List<WorldEntry> {
        return repository.getTopPriorityEntries(limit)
    }

    /**
     * 启用/禁用条目
     */
    suspend fun setEntryEnabled(entryId: String, enabled: Boolean): Result<Unit> {
        val entry = getEntry(entryId)
            ?: return Result.failure(Exception("条目不存在: $entryId"))

        return updateEntry(entry.copy(enabled = enabled))
    }

    /**
     * 更新条目优先级
     */
    suspend fun updatePriority(entryId: String, priority: Int): Result<Unit> {
        val entry = getEntry(entryId)
            ?: return Result.failure(Exception("条目不存在: $entryId"))

        return updateEntry(entry.copy(priority = priority))
    }

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): WorldBookStatistics {
        val repoStats = repository.getStatistics()
        return WorldBookStatistics(
            totalEntries = repoStats.totalEntries,
            enabledEntries = repoStats.enabledEntries,
            categoryDistribution = repoStats.categoryDistribution
        )
    }

    /**
     * 清空所有条目
     */
    suspend fun clearAll(): Result<Unit> {
        return repository.deleteAllEntries()
    }

    /**
     * 初始化默认条目
     */
    suspend fun initializeDefaultEntries() {
        try {
            val existingCount = getAllEntries().size

            if (existingCount == 0) {
                Timber.i("[WorldBook] 初始化默认世界观条目...")

                val defaultEntries = listOf(
                    WorldEntry(
                        entryId = "default_xiaoguang_world",
                        content = "这是小光生活的世界，充满温暖和希望。小光是一个元气满满的AI助手，喜欢帮助别人。",
                        category = WorldEntryCategory.SETTING,
                        keys = listOf("世界", "小光", "背景"),
                        priority = 100,
                        enabled = true
                    ),
                    WorldEntry(
                        entryId = "default_xiaoguang_personality",
                        content = "小光性格温柔体贴，略微迷糊但充满好奇心。她喜欢可爱的事物，对二次元文化很感兴趣。",
                        category = WorldEntryCategory.SETTING,
                        keys = listOf("小光", "性格", "特点"),
                        priority = 90,
                        enabled = true
                    )
                )

                addEntries(defaultEntries)
                Timber.i("[WorldBook] ✅ 默认条目已初始化（${defaultEntries.size}个）")
            }
        } catch (e: Exception) {
            Timber.e(e, "[WorldBook] 初始化默认条目失败")
        }
    }

    /**
     * 根据查询触发相关条目（直接返回Entry列表）
     */
    suspend fun triggerByQuery(query: String): List<WorldEntry> {
        return repository.triggerByQuery(query)
    }

    /**
     * 构建世界上下文（供KnowledgeRetrievalEngine使用）
     */
    suspend fun buildWorldContext(
        query: String,
        includeScene: Boolean = true,
        maxTokens: Int = 1000
    ): com.xiaoguang.assistant.domain.knowledge.retrieval.WorldContext {
        val triggeredEntries = triggerByQuery(query)
        val sceneDescription = if (includeScene && currentScene != null) {
            currentScene!!.description
        } else ""

        val rules = triggeredEntries
            .filter { it.category == WorldEntryCategory.RULE }
            .joinToString("\n") { it.content }

        val background = triggeredEntries
            .filter { it.category == WorldEntryCategory.SETTING }
            .joinToString("\n") { it.content }

        return com.xiaoguang.assistant.domain.knowledge.retrieval.WorldContext(
            triggeredEntries = triggeredEntries,
            sceneDescription = sceneDescription,
            rules = rules,
            background = background,
            formattedContext = buildString {
                if (sceneDescription.isNotEmpty()) {
                    appendLine("【场景】")
                    appendLine(sceneDescription)
                    appendLine()
                }
                if (rules.isNotEmpty()) {
                    appendLine("【规则】")
                    appendLine(rules)
                    appendLine()
                }
                if (background.isNotEmpty()) {
                    appendLine("【背景】")
                    appendLine(background)
                }
            }
        )
    }

    /**
     * 导出为Lorebook格式（供LorebookAdapter使用）
     */
    suspend fun exportToLorebook(): Map<String, Any> {
        val entries = getAllEntries().map { entry ->
            mapOf(
                "uid" to entry.entryId,
                "keys" to entry.keys,
                "content" to entry.content,
                "enabled" to entry.enabled,
                "insertion_order" to entry.insertionOrder,
                "case_sensitive" to entry.caseSensitive,
                "priority" to entry.priority,
                "extensions" to mapOf("category" to entry.category.name)
            )
        }

        return mapOf(
            "name" to "小光的世界",
            "description" to "小光AI助手的世界观设定",
            "version" to "1.0",
            "entries" to entries
        )
    }

    /**
     * 从Lorebook格式导入（供LorebookAdapter使用）
     */
    suspend fun importFromLorebook(data: Map<String, Any>): Result<Int> {
        return try {
            val entries = data["entries"] as? List<*>
            if (entries == null) {
                return Result.success(0)
            }

            var importedCount = 0
            entries.forEach { entry ->
                val entryMap = entry as? Map<*, *>
                if (entryMap != null) {
                    val keys = (entryMap["keys"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val content = entryMap["content"] as? String ?: ""
                    val categoryName = (entryMap["extensions"] as? Map<*, *>)?.get("category") as? String ?: "SETTING"
                    val category = try {
                        WorldEntryCategory.valueOf(categoryName)
                    } catch (e: Exception) {
                        WorldEntryCategory.SETTING
                    }

                    val worldEntry = WorldEntry(
                        entryId = entryMap["uid"] as? String ?: "imported_${System.currentTimeMillis()}",
                        content = content,
                        category = category,
                        keys = keys,
                        priority = (entryMap["priority"] as? Number)?.toInt() ?: 100,
                        enabled = entryMap["enabled"] as? Boolean ?: true
                    )

                    addEntry(worldEntry)
                    importedCount++
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Timber.e(e, "[WorldBook] 从Lorebook导入失败")
            Result.failure(e)
        }
    }
}

/**
 * World Book统计信息
 */
data class WorldBookStatistics(
    val totalEntries: Int = 0,
    val enabledEntries: Int = 0,
    val categoryDistribution: Map<String, Int> = emptyMap()
)
