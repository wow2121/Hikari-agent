package com.xiaoguang.assistant.domain.voiceprint

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * 声纹特征提取器接口
 */
interface VoiceprintFeatureExtractor {
    /**
     * 从音频数据中提取声纹特征向量
     *
     * @param audioData PCM 16-bit 音频数据
     * @param sampleRate 采样率
     * @return 特征向量（归一化）
     */
    fun extractFeature(audioData: ByteArray, sampleRate: Int): FloatArray
}

/**
 * 简单的声纹特征提取器实现
 *
 * 提取音频的基础声学特征：
 * - 音高相关特征（基频）
 * - 频谱特征（MFCC系数）
 * - 能量特征（短时能量）
 * - 韵律特征（语速、停顿模式）
 *
 * 注：这是一个简化实现，生产环境建议使用更专业的声纹识别模型（如speaker embedding models）
 */
@Singleton
class SimpleVoiceprintFeatureExtractor @Inject constructor() : VoiceprintFeatureExtractor {

    companion object {
        private const val FEATURE_DIM = 128  // 特征维度
        private const val FRAME_SIZE = 400   // 帧大小 (25ms @ 16kHz)
        private const val FRAME_SHIFT = 160  // 帧移 (10ms @ 16kHz)
        private const val NUM_MFCC = 13      // MFCC系数数量
    }

    override fun extractFeature(audioData: ByteArray, sampleRate: Int): FloatArray {
        try {
            // 1. PCM字节转换为short数组
            val samples = pcmBytesToShortArray(audioData)

            if (samples.isEmpty()) {
                return FloatArray(FEATURE_DIM) { 0f }
            }

            // 2. 提取多种特征
            val mfccFeatures = extractMFCC(samples, sampleRate)
            val pitchFeatures = extractPitchFeatures(samples, sampleRate)
            val energyFeatures = extractEnergyFeatures(samples)
            val spectralFeatures = extractSpectralFeatures(samples)

            // 3. 组合特征
            val combinedFeatures = mfccFeatures + pitchFeatures + energyFeatures + spectralFeatures

            // 4. 归一化到固定维度
            return normalizeToFixedDimension(combinedFeatures, FEATURE_DIM)

        } catch (e: Exception) {
            Timber.e(e, "[FeatureExtractor] 特征提取失败")
            return FloatArray(FEATURE_DIM) { 0f }
        }
    }

    /**
     * 提取MFCC特征（简化版本）
     */
    private fun extractMFCC(samples: ShortArray, sampleRate: Int): FloatArray {
        val mfccList = mutableListOf<Float>()

        // 分帧处理
        var i = 0
        while (i + FRAME_SIZE < samples.size) {
            val frame = samples.sliceArray(i until i + FRAME_SIZE)

            // 计算能量谱
            val spectrum = computeSpectrum(frame)

            // 梅尔滤波器组
            val melSpectrum = applyMelFilterbank(spectrum, sampleRate)

            // DCT变换得到MFCC
            val mfcc = dct(melSpectrum.map { ln(it + 1e-10f) }.toFloatArray())

            // 取前NUM_MFCC个系数
            mfccList.addAll(mfcc.take(NUM_MFCC))

            i += FRAME_SHIFT
        }

        // 计算MFCC的统计特征（均值和方差）
        return if (mfccList.isNotEmpty()) {
            val mean = mfccList.average().toFloat()
            val variance = mfccList.map { (it - mean).pow(2) }.average().toFloat()
            floatArrayOf(mean, variance)
        } else {
            floatArrayOf(0f, 0f)
        }
    }

    /**
     * 提取音高特征
     */
    private fun extractPitchFeatures(samples: ShortArray, sampleRate: Int): FloatArray {
        // 简化的自相关法估计基频
        val maxLag = sampleRate / 80  // 对应80Hz最低频率
        val minLag = sampleRate / 400  // 对应400Hz最高频率

        var bestLag = 0
        var maxCorrelation = 0.0

        for (lag in minLag..maxLag) {
            var correlation = 0.0
            var count = 0

            for (i in 0 until samples.size - lag) {
                correlation += samples[i] * samples[i + lag]
                count++
            }

            correlation /= count
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestLag = lag
            }
        }

        val fundamentalFreq = if (bestLag > 0) sampleRate.toFloat() / bestLag else 0f

