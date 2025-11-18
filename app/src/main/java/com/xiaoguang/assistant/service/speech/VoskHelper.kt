package com.xiaoguang.assistant.service.speech

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vosk离线语音识别助手
 * 管理Vosk模型下载、加载和识别器创建
 */
@Singleton
class VoskHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VoskHelper"
        private const val MODEL_NAME = "vosk-model-small-cn-0.22"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        private const val SAMPLE_RATE = 16000f
    }

    private var model: Model? = null
    private val modelDir: File
        get() = File(context.filesDir, "vosk-models")

    /**
     * 检查模型是否已下载
     */
    fun isModelDownloaded(): Boolean {
        val modelPath = File(modelDir, MODEL_NAME)
        return modelPath.exists() && modelPath.isDirectory && modelPath.list()?.isNotEmpty() == true
    }

    /**
     * 获取模型路径
     */
    fun getModelPath(): File {
        return File(modelDir, MODEL_NAME)
    }

    /**
     * 下载Vosk模型
     * 注意：实际应用中建议使用更可靠的下载方案（如DownloadManager）
     */
    suspend fun downloadModel(
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始下载Vosk模型: $MODEL_URL")

            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            val zipFile = File(context.cacheDir, "$MODEL_NAME.zip")

            // 下载ZIP文件
            val connection = URL(MODEL_URL).openConnection()
            connection.connect()

            val fileLength = connection.contentLength
            connection.getInputStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var count: Int

                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)

                        // 报告进度
                        if (fileLength > 0) {
                            onProgress((total * 100 / fileLength).toInt())
                        }
                    }
                }
            }

            // 解压ZIP文件
            Log.d(TAG, "解压模型文件...")
            unzip(zipFile, modelDir)

            // 清理临时文件
            zipFile.delete()

            Log.d(TAG, "模型下载完成: ${getModelPath()}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "模型下载失败", e)
            Result.failure(e)
        }
    }

    /**
     * 加载Vosk模型
     */
    suspend fun loadModel(): Result<Model> = withContext(Dispatchers.IO) {
        try {
            if (model != null) {
                return@withContext Result.success(model!!)
            }

            if (!isModelDownloaded()) {
                return@withContext Result.failure(
                    IOException("模型未下载，请先下载模型")
                )
            }

            Log.d(TAG, "加载Vosk模型: ${getModelPath()}")
            model = Model(getModelPath().absolutePath)
            Result.success(model!!)
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            Result.failure(e)
        }
    }

    /**
     * 创建识别器
     */
    fun createRecognizer(): Recognizer? {
        return try {
            model?.let { Recognizer(it, SAMPLE_RATE) }
        } catch (e: Exception) {
            Log.e(TAG, "创建识别器失败", e)
            null
        }
    }

    /**
     * 释放资源
     */
    fun cleanup() {
        model?.close()
        model = null
    }

    /**
     * 解压ZIP文件
     */
    private fun unzip(zipFile: File, targetDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zip.copyTo(output)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
