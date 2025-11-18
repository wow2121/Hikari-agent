package com.xiaoguang.assistant.domain.flow.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时环境状态
 *
 * ⚠️ 设计考虑：
 * 1. 数据新鲜度：每个字段都带时间戳，心流可以判断数据是否过期
 * 2. 线程安全：使用 StateFlow 保证并发安全
 * 3. 时间尺度问题：
 *    - VAD状态：实时（几百毫秒更新）
 *    - 识别文字：分段（3秒静音或5分钟）
 *    - 声纹识别：片段（需要完整语句）
 *
 * ⚠️ 已知问题：
 * - 环境监听和心流的时间尺度不匹配
 * - 声纹识别的时机不明确
 * - 可能需要事件流而不是状态
 */
@Singleton
class EnvironmentState @Inject constructor() {

    // === 语音活动检测（VAD）===
    private val _isVoiceActive = MutableStateFlow(VoiceActivityData())
    val isVoiceActive: StateFlow<VoiceActivityData> = _isVoiceActive.asStateFlow()

    // === 环境音量 ===
    private val _audioLevel = MutableStateFlow(AudioLevelData())
    val audioLevel: StateFlow<AudioLevelData> = _audioLevel.asStateFlow()

    // === 最近识别的文字（实时，包括部分结果）===
    private val _recentTranscription = MutableStateFlow(TranscriptionData())
    val recentTranscription: StateFlow<TranscriptionData> = _recentTranscription.asStateFlow()

    // === 最近的完整语句列表（最近30秒）===
    private val _recentUtterances = MutableStateFlow<List<Utterance>>(emptyList())
    val recentUtterances: StateFlow<List<Utterance>> = _recentUtterances.asStateFlow()

    // === 当前说话人 ===
    private val _currentSpeaker = MutableStateFlow(SpeakerData())
    val currentSpeaker: StateFlow<SpeakerData> = _currentSpeaker.asStateFlow()

    // === 在场人员列表 ===
    private val _presentPeople = MutableStateFlow<List<SpeakerData>>(emptyList())
    val presentPeople: StateFlow<List<SpeakerData>> = _presentPeople.asStateFlow()

    /**
     * 更新语音活动状态
     */
    fun updateVoiceActivity(isActive: Boolean, energy: Float = 0f) {
        val data = VoiceActivityData(
            isActive = isActive,
            energy = energy,
            timestamp = System.currentTimeMillis()
        )
        _isVoiceActive.value = data

        if (isActive) {
            Timber.v("[EnvironmentState] 检测到语音活动: energy=$energy")
        }
    }

    /**
     * 更新音量级别
     */
    fun updateAudioLevel(level: Float) {
        val data = AudioLevelData(
            level = level,
            timestamp = System.currentTimeMillis()
        )
        _audioLevel.value = data
    }

    /**
     * 更新识别文字（实时，包括部分结果）
     * @param isPartial 是否是部分结果（实时识别中间状态）
     */
    fun updateTranscription(text: String, isPartial: Boolean = false) {
        if (text.isBlank()) return

        val data = TranscriptionData(
            text = text,
            isPartial = isPartial,
            timestamp = System.currentTimeMillis()
        )
        _recentTranscription.value = data

        Timber.d("[EnvironmentState] 识别文字: ${text.take(50)}... (partial=$isPartial)")
    }

    /**
     * 添加完整语句（识别出一句完整的话）
     */
    fun addUtterance(
        text: String,
        speakerId: String? = null,
        speakerName: String? = null,
        confidence: Float = 1.0f,
        speakerCount: Int = 1,           // ✅ Phase 1: 说话人数量
        isOverlapping: Boolean = false    // ✅ Phase 1: 是否重叠
    ) {
        if (text.isBlank()) return

        val utterance = Utterance(
            text = text,
            speakerId = speakerId,
            speakerName = speakerName,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            speakerCount = speakerCount,
            isOverlapping = isOverlapping
        )

        // 添加到列表
        val currentList = _recentUtterances.value.toMutableList()
        currentList.add(utterance)

        // 移除过期的语句（超过30秒）
        val currentTime = System.currentTimeMillis()
        currentList.removeAll { currentTime - it.timestamp > 30 * 1000 }

        _recentUtterances.value = currentList

        val speakerInfo = when {
            speakerCount > 1 -> "${speakerName ?: speakerId ?: "未知"} (+${speakerCount-1}人)"
            else -> speakerName ?: speakerId ?: "未知"
        }
        Timber.d("[EnvironmentState] 新增语句: ${text.take(50)}... (说话人: $speakerInfo)")
    }

