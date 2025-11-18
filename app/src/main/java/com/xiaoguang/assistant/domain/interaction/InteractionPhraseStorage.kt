package com.xiaoguang.assistant.domain.interaction

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 互动语句存储
 *
 * 使用 SharedPreferences 存储 LLM 生成的互动语句
 */
@Singleton
class InteractionPhraseStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "interaction_phrases"
        private const val KEY_PHRASES = "phrases"
        private const val KEY_LAST_GENERATED = "last_generated"
        private const val SEPARATOR = "|||"
    }

    /**
     * 保存语句列表
     */
    suspend fun savePhrases(phrases: List<String>) = withContext(Dispatchers.IO) {
        try {
            val phrasesString = phrases.joinToString(SEPARATOR)
            val currentDate = getCurrentDate()

            prefs.edit {
                putString(KEY_PHRASES, phrasesString)
                putString(KEY_LAST_GENERATED, currentDate)
            }

            Timber.i("[PhraseStorage] 已保存 ${phrases.size} 条语句，日期: $currentDate")
        } catch (e: Exception) {
            Timber.e(e, "[PhraseStorage] 保存语句失败")
        }
    }

    /**
     * 获取存储的语句
     */
    suspend fun getPhrases(): List<String> = withContext(Dispatchers.IO) {
        try {
            val phrasesString = prefs.getString(KEY_PHRASES, null)
            if (phrasesString != null) {
                phrasesString.split(SEPARATOR)
                    .filter { it.isNotBlank() }
            } else {
                getDefaultPhrases()
            }
        } catch (e: Exception) {
            Timber.e(e, "[PhraseStorage] 获取语句失败，使用默认语句")
            getDefaultPhrases()
        }
    }

    /**
     * 判断是否需要重新生成（每天一次）
     */
    suspend fun shouldRegenerate(): Boolean = withContext(Dispatchers.IO) {
        val lastGenerated = prefs.getString(KEY_LAST_GENERATED, null)
        val currentDate = getCurrentDate()

        val shouldRegenerate = lastGenerated != currentDate

        if (shouldRegenerate) {
            Timber.i("[PhraseStorage] 需要重新生成互动语句 (上次: $lastGenerated, 当前: $currentDate)")
        }

        shouldRegenerate
    }

    /**
     * 获取当前日期（用于判断是否需要重新生成）
     */
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    /**
     * 默认语句（作为备用）
     */
    private fun getDefaultPhrases(): List<String> {
        return listOf(
            "嗯？主人叫我吗？",
            "我在这里呢~",
            "有什么事吗？",
            "怎么啦~",
            "我正在想事情呢~",
            "嘿嘿，摸我干嘛",
            "主人想聊天了吗？",
            "我在听哦~",
            "需要我帮忙吗？",
            "今天也要加油哦！"
        )
    }
}
