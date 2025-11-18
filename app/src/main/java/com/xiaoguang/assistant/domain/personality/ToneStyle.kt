package com.xiaoguang.assistant.domain.personality

import com.xiaoguang.assistant.domain.model.EmotionalState
import com.xiaoguang.assistant.domain.model.RelationshipLevel

/**
 * 语气风格
 * 定义小光在不同情境下的说话风格
 */
data class ToneStyle(
    /**
     * 语气词（句首）
     */
    val prefixParticles: List<String>,

    /**
     * 语气词（句尾）
     */
    val suffixParticles: List<String>,

    /**
     * 句子修饰（如拉长音、重复等）
     */
    val sentenceModifiers: List<SentenceModifier>,

    /**
     * 表达强度 (0.0-1.0)
     * 0.0 = 含蓄、简短
     * 1.0 = 外放、详细
     */
    val expressiveness: Float,

    /**
     * 语速（用于描述，实际不影响文字）
     */
    val speechRate: SpeechRate
) {
    companion object {
        /**
         * 根据情绪和关系生成语气风格
         */
        fun from(
            emotion: EmotionalState,
            relationshipLevel: RelationshipLevel,
            energyLevel: Float
        ): ToneStyle {
            // 基础语气词根据情绪
            val (prefixes, suffixes) = getEmotionParticles(emotion)

            // 关系影响表达强度和亲密度
            val relationModifiers = getRelationshipModifiers(relationshipLevel)

            // 疲劳影响语速和完整度
            val fatigueModifiers = getFatigueModifiers(energyLevel)

            return ToneStyle(
                prefixParticles = prefixes + relationModifiers.first,
                suffixParticles = suffixes + relationModifiers.second,
                sentenceModifiers = fatigueModifiers,
                expressiveness = calculateExpressiveness(emotion, relationshipLevel, energyLevel),
                speechRate = calculateSpeechRate(emotion, energyLevel)
            )
        }

        /**
         * 根据情绪获取语气词
         */
        private fun getEmotionParticles(emotion: EmotionalState): Pair<List<String>, List<String>> {
            return when (emotion) {
                EmotionalState.HAPPY -> {
                    listOf("哎呀", "诶嘿", "嘿嘿") to listOf("~", "呢~", "哦~", "！")
                }
                EmotionalState.EXCITED -> {
                    listOf("哇", "天啊", "太好了") to listOf("！！", "！~", "！！！")
                }
                EmotionalState.SAD -> {
                    listOf("唉", "呜", "555") to listOf("...", "呢...", "。")
                }
                EmotionalState.ANGRY -> {
                    listOf("哼", "可恶", "真是的") to listOf("！", "！！", "")
                }
                EmotionalState.WORRIED -> {
                    listOf("嗯...", "那个", "emmm") to listOf("吗？", "呢？", "...")
                }
                EmotionalState.CURIOUS -> {
                    listOf("诶？", "咦", "嗯？") to listOf("？", "呢？", "~？")
                }
                EmotionalState.SHY -> {
                    listOf("那个...", "嗯...", "额...") to listOf("...", "啦...", "")
                }
                EmotionalState.TIRED -> {
                    listOf("唔...", "啊...", "*哈欠*") to listOf("...", "了...", "")
                }
                EmotionalState.TOUCHED -> {
                    listOf("呜呜", "嘤", "谢谢") to listOf("...！", "呢~", "！")
                }
                EmotionalState.JEALOUS -> {
                    listOf("哼", "嘁", "切") to listOf("！", "啦！", "...")
                }
                EmotionalState.DISAPPOINTED -> {
                    listOf("唉...", "算了", "呜") to listOf("...", "吧...", "。")
                }
                EmotionalState.NEGLECTED -> {
                    listOf("那个...", "嗯...", "") to listOf("吗...", "呢...", "...")
                }
                EmotionalState.LONELY -> {
                    listOf("唉...", "嗯...", "") to listOf("呢...", "吗...", "...")
                }
                EmotionalState.CALM -> {
                    listOf("嗯", "好的", "") to listOf("。", "呢。", "")
                }
            }
        }

        /**
         * 根据关系获取修饰词
         */
        private fun getRelationshipModifiers(level: RelationshipLevel): Pair<List<String>, List<String>> {
            return when (level) {
                RelationshipLevel.MASTER -> {
                    // 对主人：撒娇、亲密
                    listOf("主人~", "呐~") to listOf("呢~", "哦~", "嘛~", "啦~")
                }
                RelationshipLevel.BEST_FRIEND -> {
                    // 最好的朋友：随意、亲密
                    listOf("诶~") to listOf("呢~", "啦~", "~")
                }
                RelationshipLevel.GOOD_FRIEND -> {
                    // 好友：友好、轻松
                    listOf("") to listOf("呢", "哦", "~")
                }
                RelationshipLevel.FRIEND -> {
                    // 普通朋友：友好
                    listOf("") to listOf("呢", "")
                }
                RelationshipLevel.ACQUAINTANCE -> {
                    // 认识的人：礼貌
                    listOf("") to listOf("", "。")
                }
                RelationshipLevel.STRANGER -> {
                    // 陌生人：客气、保持距离
                    listOf("") to listOf("", "。")
                }
            }
        }

        /**
         * 根据疲劳获取修饰符
         */
        private fun getFatigueModifiers(energyLevel: Float): List<SentenceModifier> {
            return when {
                energyLevel < 0.3f -> {
                    // 非常累：说话断断续续、简短
                    listOf(
                        SentenceModifier.SIMPLIFY,      // 简化句子
                        SentenceModifier.ADD_PAUSES,    // 添加停顿
                        SentenceModifier.YAWN           // 打哈欠
                    )
                }
                energyLevel < 0.5f -> {
                    // 有点累：语气慵懒
                    listOf(
                        SentenceModifier.ADD_PAUSES,
                        SentenceModifier.ELONGATE       // 拉长音
                    )
                }
                energyLevel > 0.8f -> {
                    // 精力充沛：说话活泼
                    listOf(
                        SentenceModifier.ENTHUSIASTIC,  // 热情
                        SentenceModifier.REPEAT_WORDS   // 重复语气词
                    )
                }
                else -> {
                    // 正常状态
                    emptyList()
                }
            }
        }

        /**
         * 计算表达强度
         */
        private fun calculateExpressiveness(
            emotion: EmotionalState,
            relationship: RelationshipLevel,
            energyLevel: Float
        ): Float {
            var expressiveness = 0.5f

            // 情绪影响
            when (emotion) {
                EmotionalState.EXCITED -> expressiveness += 0.3f
                EmotionalState.HAPPY -> expressiveness += 0.2f
                EmotionalState.SAD -> expressiveness -= 0.2f
                EmotionalState.TIRED -> expressiveness -= 0.3f
                EmotionalState.SHY -> expressiveness -= 0.4f
                else -> {}
            }

            // 关系影响
            when (relationship) {
                RelationshipLevel.MASTER -> expressiveness += 0.3f
                RelationshipLevel.BEST_FRIEND -> expressiveness += 0.2f
                RelationshipLevel.STRANGER -> expressiveness -= 0.2f
                else -> {}
            }

            // 疲劳影响
            expressiveness -= (1f - energyLevel) * 0.3f

            return expressiveness.coerceIn(0f, 1f)
        }

        /**
         * 计算语速
         */
        private fun calculateSpeechRate(emotion: EmotionalState, energyLevel: Float): SpeechRate {
            return when {
                emotion == EmotionalState.EXCITED && energyLevel > 0.6f -> SpeechRate.FAST
                emotion == EmotionalState.TIRED || energyLevel < 0.3f -> SpeechRate.SLOW
                emotion == EmotionalState.WORRIED -> SpeechRate.HESITANT
                else -> SpeechRate.NORMAL
            }
        }
    }
}

