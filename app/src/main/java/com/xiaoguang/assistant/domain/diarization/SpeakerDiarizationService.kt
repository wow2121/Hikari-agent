package com.xiaoguang.assistant.domain.diarization

/**
 * 说话人分离服务接口
 * Phase 2: Speaker Diarization
 *
 * 负责将音频分离成不同说话人的片段
 */
interface SpeakerDiarizationService {

    /**
     * 初始化模型
     * 应在应用启动时或首次使用前调用
     */
    suspend fun initialize()

    /**
     * 处理音频片段，返回说话人分离结果
     *
     * @param audioData PCM 16-bit 单声道音频数据
     * @param sampleRate 采样率（Hz），默认 16000
     * @return 说话人分离结果，包含各说话人的时间片段
     */
    suspend fun process(
        audioData: ByteArray,
        sampleRate: Int = 16000
    ): DiarizationResult

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean

    /**
     * 释放资源
     */
    fun release()
}
