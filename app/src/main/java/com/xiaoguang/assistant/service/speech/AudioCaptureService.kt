package com.xiaoguang.assistant.service.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频捕获服务
 * 负责录制音频并提供PCM数据流
 */
@Singleton
class AudioCaptureService @Inject constructor() {
    companion object {
        private const val TAG = "AudioCapture"
        private const val SAMPLE_RATE = 16000 // 16kHz，Vosk要求
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_MULTIPLIER

    /**
     * 开始录音
     * @param onAudioData 音频数据回调，用于实时识别
     */
    fun startRecording(onAudioData: (ByteArray) -> Unit) {
        if (_isRecording.value) {
            Log.w(TAG, "已在录音中")
            return
        }

        try {
            Log.d(TAG, "[AudioRecord] ========== 初始化音频录制 ==========")
            Log.d(TAG, "[AudioRecord] 采样率: $SAMPLE_RATE Hz")
            Log.d(TAG, "[AudioRecord] 声道配置: MONO")
            Log.d(TAG, "[AudioRecord] 音频格式: PCM_16BIT")
            Log.d(TAG, "[AudioRecord] 最小缓冲大小: ${bufferSize / BUFFER_SIZE_MULTIPLIER} bytes")
            Log.d(TAG, "[AudioRecord] 实际缓冲大小: $bufferSize bytes (${BUFFER_SIZE_MULTIPLIER}x)")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            val state = audioRecord?.state
            Log.d(TAG, "[AudioRecord] AudioRecord State: $state (${getStateDescription(state)})")

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "[AudioRecord] ❌ AudioRecord 初始化失败！")
                Log.e(TAG, "[AudioRecord] State: $state (期望: ${AudioRecord.STATE_INITIALIZED})")
                Log.e(TAG, "[AudioRecord] 可能原因:")
                Log.e(TAG, "[AudioRecord]   1. 设备不支持指定的音频配置")
                Log.e(TAG, "[AudioRecord]   2. 麦克风被其他应用占用")
                Log.e(TAG, "[AudioRecord]   3. 系统资源不足")
                Log.e(TAG, "[AudioRecord]   4. 权限问题（虽然已通过，但可能被系统拒绝）")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            val recordingState = audioRecord?.recordingState
            Log.d(TAG, "[AudioRecord] Recording State: $recordingState (${getRecordingStateDescription(recordingState)})")

            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "[AudioRecord] ❌ 录音启动失败！Recording State: $recordingState")
                audioRecord?.release()
                audioRecord = null
                return
            }

            _isRecording.value = true
            Log.i(TAG, "[AudioRecord] ✅ 开始录音成功，采样率: $SAMPLE_RATE Hz, 缓冲: $bufferSize bytes")
            Log.d(TAG, "[AudioRecord] =========================================")

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                var readCount = 0