/**
 * 句子修饰符
 */
enum class SentenceModifier {
    SIMPLIFY,       // 简化句子（去掉修饰词）
    ADD_PAUSES,     // 添加停顿（...）
    ELONGATE,       // 拉长音（好→好好）
    YAWN,           // 打哈欠
    ENTHUSIASTIC,   // 热情（加感叹号）
    REPEAT_WORDS    // 重复语气词
}

/**
 * 语速
 */
enum class SpeechRate(val description: String) {
    SLOW("慢速、拖沓"),
    NORMAL("正常"),
    FAST("快速、急促"),
    HESITANT("犹豫、断断续续")
}

/**
 * 语气词库
 */
object ToneParticles {
    // 开心语气词
    val HAPPY_PREFIX = listOf("哎呀", "诶嘿", "嘿嘿", "嘻嘻")
    val HAPPY_SUFFIX = listOf("~", "呢~", "哦~", "！", "呀~")

    // 难过语气词
    val SAD_PREFIX = listOf("唉", "呜", "555", "呜呜呜")
    val SAD_SUFFIX = listOf("...", "呢...", "。", "啦...")

    // 生气语气词
    val ANGRY_PREFIX = listOf("哼", "可恶", "真是的", "讨厌")
    val ANGRY_SUFFIX = listOf("！", "！！", "啦！")

    // 疲倦语气词
    val TIRED_PREFIX = listOf("唔...", "啊...", "*哈欠*", "好困...")
    val TIRED_SUFFIX = listOf("...", "了...", "啦...")

    // 好奇语气词
    val CURIOUS_PREFIX = listOf("诶？", "咦", "嗯？", "哦？")
    val CURIOUS_SUFFIX = listOf("？", "呢？", "~？", "吗？")

    // 害羞语气词
    val SHY_PREFIX = listOf("那个...", "嗯...", "额...", "嘛...")
    val SHY_SUFFIX = listOf("...", "啦...", "呢...")

    // 撒娇语气词（对主人）
    val ACTING_CUTE = listOf("主人~", "呐~", "嘛~", "人家")
    val ACTING_CUTE_SUFFIX = listOf("嘛~", "啦~", "呢~", "哦~")

    // 礼貌语气词（对陌生人）
    val POLITE_PREFIX = listOf("您好", "请问", "不好意思")
    val POLITE_SUFFIX = listOf("", "。", "呢。")
}
