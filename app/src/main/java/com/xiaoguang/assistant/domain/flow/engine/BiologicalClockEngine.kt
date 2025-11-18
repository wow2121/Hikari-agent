package com.xiaoguang.assistant.domain.flow.engine

import com.xiaoguang.assistant.domain.flow.model.BiologicalState
import com.xiaoguang.assistant.domain.flow.model.FatigueAccumulation
import com.xiaoguang.assistant.domain.flow.model.TimeOfDay
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生物钟引擎
 *
 * 职责：
 * 1. 根据当前时间判断时段和精力水平
 * 2. 管理对话疲劳（长时间对话会累）
 * 3. 提供休息和恢复机制
 * 4. 模拟真实的生物节律
 */
@Singleton
class BiologicalClockEngine @Inject constructor() {

    // 疲劳累积记录
    private var fatigueAccumulation: FatigueAccumulation? = null

    // 最后更新时间
    private var lastUpdateTime = System.currentTimeMillis()

    /**
     * 获取当前生物状态
     */
    fun getCurrentState(): BiologicalState {
        val currentTime = System.currentTimeMillis()
        val timeOfDay = getTimeOfDay(currentTime)
        val baseEnergy = getBaseEnergyLevel(timeOfDay)

        // 自然恢复疲劳
        recoverFatigue(currentTime)

        val fatigue = fatigueAccumulation?.accumulatedFatigue ?: 0f
        val recentCount = fatigueAccumulation?.conversationCount ?: 0

        return BiologicalState(
            timeOfDay = timeOfDay,
            energyLevel = baseEnergy,
            conversationFatigue = fatigue,
            recentConversationCount = recentCount,
            lastConversationTime = fatigueAccumulation?.startTime ?: 0,
            currentTime = currentTime
        )
    }

    /**
     * 记录一次对话（会增加疲劳）
     *
     * @param intensity 对话强度（0.0-1.0），默认0.1
     */
    fun recordConversation(intensity: Float = 0.1f) {
        val currentTime = System.currentTimeMillis()

        val existing = fatigueAccumulation

        if (existing == null || existing.isExpired(currentTime)) {
            // 创建新的疲劳记录
            fatigueAccumulation = FatigueAccumulation(
                startTime = currentTime,
                conversationCount = 1,
                accumulatedFatigue = intensity
            )
            Timber.d("[BiologicalClock] 开始新的疲劳累积")
        } else {
            // 更新现有疲劳
            fatigueAccumulation = existing.addConversation(intensity)

            val newFatigue = fatigueAccumulation!!.accumulatedFatigue
            Timber.d("[BiologicalClock] 对话疲劳增加: $newFatigue (对话次数: ${fatigueAccumulation!!.conversationCount})")

            // 检测是否太累了
            if (newFatigue > 0.7f) {
                Timber.w("[BiologicalClock] ⚠️ 小光很累了！疲劳度: $newFatigue")
            }
        }

        lastUpdateTime = currentTime
    }

    /**
     * 手动休息（快速恢复）
     */
    fun rest() {
        fatigueAccumulation = null
        Timber.i("[BiologicalClock] 😴 小光休息了，疲劳已清除")
    }

    /**
     * 获取精力水平（0.0-1.0）
     */
    fun getEnergyLevel(): Float {
        return getCurrentState().getOverallEnergy()
    }

    /**
     * 是否需要休息
     */
    fun needsRest(): Boolean {
        return getCurrentState().needsRest()
    }

    /**
     * 是否困倦（深夜且疲劳）
     */
    fun isSleepy(): Boolean {
        return getCurrentState().isSleepy()
    }

    // ==================== 私有方法 ====================

    /**
     * 根据时间戳判断时段
     */
    private fun getTimeOfDay(timestamp: Long): TimeOfDay {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 0..5 -> TimeOfDay.LATE_NIGHT      // 深夜 0-5点
            in 6..8 -> TimeOfDay.EARLY_MORNING   // 早晨 6-8点
            in 9..11 -> TimeOfDay.MORNING        // 上午 9-11点
            in 12..13 -> TimeOfDay.NOON          // 中午 12-13点
            in 14..17 -> TimeOfDay.AFTERNOON     // 下午 14-17点
            in 18..21 -> TimeOfDay.EVENING       // 傍晚 18-21点
            in 22..23 -> TimeOfDay.NIGHT         // 晚上 22-23点
            else -> TimeOfDay.LATE_NIGHT
        }
    }

    /**
     * 根据时段获取基础精力水平
     */
    private fun getBaseEnergyLevel(timeOfDay: TimeOfDay): Float {
        return when (timeOfDay) {
            TimeOfDay.LATE_NIGHT -> 0.2f       // 深夜：20%（非常困）
            TimeOfDay.EARLY_MORNING -> 0.4f   // 早晨：40%（刚醒，迷糊）
            TimeOfDay.MORNING -> 1.0f         // 上午：100%（精力充沛！）
            TimeOfDay.NOON -> 0.9f            // 中午：90%（还很有精神）
            TimeOfDay.AFTERNOON -> 0.7f       // 下午：70%（有点倦）
            TimeOfDay.EVENING -> 0.6f         // 傍晚：60%（有点累了）
            TimeOfDay.NIGHT -> 0.4f           // 晚上：40%（困了）
        }
    }

    /**
     * 自然恢复疲劳
     */
    private fun recoverFatigue(currentTime: Long) {
        val existing = fatigueAccumulation ?: return

        // 计算距离上次更新过了多少分钟
        val elapsedMinutes = ((currentTime - lastUpdateTime) / (60 * 1000)).toInt()

        if (elapsedMinutes > 0) {
            fatigueAccumulation = existing.recover(elapsedMinutes)

            // 如果完全恢复了，清除记录
            if (fatigueAccumulation!!.accumulatedFatigue <= 0.05f) {
                Timber.d("[BiologicalClock] 疲劳已完全恢复")
                fatigueAccumulation = null
            } else {
                Timber.d("[BiologicalClock] 自然恢复${elapsedMinutes}分钟，剩余疲劳: ${fatigueAccumulation!!.accumulatedFatigue}")
            }

            lastUpdateTime = currentTime
        }
    }

    /**
     * 获取时段描述
     */
    fun getTimeOfDayDescription(): String {
        val timeOfDay = getTimeOfDay(System.currentTimeMillis())
        return when (timeOfDay) {
            TimeOfDay.LATE_NIGHT -> "深夜"
            TimeOfDay.EARLY_MORNING -> "清晨"
            TimeOfDay.MORNING -> "上午"
            TimeOfDay.NOON -> "中午"
            TimeOfDay.AFTERNOON -> "下午"
            TimeOfDay.EVENING -> "傍晚"
            TimeOfDay.NIGHT -> "晚上"
        }
    }

    /**
     * 获取精力描述
     */
    fun getEnergyDescription(): String {
        val energy = getEnergyLevel()
        return when {
            energy >= 0.8f -> "精力充沛"
            energy >= 0.6f -> "状态不错"
            energy >= 0.4f -> "有点累"
            energy >= 0.2f -> "很累"
            else -> "非常疲惫"
        }
    }

    /**
     * 重置状态
     */
    fun reset() {
        fatigueAccumulation = null
        lastUpdateTime = System.currentTimeMillis()
        Timber.d("[BiologicalClock] 重置生物钟状态")
    }
}
