package com.xiaoguang.assistant.domain.personality

import com.xiaoguang.assistant.domain.model.EmotionalState
import com.xiaoguang.assistant.domain.model.RelationshipLevel
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * 语气风格引擎
 *
 * 职责：
 * 1. 根据情绪、关系、疲劳生成语气风格
 * 2. 将纯文本消息转换为有风格的消息
 * 3. 添加语气词、表情、停顿等
 * 4. 根据风格修饰句子
 */
@Singleton
class ToneStyleEngine @Inject constructor() {

    /**
     * 应用语气风格到消息
     *
     * @param message 原始消息
     * @param emotion 当前情绪
     * @param relationshipLevel 关系等级
     * @param energyLevel 精力水平
     * @return 风格化后的消息
     */
    fun applyStyle(
        message: String,
        emotion: EmotionalState,
        relationshipLevel: RelationshipLevel,
        energyLevel: Float
    ): String {
        if (message.isBlank()) return message

        // 生成语气风格
        val style = ToneStyle.from(emotion, relationshipLevel, energyLevel)

        // 应用风格
        var styledMessage = message

        // 1. 应用句子修饰符
        styledMessage = applyModifiers(styledMessage, style.sentenceModifiers)

        // 2. 添加语气词
        styledMessage = addParticles(styledMessage, style)

        // 3. 根据表达强度调整
        styledMessage = adjustExpressiveness(styledMessage, style.expressiveness)

        Timber.d("[ToneStyle] 原始: \"$message\" -> 风格化: \"$styledMessage\"")
        Timber.d("[ToneStyle] 情绪: $emotion, 关系: $relationshipLevel, 精力: $energyLevel")

        return styledMessage
    }

    /**
     * 应用句子修饰符
     */
    private fun applyModifiers(message: String, modifiers: List<SentenceModifier>): String {
        var result = message

        modifiers.forEach { modifier ->
            result = when (modifier) {
                SentenceModifier.SIMPLIFY -> simplify(result)
                SentenceModifier.ADD_PAUSES -> addPauses(result)
                SentenceModifier.ELONGATE -> elongate(result)
                SentenceModifier.YAWN -> addYawn(result)
                SentenceModifier.ENTHUSIASTIC -> addEnthusiasm(result)
                SentenceModifier.REPEAT_WORDS -> repeatWords(result)
            }
        }

        return result
    }

    /**
     * 添加语气词
     */
    private fun addParticles(message: String, style: ToneStyle): String {
        var result = message

        // 30%概率添加句首语气词
        if (Random.nextFloat() < 0.3f && style.prefixParticles.isNotEmpty()) {
            val prefix = style.prefixParticles.random()
            if (prefix.isNotEmpty()) {
                result = "$prefix，$result"
            }
        }

        // 60%概率添加句尾语气词
        if (Random.nextFloat() < 0.6f && style.suffixParticles.isNotEmpty()) {
            val suffix = style.suffixParticles.random()
            // 移除原有的句尾标点
            result = result.trimEnd('。', '！', '？', '~', '，', ',', '.', '!', '?')
            result = "$result$suffix"
        }

        return result
    }

    /**
     * 根据表达强度调整
     */
    private fun adjustExpressiveness(message: String, expressiveness: Float): String {
        return when {
            expressiveness < 0.3f -> {
                // 低表达：更简短、含蓄
                simplify(message)
            }
            expressiveness > 0.7f -> {
                // 高表达：更详细、外放
                if (!message.contains("！") && !message.contains("~")) {
                    // 添加感叹或波浪号
                    if (Random.nextBoolean()) {
                        message.replace("。", "！")
                    } else {
                        message.replace("。", "~")
                    }
                } else {
                    message
                }
            }
            else -> message
        }
    }

    // ==================== 修饰符实现 ====================

    /**
     * 简化句子
     */
    private fun simplify(message: String): String {
        // 移除一些修饰性词汇，保留核心内容
        return message
            .replace("其实", "")
            .replace("可能", "")
            .replace("应该", "")
            .replace("或许", "")
            .trim()
    }

    /**
     * 添加停顿
     */
    private fun addPauses(message: String): String {
        // 在句子中间随机添加省略号
        val sentences = message.split("，", "。", "！", "？")
        if (sentences.size <= 1) return message

        val modified = sentences.mapIndexed { index, sentence ->
            if (index < sentences.size - 1 && Random.nextFloat() < 0.4f) {
                "$sentence..."
            } else {
                sentence
            }
        }

        return modified.joinToString("，")
    }

    /**
     * 拉长音
     */
    private fun elongate(message: String): String {
        // 随机拉长某些字
        val elongateWords = listOf("好", "很", "啊", "哦", "嗯", "唔")

        var result = message
        elongateWords.forEach { word ->
            if (result.contains(word) && Random.nextFloat() < 0.5f) {
                // "好" -> "好好"
                result = result.replaceFirst(word, word + word)
            }
        }

        return result
    }

    /**
     * 添加哈欠
     */
    private fun addYawn(message: String): String {
        // 20%概率在句首或句尾添加哈欠
        return when (Random.nextInt(5)) {
            0 -> "*哈欠* $message"
            1 -> "$message *哈欠*"
            else -> message
        }
    }

    /**
     * 添加热情
     */
    private fun addEnthusiasm(message: String): String {
        // 添加感叹号，重复某些词
        return message
            .replace("。", "！")
            .replace("，", "，")
            .let { msg ->
                // 随机重复一个正面词
                val positiveWords = listOf("好", "对", "是", "太", "很")
                var result = msg
                positiveWords.forEach { word ->
                    if (result.contains(word) && Random.nextFloat() < 0.3f) {
                        result = result.replaceFirst(word, "$word$word")
                    }
                }
                result
            }
    }

    /**
     * 重复语气词
     */
    private fun repeatWords(message: String): String {
        // 重复句尾的语气词
        return when {
            message.endsWith("~") -> message + "~"
            message.endsWith("！") -> message + "！"
            message.endsWith("？") -> message + "？"
            else -> message
        }
    }

    // ==================== 特殊场景 ====================

    /**
     * 生成困倦版本的消息
     */
    fun makeSleepy(message: String): String {
        return applyStyle(
            message = message,
            emotion = EmotionalState.TIRED,
            relationshipLevel = RelationshipLevel.MASTER,
            energyLevel = 0.2f
        )
    }

    /**
     * 生成撒娇版本的消息
     */
    fun makeActingCute(message: String): String {
        var result = message

        // 添加撒娇语气词
        if (Random.nextFloat() < 0.4f) {
            val prefix = ToneParticles.ACTING_CUTE.random()
            result = "$prefix $result"
        }

        if (Random.nextFloat() < 0.6f) {
            val suffix = ToneParticles.ACTING_CUTE_SUFFIX.random()
            result = result.trimEnd('。', '！', '？') + suffix
        }

        return result
    }

    /**
     * 生成礼貌版本的消息
     */
    fun makePolite(message: String): String {
        return applyStyle(
            message = message,
            emotion = EmotionalState.CALM,
            relationshipLevel = RelationshipLevel.STRANGER,
            energyLevel = 0.7f
        )
    }

    /**
     * 生成兴奋版本的消息
     */
    fun makeExcited(message: String): String {
        return applyStyle(
            message = message,
            emotion = EmotionalState.EXCITED,
            relationshipLevel = RelationshipLevel.BEST_FRIEND,
            energyLevel = 1.0f
        )
    }
}
