package com.xiaoguang.assistant.presentation.ui.voiceprint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintRegistrationRequest
import com.xiaoguang.assistant.data.local.database.dao.VoiceprintDao
import com.xiaoguang.assistant.data.local.database.entity.VoiceprintEntity
import com.xiaoguang.assistant.service.AudioRecorder
import com.xiaoguang.assistant.service.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 声纹录制 ViewModel
 *
 * 功能：
 * - 录制3个声纹样本
 * - 注册新声纹
 * - 支持主人标记
 * - 支持命名/匿名注册
 */
@HiltViewModel
class VoiceprintRecordingViewModel @Inject constructor(
    private val voiceprintManager: VoiceprintManager,
    private val voiceprintDao: VoiceprintDao
) : ViewModel() {

    companion object {
        private const val MIN_SAMPLES = 3  // 最少样本数
    }

    private val _uiState = MutableStateFlow(VoiceprintRecordingUiState())
    val uiState: StateFlow<VoiceprintRecordingUiState> = _uiState.asStateFlow()

    // 音频录制器
    private val audioRecorder = AudioRecorder()
    val volumeLevel: StateFlow<Float> = audioRecorder.volumeLevel
    val recordingDuration: StateFlow<Long> = audioRecorder.recordingDuration
    val recordingState: StateFlow<RecordingState> = audioRecorder.recordingState

    // 收集的音频样本
    private val collectedSamples = mutableListOf<ByteArray>()

    /**
     * 更新姓名
     */
    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(personName = name)
    }

    /**
     * 切换主人标记
     */
    fun toggleIsMaster() {
        _uiState.value = _uiState.value.copy(isMaster = !_uiState.value.isMaster)
    }

    /**
     * 开始录制声纹样本
     */
    fun startRecording() {
        if (audioRecorder.isRecording()) {
            Timber.w("[VoiceprintRecording] 已经在录制中")
            return
        }

        viewModelScope.launch {
            Timber.d("[VoiceprintRecording] 开始录制声纹样本 ${collectedSamples.size + 1}/$MIN_SAMPLES")
            _uiState.value = _uiState.value.copy(
                errorMessage = null,
                successMessage = null,
                isRecording = true
            )

            val success = audioRecorder.startRecording()
            if (!success) {
                Timber.e("[VoiceprintRecording] 启动录制失败")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "启动录制失败",
                    isRecording = false
                )
            }
        }
    }

    /**
     * 停止录制并收集样本
     */
    fun stopRecordingAndCollect() {
        viewModelScope.launch {
            Timber.d("[VoiceprintRecording] 停止录制样本 ${collectedSamples.size + 1}")

            val audioData = audioRecorder.stopRecording()
            _uiState.value = _uiState.value.copy(isRecording = false)

            if (audioData == null) {
                val error = when (val state = recordingState.value) {
                    is RecordingState.ERROR -> state.message
                    else -> "录制失败，请重试"
                }
                _uiState.value = _uiState.value.copy(errorMessage = error)
                return@launch
            }

            // 添加到样本列表
            collectedSamples.add(audioData)
            _uiState.value = _uiState.value.copy(
                currentSample = collectedSamples.size,
                totalSamples = MIN_SAMPLES
            )

            // 检查是否收集足够的样本
            if (collectedSamples.size >= MIN_SAMPLES) {
                _uiState.value = _uiState.value.copy(
                    successMessage = "已收集 ${collectedSamples.size} 个样本，可以注册了！",
                    canRegister = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    successMessage = "样本 ${collectedSamples.size}/$MIN_SAMPLES 已录制"
                )
            }
        }
    }

    /**
     * 重新录制当前样本
     */
    fun rerecordCurrentSample() {
        if (collectedSamples.isNotEmpty()) {
            collectedSamples.removeLast()
            _uiState.value = _uiState.value.copy(
                currentSample = collectedSamples.size,
                canRegister = collectedSamples.size >= MIN_SAMPLES,
                successMessage = "已删除最后一个样本，请重新录制"
            )
        }
    }

    /**
     * 注册声纹
     */
    fun registerVoiceprint() {
        if (collectedSamples.size < MIN_SAMPLES) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "需要至少 $MIN_SAMPLES 个样本，当前只有 ${collectedSamples.size} 个"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val personName = _uiState.value.personName.ifBlank { null }
                val isMaster = _uiState.value.isMaster

                Timber.d("[VoiceprintRecording] 开始注册声纹: name=$personName, isMaster=$isMaster, samples=${collectedSamples.size}")

                val request = VoiceprintRegistrationRequest(
                    audioSamples = collectedSamples.toList(),
                    sampleRate = 16000,
                    personId = null,  // 新人物，没有 personId
                    personName = personName,
                    isMaster = isMaster,
                    metadata = emptyMap()
                )

                val result = voiceprintManager.registerVoiceprint(request)

                if (result.isSuccess) {
                    val profile = result.getOrNull()!!
                    Timber.i("[VoiceprintRecording] 声纹注册成功: ${profile.voiceprintId}")

                    // 同步到Room数据库
                    val entity = VoiceprintEntity(
                        voiceprintId = profile.voiceprintId,
                        personId = profile.personId,
                        personName = profile.personName ?: "",
                        displayName = profile.displayName,
                        sampleCount = profile.sampleCount,
                        confidence = profile.confidence,
                        isMaster = profile.isMaster,
                        isStranger = profile.isStranger,
                        lastRecognized = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    voiceprintDao.insert(entity)

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "声纹注册成功！${if (personName != null) "姓名: $personName" else ""}",
                        registrationComplete = true
                    )

                    // 清空样本
                    collectedSamples.clear()
                    _uiState.value = _uiState.value.copy(
                        currentSample = 0,
                        canRegister = false
                    )
                } else {
                    val error = result.exceptionOrNull()?.message ?: "未知错误"
                    Timber.e("[VoiceprintRecording] 声纹注册失败: $error")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "注册失败: $error"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[VoiceprintRecording] 注册声纹时发生异常")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "注册失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 取消录制
     */
    fun cancelRecording() {
        audioRecorder.cancelRecording()
        _uiState.value = _uiState.value.copy(isRecording = false)
        Timber.d("[VoiceprintRecording] 录制已取消")
    }

    /**
     * 重置状态
     */
    fun resetState() {
        collectedSamples.clear()
        _uiState.value = VoiceprintRecordingUiState()
    }

    /**
     * 清除消息
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            successMessage = null,
            errorMessage = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (audioRecorder.isRecording()) {
            audioRecorder.cancelRecording()
        }
    }
}

/**
 * 声纹录制 UI 状态
 */
data class VoiceprintRecordingUiState(
    val personName: String = "",
    val isMaster: Boolean = false,
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val currentSample: Int = 0,  // 当前已收集的样本数
    val totalSamples: Int = 3,  // 总共需要的样本数
    val canRegister: Boolean = false,  // 是否可以注册（已收集足够样本）
    val registrationComplete: Boolean = false,  // 注册是否完成
    val successMessage: String? = null,
    val errorMessage: String? = null
)
