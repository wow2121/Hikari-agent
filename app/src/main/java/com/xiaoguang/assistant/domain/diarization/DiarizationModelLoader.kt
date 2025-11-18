package com.xiaoguang.assistant.domain.diarization

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 说话人分离模型加载器
 * Phase 2: 负责从 assets 加载 ONNX 模型到内部存储
 */
@Singleton
class DiarizationModelLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var ortEnvironment: OrtEnvironment? = null
    private var segmentationSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null

    private val segmentationModelName = "segmentation.onnx"
    private val embeddingModelName = "speaker_embedding.onnx"

    /**
     * 加载模型
     */
    suspend fun loadModels(): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.i("[DiarizationModelLoader] 开始加载说话人分离模型...")

            // 初始化 ONNX Runtime 环境
            if (ortEnvironment == null) {
                ortEnvironment = OrtEnvironment.getEnvironment()
            }

            // 复制模型文件到缓存目录
            val segmentationFile = copyAssetToCache(
                assetPath = "models/diarization/$segmentationModelName",
                cacheFileName = segmentationModelName
            )

            val embeddingFile = copyAssetToCache(
                assetPath = "models/diarization/$embeddingModelName",
                cacheFileName = embeddingModelName
            )

            // 加载模型
            segmentationSession = ortEnvironment?.createSession(
                segmentationFile.absolutePath,
                OrtSession.SessionOptions()
            )

            embeddingSession = ortEnvironment?.createSession(
                embeddingFile.absolutePath,
                OrtSession.SessionOptions()
            )

            Timber.i("[DiarizationModelLoader] 模型加载成功")
            true

        } catch (e: Exception) {
            Timber.e(e, "[DiarizationModelLoader] 模型加载失败")
            false
        }
    }

    /**
     * 从 assets 复制文件到缓存目录
     */
    private fun copyAssetToCache(assetPath: String, cacheFileName: String): File {
        val cacheFile = File(context.cacheDir, cacheFileName)

        // 如果已存在且大小正确，直接返回
        if (cacheFile.exists()) {
            Timber.d("[DiarizationModelLoader] 模型文件已存在: ${cacheFile.absolutePath}")
            return cacheFile
        }

        // 复制文件
        context.assets.open(assetPath).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }

        Timber.d("[DiarizationModelLoader] 模型文件已复制: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
        return cacheFile
    }

    /**
     * 获取分割模型 session
     */
    fun getSegmentationSession(): OrtSession? = segmentationSession

    /**
     * 获取嵌入模型 session
     */
    fun getEmbeddingSession(): OrtSession? = embeddingSession

    /**
     * 释放资源
     */
    fun release() {
        try {
            segmentationSession?.close()
            embeddingSession?.close()
            // 注意：OrtEnvironment 是单例，不应该关闭
            segmentationSession = null
            embeddingSession = null
            Timber.i("[DiarizationModelLoader] 模型资源已释放")
        } catch (e: Exception) {
            Timber.e(e, "[DiarizationModelLoader] 释放资源失败")
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isLoaded(): Boolean {
        return segmentationSession != null && embeddingSession != null
    }
}