    /**
     * 更新最近一条语句的说话人信息
     */
    fun updateLastUtterance(
        speakerId: String? = null,
        speakerName: String? = null,
        confidence: Float? = null,
        speakerCount: Int? = null,
        isOverlapping: Boolean? = null
    ) {
        val currentList = _recentUtterances.value.toMutableList()
        if (currentList.isEmpty()) return

        // 更新最后一条
        val last = currentList.last()
        val updated = last.copy(
            speakerId = speakerId ?: last.speakerId,
            speakerName = speakerName ?: last.speakerName,
            confidence = confidence ?: last.confidence,
            speakerCount = speakerCount ?: last.speakerCount,
            isOverlapping = isOverlapping ?: last.isOverlapping
        )

        currentList[currentList.size - 1] = updated
        _recentUtterances.value = currentList

        Timber.d("[EnvironmentState] 更新语句信息: 说话人=${speakerName ?: speakerId}, 人数=$speakerCount")
    }

    /**
     * 更新当前说话人
     */
    fun updateCurrentSpeaker(
        speakerId: String?,
        speakerName: String?,
        confidence: Float,
        isMaster: Boolean
    ) {
        val data = SpeakerData(
            speakerId = speakerId,
            speakerName = speakerName,
            confidence = confidence,
            isMaster = isMaster,
            timestamp = System.currentTimeMillis()
        )
        _currentSpeaker.value = data

        if (speakerId != null) {
            Timber.d("[EnvironmentState] 识别说话人: ${speakerName ?: speakerId} (置信度: $confidence)")

            // 更新在场人员列表
            addPresentPerson(data)
        }
    }

    /**
     * 添加在场人员
     */
    private fun addPresentPerson(speaker: SpeakerData) {
        if (speaker.speakerId == null) return

        val currentList = _presentPeople.value.toMutableList()

        // 查找是否已存在
        val existingIndex = currentList.indexOfFirst { it.speakerId == speaker.speakerId }

        if (existingIndex >= 0) {
            // 更新时间戳
            currentList[existingIndex] = speaker
        } else {
            // 添加新人
            currentList.add(speaker)
        }

        // 移除过期的人员（超过5分钟未检测到）
        val currentTime = System.currentTimeMillis()
        currentList.removeAll { currentTime - it.timestamp > 5 * 60 * 1000 }

        _presentPeople.value = currentList
    }

    /**
     * 清空识别文字（用于分段后重置）
     */
    fun clearTranscription() {
        _recentTranscription.value = TranscriptionData()
    }

    /**
     * 重置所有状态
     */
    fun reset() {
        _isVoiceActive.value = VoiceActivityData()
        _audioLevel.value = AudioLevelData()
        _recentTranscription.value = TranscriptionData()
        _currentSpeaker.value = SpeakerData()
        _presentPeople.value = emptyList()

        Timber.d("[EnvironmentState] 状态已重置")
    }
}

/**
 * 语音活动数据
 */
data class VoiceActivityData(
    val isActive: Boolean = false,
    val energy: Float = 0f,
    val timestamp: Long = 0L
) {
    fun isExpired(maxAgeMs: Long = 1000): Boolean {
        return System.currentTimeMillis() - timestamp > maxAgeMs
    }
}

/**
 * 音量数据
 */
data class AudioLevelData(
    val level: Float = 0f,  // 0.0-1.0
    val timestamp: Long = 0L
) {
    fun isExpired(maxAgeMs: Long = 1000): Boolean {
        return System.currentTimeMillis() - timestamp > maxAgeMs
    }
}

/**
 * 识别文字数据
 */
data class TranscriptionData(
    val text: String = "",
    val isPartial: Boolean = false,  // 是否是部分结果
    val timestamp: Long = 0L
) {
    fun isExpired(maxAgeMs: Long = 30000): Boolean {  // 30秒过期
        return System.currentTimeMillis() - timestamp > maxAgeMs
    }
}

/**
 * 说话人数据
 */
data class SpeakerData(
    val speakerId: String? = null,
    val speakerName: String? = null,
    val confidence: Float = 0f,
    val isMaster: Boolean = false,
    val timestamp: Long = 0L
) {
    fun isExpired(maxAgeMs: Long = 60000): Boolean {  // 1分钟过期
        return System.currentTimeMillis() - timestamp > maxAgeMs
    }
}

/**
 * 语句（完整识别出的一句话）
 */
data class Utterance(
    val text: String,
    val speakerId: String? = null,
    val speakerName: String? = null,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis(),

    // ✅ Phase 1: 多说话人检测字段
    val speakerCount: Int = 1,           // 估计的说话人数量（1, 2, 3+）
    val isOverlapping: Boolean = false    // 是否有多人同时说话
) {
    fun getAge(): Long {
        return System.currentTimeMillis() - timestamp
    }

    fun getAgeSeconds(): Long {
        return getAge() / 1000
    }

    fun isExpired(maxAgeMs: Long = 30000): Boolean {  // 30秒过期
        return getAge() > maxAgeMs
    }

    /**
     * 是否是多人场景
     */
    fun isMultiSpeaker(): Boolean = speakerCount > 1 || isOverlapping
}
