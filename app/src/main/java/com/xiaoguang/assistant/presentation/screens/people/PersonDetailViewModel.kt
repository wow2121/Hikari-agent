package com.xiaoguang.assistant.presentation.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.knowledge.CharacterBook
import com.xiaoguang.assistant.domain.memory.UnifiedMemorySystem
import com.xiaoguang.assistant.domain.memory.models.MemoryCategory
import com.xiaoguang.assistant.domain.memory.models.MemoryQuery
import com.xiaoguang.assistant.domain.memory.models.TemporalQuery
import com.xiaoguang.assistant.domain.social.UnifiedSocialManager
import com.xiaoguang.assistant.domain.voiceprint.VoiceprintManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 人物详情UI状态
 */
data class PersonDetailUiState(
    val personId: String = "",
    val personName: String = "",
    val displayName: String = "",
    val isMaster: Boolean = false,
    val relationship: String = "陌生人",
    val affinity: Float = 0.5f,
    val hasVoiceprint: Boolean = false,
    val voiceprintSampleCount: Int = 0,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val interactions: List<InteractionRecord> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * 互动记录
 */
data class InteractionRecord(
    val timestamp: Long,
    val type: String,
    val content: String
)

/**
 * 人物详情ViewModel
 */
@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val socialManager: UnifiedSocialManager,
    private val voiceprintManager: VoiceprintManager,
    private val characterBook: CharacterBook,
    private val unifiedMemorySystem: UnifiedMemorySystem
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    /**
     * 加载人物详情
     */
    fun loadPersonDetail(personId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // 1. 从社交系统获取关系信息
                val relations = socialManager.getAllRelations()
                val relation = relations.find { it.characterId == personId }

                if (relation != null) {
                    // 2. 从 CharacterBook 获取角色档案
                    val profile = characterBook.getProfile(personId)

                    // 3. 获取声纹信息
                    val voiceprint = voiceprintManager.getAllProfiles()
                        .find { it.personId == personId }

                    // 4. 从 UnifiedMemorySystem 查询相关记忆作为互动记录
                    val relatedMemories = unifiedMemorySystem.queryMemories(
                        MemoryQuery(
                            characters = listOf(personId),
                            temporal = TemporalQuery.RecentDays(30),
                            category = MemoryCategory.PERSON,
                            limit = 10
                        )
                    )

                    // 转换记忆为互动记录
                    val interactions = relatedMemories.map { rankedMemory ->
                        InteractionRecord(
                            timestamp = rankedMemory.memory.timestamp,
                            type = when (rankedMemory.memory.intent) {
                                com.xiaoguang.assistant.domain.memory.models.IntentType.QUESTION -> "提问"
                                com.xiaoguang.assistant.domain.memory.models.IntentType.STATEMENT -> "对话"
                                com.xiaoguang.assistant.domain.memory.models.IntentType.GREETING -> "问候"
                                com.xiaoguang.assistant.domain.memory.models.IntentType.EMOTION -> "情感表达"
                                else -> "互动"
                            },
                            content = rankedMemory.memory.content.take(100)
                        )
                    }

                    // 5. 组装 UI 状态
                    _uiState.value = PersonDetailUiState(
                        personId = personId,
                        personName = relation.personName,
                        displayName = relation.personName,
                        isMaster = relation.isMaster,
                        relationship = relation.relationshipType ?: "朋友",
                        affinity = relation.affectionLevel.toFloat() / 100f,
                        hasVoiceprint = voiceprint != null,
                        voiceprintSampleCount = voiceprint?.sampleCount ?: 0,
                        // 从角色档案获取标签
                        tags = profile?.preferences?.interests?.take(3) ?: listOf("友善"),
                        // 从角色档案获取备注
                        notes = profile?.basicInfo?.bio ?: "暂无备注",
                        interactions = interactions,
                        isLoading = false
                    )

                    Timber.d("加载人物详情成功: $personId")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "未找到该人物"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "加载人物详情失败: $personId")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "加载失败"
                )
            }
        }
    }
}
