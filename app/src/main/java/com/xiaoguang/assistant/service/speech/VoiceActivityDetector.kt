package com.xiaoguang.assistant.service.speech

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音活动检测器（Voice Activity Detector）
 * 使用自适应阈值，根据环境噪声自动调整灵敏度
 */
@Singleton
class VoiceActivityDetector @Inject constructor() {
    companion object {
        private const val TAG = "VAD"

        // 自适应阈值参数
        private const val NOISE_FLOOR_INIT = 0.0003f  // 初始噪声底（适应 VOICE_RECOGNITION 源）
        private const val NOISE_FLOOR_ALPHA = 0.05f   // 噪声底更新系数（越小越平滑）
        private const val SPEECH_THRESHOLD_RATIO = 2.5f  // 语音阈值 = 噪声底 × 此倍数
        private const val MIN_THRESHOLD = 0.0002f    // 最低阈值，防止过于敏感
        private const val MAX_THRESHOLD = 0.01f      // 最高阈值，防止过于迟钝

        // 零交叉率阈值
        private const val ZCR_THRESHOLD = 0.3f

        // 连续帧数要求
        private const val MIN_SPEECH_FRAMES = 3
        private const val MIN_SILENCE_FRAMES = 10

        // 初始化帧数（用于学习环境噪声）
        private const val INIT_FRAMES = 50
    }

    private val _isSpeechDetected = MutableStateFlow(false)
    val isSpeechDetected: StateFlow<Boolean> = _isSpeechDetected.asStateFlow()

    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var lastEnergy = 0f  // 最后一次计算的能量值

    // 自适应阈值相关
    private var noiseFloor = NOISE_FLOOR_INIT  // 当前噪声底估计
    private var dynamicThreshold = NOISE_FLOOR_INIT * SPEECH_THRESHOLD_RATIO  // 动态阈值
    private var initFrameCount = 0  // 初始化计数器
    private var isInitialized = false  // 是否完成初始化

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

        // 初始化阶段：学习环境噪声
        if (!isInitialized) {
            initFrameCount++
            // 使用指数移动平均更新噪声底
            noiseFloor = noiseFloor * (1 - NOISE_FLOOR_ALPHA) + energy * NOISE_FLOOR_ALPHA

            if (initFrameCount >= INIT_FRAMES) {
                isInitialized = true
                dynamicThreshold = (noiseFloor * SPEECH_THRESHOLD_RATIO).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
                Log.i(TAG, "🎯 VAD 初始化完成: 噪声底=${String.format("%.5f", noiseFloor)}, 阈值=${String.format("%.5f", dynamicThreshold)}")
            }

            frameCount++
            return false  // 初始化期间不检测语音
        }

        // 自适应更新噪声底（只在静音时更新）
        if (!_isSpeechDetected.value && energy < dynamicThreshold) {
            noiseFloor = noiseFloor * (1 - NOISE_FLOOR_ALPHA * 0.1f) + energy * (NOISE_FLOOR_ALPHA * 0.1f)
            dynamicThreshold = (noiseFloor * SPEECH_THRESHOLD_RATIO).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        }

        // 判断是否为语音（使用动态阈值）
        val isSpeech = energy > dynamicThreshold && zcr < ZCR_THRESHOLD

        // 调试：定期输出能量和阈值
        if (frameCount % 100 == 0L) {
            Log.d(TAG, "[VAD] 能量: ${String.format("%.5f", energy)} (阈值: ${String.format("%.5f", dynamicThreshold)}, 噪声底: ${String.format("%.5f", noiseFloor)}), ZCR: ${String.format("%.3f", zcr)}, 语音: $isSpeech")
        }
        frameCount++

        // 平滑处理：需要连续检测到语音/静音才改变状态
        if (isSpeech) {
            speechFrameCount++
            silenceFrameCount = 0

            if (!_isSpeechDetected.value && speechFrameCount >= MIN_SPEECH_FRAMES) {
                _isSpeechDetected.value = true
                Log.i(TAG, "🎤 检测到语音活动 (能量: ${String.format("%.5f", energy)}, 阈值: ${String.format("%.5f", dynamicThreshold)})")
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

        // 重置自适应状态
        noiseFloor = NOISE_FLOOR_INIT
        dynamicThreshold = NOISE_FLOOR_INIT * SPEECH_THRESHOLD_RATIO
        initFrameCount = 0
        isInitialized = false
        frameCount = 0L

        Log.d(TAG, "VAD 已重置，将重新学习环境噪声")
    }
}
