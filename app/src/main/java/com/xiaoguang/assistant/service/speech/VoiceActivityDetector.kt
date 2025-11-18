package com.xiaoguang.assistant.service.speech

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音活动检测器（Voice Activity Detector）
 * 用于检测音频中是否存在语音活动，节省电量
 */
@Singleton
class VoiceActivityDetector @Inject constructor() {
    companion object {
        private const val TAG = "VAD"

        // 能量阈值（可根据环境调整）- 降低阈值以提高灵敏度
        private const val ENERGY_THRESHOLD = 0.005f  // 从 0.02 降低到 0.005

        // 零交叉率阈值
        private const val ZCR_THRESHOLD = 0.3f

        // 连续帧数要求
        private const val MIN_SPEECH_FRAMES = 3
        private const val MIN_SILENCE_FRAMES = 10
    }

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var lastEnergy = 0f  // 最后一次计算的能量值

    /**
     * 检测音频帧是否包含语音
     * @param audioData PCM 16位音频数据
     * @return true表示检测到语音活动
     */
    fun detectVoiceActivity(audioData: ByteArray): Boolean {
        // 计算能量
        val energy = calculateEnergy(audioData)
        lastEnergy = energy  // 保存最后一次的能量值

        // 计算零交叉率
        val zcr = calculateZeroCrossingRate(audioData)

        // 判断是否为语音
        val isSpeech = energy > ENERGY_THRESHOLD && zcr < ZCR_THRESHOLD

        // 调试：定期输出能量和 ZCR 值
        if (frameCount % 100 == 0L) {
            Log.d(TAG, "[VAD] 能量: ${String.format("%.5f", energy)} (阈值: $ENERGY_THRESHOLD), ZCR: ${String.format("%.3f", zcr)}, 语音检测: $isSpeech")
        }
        frameCount++

        // 平滑处理：需要连续检测到语音/静音才改变状态
        if (isSpeech) {
            speechFrameCount++
            silenceFrameCount = 0

            if (!_isSpeechDetected.value && speechFrameCount >= MIN_SPEECH_FRAMES) {
                _isSpeechDetected.value = true
                Log.i(TAG, "🎤 检测到语音活动 (能量: ${String.format("%.5f", energy)}, ZCR: ${String.format("%.3f", zcr)})")
            }
        } else {
            silenceFrameCount++
            speechFrameCount = 0

            if (_isSpeechDetected.value && silenceFrameCount >= MIN_SILENCE_FRAMES) {
                _isSpeechDetected.value = false
                Log.i(TAG, "🔇 语音活动结束")
            }
        }

        return _isSpeechDetected.value
    }

    private var frameCount = 0L  // 帧计数器，用于调试日志

    /**
     * 计算音频能量
     */
    private fun calculateEnergy(audioData: ByteArray): Float {
        var sum = 0.0
        var count = 0

        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
                val normalized = sample / 32768.0
                sum += normalized * normalized
                count++
            }
        }

        return if (count > 0) {
            kotlin.math.sqrt(sum / count).toFloat()
        } else {
            0f
        }
    }

    /**
     * 计算零交叉率（Zero Crossing Rate）
     * 语音信号的零交叉率通常较低，噪声较高
     */
    private fun calculateZeroCrossingRate(audioData: ByteArray): Float {
        var zeroCrossings = 0
        var previousSample: Short = 0

        for (i in audioData.indices step 2) {
            if (i + 1 < audioData.size) {
                val currentSample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()

                if (i > 0) {
                    if ((previousSample >= 0 && currentSample < 0) ||
                        (previousSample < 0 && currentSample >= 0)) {
                        zeroCrossings++
                    }
                }

                previousSample = currentSample
            }
        }

        val frameCount = audioData.size / 2
        return if (frameCount > 0) {
            zeroCrossings.toFloat() / frameCount
        } else {
            0f
        }
    }

    /**
     * 获取最后一次计算的能量值
     */
    fun getLastEnergy(): Float {
        return lastEnergy
    }

    /**
     * 重置状态
     */
    fun reset() {
        _isSpeechDetected.value = false
        speechFrameCount = 0
        silenceFrameCount = 0
        lastEnergy = 0f
    }
}
