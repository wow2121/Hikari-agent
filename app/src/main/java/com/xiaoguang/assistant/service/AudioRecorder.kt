package com.xiaoguang.assistant.service

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 音频录制器
 * 用于声纹注册的实时音频录制
 *
 * 录制格式：PCM 16-bit, 16kHz, 单声道
 */
class AudioRecorder {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2

        private const val MIN_RECORDING_DURATION_MS = 3000L // 最少录制3秒
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioData = ByteArrayOutputStream()
    private var recordingStartTime = 0L

    // 音量级别（0.0-1.0）
    private val _volumeLevel = MutableStateFlow(0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    // 录制时长（毫秒）
    private val _recordingDuration = MutableStateFlow(0L)
    val recordingDuration: StateFlow<Long> = _recordingDuration.asStateFlow()

    // 录制状态
    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    /**
     * 开始录制
     * @return 是否成功启动
     */
    suspend fun startRecording(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isRecording) {
                Timber.w("[AudioRecorder] 已经在录制中")
                return@withContext false
            }

            // 计算缓冲区大小
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Timber.e("[AudioRecorder] 无法获取最小缓冲区大小")
                _recordingState.value = RecordingState.ERROR("设备不支持音频录制")
                return@withContext false
            }

            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER

            // 创建 AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("[AudioRecorder] AudioRecord 初始化失败")
                _recordingState.value = RecordingState.ERROR("音频录制器初始化失败")
                audioRecord?.release()
                audioRecord = null
                return@withContext false
            }

            // 清空数据
            audioData.reset()
            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            _recordingState.value = RecordingState.RECORDING

            // 开始录制
            audioRecord?.startRecording()

            // 在后台线程持续读取数据
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (readSize > 0) {
                    // 保存音频数据
                    audioData.write(buffer, 0, readSize)

                    // 计算音量级别
                    val volume = calculateVolume(buffer, readSize)
                    _volumeLevel.value = volume

                    // 更新录制时长
                    _recordingDuration.value = System.currentTimeMillis() - recordingStartTime
                }
            }

            Timber.d("[AudioRecorder] 录制完成，数据大小: ${audioData.size()} bytes, 时长: ${_recordingDuration.value}ms")
            return@withContext true

        } catch (e: Exception) {
            Timber.e(e, "[AudioRecorder] 录制失败")
            _recordingState.value = RecordingState.ERROR("录制失败: ${e.message}")
            stopRecording()
            return@withContext false
        }
    }

    /**
     * 停止录制
     * @return 录制的音频数据，如果录制时长不足返回 null
     */
    fun stopRecording(): ByteArray? {
        if (!isRecording) {
            Timber.w("[AudioRecorder] 未在录制中")
            return null
        }

        try {
            isRecording = false

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            val duration = System.currentTimeMillis() - recordingStartTime

            // 检查录制时长
            if (duration < MIN_RECORDING_DURATION_MS) {
                Timber.w("[AudioRecorder] 录制时长不足: ${duration}ms < ${MIN_RECORDING_DURATION_MS}ms")
                _recordingState.value = RecordingState.ERROR("录制时长不足，请至少录制3秒")
                return null
            }

            val data = audioData.toByteArray()
            audioData.reset()

            _recordingState.value = RecordingState.COMPLETED
            _volumeLevel.value = 0f
            _recordingDuration.value = 0L

            Timber.i("[AudioRecorder] 录制成功，时长: ${duration}ms, 大小: ${data.size} bytes")
            return data

        } catch (e: Exception) {
            Timber.e(e, "[AudioRecorder] 停止录制失败")
            _recordingState.value = RecordingState.ERROR("停止失败: ${e.message}")
            return null
        }
    }

    /**
     * 取消录制
     */
    fun cancelRecording() {
        if (!isRecording) return

        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioData.reset()

        _recordingState.value = RecordingState.IDLE
        _volumeLevel.value = 0f
        _recordingDuration.value = 0L

        Timber.d("[AudioRecorder] 录制已取消")
    }

    /**
     * 重置状态
     */
    fun reset() {
        _recordingState.value = RecordingState.IDLE
        _volumeLevel.value = 0f
        _recordingDuration.value = 0L
    }

    /**
     * 计算音量级别（RMS）
     */
    private fun calculateVolume(buffer: ByteArray, size: Int): Float {
        var sum = 0.0
        var count = 0

        for (i in 0 until size step 2) {
            if (i + 1 < size) {
                // 将两个字节转换为 16-bit 样本
                val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                sum += sample * sample
                count++
            }
        }

        if (count == 0) return 0f

        // 计算 RMS（均方根）
        val rms = sqrt(sum / count)

        // 归一化到 0.0-1.0
        // 16-bit 最大值是 32768，实际音量通常在 0-10000 范围
        val normalized = (rms / 10000.0).coerceIn(0.0, 1.0)

        return normalized.toFloat()
    }

    /**
     * 检查是否正在录制
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 获取采样率
     */
    fun getSampleRate(): Int = SAMPLE_RATE
}

/**
 * 录制状态
 */
sealed class RecordingState {
    object IDLE : RecordingState()
    object RECORDING : RecordingState()
    object COMPLETED : RecordingState()
    data class ERROR(val message: String) : RecordingState()
}
