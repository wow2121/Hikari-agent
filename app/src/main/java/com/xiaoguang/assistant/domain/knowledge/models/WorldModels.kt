package com.xiaoguang.assistant.domain.knowledge.models

/**
 * World Book 数据模型
 * 用于存储世界观设定、场景、规则等全局知识
 */

/**
 * 世界条目类型
 */
enum class WorldEntryCategory(val displayName: String) {
    SETTING("全局设定"),      // 世界观、背景设定
    SCENE("场景"),           // 特定场景描述
    RULE("规则"),            // 世界规则和约束
    EVENT("事件"),           // 重要事件记录
    LOCATION("地点"),        // 地理位置信息
    KNOWLEDGE("知识")        // 通用知识条目
}

/**
 * 世界条目
 * 对应Lorebook的Entry概念
 */
data class WorldEntry(
    val entryId: String,                    // 唯一ID
    val keys: List<String>,                 // 触发关键词
    val content: String,                    // 注入内容
    val category: WorldEntryCategory,       // 分类
    val priority: Int = 100,                // 优先级（越大越优先）
    val enabled: Boolean = true,            // 是否启用
    val insertionOrder: Int = 100,          // 插入顺序（兼容Lorebook）
    val caseSensitive: Boolean = false,     // 关键词是否区分大小写
    val metadata: Map<String, Any> = emptyMap(), // 扩展元数据
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 检查是否匹配查询
     */
    fun matches(query: String): Boolean {
        if (!enabled) return false

        val searchQuery = if (caseSensitive) query else query.lowercase()
        return keys.any { key ->
            val searchKey = if (caseSensitive) key else key.lowercase()
            searchQuery.contains(searchKey)
        }
    }

    /**
     * 转换为Lorebook格式
     */
    fun toLorebookEntry(): Map<String, Any> {
        return mapOf(
            "keys" to keys,
            "content" to content,
            "insertion_order" to insertionOrder,
            "enabled" to enabled,
            "case_sensitive" to caseSensitive,
            "extensions" to mapOf(
                "category" to category.name,
                "xiaoguang_id" to entryId,
                "priority" to priority,
                "metadata" to metadata
            )
        )
    }

    companion object {
        /**
         * 从Lorebook格式创建
         */
        fun fromLorebookEntry(entry: Map<String, Any>, entryId: String = java.util.UUID.randomUUID().toString()): WorldEntry {
            @Suppress("UNCHECKED_CAST")
            val extensions = entry["extensions"] as? Map<String, Any> ?: emptyMap()

            return WorldEntry(
                entryId = extensions["xiaoguang_id"] as? String ?: entryId,
                keys = (entry["keys"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                content = entry["content"] as? String ?: "",
                category = try {
                    WorldEntryCategory.valueOf(extensions["category"] as? String ?: "KNOWLEDGE")
                } catch (e: Exception) {
                    WorldEntryCategory.KNOWLEDGE
                },
                priority = extensions["priority"] as? Int ?: 100,
                enabled = entry["enabled"] as? Boolean ?: true,
                insertionOrder = entry["insertion_order"] as? Int ?: 100,
                caseSensitive = entry["case_sensitive"] as? Boolean ?: false,
                metadata = extensions["metadata"] as? Map<String, Any> ?: emptyMap()
            )
        }
    }
}

/**
 * 世界场景
 * 代表不同的对话场景（如群聊、私聊等）
 */
data class WorldScene(
    val sceneId: String,                    // 场景ID
    val name: String,                       // 场景名称
    val description: String,                // 场景描述
    val activeRules: List<String> = emptyList(), // 激活的规则ID列表
    val contextData: Map<String, Any> = emptyMap(), // 场景特定上下文
    val priority: Int = 50,                 // 场景优先级
    val enabled: Boolean = true
) {
    /**
     * 获取场景上下文字符串
     */
    fun getContextString(): String {
        return buildString {
            appendLine("【当前场景】")
            appendLine("场景: $name")
            appendLine("描述: $description")
            if (contextData.isNotEmpty()) {
                appendLine("上下文:")
                contextData.forEach { (key, value) ->
                    appendLine("- $key: $value")
                }
            }
        }
    }
}

/**
 * 世界事件
 * 记录重要事件的时间线
 */
data class WorldEvent(
    val eventId: String,
    val title: String,                      // 事件标题
    val description: String,                // 事件描述
    val timestamp: Long,                    // 事件时间戳
    val participants: List<String> = emptyList(), // 参与者ID
    val tags: List<String> = emptyList(),   // 标签
    val importance: Float = 0.5f,           // 重要性 (0-1)
    val relatedEntries: List<String> = emptyList() // 相关条目ID
)

/**
 * World Book配置
 */
data class WorldBookConfig(
    val name: String = "小光的世界书",
    val description: String = "小光的世界观和全局知识",
    val version: String = "1.0.0",
    val maxEntries: Int = 1000,             // 最大条目数
    val maxTokensPerInjection: Int = 2000,  // 单次注入最大token数
    val enableAutoTrigger: Boolean = true,  // 是否启用自动触发
    val defaultPriority: Int = 100
)
