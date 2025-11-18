package com.xiaoguang.assistant.domain.flow.model

/**
 * 多因素评分结果
 */
data class Scores(
    val timeScore: Float,        // 基于时间的得分 0-1
    val emotionScore: Float,     // 基于情绪的得分 0-1
    val relationScore: Float,    // 基于关系的得分 0-1
    val contextScore: Float,     // 基于上下文的得分 0-1
    val biologicalScore: Float,  // 基于生物钟的得分 0-1
    val curiosityScore: Float,   // 基于好奇心的得分 0-1
    val urgencyScore: Float,     // 基于紧急度的得分 0-1
    val overallScore: Float,     // 综合得分 0-1
    val overallConfidence: Float,// 置信度 0-1
    val topReason: String,       // 主要原因
    val breakdown: Map<String, Float> = emptyMap()  // 详细分解
) {
    /**
     * 是否达到发言阈值
     */
    fun reachesThreshold(threshold: Float = 0.5f): Boolean {
        return overallScore >= threshold
    }

    /**
     * 获取主导因素
     */
    fun getDominantFactor(): Pair<String, Float> {
        val factors = mapOf(
            "时间" to timeScore,
            "情绪" to emotionScore,
            "关系" to relationScore,
            "上下文" to contextScore,
            "好奇心" to curiosityScore,
            "紧急度" to urgencyScore
        )
        return factors.maxByOrNull { it.value }!!.toPair()
    }

    /**
     * 生成评分报告
     */
    fun generateReport(): String {
        return buildString {
            appendLine("综合评分: ${String.format("%.2f", overallScore)} (置信度: ${String.format("%.2f", overallConfidence)})")
            appendLine("主要原因: $topReason")
            appendLine("详细分数:")
            appendLine("  时间因素: ${String.format("%.2f", timeScore)}")
            appendLine("  情绪因素: ${String.format("%.2f", emotionScore)}")
            appendLine("  关系因素: ${String.format("%.2f", relationScore)}")
            appendLine("  上下文: ${String.format("%.2f", contextScore)}")
            appendLine("  好奇心: ${String.format("%.2f", curiosityScore)}")
            appendLine("  紧急度: ${String.format("%.2f", urgencyScore)}")
        }
    }
}
