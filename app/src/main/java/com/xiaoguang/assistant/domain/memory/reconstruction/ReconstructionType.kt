package com.xiaoguang.assistant.domain.memory.reconstruction

/**
 * 记忆重构类型
 *
 * 表示对记忆进行不同类型的重构操作
 */
enum class ReconstructionType(
    val description: String,
    val priority: Int  // 优先级，数字越小优先级越高
) {
    /**
     * APPEND - 追加信息
     * 在现有记忆基础上追加新信息，不修改原有内容
     */
    APPEND(
        description = "追加新信息到现有记忆",
        priority = 1
    ),

    /**
     * UPDATE - 智能更新
     * 更新记忆中的某些字段，保留其他字段不变
     * 例如：更新重要性、添加新标签
     */
    UPDATE(
        description = "智能更新记忆字段",
        priority = 2
    ),

    /**
     * REPLACE - 完全替换
     * 完全替换旧记忆，用新记忆取代
     * 适用于内容完全过时的情况
     */
    REPLACE(
        description = "完全替换旧记忆",
        priority = 3
    ),

    /**
     * CORRECTION - 纠正错误
     * 纠正记忆中的错误信息，保留正确的部分
     * 例如：纠正时间、地点等事实错误
     */
    CORRECTION(
        description = "纠正记忆中的错误信息",
        priority = 1
    ),

    /**
     * REINTERPRETATION - 重新诠释
     * 基于新信息重新理解和解释现有记忆
     * 例如：添加上下文、改变理解角度
     */
    REINTERPRETATION(
        description = "重新诠释和理解记忆",
        priority = 2
    ),

    /**
     * MERGE - 合并记忆
     * 将相似或相关的记忆合并为一个
     * 适用于记忆碎片化的情况
     */
    MERGE(
        description = "合并相似记忆",
        priority = 1
    );

    companion object {
        /**
         * 根据描述获取重构类型
         */
        fun fromDescription(desc: String): ReconstructionType? {
            return values().find { it.description == desc }
        }

        /**
         * 按优先级排序获取所有类型
         */
        fun getByPriority(): List<ReconstructionType> {
            return values().sortedBy { it.priority }
        }
    }
}
