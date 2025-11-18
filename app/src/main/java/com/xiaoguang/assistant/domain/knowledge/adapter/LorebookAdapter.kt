package com.xiaoguang.assistant.domain.knowledge.adapter

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.knowledge.WorldBook
import com.xiaoguang.assistant.domain.knowledge.models.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lorebook格式适配器
 * 提供与SillyTavern、RisuAI等工具的兼容性
 *
 * 支持格式：
 * - Lorebook JSON (SillyTavern)
 * - Character Card V2 (TavernAI)
 * - World Info (NovelAI)
 */
@Singleton
class LorebookAdapter @Inject constructor(
    private val worldBook: WorldBook,
    private val characterBook: CharacterBook,
    private val gson: Gson
) {

    // ==================== World Book Export/Import ====================

    /**
     * 导出为标准Lorebook格式
     *
     * 格式示例：
     * {
     *   "name": "小光的世界",
     *   "description": "小光AI助手的世界观设定",
     *   "version": "1.0",
     *   "entries": [
     *     {
     *       "uid": "entry_001",
     *       "keys": ["小光", "个人设定"],
     *       "content": "小光是一个元气满满的二次元美少女...",
     *       "enabled": true,
     *       "insertion_order": 100,
     *       "case_sensitive": false,
     *       "priority": 200,
     *       "extensions": {
     *         "category": "SETTING"
     *       }
     *     }
     *   ]
     * }
     */
    suspend fun exportWorldBookToLorebook(): String {
        return try {
            val lorebookData = worldBook.exportToLorebook()
            gson.toJson(lorebookData)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 导出World Book失败")
            "{}"
        }
    }

    /**
     * 从标准Lorebook格式导入
     */
    suspend fun importWorldBookFromLorebook(jsonString: String): Result<Int> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val lorebookData = gson.fromJson(jsonString, Map::class.java) as Map<String, Any>
            worldBook.importFromLorebook(lorebookData)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 导入Lorebook失败")
            Result.failure(e)
        }
    }

    /**
     * 从文件导入Lorebook
     */
    suspend fun importWorldBookFromFile(file: File): Result<Int> {
        return try {
            val jsonString = file.readText()
            importWorldBookFromLorebook(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 从文件导入失败: ${file.path}")
            Result.failure(e)
        }
    }

    /**
     * 导出到文件
     */
    suspend fun exportWorldBookToFile(file: File): Result<Unit> {
        return try {
            val jsonString = exportWorldBookToLorebook()
            file.writeText(jsonString)
            Timber.i("[LorebookAdapter] 导出World Book到文件: ${file.path}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 导出到文件失败")
            Result.failure(e)
        }
    }

    // ==================== Character Card Export/Import ====================

    /**
     * 导出为Character Card V2格式
     *
     * 格式示例：
     * {
     *   "spec": "chara_card_v2",
     *   "spec_version": "2.0",
     *   "data": {
     *     "name": "小光",
     *     "description": "元气满满的二次元美少女",
     *     "personality": "活泼、贴心、真诚",
     *     "scenario": "QQ群聊环境",
     *     "first_mes": "嗨！我是小光~",
     *     "mes_example": "<START>\n{{user}}: 你好\n{{char}}: 你好呀！...",
     *     "creator_notes": "",
     *     "system_prompt": "",
     *     "tags": ["AI助手", "二次元", "陪伴"],
     *     "creator": "",
     *     "character_version": "1.0",
     *     "extensions": {
     *       "world_info": {
     *         "entries": [...]
     *       }
     *     }
     *   }
     * }
     */
    suspend fun exportCharacterToCard(characterId: String): String? {
        return try {
            val cardData = characterBook.exportToCharacterCard(characterId) ?: return null

            val characterCardV2 = mapOf(
                "spec" to "chara_card_v2",
                "spec_version" to "2.0",
                "data" to cardData
            )

            gson.toJson(characterCardV2)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 导出Character Card失败")
            null
        }
    }

    /**
     * 从Character Card V2格式导入
     */
    suspend fun importCharacterFromCard(jsonString: String): Result<String> {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            // 解析Character Card V2格式
            val cardData = if (json.has("data")) {
                // V2 格式
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(json.getAsJsonObject("data"), Map::class.java) as Map<String, Any>
            } else {
                // V1 格式或直接格式
                @Suppress("UNCHECKED_CAST")
                gson.fromJson(json, Map::class.java) as Map<String, Any>
            }

            characterBook.importFromCharacterCard(cardData)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 导入Character Card失败")
            Result.failure(e)
        }
    }

    /**
     * 从文件导入Character Card
     */
    suspend fun importCharacterFromFile(file: File): Result<String> {
        return try {
            val jsonString = file.readText()
            importCharacterFromCard(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 从文件导入Character失败: ${file.path}")
            Result.failure(e)
        }
    }

    /**
     * 导出Character Card到文件
     */
    suspend fun exportCharacterToFile(characterId: String, file: File): Result<Unit> {
        return try {
            val jsonString = exportCharacterToCard(characterId) ?: return Result.failure(
                Exception("角色不存在或导出失败")
            )
            file.writeText(jsonString)
            Timber.i("[LorebookAdapter] 导出Character Card到文件: ${file.path}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 导出到文件失败")
            Result.failure(e)
        }
    }

    // ==================== Batch Operations ====================

    /**
     * 批量导出所有角色
     */
    suspend fun exportAllCharacters(directory: File): Result<Int> {
        return try {
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val profiles = characterBook.getAllProfiles()
            var exportedCount = 0

            for (profile in profiles) {
                val fileName = "${profile.basicInfo.name}_${profile.basicInfo.characterId}.json"
                val file = File(directory, fileName)

                val result = exportCharacterToFile(profile.basicInfo.characterId, file)
                if (result.isSuccess) {
                    exportedCount++
                }
            }

            Timber.i("[LorebookAdapter] 批量导出 $exportedCount 个角色")
            Result.success(exportedCount)

        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 批量导出失败")
            Result.failure(e)
        }
    }

    /**
     * 批量导入角色（从目录）
     */
    suspend fun importCharactersFromDirectory(directory: File): Result<Int> {
        return try {
            if (!directory.exists() || !directory.isDirectory) {
                return Result.failure(Exception("目录不存在: ${directory.path}"))
            }

            val jsonFiles = directory.listFiles { file ->
                file.extension.lowercase() == "json"
            } ?: emptyArray()

            var importedCount = 0

            for (file in jsonFiles) {
                val result = importCharacterFromFile(file)
                if (result.isSuccess) {
                    importedCount++
                    Timber.d("[LorebookAdapter] 导入成功: ${file.name}")
                } else {
                    Timber.w("[LorebookAdapter] 导入失败: ${file.name}")
                }
            }

            Timber.i("[LorebookAdapter] 批量导入 $importedCount 个角色")
            Result.success(importedCount)

        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] 批量导入失败")
            Result.failure(e)
        }
    }

    // ==================== Format Validation ====================

    /**
     * 验证Lorebook格式
     */
    fun validateLorebookFormat(jsonString: String): ValidationResult {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            val errors = mutableListOf<String>()

            // 检查必需字段
            if (!json.has("entries")) {
                errors.add("缺少'entries'字段")
            }

            if (json.has("entries")) {
                val entries = json.getAsJsonArray("entries")
                entries.forEachIndexed { index, entry ->
                    val entryObj = entry.asJsonObject
                    if (!entryObj.has("keys")) {
                        errors.add("条目 $index 缺少'keys'字段")
                    }
                    if (!entryObj.has("content")) {
                        errors.add("条目 $index 缺少'content'字段")
                    }
                }
            }

            if (errors.isEmpty()) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(errors)
            }

        } catch (e: Exception) {
            ValidationResult.Invalid(listOf("JSON格式错误: ${e.message}"))
        }
    }

    /**
     * 验证Character Card格式
     */
    fun validateCharacterCardFormat(jsonString: String): ValidationResult {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            val errors = mutableListOf<String>()

            // 检查是否是V2格式
            val data = if (json.has("data")) {
                json.getAsJsonObject("data")
            } else {
                json
            }

            // 检查必需字段
            if (!data.has("name")) {
                errors.add("缺少'name'字段")
            }

            if (errors.isEmpty()) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(errors)
            }

        } catch (e: Exception) {
            ValidationResult.Invalid(listOf("JSON格式错误: ${e.message}"))
        }
    }

    // ==================== Format Conversion ====================

    /**
     * 将NovelAI World Info转换为Lorebook格式
     */
    fun convertNovelAIToLorebook(novelAIJson: String): String {
        return try {
            @Suppress("UNCHECKED_CAST")
            val data = gson.fromJson(novelAIJson, Map::class.java) as Map<String, Any>

            @Suppress("UNCHECKED_CAST")
            val worldInfo = data["worldInfo"] as? List<Map<String, Any>> ?: emptyList()

            val lorebookEntries = worldInfo.mapIndexed { index, entry ->
                mapOf(
                    "uid" to "novelai_import_$index",
                    "keys" to (entry["keys"] ?: emptyList<String>()),
                    "content" to (entry["entry"] ?: ""),
                    "enabled" to true,
                    "insertion_order" to 100,
                    "case_sensitive" to false,
                    "priority" to 100
                )
            }

            val lorebook = mapOf(
                "name" to "Imported from NovelAI",
                "description" to "Converted from NovelAI World Info",
                "version" to "1.0",
                "entries" to lorebookEntries
            )

            gson.toJson(lorebook)

        } catch (e: Exception) {
            Timber.e(e, "[LorebookAdapter] NovelAI转换失败")
            "{}"
        }
    }

    /**
     * 检测JSON格式类型
     */
    fun detectFormat(jsonString: String): FormatType {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            when {
                json.has("spec") && json.get("spec").asString == "chara_card_v2" -> {
                    FormatType.CHARACTER_CARD_V2
                }
                json.has("name") && json.has("personality") -> {
                    FormatType.CHARACTER_CARD_V1
                }
                json.has("entries") -> {
                    FormatType.LOREBOOK
                }
                json.has("worldInfo") -> {
                    FormatType.NOVELAI_WORLD_INFO
                }
                else -> {
                    FormatType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            FormatType.INVALID
        }
    }
}

/**
 * 格式验证结果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

/**
 * 支持的格式类型
 */
enum class FormatType {
    LOREBOOK,
    CHARACTER_CARD_V1,
    CHARACTER_CARD_V2,
    NOVELAI_WORLD_INFO,
    UNKNOWN,
    INVALID
}
