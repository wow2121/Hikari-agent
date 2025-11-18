package com.xiaoguang.assistant.service.speech

/**
 * 语音识别结果
 */
sealed class SpeechRecognitionResult {
    /**
     * 部分识别结果（实时）
     */
    data class Partial(val text: String) : SpeechRecognitionResult()

    /**
     * 最终识别结果
     */
    data class Final(
        val text: String,
        val confidence: Float = 1.0f,
        val method: RecognitionMethodUsed
    ) : SpeechRecognitionResult()

    /**
     * 识别错误
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : SpeechRecognitionResult()

    /**
     * 识别开始
     */
    object Started : SpeechRecognitionResult()

    /**
     * 识别结束
     */
    object Stopped : SpeechRecognitionResult()
}

/**
 * 实际使用的识别方法
 */
enum class RecognitionMethodUsed {
    ONLINE_ANDROID,     // Android SpeechRecognizer
    ONLINE_SILICON_FLOW, // SiliconFlow API
    OFFLINE_VOSK        // Vosk离线识别
}
