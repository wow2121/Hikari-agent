package com.xiaoguang.assistant.domain.flow.engine

import com.xiaoguang.assistant.domain.flow.model.FlowConfig
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.flow.model.Perception
import com.xiaoguang.assistant.domain.flow.model.ThoughtType
import com.xiaoguang.assistant.domain.flow.service.FlowLlmService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 好奇心引擎（纯LLM驱动）
 * 检测对话中的矛盾、未完成话题、新信息等，生成好奇的问题
 */
@Singleton
class CuriosityEngine @Inject constructor(
    private val flowLlmService: FlowLlmService,
    private val config: FlowConfig
) {
    /**
     * 检测好奇心（纯LLM）
     */
    suspend fun detect(perception: Perception): InnerThought? {
        if (!config.enableCuriosity) return null
        if (perception.recentMessages.isEmpty()) return null

        // ✅ 用LLM检测好奇点
        val result = flowLlmService.detectCuriosity(perception)
            ?: return null  // 无好奇点

        Timber.d("[CuriosityEngine] LLM检测到好奇点: ${result.reason}")

        return InnerThought(
            type = ThoughtType.CURIOSITY,
            content = result.question ?: result.reason,
            urgency = result.urgency
        )
    }
}
