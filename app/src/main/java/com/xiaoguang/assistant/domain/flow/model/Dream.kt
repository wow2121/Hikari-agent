package com.xiaoguang.assistant.domain.flow.model

/**
 * 梦境数据模型
 */
data class Dream(
    val content: String,      // 梦境内容
    val mood: String,          // 梦境氛围（HAPPY/SAD/MYSTERIOUS）
    val timestamp: Long        // 梦境生成时间
)