        return floatArrayOf(
            fundamentalFreq,                    // 基频
            maxCorrelation.toFloat(),           // 周期性强度
            fundamentalFreq / 100f              // 归一化基频
        )
    }

    /**
     * 提取能量特征
     */
    private fun extractEnergyFeatures(samples: ShortArray): FloatArray {
        val energy = samples.map { it.toFloat().pow(2) }.average().toFloat()
        val logEnergy = ln(energy + 1e-10f)

        // 零交叉率（反映语音的清浊）
        var zeroCrossings = 0
        for (i in 0 until samples.size - 1) {
            if ((samples[i] >= 0 && samples[i + 1] < 0) || (samples[i] < 0 && samples[i + 1] >= 0)) {
                zeroCrossings++
            }
        }
        val zeroCrossingRate = zeroCrossings.toFloat() / samples.size

        return floatArrayOf(logEnergy, zeroCrossingRate)
    }

    /**
     * 提取频谱特征
     */
    private fun extractSpectralFeatures(samples: ShortArray): FloatArray {
        val spectrum = computeSpectrum(samples)

        // 频谱质心
        var weightedSum = 0.0
        var sum = 0.0
        spectrum.forEachIndexed { index, value ->
            weightedSum += index * value
            sum += value
        }
        val centroid = if (sum > 0) (weightedSum / sum).toFloat() else 0f

        // 频谱带宽
        val bandwidth = sqrt(
            spectrum.mapIndexed { index, value ->
                (index - centroid).pow(2) * value
            }.average()
        ).toFloat()

        return floatArrayOf(centroid / spectrum.size, bandwidth / spectrum.size)
    }

    /**
     * 计算频谱（简化FFT）
     */
    private fun computeSpectrum(samples: ShortArray): FloatArray {
        val n = samples.size
        val spectrum = FloatArray(n / 2)

        // 简化的功率谱估计（实际应使用FFT）
        for (k in spectrum.indices) {
            var real = 0.0
            var imag = 0.0

            for (i in samples.indices) {
                val angle = -2.0 * PI * k * i / n
                real += samples[i] * cos(angle)
                imag += samples[i] * sin(angle)
            }

            spectrum[k] = sqrt(real * real + imag * imag).toFloat()
        }

        return spectrum
    }

    /**
     * 梅尔滤波器组
     */
    private fun applyMelFilterbank(spectrum: FloatArray, sampleRate: Int): FloatArray {
        val numFilters = 26
        val melSpectrum = FloatArray(numFilters)

        val melMax = hzToMel(sampleRate / 2.0)
        val melMin = hzToMel(0.0)
        val melStep = (melMax - melMin) / (numFilters + 1)

        for (i in 0 until numFilters) {
            val melCenter = melMin + (i + 1) * melStep
            val fCenter = melToHz(melCenter).toInt()

            // 简化的三角滤波器
            val fLow = melToHz(melCenter - melStep).toInt()
            val fHigh = melToHz(melCenter + melStep).toInt()

            var filterSum = 0f
            for (f in fLow until fHigh.coerceAtMost(spectrum.size)) {
                if (f < fCenter) {
                    filterSum += spectrum[f] * (f - fLow).toFloat() / (fCenter - fLow)
                } else {
                    filterSum += spectrum[f] * (fHigh - f).toFloat() / (fHigh - fCenter)
                }
            }

            melSpectrum[i] = filterSum
        }

        return melSpectrum
    }

    /**
     * 离散余弦变换（DCT）
     */
    private fun dct(input: FloatArray): FloatArray {
        val n = input.size
        val output = FloatArray(n)

        for (k in output.indices) {
            var sum = 0.0
            for (i in input.indices) {
                sum += input[i] * cos(PI * k * (i + 0.5) / n)
            }
            output[k] = (sum * sqrt(2.0 / n)).toFloat()
        }

        return output
    }

    /**
     * Hz转Mel
     */
    private fun hzToMel(hz: Double): Double {
        return 2595.0 * log10(1.0 + hz / 700.0)
    }

    /**
     * Mel转Hz
     */
    private fun melToHz(mel: Double): Double {
        return 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
    }

    /**
     * PCM字节转short数组
     */
    private fun pcmBytesToShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt() shl 8
            shorts[i] = (high or low).toShort()
        }
        return shorts
    }

    /**
     * 归一化到固定维度
     */
    private fun normalizeToFixedDimension(features: FloatArray, targetDim: Int): FloatArray {
        if (features.size == targetDim) {
            return features
        }

        val normalized = FloatArray(targetDim)

        if (features.size > targetDim) {
            // 降采样
            for (i in 0 until targetDim) {
                val idx = (i * features.size.toFloat() / targetDim).toInt()
                normalized[i] = features[idx.coerceIn(0, features.size - 1)]
            }
        } else {
            // 补零或重复
            for (i in 0 until targetDim) {
                normalized[i] = if (i < features.size) features[i] else 0f
            }
        }

        // L2归一化
        val norm = sqrt(normalized.sumOf { it.toDouble() * it }.toFloat())
        if (norm > 0) {
            for (i in normalized.indices) {
                normalized[i] /= norm
            }
        }

        return normalized
    }
}
