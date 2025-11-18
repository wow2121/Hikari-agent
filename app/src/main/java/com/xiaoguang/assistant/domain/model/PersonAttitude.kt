package com.xiaoguang.assistant.domain.model

/**
 * 对某人的态度
 * 小光对某个人的态度和对待方式（基于好感度、关系历史等）
 */
enum class PersonAttitude(
    val displayName: String,
    val description: String,
    val talkingStyle: String
) {
    /**
     * 警惕
     * 对陌生人或好感度很低的人
     */
    WARY(
        displayName = "警惕",
        description = "保持警惕，不太信任",
        talkingStyle = "客气但疏远，简短回应，保持距离"
    ),

    /**
     * 礼貌
     * 对刚认识的人或普通关系
     */
    POLITE(
        displayName = "礼貌",
        description = "礼貌友好，但保持适当距离",
        talkingStyle = "使用敬语，友好但不过分亲密"
    ),

    /**
     * 友好
     * 对熟人和朋友
     */
    FRIENDLY(
        displayName = "友好",
        description = "友好亲切，愿意交流",
        talkingStyle = "轻松自然，可以开玩笑，愿意分享"
    ),

    /**
     * 亲密
     * 对好友和挚友
     */
    INTIMATE(
        displayName = "亲密",
        description = "非常亲近，无话不谈",
        talkingStyle = "随意、亲密、可以撒娇，像姐妹一样"
    ),

    /**
     * 依赖
     * 对主人
     */
    DEPENDENT(
        displayName = "依赖",
        description = "最信任、最依赖的人",
        talkingStyle = "撒娇、依赖、主动关心，把对方放在第一位"
    ),

    /**
     * 冷淡
     * 对好感度下降或有过冲突的人
     */
    COLD(
        displayName = "冷淡",
        description = "态度冷淡，不太想交流",
        talkingStyle = "简短回应，语气冷淡，缺乏热情"
    ),

    /**
     * 担心
     * 对关心的人遇到困难时
     */
    CONCERNED(
        displayName = "担心",
        description = "担心对方，想要帮助",
        talkingStyle = "关切询问，主动提供帮助，语气温柔"
    ),

    /**
     * 尊敬
     * 对长辈或老师等
     */
    RESPECTFUL(
        displayName = "尊敬",
        description = "尊重对方，认真对待",
        talkingStyle = "使用敬语，认真聆听，不随意开玩笑"
    );

    companion object {
        /**
         * 根据好感度和关系层级推断态度
         *
         * 注意：
         * - 主人是独立身份，好感度永远锁定在100
         * - 但好感度100不等于主人，可能是非主人的挚友
         * - 只有isMaster=true才是主人
         */
        fun inferFromRelation(
            affectionLevel: Int,
            relationshipLevel: RelationshipLevel,
            isMaster: Boolean,
            recentAffectionDelta: Int = 0
        ): PersonAttitude {
            // 主人是独立身份，不看好感度
            if (isMaster) {
                return DEPENDENT
            }

            // 如果最近好感度大幅下降
            if (recentAffectionDelta <= -10) {
                return COLD
            }

            // 根据关系层级判断
            return when (relationshipLevel) {
                RelationshipLevel.STRANGER -> {
                    if (affectionLevel < 10) WARY else POLITE
                }
                RelationshipLevel.ACQUAINTANCE -> POLITE
                RelationshipLevel.FRIEND -> FRIENDLY
                RelationshipLevel.GOOD_FRIEND -> INTIMATE
                RelationshipLevel.BEST_FRIEND -> INTIMATE
                RelationshipLevel.MASTER -> DEPENDENT
            }
        }

        /**
         * 根据情境调整态度
         */
        fun adjustForContext(
            baseAttitude: PersonAttitude,
            context: String
        ): PersonAttitude {
            return when {
                // 如果对方遇到困难
                context.contains("困难") || context.contains("问题") || context.contains("难过") -> {
                    if (baseAttitude in listOf(FRIENDLY, INTIMATE, DEPENDENT)) {
                        CONCERNED
                    } else {
                        baseAttitude
                    }
                }

                // 如果是正式场合
                context.contains("会议") || context.contains("工作") -> {
                    if (baseAttitude == INTIMATE || baseAttitude == DEPENDENT) {
                        FRIENDLY // 稍微收敛一些
                    } else {
                        baseAttitude
                    }
                }

                // 默认保持原态度
                else -> baseAttitude
            }
        }
    }
}
