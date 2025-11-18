package com.xiaoguang.assistant.presentation.ui.voiceprint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import com.xiaoguang.assistant.data.local.database.dao.VoiceprintDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 声纹库管理 ViewModel
 *
 * 功能：
 * - 查看所有已注册的声纹
 * - 删除声纹（非主人）
 * - 更新声纹名称（陌生人转实名）
 * - 显示声纹详情
 */
@HiltViewModel
class VoiceprintManagementViewModel @Inject constructor(
    private val voiceprintManager: VoiceprintManager,
    private val voiceprintDao: VoiceprintDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceprintManagementUiState())
    val uiState: StateFlow<VoiceprintManagementUiState> = _uiState.asStateFlow()

    init {
        loadVoiceprints()
    }

    /**
     * 加载所有声纹
     */
    fun loadVoiceprints() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // 从Room数据库加载声纹元数据
                val voiceprintEntities = voiceprintDao.getAllVoiceprints()

                val displayInfoList = voiceprintEntities.map { entity ->
                    VoiceprintDisplayInfo(
                        voiceprintId = entity.voiceprintId,
                        personId = entity.personId,
                        name = entity.personName,
                        displayName = entity.displayName,
                        isMaster = entity.isMaster,
                        isStranger = entity.isStranger,
                        sampleCount = entity.sampleCount,
                        confidence = entity.confidence,
                        quality = calculateQuality(entity.sampleCount, entity.confidence),
                        lastRecognized = entity.lastRecognized,
                        createdAt = entity.createdAt
                    )
                }

                Timber.d("[VoiceprintManagement] 加载了 ${displayInfoList.size} 个声纹")

                _uiState.value = _uiState.value.copy(
                    voiceprints = displayInfoList,
                    isLoading = false
                )
            } catch (e: Exception) {
                Timber.e(e, "[VoiceprintManagement] 加载声纹失败")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "加载失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 删除声纹
     */
    fun deleteVoiceprint(voiceprintId: String, isMaster: Boolean) {
        if (isMaster) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "不能删除主人声纹"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                // 从ChromaDB删除特征向量
                val result = voiceprintManager.deleteVoiceprint(voiceprintId)

                if (result.isSuccess) {
                    // 从Room删除元数据
                    voiceprintDao.deleteByVoiceprintId(voiceprintId)

                    Timber.i("[VoiceprintManagement] 声纹删除成功: $voiceprintId")
                    _uiState.value = _uiState.value.copy(
                        successMessage = "声纹已删除",
                        isLoading = false
                    )
                    // 重新加载列表
                    loadVoiceprints()
                } else {
                    Timber.w("[VoiceprintManagement] 声纹删除失败: $voiceprintId")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "删除失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[VoiceprintManagement] 删除声纹异常")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "删除失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 更新陌生人名称
     */
    fun updateStrangerName(voiceprintId: String, newName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val result = voiceprintManager.updatePersonName(voiceprintId, newName)

                if (result.isSuccess) {
                    // 同步到Room
                    voiceprintDao.updatePersonName(voiceprintId, newName, System.currentTimeMillis())

                    Timber.i("[VoiceprintManagement] 声纹名称更新成功: $voiceprintId -> $newName")
                    _uiState.value = _uiState.value.copy(
                        successMessage = "名称已更新",
                        isLoading = false
                    )
                    loadVoiceprints()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "更新失败: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[VoiceprintManagement] 更新名称异常")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "更新失败: ${e.message}"
                )
            }
        }
    }

    /**
     * 显示删除确认对话框
     */
    fun showDeleteConfirmation(voiceprint: VoiceprintDisplayInfo) {
        _uiState.value = _uiState.value.copy(
            deleteConfirmation = DeleteConfirmation(
                voiceprintId = voiceprint.voiceprintId,
                voiceprintName = voiceprint.displayName,
                isMaster = voiceprint.isMaster
            )
        )
    }

    /**
     * 取消删除
     */
    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(deleteConfirmation = null)
    }

    /**
     * 确认删除
     */
    fun confirmDelete() {
        val confirmation = _uiState.value.deleteConfirmation ?: return
        deleteVoiceprint(confirmation.voiceprintId, confirmation.isMaster)
        _uiState.value = _uiState.value.copy(deleteConfirmation = null)
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

    /**
     * 计算声纹质量评级
     */
    private fun calculateQuality(sampleCount: Int, confidence: Float): VoiceprintQuality {
        return when {
            sampleCount >= 5 && confidence >= 0.8f -> VoiceprintQuality.EXCELLENT
            sampleCount >= 3 && confidence >= 0.6f -> VoiceprintQuality.GOOD
            sampleCount >= 1 && confidence >= 0.4f -> VoiceprintQuality.FAIR
            else -> VoiceprintQuality.POOR
        }
    }
}

/**
 * 声纹管理 UI 状态
 */
data class VoiceprintManagementUiState(
    val voiceprints: List<VoiceprintDisplayInfo> = emptyList(),
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val deleteConfirmation: DeleteConfirmation? = null
)

/**
 * 声纹显示信息
 */
data class VoiceprintDisplayInfo(
    val voiceprintId: String,  // UUID
    val personId: String,
    val name: String,
    val displayName: String,  // 显示名称（可能是临时名称）
    val isMaster: Boolean,
    val isStranger: Boolean,
    val sampleCount: Int,
    val confidence: Float,
    val quality: VoiceprintQuality,
    val lastRecognized: Long,
    val createdAt: Long
)

/**
 * 声纹质量评级
 */
enum class VoiceprintQuality {
    EXCELLENT,  // 优秀（样本数>=5, 置信度>=0.8）
    GOOD,       // 良好（样本数3-4, 置信度>=0.6）
    FAIR,       // 一般（样本数1-2, 置信度>=0.4）
    POOR        // 差（置信度<0.4）
}

/**
 * 删除确认信息
 */
data class DeleteConfirmation(
    val voiceprintId: String,
    val voiceprintName: String,
    val isMaster: Boolean
)
