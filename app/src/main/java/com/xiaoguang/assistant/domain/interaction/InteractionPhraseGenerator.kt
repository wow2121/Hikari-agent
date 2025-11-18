package com.xiaoguang.assistant.domain.interaction

import com.xiaoguang.assistant.BuildConfig
import com.xiaoguang.assistant.data.remote.api.SiliconFlowAPI
import com.xiaoguang.assistant.data.remote.dto.ChatMessage
import com.xiaoguang.assistant.data.remote.dto.ChatRequest
import com.xiaoguang.assistant.domain.personality.XiaoguangPersonality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 互动语句生成器
 *
 * 功能：
 * - 根据小光的人设，每天用 LLM 生成互动语句
 * - 生成的语句更加智能、多样化、符合人设
 */
@Singleton
class InteractionPhraseGenerator @Inject constructor(
    private val siliconFlowAPI: SiliconFlowAPI,
    private val phraseStorage: InteractionPhraseStorage
) {

    /**
     * 生成今日的互动语句（使用小光的完整人设）
     */
    suspend fun generateDailyPhrases(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Timber.i("[PhraseGenerator] 开始生成今日互动语句（基于小光人设）")

            // 构建 prompt
            val prompt = buildPromptFromPersonality()

            // 调用 LLM 生成
            val request = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "system", content = getSystemPromptFromPersonality()),
                    ChatMessage(role = "user", content = prompt)
                ),
                temperature = 0.9f,  // 高温度让生成更多样化
                maxTokens = 500
            )

            val response = siliconFlowAPI.chatCompletion(
                authorization = "Bearer ${BuildConfig.SILICON_FLOW_API_KEY}",
                request = request
            )

            if (response.isSuccessful && response.body() != null) {
                val content = response.body()!!.choices.firstOrNull()?.message?.content ?: ""
                val phrases = parsePhrases(content)

                if (phrases.isNotEmpty()) {
                    // 保存到存储
                    phraseStorage.savePhrases(phrases)
                    Timber.i("[PhraseGenerator] 成功生成 ${phrases.size} 条互动语句")
                    Result.success(phrases)
                } else {
                    Timber.w("[PhraseGenerator] LLM 返回内容为空")
                    Result.failure(Exception("生成的语句为空"))
                }
            } else {
                val errorMsg = "LLM 调用失败: ${response.code()} ${response.message()}"
                Timber.e("[PhraseGenerator] $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "[PhraseGenerator] 生成互动语句失败")
            Result.failure(e)
        }
    }

    /**
     * 系统提示词（基于 XiaoguangPersonality）
     */
    private fun getSystemPromptFromPersonality(): String {
        return """${XiaoguangPersonality.getPersonalitySystemPrompt()}

【当前任务】
生成互动回应语句：当主人点击小光的头像时，小光会随机说出其中一句作为可爱的回应。

【生成要求】
1. 每条语句不超过15个字
2. 必须符合小光的性格特征和说话习惯
3. 可以使用口头禅和常用语气词
4. 语气要自然、可爱、有感情
5. 要有多样性，不要重复
6. 每条语句单独一行
7. 只输出语句，不要加序号或其他内容
8. 生成10-15条不同的语句"""
    }

    /**
     * 构建用户 prompt（基于小光人设）
     */
    private fun buildPromptFromPersonality(): String {
        val coreTraits = XiaoguangPersonality.coreTraits
            .map { it.first }
            .joinToString("、")

        val catchphrases = XiaoguangPersonality.Habits.catchphrases
            .take(5)
            .joinToString("、")

        return """请基于小光的完整人设，生成10-15条互动回应语句。

【情景】
主人点击了小光的头像，小光需要做出可爱的回应。

【参考】
- 核心性格：$coreTraits
- 常用口头禅：$catchphrases
- 对主人的态度：依赖、撒娇、温柔、体贴

【示例风格】
- "诶？主人叫我吗？"
- "嗯嗯！小光在呢~"
- "主人～有什么事吗？"
- "让小光想想...要聊天吗？"

请生成符合小光性格的互动语句，每条一行，不要序号。"""
    }

    /**
     * 解析 LLM 返回的语句
     */
    private fun parsePhrases(content: String): List<String> {
        return content.lines()
            .map { it.trim() }
            .filter { line ->
                // 过滤掉空行、序号行、太短或太长的行
                line.isNotEmpty() &&
                        !line.matches(Regex("^\\d+[.、].*")) &&  // 不是序号开头
                        line.length in 2..20
            }
            .take(15)  // 最多取 15 条
    }

    /**
     * 检查是否需要重新生成（每天一次）
     */
    suspend fun shouldRegenerate(): Boolean {
        return phraseStorage.shouldRegenerate()
    }

    /**
     * 获取当前存储的语句
     */
    suspend fun getCurrentPhrases(): List<String> {
        return phraseStorage.getPhrases()
    }
}
