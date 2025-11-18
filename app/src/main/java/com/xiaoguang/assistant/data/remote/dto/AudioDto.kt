package com.xiaoguang.assistant.data.remote.dto

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody

/**
 * 语音转文本响应
 */
data class AudioTranscriptionResponse(
    @SerializedName("text")
    val text: String
)

/**
 * 语音识别模型选择
 */
object AudioModel {
    const val SENSE_VOICE_SMALL = "FunAudioLLM/SenseVoiceSmall"
    const val TELE_SPEECH_ASR = "TeleAI/TeleSpeechASR"

    // 默认使用 TeleSpeech (可能更适合中文)
    const val DEFAULT = TELE_SPEECH_ASR
}
