package com.xiaoguang.assistant.service

import android.util.Log
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.domain.model.MonitoringMode
import com.xiaoguang.assistant.service.speech.*
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 环境监听协调器
 * 协调语音捕获、VAD、语音识别和对话缓冲
 */
@Singleton
class EnvironmentMonitor @Inject constructor(
    private val appPreferences: AppPreferences,
    private val audioCaptureService: AudioCaptureService,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val voiceActivityDetector: VoiceActivityDetector,
    private val voiceprintManager: VoiceprintManager,  // ✅ 新的声纹管理器
    private val environmentState: com.xiaoguang.assistant.domain.flow.model.EnvironmentState,
    // ✅ Phase 2: 说话人分离服务
    private val speakerDiarizationService: com.xiaoguang.assistant.domain.diarization.SpeakerDiarizationService
) {

    // 额外的音频处理回调（用于唤醒词检测等）
    private var audioProcessorCallback: (suspend (ByteArray) -> Unit)? = null
    companion object {
        private const val TAG = "EnvironmentMonitor"

        // 缓冲设置
        private const val SEGMENT_DURATION_MS = 5 * 60 * 1000L // 5分钟
        private const val SILENCE_PAUSE_MS = 3000L // 3秒静音视为分段

        // 音频缓冲设置（用于声纹识别）
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val AUDIO_BUFFER_DURATION_MS = 5000L // 保留最近5秒音频
        private const val AUDIO_BUFFER_SIZE = (SAMPLE_RATE * 2 * AUDIO_BUFFER_DURATION_MS / 1000).toInt() // 160,000 bytes
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    // 对话缓冲区
    private val conversationBuffer = StringBuilder()
    private var lastSpeechTime = 0L
    private var segmentStartTime = 0L

    // 识别结果回调
    private var onSegmentComplete: ((String) -> Unit)? = null

    // ✅ 音频缓冲区（用于声纹识别）
    private val audioBuffer = AudioRingBuffer(AUDIO_BUFFER_SIZE)
    private var lastUtteranceStartTime = 0L  // 最后一句话开始的时间

    /**
     * 设置额外的音频处理器（例如用于唤醒词检测）
     */
    fun setAudioProcessor(processor: suspend (ByteArray) -> Unit) {
        this.audioProcessorCallback = processor
    }

    /**
     * 开始环境监听
     */
    suspend fun startMonitoring(
        onSegmentComplete: (String) -> Unit
    ) {
        if (_isMonitoring.value) {
            Log.w(TAG, "监听已在进行中")
            return
        }

        this.onSegmentComplete = onSegmentComplete

        val monitoringMode = appPreferences.monitoringMode.first()
        if (monitoringMode == MonitoringMode.DISABLED) {
            Log.w(TAG, "监听模式已禁用")
            return
        }

        _isMonitoring.value = true
        segmentStartTime = System.currentTimeMillis()

        Log.d(TAG, "开始环境监听，模式: $monitoringMode")

        // 启动音频捕获
        Log.d(TAG, "[DEBUG] 即将调用 startAudioCapture()...")
        try {
            startAudioCapture()
            Log.d(TAG, "[DEBUG] startAudioCapture() 调用完成")
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] startAudioCapture() 抛出异常!", e)
        }

        // 监听识别结果
        Log.d(TAG, "[DEBUG] 开始监听识别结果...")
        observeRecognitionResults()

        // 启动定时检查（每5分钟或长时间静音时处理缓冲）
        Log.d(TAG, "[DEBUG] 启动分段定时器...")
        startSegmentTimer()
    }

    /**
     * 停止环境监听
     */
    suspend fun stopMonitoring() {
        if (!_isMonitoring.value) return

        Log.d(TAG, "停止环境监听")

        // 处理剩余缓冲
        if (conversationBuffer.isNotEmpty()) {
            processConversationSegment()
        }

        audioCaptureService.stopRecording()
        speechRecognitionManager.stopRecognition()
        voiceActivityDetector.reset()

        _isMonitoring.value = false
        _currentTranscript.value = ""
    }

    /**
     * 暂停监听（例如前台模式下应用进入后台）
     */
    suspend fun pauseMonitoring() {
        if (!_isMonitoring.value) return

        Log.d(TAG, "暂停监听")
        audioCaptureService.stopRecording()
        speechRecognitionManager.stopRecognition()
    }

    /**
     * 恢复监听
     */
    suspend fun resumeMonitoring() {
        if (!_isMonitoring.value) return

        Log.d(TAG, "恢复监听")
        startAudioCapture()
    }

    /**
     * 启动音频捕获
     */
    private suspend fun startAudioCapture() {
        Log.i(TAG, "[AudioCapture] ========== 启动音频捕获 ==========")

        // 先启动语音识别器
        Log.d(TAG, "[AudioCapture] 1/2 启动语音识别管理器...")
        try {
            speechRecognitionManager.startRecognition()
            Log.i(TAG, "[AudioCapture] ✅ 语音识别管理器已启动")
        } catch (e: Exception) {
            Log.e(TAG, "[AudioCapture] ❌ 启动语音识别管理器失败", e)
            throw e
        }

        Log.d(TAG, "[AudioCapture] 2/2 启动音频录制服务...")
        try {
            audioCaptureService.startRecording { audioData ->
                // ✅ 将音频写入缓冲区（用于声纹识别）
                audioBuffer.write(audioData)

                // ✅ 更新实时音频级别
                val audioLevel = calculateAudioLevel(audioData)
                environmentState.updateAudioLevel(audioLevel)

                // 额外的音频处理（例如唤醒词检测）
                audioProcessorCallback?.let { processor ->
                    scope.launch {
                        try {
                            processor(audioData)
                        } catch (e: Exception) {
                            Log.e(TAG, "音频处理器异常", e)
                        }
                    }
                }

                // 语音活动检测
                val vadResult = voiceActivityDetector.detectVoiceActivity(audioData)

                // ✅ 更新实时环境状态（VAD）
                environmentState.updateVoiceActivity(
                    isActive = vadResult,
                    energy = voiceActivityDetector.getLastEnergy()
                )

                if (vadResult) {
                    lastSpeechTime = System.currentTimeMillis()

                    // 记录语句开始时间（用于提取对应音频）
                    if (lastUtteranceStartTime == 0L) {
                        lastUtteranceStartTime = System.currentTimeMillis()
                    }

                    // 将音频数据传递给语音识别
                    scope.launch {
                        // 对于Vosk离线识别，直接处理音频数据
                        val recognizedText = speechRecognitionManager.processVoskAudio(audioData)
                        if (!recognizedText.isNullOrEmpty()) {
                            handleRecognitionResult(recognizedText)
                        }
                    }
                } else {
                    // 检查是否长时间静音
                    val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                    if (silenceDuration > SILENCE_PAUSE_MS && conversationBuffer.isNotEmpty()) {
                        scope.launch {
                            Log.d(TAG, "检测到长时间静音，处理对话分段")
                            processConversationSegment()
                        }
                    }
                }
            }

            // 检查音频录制是否真正启动
            delay(500) // 等待500ms让录制启动
            if (audioCaptureService.isRecording.value) {
                Log.i(TAG, "[AudioCapture] ✅ 音频录制服务已启动并正在录音")
            } else {
                Log.e(TAG, "[AudioCapture] ❌ 音频录制服务启动失败（isRecording = false）")
                Log.e(TAG, "[AudioCapture] 请检查 AudioCaptureService 的日志以了解详细错误")
            }

            Log.i(TAG, "[AudioCapture] =========================================")
        } catch (e: Exception) {
            Log.e(TAG, "[AudioCapture] ❌ 启动音频录制服务失败", e)
            throw e
        }
    }

    /**
     * 监听语音识别结果
     */
    private fun observeRecognitionResults() {
        scope.launch {
            speechRecognitionManager.recognitionResults.collect { result ->
                when (result) {
                    is SpeechRecognitionResult.Partial -> {
                        _currentTranscript.value = result.text

                        // ✅ 实时更新部分识别结果（拟人：边说边听到）
                        environmentState.updateTranscription(result.text, isPartial = true)

                        Log.d(TAG, "部分结果: ${result.text}")
                    }

                    is SpeechRecognitionResult.Final -> {
                        // ✅ 立即更新完整识别结果
                        environmentState.updateTranscription(result.text, isPartial = false)

                        // ✅ 添加到语句历史（最近30秒）
                        environmentState.addUtterance(
                            text = result.text,
                            speakerId = null,  // 暂时未知，声纹识别后更新
                            speakerName = null
                        )

                        // 加入缓冲区（用于信息提取）
                        handleRecognitionResult(result.text)

                        Log.d(TAG, "最终结果: ${result.text} (${result.method})")

                        // ✅ 触发说话人识别/分离（对这句话的音频）
                        val utteranceDuration = System.currentTimeMillis() - lastUtteranceStartTime
                        if (utteranceDuration > 0) {
                            // ✅ Phase 1 vs Phase 2 选择
                            // TODO: 可以从 AppPreferences 读取配置决定使用哪个阶段
                            val usePhase2 = speakerDiarizationService.isInitialized()

                            if (usePhase2) {
                                // Phase 2: 真实的多说话人分离
                                performSpeakerDiarization(
                                    text = result.text,
                                    utteranceDurationMs = utteranceDuration
                                )
                            } else {
                                // Phase 1: 能量分析 + 声纹识别
                                performSpeakerIdentification(
                                    text = result.text,
                                    utteranceDurationMs = utteranceDuration
                                )
                            }
                        }

                        // 重置语句开始时间
                        lastUtteranceStartTime = 0L
                    }

                    is SpeechRecognitionResult.Error -> {
                        Log.e(TAG, "识别错误: ${result.message}")
                        // 尝试重启识别
                        delay(1000)
                        speechRecognitionManager.startRecognition()
                    }

                    is SpeechRecognitionResult.Started -> {
                        Log.d(TAG, "语音识别已启动")
                    }

                    is SpeechRecognitionResult.Stopped -> {
                        Log.d(TAG, "语音识别已停止")
                    }
                }
            }
        }
    }

    /**
     * 处理识别结果
     */
    private fun handleRecognitionResult(text: String) {
        if (text.isBlank()) return

        // 添加到缓冲区
        if (conversationBuffer.isNotEmpty()) {
            conversationBuffer.append(" ")
        }
        conversationBuffer.append(text)

        _currentTranscript.value = conversationBuffer.toString()

        lastSpeechTime = System.currentTimeMillis()
    }

    /**
     * 启动分段定时器
     */
    private fun startSegmentTimer() {
        scope.launch {
            while (_isMonitoring.value) {
                delay(SEGMENT_DURATION_MS)

                if (conversationBuffer.isNotEmpty()) {
                    Log.d(TAG, "5分钟时间到，处理对话分段")
                    processConversationSegment()
                }
            }
        }
    }

    /**
     * 处理对话分段
     * 将缓冲的对话文本发送给信息提取服务
     */
    private suspend fun processConversationSegment() {
        val segment = conversationBuffer.toString()
        if (segment.isBlank()) return

        Log.d(TAG, "处理对话分段 (${segment.length}字): $segment")

        // 回调给信息提取服务
        onSegmentComplete?.invoke(segment)

        // 清空缓冲区
        conversationBuffer.clear()
        _currentTranscript.value = ""
        segmentStartTime = System.currentTimeMillis()
    }

    /**
     * 手动触发分段处理（用于测试或用户主动触发）
     */
    suspend fun flushCurrentSegment() {
        processConversationSegment()
    }

    /**
     * 获取当前缓冲的对话长度
     */
    fun getCurrentBufferLength(): Int {
        return conversationBuffer.length
    }

    /**
     * 获取当前分段已持续时间
     */
    fun getCurrentSegmentDuration(): Long {
        return System.currentTimeMillis() - segmentStartTime
    }

    /**
     * 检测音频中的说话人数量估计
     * @return 估计的说话人数量（1, 2, 3+）
     */
    private fun estimateSpeakerCount(audioSegment: ByteArray): Int {
        if (audioSegment.isEmpty()) return 0

        // 将字节数组转换为能量序列（每50ms一个窗口）
        val windowSize = 1600 // 50ms @ 16kHz * 2 bytes
        val energySequence = mutableListOf<Float>()

        for (i in audioSegment.indices step windowSize) {
            val endIdx = (i + windowSize).coerceAtMost(audioSegment.size)
            val window = audioSegment.sliceArray(i until endIdx)
            val energy = calculateAudioLevel(window)
            energySequence.add(energy)
        }

        if (energySequence.isEmpty()) return 0

        // 查找能量峰值
        val avgEnergy = energySequence.average().toFloat()
        val threshold = avgEnergy * 1.5f  // 峰值阈值：平均值的1.5倍

        val peaks = mutableListOf<Int>()
        for (i in 1 until energySequence.size - 1) {
            if (energySequence[i] > threshold &&
                energySequence[i] > energySequence[i - 1] &&
                energySequence[i] > energySequence[i + 1]) {
                peaks.add(i)
            }
        }

        // 合并相近的峰值（距离<5个窗口 = 250ms）
        val mergedPeaks = mutableListOf<Int>()
        var lastPeak = -10
        for (peak in peaks) {
            if (peak - lastPeak > 5) {
                mergedPeaks.add(peak)
                lastPeak = peak
            }
        }

        // 根据峰值数量估计说话人数
        return when {
            mergedPeaks.isEmpty() -> 0
            mergedPeaks.size == 1 -> 1
            mergedPeaks.size == 2 -> 2
            else -> 3  // 3表示"3人或更多"
        }
    }

    /**
     * 提取主导说话人的音频（能量最高的部分）
     * @param audioSegment 原始音频
     * @param ratio 提取比例（0.5 = 保留能量最高的50%）
     * @return 过滤后的音频
     */
    private fun extractDominantSpeaker(audioSegment: ByteArray, ratio: Float = 0.6f): ByteArray {
        if (audioSegment.isEmpty()) return audioSegment

        val windowSize = 1600 // 50ms
        val windows = mutableListOf<Pair<Int, Float>>()  // (startIdx, energy)

        for (i in audioSegment.indices step windowSize) {
            val endIdx = (i + windowSize).coerceAtMost(audioSegment.size)
            val window = audioSegment.sliceArray(i until endIdx)
            val energy = calculateAudioLevel(window)
            windows.add(i to energy)
        }

        // 按能量排序，保留最高的部分
        val threshold = windows.map { it.second }.sorted().let { sorted ->
            val index = (sorted.size * (1 - ratio)).toInt()
            sorted[index]
        }

        // 重建音频（只保留高能量窗口）
        val result = mutableListOf<Byte>()
        for ((startIdx, energy) in windows) {
            if (energy >= threshold) {
                val endIdx = (startIdx + windowSize).coerceAtMost(audioSegment.size)
                result.addAll(audioSegment.sliceArray(startIdx until endIdx).toList())
            }
        }

        return result.toByteArray()
    }

    /**
     * 执行声纹识别（Phase 1: 支持多说话人检测）
     */
    private fun performSpeakerIdentification(text: String, utteranceDurationMs: Long) {
        scope.launch {
            try {
                // 提取对应时长的音频（限制在5秒内）
                val audioDurationMs = utteranceDurationMs.coerceAtMost(5000)
                val audioSegment = audioBuffer.getRecentAudio(audioDurationMs)

                if (audioSegment.size < 1000) {
                    // 音频太短，无法识别
                    Log.w(TAG, "音频太短，跳过声纹识别 (${audioSegment.size} bytes)")
                    return@launch
                }

                // ✅ Phase 1: 估计说话人数量
                val speakerCount = estimateSpeakerCount(audioSegment)
                val isMultiSpeaker = speakerCount > 1

                Log.d(TAG, "开始声纹识别，音频长度: ${audioSegment.size} bytes (${audioDurationMs}ms), 估计说话人: $speakerCount")

                // ✅ Phase 1: 根据说话人数量选择策略
                val audioForRecognition = if (isMultiSpeaker) {
                    // 多人场景 → 提取主导说话人
                    Log.d(TAG, "检测到多人说话($speakerCount 人)，提取主导说话人...")
                    extractDominantSpeaker(audioSegment, ratio = 0.6f)
                } else {
                    // 单人场景 → 直接使用原音频
                    audioSegment
                }

                // ✅ 调用新的声纹识别服务
                val identityResult = try {
                    val result = voiceprintManager.identifySpeaker(audioForRecognition, SAMPLE_RATE)
                    if (result.isSuccess) {
                        result.getOrNull()
                    } else {
                        Log.w(TAG, "声纹识别失败: ${result.exceptionOrNull()?.message}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "声纹识别异常: ${e.message}")
                    null
                }

                if (identityResult != null && identityResult.matched) {
                    // ✅ Phase 1: 多人场景降低置信度要求
                    val adjustedConfidence = if (isMultiSpeaker) {
                        identityResult.confidence * 0.8f  // 多人场景置信度打折
                    } else {
                        identityResult.confidence
                    }

                    val speakerInfo = if (isMultiSpeaker) {
                        "(主导说话人, +${speakerCount-1}人在场)"
                    } else {
                        ""
                    }

                    val speakerId = identityResult.getEffectiveSpeakerId()
                    val speakerName = identityResult.getDisplayName()
                    val isMaster = identityResult.profile?.isMaster ?: false

                    Log.d(TAG, "声纹识别成功: $speakerName (置信度: $adjustedConfidence) $speakerInfo")

                    // ✅ 更新环境状态中的当前说话人
                    environmentState.updateCurrentSpeaker(
                        speakerId = speakerId,
                        speakerName = speakerName,
                        confidence = adjustedConfidence,
                        isMaster = isMaster
                    )

                    // ✅ Phase 1: 更新最近语句的说话人信息和多人检测结果
                    environmentState.updateLastUtterance(
                        speakerId = speakerId,
                        speakerName = speakerName,
                        confidence = adjustedConfidence,
                        speakerCount = speakerCount,
                        isOverlapping = isMultiSpeaker
                    )

                    Log.d(TAG, "说话人: ${identityResult.profile?.personName ?: identityResult.profile?.personId ?: identityResult.speakerId} " +
                            "说了: \"${text.take(20)}...\" (场景: ${if (isMultiSpeaker) "多人($speakerCount)" else "单人"})")
                } else {
                    // ✅ 识别失败，但仍然更新说话人数量
                    environmentState.updateLastUtterance(
                        speakerCount = speakerCount,
                        isOverlapping = isMultiSpeaker
                    )
                    Log.w(TAG, "声纹识别失败 (说话人数量: $speakerCount)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "声纹识别异常", e)
            }
        }
    }

    /**
     * ✅ Phase 2: 使用 Speaker Diarization 进行真实的多说话人分离
     */
    private fun performSpeakerDiarization(text: String, utteranceDurationMs: Long) {
        scope.launch {
            try {
                // 提取对应时长的音频
                val audioDurationMs = utteranceDurationMs.coerceAtMost(5000)
                val audioSegment = audioBuffer.getRecentAudio(audioDurationMs)

                if (audioSegment.size < 1000) {
                    Log.w(TAG, "音频太短，跳过说话人分离 (${audioSegment.size} bytes)")
                    return@launch
                }

                Log.d(TAG, "[Phase 2] 开始说话人分离，音频长度: ${audioSegment.size} bytes (${audioDurationMs}ms)")

                // 调用 diarization 服务
                val diarizationResult = speakerDiarizationService.process(
                    audioData = audioSegment,
                    sampleRate = SAMPLE_RATE
                )

                Log.d(TAG, "[Phase 2] 分离完成: 检测到 ${diarizationResult.uniqueSpeakerCount} 个说话人, " +
                        "${diarizationResult.segments.size} 个片段, 耗时 ${diarizationResult.processingTimeMs}ms")

                // 为每个说话人片段进行声纹识别
                for (segment in diarizationResult.segments) {
                    val segmentAudio = extractAudioSegment(
                        audioSegment,
                        segment.startMs,
                        segment.endMs,
                        audioDurationMs
                    )

                    if (segmentAudio.isNotEmpty()) {
                        // ✅ 调用新的声纹识别服务
                        val identity = try {
                            val result = voiceprintManager.identifySpeaker(segmentAudio, SAMPLE_RATE)
                            if (result.isSuccess) {
                                result.getOrNull()
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "[Phase 2] 片段声纹识别异常: ${e.message}")
                            null
                        }

                        if (identity != null && identity.matched) {
                            // 更新片段的本地说话人映射
                            segment.localSpeakerId = identity.getEffectiveSpeakerId()
                            segment.localSpeakerName = identity.getDisplayName()
                            segment.isMaster = identity.profile?.isMaster ?: false  // ✅ 保存主人信息

                            Log.d(TAG, "[Phase 2] 片段 ${segment.label} (${segment.durationSeconds()}s) " +
                                    "识别为: ${identity.getDisplayName()}" +
                                    "${if (identity.profile?.isMaster == true) " [主人]" else ""}")
                        }
                    }
                }

                // 获取主导说话人
                val dominantSegment = diarizationResult.getDominantSpeaker()

                // 更新最近语句的信息
                environmentState.updateLastUtterance(
                    speakerId = dominantSegment?.localSpeakerId,
                    speakerName = dominantSegment?.localSpeakerName,
                    confidence = dominantSegment?.confidence ?: 0.5f,
                    speakerCount = diarizationResult.uniqueSpeakerCount,
                    isOverlapping = diarizationResult.hasOverlap()
                )

                // 更新当前说话人（使用主导说话人）
                if (dominantSegment != null && dominantSegment.localSpeakerId != null) {
                    environmentState.updateCurrentSpeaker(
                        speakerId = dominantSegment.localSpeakerId,
                        speakerName = dominantSegment.localSpeakerName,
                        confidence = dominantSegment.confidence,
                        isMaster = dominantSegment.isMaster  // ✅ 从识别结果读取主人信息
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "[Phase 2] 说话人分离异常", e)
                // 降级到 Phase 1
                Log.w(TAG, "[Phase 2] 降级到 Phase 1 处理")
                performSpeakerIdentification(text, utteranceDurationMs)
            }
        }
    }

    /**
     * 从完整音频中提取指定时间段的片段
     */
    private fun extractAudioSegment(
        fullAudio: ByteArray,
        startMs: Long,
        endMs: Long,
        totalDurationMs: Long
    ): ByteArray {
        if (totalDurationMs <= 0 || fullAudio.isEmpty()) return ByteArray(0)

        // 计算字节偏移（PCM 16-bit, 16kHz）
        val bytesPerMs = (SAMPLE_RATE * 2) / 1000  // 2 bytes per sample
        val startByte = ((startMs * bytesPerMs).toInt()).coerceIn(0, fullAudio.size)
        val endByte = ((endMs * bytesPerMs).toInt()).coerceIn(0, fullAudio.size)

        if (startByte >= endByte) return ByteArray(0)

        return fullAudio.copyOfRange(startByte, endByte)
    }

    /**
     * 计算音频级别（0.0-1.0）
     */
    private fun calculateAudioLevel(audioData: ByteArray): Float {
        var sum = 0L
        val size = audioData.size

        for (i in 0 until size step 2) {
            if (i + 1 < size) {
                val sample = ((audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)).toShort()
                sum += (sample * sample).toLong()
            }
        }

        val rms = kotlin.math.sqrt(sum.toDouble() / (size / 2))
        // 归一化到 0-1 范围
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
        audioCaptureService.cleanup()
        speechRecognitionManager.cleanup()
    }
}

/**
 * 音频环形缓冲区
 * 用于保存最近N秒的音频数据，供声纹识别使用
 */
private class AudioRingBuffer(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var writePosition = 0
    private var size = 0

    /**
     * 添加音频数据
     */
    @Synchronized
    fun write(data: ByteArray) {
        for (byte in data) {
            buffer[writePosition] = byte
            writePosition = (writePosition + 1) % capacity

            if (size < capacity) {
                size++
            }
        }
    }

    /**
     * 获取最近的音频数据
     * @param durationMs 时长（毫秒）
     * @return 音频数据（PCM）
     */
    @Synchronized
    fun getRecentAudio(durationMs: Long): ByteArray {
        // 计算需要的字节数（16kHz, 16-bit = 2 bytes/sample）
        val bytesNeeded = (16000 * 2 * durationMs / 1000).toInt().coerceAtMost(size)

        if (bytesNeeded <= 0) return ByteArray(0)

        val result = ByteArray(bytesNeeded)

        // 从当前写位置向前读取
        var readPos = (writePosition - bytesNeeded + capacity) % capacity

        for (i in 0 until bytesNeeded) {
            result[i] = buffer[readPos]
            readPos = (readPos + 1) % capacity
        }

        return result
    }

    /**
     * 清空缓冲区
     */
    @Synchronized
    fun clear() {
        writePosition = 0
        size = 0
    }
}