                while (isActive && _isRecording.value) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (readResult > 0) {
                        readCount++
                        if (readCount == 1) {
                            Log.d(TAG, "[AudioRecord] ✅ 首次成功读取音频数据: $readResult bytes")
                        }

                        // 计算音频电平（用于VAD）
                        val level = calculateAudioLevel(buffer, readResult)
                        _audioLevel.value = level

                        // 调试：定期输出音频级别
                        if (readCount % 50 == 0) {
                            Log.d(TAG, "[AudioLevel] 读取次数: $readCount, 音频级别: ${String.format("%.4f", level)} (${String.format("%.1f", level * 100)}%)")
                        }

                        // 回调音频数据
                        onAudioData(buffer.copyOf(readResult))
                    } else if (readResult < 0) {
                        val errorDesc = when (readResult) {
                            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                            AudioRecord.ERROR -> "ERROR"
                            else -> "Unknown($readResult)"
                        }
                        Log.e(TAG, "[AudioRecord] ❌ 录音读取错误: $errorDesc")
                        break
                    }
                }

                Log.d(TAG, "[AudioRecord] 录音循环结束，共读取 $readCount 次")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[AudioRecord] ❌ 录音权限被拒绝（SecurityException）", e)
            Log.e(TAG, "[AudioRecord] 详细信息: ${e.message}")
            Log.e(TAG, "[AudioRecord] 请检查:")
            Log.e(TAG, "[AudioRecord]   1. AndroidManifest.xml 中是否声明了 RECORD_AUDIO 权限")
            Log.e(TAG, "[AudioRecord]   2. 运行时权限是否已授予")
            Log.e(TAG, "[AudioRecord]   3. 设置 → 应用 → 权限中麦克风权限状态")
            _isRecording.value = false
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "[AudioRecord] ❌ 参数错误（IllegalArgumentException）", e)
            Log.e(TAG, "[AudioRecord] 详细信息: ${e.message}")
            Log.e(TAG, "[AudioRecord] 音频配置可能不被设备支持")
            _isRecording.value = false
        } catch (e: Exception) {
            Log.e(TAG, "[AudioRecord] ❌ 录音启动失败（${e.javaClass.simpleName}）", e)
            Log.e(TAG, "[AudioRecord] 详细信息: ${e.message}")
            _isRecording.value = false
        }
    }

    private fun getStateDescription(state: Int?): String {
        return when (state) {
            AudioRecord.STATE_INITIALIZED -> "STATE_INITIALIZED (已初始化)"
            AudioRecord.STATE_UNINITIALIZED -> "STATE_UNINITIALIZED (未初始化)"
            else -> "UNKNOWN($state)"
        }
    }

    private fun getRecordingStateDescription(state: Int?): String {
        return when (state) {
            AudioRecord.RECORDSTATE_RECORDING -> "RECORDSTATE_RECORDING (正在录音)"
            AudioRecord.RECORDSTATE_STOPPED -> "RECORDSTATE_STOPPED (已停止)"
            else -> "UNKNOWN($state)"
        }
    }

    /**
     * 停止录音
     */
    fun stopRecording() {
        if (!_isRecording.value) return

        Log.d(TAG, "停止录音")

        recordingJob?.cancel()
        recordingJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        _isRecording.value = false
        _audioLevel.value = 0f
    }

    /**
     * 录制音频到文件（用于SiliconFlow API）
     * @param outputFile 输出文件路径
     * @param durationMs 录制时长（毫秒）
     */
    suspend fun recordToFile(
        outputFile: File,
        durationMs: Long = 5000
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return@withContext Result.failure(Exception("AudioRecord初始化失败"))
            }

            audioRecord.startRecording()

            FileOutputStream(outputFile).use { fos ->
                val buffer = ByteArray(bufferSize)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < durationMs) {
                    val readResult = audioRecord.read(buffer, 0, buffer.size)
                    if (readResult > 0) {
                        fos.write(buffer, 0, readResult)
                    }
                }
            }

            audioRecord.stop()
            audioRecord.release()

            // 添加WAV头
            val wavFile = File(outputFile.parent, "${outputFile.nameWithoutExtension}.wav")
            addWavHeader(outputFile, wavFile)

            outputFile.delete()

            Result.success(wavFile)
        } catch (e: Exception) {
            Log.e(TAG, "录制到文件失败", e)
            Result.failure(e)
        }
    }

    /**
     * 计算音频电平（用于语音活动检测）
     */
    private fun calculateAudioLevel(buffer: ByteArray, size: Int): Float {
        var sum = 0L
        for (i in 0 until size step 2) {
            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
            sum += (sample * sample).toLong()
        }

        val rms = kotlin.math.sqrt(sum.toDouble() / (size / 2))
        // 归一化到0-1范围
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 为PCM文件添加WAV头
     */
    private fun addWavHeader(pcmFile: File, wavFile: File) {
        val pcmData = pcmFile.readBytes()
        val wavHeader = createWavHeader(pcmData.size, SAMPLE_RATE, 1, 16)

        FileOutputStream(wavFile).use { fos ->
            fos.write(wavHeader)
            fos.write(pcmData)
        }
    }

    /**
     * 创建WAV文件头
     */
    private fun createWavHeader(
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmDataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt subchunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat (1 for PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // BlockAlign
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // data subchunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmDataSize and 0xff).toByte()
        header[41] = ((pcmDataSize shr 8) and 0xff).toByte()
        header[42] = ((pcmDataSize shr 16) and 0xff).toByte()
        header[43] = ((pcmDataSize shr 24) and 0xff).toByte()

        return header
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        stopRecording()
        scope.cancel()
    }
}
