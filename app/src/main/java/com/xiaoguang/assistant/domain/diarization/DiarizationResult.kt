package com.xiaoguang.assistant.domain.diarization

/**
 * 说话人分离结果
 * Phase 2: Speaker Diarization 数据模型
 */
data class DiarizationResult(
    /**
     * 说话人片段列表（按时间顺序）
     */
    val segments: List<SpeakerSegment>,

    /**
     * 处理耗时（毫秒）
     */
    val processingTimeMs: Long,

    /**
     * 检测到的唯一说话人数量
     */
    val uniqueSpeakerCount: Int = segments.map { it.label }.distinct().size,

    /**
     * 音频总时长（毫秒）
     */
    val totalDurationMs: Long = segments.maxOfOrNull { it.endMs } ?: 0L
) {
    /**
     * 是否有多个说话人
     */
    fun hasMultipleSpeakers(): Boolean = uniqueSpeakerCount > 1

    /**
     * 是否有重叠（同一时间段多个说话人）
     */
    fun hasOverlap(): Boolean {
        for (i in 0 until segments.size - 1) {
            if (segments[i].endMs > segments[i + 1].startMs) {
                return true
            }
        }
        return false
    }

    /**
     * 获取主导说话人（说话时长最长的）
     */
    fun getDominantSpeaker(): SpeakerSegment? {
        return segments.groupBy { it.label }
            .mapValues { entry -> entry.value.sumOf { it.endMs - it.startMs } }
            .maxByOrNull { it.value }
            ?.let { (label, _) ->
                segments.first { it.label == label }
            }
    }
}

/**
 * 说话人片段
 */
data class SpeakerSegment(
    /**
     * 说话人标签（来自 diarization 模型的临时 ID）
     * 例如："speaker_0", "speaker_1", "speaker_2"
     */
    val label: String,

    /**
     * 开始时间（毫秒）
     */
    val startMs: Long,

    /**
     * 结束时间（毫秒）
     */
    val endMs: Long,

    /**
     * 置信度（0.0-1.0）
     */
    val confidence: Float = 1.0f,

    /**
     * 映射后的本地说话人 ID（通过声纹识别得到）
     * 初始为 null，需要后续通过 VoiceprintRecognitionUseCase 填充
     */
    var localSpeakerId: String? = null,

    /**
     * 映射后的本地说话人名称
     */
    var localSpeakerName: String? = null,

    /**
     * 是否是主人
     * 通过声纹识别得到，初始为 false
     */
    var isMaster: Boolean = false
) {
    /**
     * 片段时长（毫秒）
     */
    fun durationMs(): Long = endMs - startMs

    /**
     * 片段时长（秒）
     */
    fun durationSeconds(): Float = durationMs() / 1000f

    /**
     * 是否已映射到本地说话人
     */
    fun isMapped(): Boolean = localSpeakerId != null
}
