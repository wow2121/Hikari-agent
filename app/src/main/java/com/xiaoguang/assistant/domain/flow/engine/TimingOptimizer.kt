package com.xiaoguang.assistant.domain.flow.engine

import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.SpeakTiming
import com.xiaoguang.assistant.domain.flow.model.Thoughts
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 时机优化器
 * 判断何时说话最合适
 */
@Singleton
class TimingOptimizer @Inject constructor() {

    /**
     * 优化发言时机
     */
    fun optimize(
        perception: Perception,
        thoughts: Thoughts,
        shouldSpeak: Boolean
    ): SpeakTiming {
        if (!shouldSpeak) return SpeakTiming.DONT_SPEAK

        // 1. 紧急情况：立即说
        val urgentThought = thoughts.getMostUrgent()
        if (urgentThought != null && urgentThought.urgency > 0.9f) {
            return SpeakTiming.IMMEDIATE
        }

        // 2. 被叫到名字：立即说
        if (perception.mentionsXiaoguang) {
            return SpeakTiming.IMMEDIATE
        }

        // 3. 正在热烈讨论：等待间隙
        if (perception.environmentNoise > 0.7f) {
            return SpeakTiming.WAIT_FOR_GAP
        }

        // 4. 有人刚说完话（最后一条消息在2秒内）：等一小会
        val lastMessageTime = perception.recentMessages.lastOrNull()?.timestamp ?: 0
        val timeSinceLastMessage = System.currentTimeMillis() - lastMessageTime

        if (timeSinceLastMessage < 2000 && perception.hasRecentMessages) {
            return SpeakTiming.WAIT_FOR_GAP
        }

        // 5. 主动性得分不高：等待更好机会
        if (thoughts.proactivityScore < 0.6f) {
            return SpeakTiming.WAIT_FOR_OPPORTUNITY
        }

        // 6. 默认：立即说
        return SpeakTiming.IMMEDIATE
    }

    /**
     * 计算等待时间（毫秒）
     */
    fun calculateWaitTime(timing: SpeakTiming, perception: Perception): Long {
        return when (timing) {
            SpeakTiming.IMMEDIATE -> 0L
            SpeakTiming.WAIT_FOR_GAP -> {
                // 等待0.5-2秒
                if (perception.environmentNoise > 0.5f) {
                    2000L
                } else {
                    500L
                }
            }
            SpeakTiming.WAIT_FOR_OPPORTUNITY -> {
                // 等待5-10秒
                5000L + (Math.random() * 5000).toLong()
            }
            SpeakTiming.DONT_SPEAK -> Long.MAX_VALUE
        }
    }
}
