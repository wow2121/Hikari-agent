package com.xiaoguang.assistant.presentation.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.social.UnifiedSocialManager
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 人物信息（整合声纹+社交）
 */
data class PersonInfo(
    val personId: String,
    val personName: String,
    val displayName: String,
    val isMaster: Boolean,
    val relationship: String?,
    val lastSeenTimestamp: Long,
    val hasVoiceprint: Boolean,
    val voiceprintSampleCount: Int,
    val intimacyScore: Float
)

/**
 * 人物列表UI状态
 */
data class PeopleUiState(
    val people: List<PersonInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val selectedFilter: PeopleFilter = PeopleFilter.ALL
)

/**
 * 人物筛选类型
 */
enum class PeopleFilter {
    ALL,          // 所有人
    MASTER,       // 主人
    FRIENDS,      // 朋友
    ACQUAINTANCES, // 认识的人
    STRANGERS,    // 陌生人
    WITH_VOICEPRINT // 有声纹的
}

/**
 * 人物管理页面ViewModel
 *
 * 整合声纹识别系统和社交系统
 */
@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val voiceprintManager: VoiceprintManager,
    private val socialManager: UnifiedSocialManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PeopleUiState())
    val uiState: StateFlow<PeopleUiState> = _uiState.asStateFlow()

    init {
        loadPeople()
    }

    /**
     * 加载人物列表
     */
    fun loadPeople() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 获取所有社交关系中的人物
                val allPeople = socialManager.getAllRelations()

                // 获取所有声纹
                val allVoiceprints = voiceprintManager.getAllProfiles()

                // 整合信息
                val peopleInfo = allPeople.map { relation ->
                    val voiceprint = allVoiceprints.find { it.personId == relation.characterId }

                    PersonInfo(
                        personId = relation.characterId,
                        personName = relation.personName,
                        displayName = relation.personName,
                        isMaster = relation.isMaster,
                        relationship = relation.relationshipType,
                        lastSeenTimestamp = voiceprint?.updatedAt ?: relation.lastInteractionAt,
                        hasVoiceprint = voiceprint != null,
                        voiceprintSampleCount = voiceprint?.sampleCount ?: 0,
                        intimacyScore = relation.affectionLevel.toFloat() / 100f
                    )
                }.sortedByDescending { it.intimacyScore } // 按亲密度排序

                _uiState.update {
                    it.copy(
                        people = peopleInfo,
                        isLoading = false
                    )
                }

                Timber.d("已加载 ${peopleInfo.size} 个人物")
            } catch (e: Exception) {
                Timber.e(e, "加载人物列表失败")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    /**
     * 搜索人物
     */
    fun searchPeople(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * 设置筛选条件
     */
    fun setFilter(filter: PeopleFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    /**
     * 获取主人信息
     */
    suspend fun getMasterInfo(): PersonInfo? {
        val masterProfile = voiceprintManager.getMasterProfile()
        if (masterProfile != null && masterProfile.personName != null) {
            val relation = socialManager.getOrCreateRelation(masterProfile.personName!!)
            return PersonInfo(
                personId = relation.characterId,
                personName = relation.personName,
                displayName = masterProfile.displayName,
                isMaster = true,
                relationship = "主人",
                lastSeenTimestamp = masterProfile.updatedAt,
                hasVoiceprint = true,
                voiceprintSampleCount = masterProfile.sampleCount,
                intimacyScore = 1.0f
            )
        }
        return null
    }

}
