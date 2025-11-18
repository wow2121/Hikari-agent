package com.xiaoguang.assistant.service.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * TTS 语音播放服务
 *
 * 核心功能：
 * - 中文语音播放，让小光能够发出声音进行语音回复
 * - 个性化语音参数调节（语速、音调体现小光个性）
 * - 播放队列管理和优先级控制
 * - 情绪化语音参数自动调整
 *
 * 播放优先级：
 * - NORMAL: 正常优先级，加入播放队列
 * - HIGH: 高优先级，立即打断当前播放
 *
 * 情绪化语音参数：
 * - HAPPY: 开心（快语速，高音调）
 * - EXCITED: 兴奋（很快语速，很高音调）
 * - SAD: 悲伤（慢语速，低音调）
 * - CALM: 平静（正常语速和音调）
 * - DEFAULT: 默认（小光的基础个性：略高音调，亲切）
 */
@Singleton
class TtsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null

    // 初始化状态
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // 播放状态
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // 小光的默认语音参数（体现个性）
    private var speechRate = 1.0f      // 语速（1.0 = 正常）
    private var pitch = 1.1f            // 音调（1.1 = 略高，更亲切）

    /**
     * 初始化 TTS 引擎
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            Timber.d("[TtsService] 开始初始化 TTS...")

            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    // 设置中文语言
                    val result = tts?.setLanguage(Locale.CHINESE)

                    when (result) {
                        TextToSpeech.LANG_MISSING_DATA,
                        TextToSpeech.LANG_NOT_SUPPORTED -> {
                            Timber.e("[TtsService] 中文语言包不支持")
                            _isReady.value = false
                            continuation.resume(false)
                        }
                        else -> {
                            // 设置小光的个性化参数
                            tts?.setSpeechRate(speechRate)
                            tts?.setPitch(pitch)

                            _isReady.value = true
                            Timber.i("[TtsService] TTS 初始化成功（语速: $speechRate, 音调: $pitch）")
                            continuation.resume(true)
                        }
                    }
                } else {
                    Timber.e("[TtsService] TTS 初始化失败: status=$status")
                    _isReady.value = false
                    continuation.resume(false)
                }
            }

            continuation.invokeOnCancellation {
                Timber.w("[TtsService] 初始化被取消")
            }
        }
    }

    /**
     * 播放语音
     *
     * @param text 要播放的文本
     * @param priority 播放优先级
     * @return 播放是否成功
     */
    suspend fun speak(
        text: String,
        priority: SpeakPriority = SpeakPriority.NORMAL
    ): Boolean = withContext(Dispatchers.Main) {
        if (!_isReady.value) {
            Timber.w("[TtsService] TTS 未初始化，无法播放")
            return@withContext false
        }

        if (text.isBlank()) {
            Timber.w("[TtsService] 文本为空，跳过播放")
            return@withContext false
        }

        suspendCancellableCoroutine { continuation ->
            val utteranceId = UUID.randomUUID().toString()

            // 设置播放进度监听
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    Timber.d("[TtsService] 开始播放: ${text.take(20)}...")
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    Timber.d("[TtsService] 播放完成")
                    continuation.resume(true)
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    Timber.e("[TtsService] 播放失败")
                    continuation.resume(false)
                }

            })

            // 确定播放模式
            val queueMode = when (priority) {
                SpeakPriority.HIGH -> TextToSpeech.QUEUE_FLUSH  // 打断当前播放
                SpeakPriority.NORMAL -> TextToSpeech.QUEUE_ADD  // 加入队列
            }

            // 开始播放
            val result = tts?.speak(text, queueMode, null, utteranceId)

            if (result == TextToSpeech.ERROR) {
                Timber.e("[TtsService] speak() 调用失败")
                _isSpeaking.value = false
                continuation.resume(false)
            }

            continuation.invokeOnCancellation {
                Timber.w("[TtsService] 播放被取消")
                stop()
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        if (_isSpeaking.value) {
            tts?.stop()
            _isSpeaking.value = false
            Timber.d("[TtsService] 已停止播放")
        }
    }

    /**
     * 设置语速
     * @param rate 1.0 = 正常，< 1.0 = 慢，> 1.0 = 快
     */
    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 3.0f)
        tts?.setSpeechRate(speechRate)
        Timber.d("[TtsService] 语速设置为: $speechRate")
    }

    /**
     * 设置音调
     * @param pitch 1.0 = 正常，< 1.0 = 低沉，> 1.0 = 尖锐
     */
    fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(this.pitch)
        Timber.d("[TtsService] 音调设置为: ${this.pitch}")
    }

    /**
     * 设置小光的情绪化语音参数
     * 根据不同情绪调整语速和音调
     */
    fun setEmotionParams(emotion: TtsEmotion) {
        when (emotion) {
            TtsEmotion.HAPPY -> {
                setSpeechRate(1.2f)
                setPitch(1.3f)
            }
            TtsEmotion.EXCITED -> {
                setSpeechRate(1.3f)
                setPitch(1.4f)
            }
            TtsEmotion.SAD -> {
                setSpeechRate(0.8f)
                setPitch(0.9f)
            }
            TtsEmotion.CALM -> {
                setSpeechRate(1.0f)
                setPitch(1.0f)
            }
            TtsEmotion.DEFAULT -> {
                setSpeechRate(1.0f)
                setPitch(1.1f)  // 小光的默认音调（略高，亲切）
            }
        }
        Timber.d("[TtsService] 情绪参数设置为: $emotion")
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            _isReady.value = false
            _isSpeaking.value = false
            Timber.i("[TtsService] TTS 资源已释放")
        } catch (e: Exception) {
            Timber.e(e, "[TtsService] 释放资源时发生异常")
        }
    }

    /**
     * 检查是否正在播放
     */
    fun isSpeaking(): Boolean = _isSpeaking.value
}

/**
 * 播放优先级
 */
enum class SpeakPriority {
    NORMAL,  // 正常优先级，加入队列
    HIGH     // 高优先级，立即打断当前播放
}

/**
 * TTS 情绪参数
 * 用于根据小光的情绪状态调整语音表现
 */
enum class TtsEmotion {
    DEFAULT,   // 默认（小光的基础个性：略高音调，亲切）
    HAPPY,     // 开心（快语速，高音调）
    EXCITED,   // 兴奋（很快语速，很高音调）
    SAD,       // 悲伤（慢语速，低音调）
    CALM       // 平静（正常语速和音调）
}
