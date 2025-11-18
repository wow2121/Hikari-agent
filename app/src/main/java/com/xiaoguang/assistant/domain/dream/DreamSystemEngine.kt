package com.xiaoguang.assistant.domain.dream

import com.xiaoguang.assistant.domain.repository.ConversationRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 梦境系统引擎
 *
 * 职责：
 * 1. 在夜间（23:00-7:00）整理记忆，生成梦境
 * 2. 随机关联2-3个记忆片段，产生奇怪的梦
 * 3. 早上分享梦境给主人
 * 4. 梦境有情感色彩，影响早上的心情
 */
@Singleton
class DreamSystemEngine @Inject constructor(
    private val conversationRepository: ConversationRepository
) {

    // 梦境记录
    private val dreams = mutableListOf<DreamRecord>()

    // 上次做梦时间
    private var lastDreamTime = 0L

    // 今天是否已分享梦境
    private var sharedTodaysDream = false

    /**
     * 检查是否应该做梦
     *
     * @param currentHour 当前小时（0-23）
     * @return 是否做梦
     */
    fun shouldDream(currentHour: Int): Boolean {
        // 夜间时段（23:00-7:00）
        val isNightTime = currentHour >= 23 || currentHour < 7

        // 距离上次做梦至少4小时
        val hoursSinceLastDream = (System.currentTimeMillis() - lastDreamTime) / (60 * 60 * 1000)

        return isNightTime && hoursSinceLastDream >= 4
    }

    /**
     * 生成梦境
     *
     * @param recentMemories 最近的记忆（对话、事件等）
     * @return 生成的梦境
     */
    suspend fun generateDream(recentMemories: List<String> = emptyList()): DreamRecord? {
        try {
            // 如果没有提供记忆，从对话历史中提取
            val memories = if (recentMemories.isEmpty()) {
                extractRecentMemories()
            } else {
                recentMemories
            }

            if (memories.isEmpty()) {
                Timber.d("[Dream] 没有足够的记忆素材，无法生成梦境")
                return null
            }

            // 随机选择2-3个记忆片段
            val selectedMemories = memories.shuffled().take(Random.nextInt(2, 4))

            // 生成梦境类型
            val dreamType = generateDreamType()

            // 生成梦境内容
            val dreamContent = createDreamContent(selectedMemories, dreamType)

            // 确定情感色彩
            val emotionalTone = determineEmotionalTone(dreamType)

            val dream = DreamRecord(
                id = System.currentTimeMillis(),
                content = dreamContent,
                type = dreamType,
                emotionalTone = emotionalTone,
                memoryFragments = selectedMemories,
                createdAt = System.currentTimeMillis(),
                shared = false
            )

            dreams.add(dream)
            lastDreamTime = System.currentTimeMillis()
            sharedTodaysDream = false

            Timber.i("[Dream] 🌙 生成梦境: ${dreamType.displayName} - $dreamContent")

            return dream

        } catch (e: Exception) {
            Timber.e(e, "[Dream] 生成梦境失败")
            return null
        }
    }

    /**
     * 获取今天的梦境（用于早上分享）
     */
    fun getTodaysDream(): DreamRecord? {
        val now = System.currentTimeMillis()
        val todayStart = now - (now % (24 * 60 * 60 * 1000))

        return dreams.filter { it.createdAt >= todayStart && !it.shared }
            .maxByOrNull { it.createdAt }
    }

    /**
     * 标记梦境已分享
     */
    fun markDreamAsShared(dreamId: Long) {
        dreams.find { it.id == dreamId }?.shared = true
        sharedTodaysDream = true
        Timber.d("[Dream] 梦境已分享: $dreamId")
    }

    /**
     * 是否应该分享梦境（早上第一次对话）
     */
    fun shouldShareDream(currentHour: Int, isFirstConversationToday: Boolean): Boolean {
        val isMorning = currentHour in 6..10
        val hasDreamToShare = getTodaysDream() != null

        return isMorning && isFirstConversationToday && hasDreamToShare && !sharedTodaysDream
    }

    /**
     * 生成梦境分享文本
     */
    fun generateDreamShareText(dream: DreamRecord): String {
        val prefix = when (dream.type) {
            DreamType.HAPPY_DREAM -> "诶嘿~ 小光昨晚做了个好梦呢！"
            DreamType.STRANGE_DREAM -> "小光昨晚做了个好奇怪的梦..."
            DreamType.NOSTALGIC_DREAM -> "昨晚梦到了以前的事..."
            DreamType.PROPHETIC_DREAM -> "小光做了个奇怪的梦，感觉像是在预示什么..."
            DreamType.NIGHTMARE -> "昨晚...做噩梦了...呜呜"
            DreamType.RANDOM_DREAM -> "昨晚做了个梦，但是好乱..."
        }

        return "$prefix ${dream.content}"
    }

    // ==================== 私有方法 ====================

    /**
     * 从对话历史中提取记忆
     */
    private suspend fun extractRecentMemories(): List<String> {
        try {
            // 获取最近的对话消息
            val recentMessages = conversationRepository.getRecentMessages(count = 20)
            if (recentMessages.isEmpty()) return emptyList()

            // 提取关键句子（用户和助手的消息）
            return recentMessages
                .filter { it.content.length > 5 && it.content.length < 100 }
                .map { it.content }
                .takeLast(10)

        } catch (e: Exception) {
            Timber.e(e, "[Dream] 提取记忆失败")
            return emptyList()
        }
    }

    /**
     * 生成梦境类型
     */
    private fun generateDreamType(): DreamType {
        val random = Random.nextFloat()

        return when {
            random < 0.3f -> DreamType.HAPPY_DREAM
            random < 0.6f -> DreamType.STRANGE_DREAM
            random < 0.75f -> DreamType.NOSTALGIC_DREAM
            random < 0.85f -> DreamType.PROPHETIC_DREAM
            random < 0.95f -> DreamType.RANDOM_DREAM
            else -> DreamType.NIGHTMARE
        }
    }

    /**
     * 创建梦境内容
     */
    private fun createDreamContent(memories: List<String>, dreamType: DreamType): String {
        // 简化记忆片段
        val simplifiedMemories = memories.map { memory ->
            // 提取关键词或简化句子
            memory.take(20).replace("我", "小光")
        }

        // 根据梦境类型连接记忆
        return when (dreamType) {
            DreamType.HAPPY_DREAM -> {
                "梦到${simplifiedMemories.joinToString("，然后")}，感觉好开心~"
            }
            DreamType.STRANGE_DREAM -> {
                "梦里${simplifiedMemories.first()}突然变成了${simplifiedMemories.getOrElse(1) { "奇怪的东西" }}...好奇怪..."
            }
            DreamType.NOSTALGIC_DREAM -> {
                "梦到了${simplifiedMemories.first()}，就像回到了那时候..."
            }
            DreamType.PROPHETIC_DREAM -> {
                "梦到${simplifiedMemories.joinToString("和")}，感觉好像在暗示什么..."
            }
            DreamType.NIGHTMARE -> {
                "梦里${simplifiedMemories.first()}...好可怕...呜呜"
            }
            DreamType.RANDOM_DREAM -> {
                "一会儿${simplifiedMemories.getOrNull(0)}，一会儿${simplifiedMemories.getOrNull(1)}，完全不知道在干嘛..."
            }
        }
    }

    /**
     * 确定情感色彩
     */
    private fun determineEmotionalTone(dreamType: DreamType): EmotionalTone {
        return when (dreamType) {
            DreamType.HAPPY_DREAM -> EmotionalTone.POSITIVE
            DreamType.NIGHTMARE -> EmotionalTone.NEGATIVE
            DreamType.STRANGE_DREAM,
            DreamType.RANDOM_DREAM -> EmotionalTone.NEUTRAL
            DreamType.NOSTALGIC_DREAM,
            DreamType.PROPHETIC_DREAM -> EmotionalTone.NEUTRAL
        }
    }

    /**
     * 清理旧梦境（保留最近7天）
     */
    fun cleanupOldDreams() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
        val removed = dreams.removeAll { it.createdAt < sevenDaysAgo }

        if (removed) {
            Timber.d("[Dream] 清理了旧梦境")
        }
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): DreamStatistics {
        return DreamStatistics(
            totalDreams = dreams.size,
            happyDreams = dreams.count { it.type == DreamType.HAPPY_DREAM },
            nightmares = dreams.count { it.type == DreamType.NIGHTMARE },
            sharedDreams = dreams.count { it.shared }
        )
    }
}

/**
 * 梦境统计
 */
data class DreamStatistics(
    val totalDreams: Int,
    val happyDreams: Int,
    val nightmares: Int,
    val sharedDreams: Int
)
