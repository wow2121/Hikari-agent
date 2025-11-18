package com.xiaoguang.assistant.domain.flow.engine

import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.ThoughtType
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import com.xiaoguang.assistant.domain.memory.models.MemoryQuery
import com.xiaoguang.assistant.domain.memory.models.TemporalQuery
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 纪念日引擎
 * 检测并提醒重要纪念日
 *
 * 已迁移到新记忆系统（UnifiedMemorySystem）
 */
@Singleton
class AnniversaryEngine @Inject constructor(
    private val unifiedMemorySystem: UnifiedMemorySystem,
    private val memoryLlmService: com.xiaoguang.assistant.domain.memory.MemoryLlmService
) {
    // 已检查过的日期（避免重复提醒）
    private val checkedDates = mutableSetOf<String>()

    /**
     * 检测今天是否有纪念日（包括提前提醒）
     *
     * 使用新的多维检索系统
     */
    suspend fun checkAnniversary(): InnerThought? {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val todayStr = today.toString()

        // 今天已检查过
        if (todayStr in checkedDates) return null

        try {
            // 使用新系统：多维查询纪念日记忆
            val todayMonthDay = "${today.monthValue}-${today.dayOfMonth}"

            val todayQuery = MemoryQuery(
                category = MemoryCategory.ANNIVERSARY,
                temporal = TemporalQuery.AnniversaryMatch(todayMonthDay),
                minImportance = 0.7f,
                limit = 5
            )

            val todayMemories = unifiedMemorySystem.queryMemories(todayQuery)

            // 检查是否有匹配今天日期的（高优先级）
            if (todayMemories.isNotEmpty()) {
                checkedDates.add(todayStr)

                val memory = todayMemories.first().memory
                val thought = InnerThought(
                    type = ThoughtType.SHARE,
                    content = "诶！主人，今天是${extractAnniversaryName(memory.content)}呢~",
                    urgency = 0.8f  // 高紧急度
                )

                Timber.i("[Anniversary] 检测到纪念日: ${thought.content}")
                return thought
            }

            // 检查是否有匹配明天日期的（提前提醒）
            val tomorrowMonthDay = "${tomorrow.monthValue}-${tomorrow.dayOfMonth}"

            val tomorrowQuery = MemoryQuery(
                category = MemoryCategory.ANNIVERSARY,
                temporal = TemporalQuery.AnniversaryMatch(tomorrowMonthDay),
                minImportance = 0.7f,
                limit = 5
            )

            val tomorrowMemories = unifiedMemorySystem.queryMemories(tomorrowQuery)

            if (tomorrowMemories.isNotEmpty()) {
                // 不标记为已检查，明天还要再提醒一次
                val memory = tomorrowMemories.first().memory
                val thought = InnerThought(
                    type = ThoughtType.SHARE,
                    content = "主人，明天是${extractAnniversaryName(memory.content)}呢，小光记着哦~",
                    urgency = 0.6f  // 中等紧急度
                )

                Timber.i("[Anniversary] 检测到明日纪念日: ${thought.content}")
                return thought
            }

            // 标记已检查
            checkedDates.add(todayStr)

            // 清理旧日期（保留最近7天）
            if (checkedDates.size > 7) {
                checkedDates.clear()
            }

            return null

        } catch (e: Exception) {
            Timber.e(e, "[Anniversary] 检测纪念日失败")
            return null
        }
    }

    /**
     * 从记忆内容中提取纪念日名称（LLM驱动，带fallback）
     *
     * ⭐ 优先使用LLM提取纪念日名称
     * ⚠️ LLM失败时自动降级到规则
     */
    private suspend fun extractAnniversaryName(content: String): String {
        // ⭐ 使用LLM提取
        val llmResult = memoryLlmService.extractAnniversaryName(content)
        if (llmResult.isSuccess) {
            return llmResult.getOrNull() ?: fallbackExtractAnniversaryName(content)
        }

        // ⚠️ Fallback
        return fallbackExtractAnniversaryName(content)
    }

    /**
     * 纪念日名称提取fallback（简单匹配）
     */
    private fun fallbackExtractAnniversaryName(content: String): String {
        return when {
            content.contains("生日") -> "生日"
            content.contains("纪念日") -> "纪念日"
            content.contains("节日") -> "节日"
            content.contains("周年") -> "周年纪念"
            else -> "特殊的日子"
        }
    }
}
