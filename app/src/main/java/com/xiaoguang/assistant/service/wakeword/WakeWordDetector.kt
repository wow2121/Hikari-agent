package com.xiaoguang.assistant.service.wakeword

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 唤醒词检测器
 * 使用Porcupine离线检测"小光小光"唤醒词
 * 不消耗在线API,完全本地运行
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private const val KEYWORD_FILE_NAME = "xiaoguang_android.ppn"
    }

    private var porcupine: Porcupine? = null
    private var isInitialized = false

    private val _wakeWordDetected = MutableSharedFlow<Unit>()
    val wakeWordDetected: SharedFlow<Unit> = _wakeWordDetected.asSharedFlow()

    /**
     * 初始化Porcupine
     * 需要在使用前调用
     */
    suspend fun initialize(): Result<Unit> {
        if (isInitialized) {
            return Result.success(Unit)
        }

        return try {
            // 从配置读取Access Key
            val accessKey = appPreferences.porcupineAccessKey.first()

            if (accessKey.isBlank()) {
                Log.w(TAG, "Porcupine Access Key未配置")
                return Result.failure(IllegalStateException("请先配置Picovoice Access Key"))
            }

            // 从assets复制关键词文件到内部存储
            val keywordPath = extractKeywordFile()

            // 获取灵敏度配置
            val sensitivity = appPreferences.wakeWordSensitivity.first()

            // 创建Porcupine实例
            porcupine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordPath)
                .setSensitivity(sensitivity)
                .build(context)

            isInitialized = true
            Log.d(TAG, "Porcupine初始化成功 (灵敏度: $sensitivity)")
            Result.success(Unit)
        } catch (e: PorcupineException) {
            Log.e(TAG, "Porcupine初始化失败", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "初始化异常", e)
            Result.failure(e)
        }
    }

    /**
     * 处理音频数据,检测唤醒词
     * @param audioData PCM 16位音频数据
     * @return 是否检测到唤醒词
     */
    suspend fun processAudio(audioData: ByteArray): Boolean {
        if (!isInitialized || porcupine == null) {
            Log.w(TAG, "Porcupine未初始化")
            return false
        }

        return try {
            // 将ByteArray转换为ShortArray
            val audioShortArray = ShortArray(audioData.size / 2)
            for (i in audioShortArray.indices) {
                audioShortArray[i] = ((audioData[i * 2 + 1].toInt() shl 8) or
                                      (audioData[i * 2].toInt() and 0xFF)).toShort()
            }

            // Porcupine需要固定大小的帧
            val frameLength = porcupine!!.frameLength
            if (audioShortArray.size < frameLength) {
                return false
            }

            // 处理音频帧
            val frame = audioShortArray.sliceArray(0 until frameLength)
            val keywordIndex = porcupine!!.process(frame)

            if (keywordIndex >= 0) {
                Log.d(TAG, "检测到唤醒词: 小光小光")
                _wakeWordDetected.emit(Unit)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理音频错误", e)
            false
        }
    }

    /**
     * 获取Porcupine需要的音频帧长度
     */
    fun getFrameLength(): Int {
        return porcupine?.frameLength ?: 512
    }

    /**
     * 获取Porcupine需要的采样率
     */
    fun getSampleRate(): Int {
        return porcupine?.sampleRate ?: 16000
    }

    /**
     * 从assets提取关键词文件
     */
    private fun extractKeywordFile(): String {
        val keywordFile = File(context.filesDir, KEYWORD_FILE_NAME)

        if (!keywordFile.exists()) {
            // 从assets复制文件
            try {
                context.assets.open("keywords/$KEYWORD_FILE_NAME").use { inputStream ->
                    BufferedInputStream(inputStream).use { bufferedInput ->
                        FileOutputStream(keywordFile).use { outputStream ->
                            BufferedOutputStream(outputStream).use { bufferedOutput ->
                                bufferedInput.copyTo(bufferedOutput)
                            }
                        }
                    }
                }
                Log.d(TAG, "关键词文件已提取: ${keywordFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "提取关键词文件失败", e)
                throw e
            }
        }

        return keywordFile.absolutePath
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            porcupine?.delete()
            porcupine = null
            isInitialized = false
            Log.d(TAG, "Porcupine资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理资源失败", e)
        }
    }
}
