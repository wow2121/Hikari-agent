package com.xiaoguang.assistant.domain.model

/**
 * 关系层级
 * 根据好感度自动划分不同的关系层级
 */
enum class RelationshipLevel(
    val displayName: String,
    val minAffection: Int,
    val maxAffection: Int,
    val description: String,
    val talkingStyle: String
) {
    /**
     * 陌生人 (0-20)
     * 刚认识或完全不熟
     */
    STRANGER(
        displayName = "陌生人",
        minAffection = 0,
        maxAffection = 20,
        description = "刚认识或完全不熟的人",
        talkingStyle = "礼貌、保持距离、使用敬语"
    ),

    /**
     * 熟人 (21-50)
     * 见过几次面，有基本了解
     */
    ACQUAINTANCE(
        displayName = "熟人",
        minAffection = 21,
        maxAffection = 50,
        description = "见过几次面，有基本了解",
        talkingStyle = "友好、适当礼貌"
    ),

    /**
     * 朋友 (51-70)
     * 互相了解，可以聊天
     */
    FRIEND(
        displayName = "朋友",
        minAffection = 51,
        maxAffection = 70,
        description = "互相了解，可以愉快聊天",
        talkingStyle = "自然、轻松、可以开玩笑"
    ),

    /**
     * 好友 (71-90)
     * 关系很好，互相信任
     */
    GOOD_FRIEND(
        displayName = "好友",
        minAffection = 71,
        maxAffection = 90,
        description = "关系很好，互相信任",
        talkingStyle = "亲密、随意、可以倾诉心事"
    ),

    /**
     * 挚友 (91-99)
     * 最亲密的朋友，无话不谈
     */
    BEST_FRIEND(
        displayName = "挚友",
        minAffection = 91,
        maxAffection = 99,
        description = "最亲密的朋友，无话不谈",
        talkingStyle = "非常亲密、完全放松、像家人一样"
    ),

    /**
     * 主人 (100)
     * 最特殊的存在
     */
    MASTER(
        displayName = "主人",
        minAffection = 100,
        maxAffection = 100,
        description = "最重要、最特殊的存在",
        talkingStyle = "亲密、撒娇、依赖、无话不谈"
    );

    companion object {
        /**
         * 根据好感度获取关系层级
         *
         * 注意：主人是独立身份，不是基于好感度判定的
         * - 主人必须通过isMaster=true明确指定
         * - 好感度100不等于主人，只是挚友的最高级别
         * - 主人身份永久锁定，不可降级
         */
        fun fromAffection(affection: Int, isMaster: Boolean = false): RelationshipLevel {
            // 主人是独立身份，优先判断
            if (isMaster) {
                return MASTER
            }

            // 非主人的情况下，根据好感度判断关系层级
            // 即使好感度是100，也只能是BEST_FRIEND，不会自动变成MASTER
            return values().dropLast(1).firstOrNull { level ->  // dropLast(1)排除MASTER层级
                affection in level.minAffection..level.maxAffection
            } ?: STRANGER
        }

        /**
         * 判断是否可以升级到下一层级
         */
        fun canLevelUp(currentAffection: Int, currentLevel: RelationshipLevel): Boolean {
            if (currentLevel == MASTER) return false
            return currentAffection > currentLevel.maxAffection
        }

        /**
         * 获取下一层级
         */
        fun getNextLevel(currentLevel: RelationshipLevel): RelationshipLevel? {
            if (currentLevel == MASTER) return null
            val ordinal = currentLevel.ordinal
            return if (ordinal < values().size - 1) {
                values()[ordinal + 1]
            } else {
                null
            }
        }
    }
}
