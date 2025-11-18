package com.xiaoguang.assistant.service.wakeword

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 唤醒词服务
 * 持续监听"小光小光"唤醒词
 * 完全离线运行,不消耗API配额
 */
@Singleton
class WakeWordService @Inject constructor(
    private val wakeWordDetector: WakeWordDetector
) {
    companion object {
        private const val TAG = "WakeWordService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var onWakeWordDetected: (() -> Unit)? = null

    /**
     * 开始监听唤醒词
     * @param onDetected 检测到唤醒词时的回调
     */
    fun startListening(onDetected: () -> Unit) {
        if (_isListening.value) {
            Log.w(TAG, "已在监听唤醒词")
            return
        }

        this.onWakeWordDetected = onDetected

        scope.launch {
            // 初始化Porcupine
            val initResult = wakeWordDetector.initialize()
            if (initResult.isFailure) {
                Log.e(TAG, "Porcupine初始化失败: ${initResult.exceptionOrNull()?.message}")
                return@launch
            }

            // 启动音频录制
            startAudioRecording()
        }
    }

    /**
     * 启动音频录制
     */
    private fun startAudioRecording() {
        try {
            val sampleRate = wakeWordDetector.getSampleRate()
            val frameLength = wakeWordDetector.getFrameLength()
            val bufferSize = frameLength * 2 // PCM 16位

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 4
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败")
                return
            }

            audioRecord?.startRecording()
            _isListening.value = true

            Log.d(TAG, "开始监听唤醒词 (采样率: $sampleRate Hz, 帧长: $frameLength)")

            listeningJob = scope.launch {
                val buffer = ByteArray(bufferSize)

                while (isActive && _isListening.value) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                    if (readResult > 0) {
                        // 检测唤醒词
                        val detected = wakeWordDetector.processAudio(buffer)

                        if (detected) {
                            Log.d(TAG, "唤醒词已触发!")
                            withContext(Dispatchers.Main) {
                                onWakeWordDetected?.invoke()
                            }
                        }
                    } else if (readResult < 0) {
                        Log.e(TAG, "音频读取错误: $readResult")
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "录音权限被拒绝", e)
            _isListening.value = false
        } catch (e: Exception) {
            Log.e(TAG, "启动唤醒词监听失败", e)
            _isListening.value = false
        }
    }

    /**
     * 停止监听唤醒词
     */
    fun stopListening() {
        if (!_isListening.value) return

        Log.d(TAG, "停止监听唤醒词")

        listeningJob?.cancel()
        listeningJob = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        _isListening.value = false
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        stopListening()
        wakeWordDetector.cleanup()
        scope.cancel()
    }
}
