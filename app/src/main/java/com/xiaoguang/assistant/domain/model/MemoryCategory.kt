package com.xiaoguang.assistant.domain.model

/**
 * 记忆类型枚举
 * 定义AI可以提取的各种记忆类型
 */
enum class MemoryCategory(
    val value: String,
    val displayName: String,
    val description: String
) {
    /**
     * 用户相关信息
     * 包括用户的个人信息、习惯、偏好等
     */
    USER_RELATED(
        value = "user_related",
        displayName = "用户信息",
        description = "关于用户本人的信息，如姓名、职业、兴趣、习惯等"
    ),

    /**
     * 其他人的信息
     * 包括用户提到的其他人的信息
     */
    PERSON(
        value = "person",
        displayName = "人物信息",
        description = "关于其他人的信息，如姓名、关系、特征等"
    ),

    /**
     * 他人之间的对话
     * 用户周围其他人之间的对话内容
     */
    OTHERS_CONVERSATION(
        value = "others_conversation",
        displayName = "他人对话",
        description = "用户周围其他人之间的对话内容"
    ),

    /**
     * 知识性内容
     * 如老师讲课、历史观点、科学知识等
     */
    KNOWLEDGE(
        value = "knowledge",
        displayName = "知识内容",
        description = "教育性、知识性的内容，如讲课内容、科学知识、历史事实等"
    ),

    /**
     * 重要事件
     * 值得记录的事件、活动等
     */
    EVENT(
        value = "event",
        displayName = "重要事件",
        description = "重要事件、活动、经历等"
    ),

    /**
     * 待办事项
     * 需要完成的任务、计划等
     */
    TASK(
        value = "task",
        displayName = "待办事项",
        description = "需要完成的任务、计划、提醒等"
    ),

    /**
     * 用户偏好
     * 用户的喜好、选择倾向等
     */
    PREFERENCE(
        value = "preference",
        displayName = "用户偏好",
        description = "用户的喜好、习惯、选择倾向等"
    ),

    /**
     * 其他事实
     * 其他值得记录的事实性信息
     */
    FACT(
        value = "fact",
        displayName = "其他事实",
        description = "其他值得记录的事实性信息"
    );

    companion object {
        /**
         * 从字符串值获取枚举
         */
        fun fromValue(value: String): MemoryCategory {
            return values().find { it.value == value } ?: FACT
        }

        /**
         * 获取所有类型的值列表
         */
        fun allValues(): List<String> {
            return values().map { it.value }
        }
    }
}
