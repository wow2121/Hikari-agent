package com.xiaoguang.assistant.presentation.ui.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.data.local.datastore.AppPreferences
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.model.MessageRole
import com.xiaoguang.assistant.domain.repository.ConversationRepository
import com.xiaoguang.assistant.domain.usecase.InformationExtractorUseCase
import com.xiaoguang.assistant.domain.usecase.ProcessConversationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val isStreaming: Boolean = false,
    val currentStreamingMessage: String = "",
    val error: String? = null,
    val isLoading: Boolean = false,
    val extractedTasksCount: Int = 0,
    val showExtractionSuccess: Boolean = false
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val processConversationUseCase: ProcessConversationUseCase,
    private val informationExtractorUseCase: InformationExtractorUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    init {
        observeMessages()
    }

    private fun observeMessages() {
        viewModelScope.launch {
            conversationRepository.observeMessages()
                .catch { e ->
                    Timber.e(e, "Error observing messages")
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { messages ->
                    Timber.d("💾 数据库更新 - messages数量: ${messages.size}, 最后一条: ${messages.lastOrNull()?.content?.take(50)}")
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            try {
                Timber.d("📤 发送消息: $text")
                _uiState.update {
                    it.copy(
                        isStreaming = true,
                        currentStreamingMessage = "",
                        error = null
                    )
                }

                processConversationUseCase.processUserInput(text)
                    .catch { e ->
                        Timber.e(e, "Error processing message")
                        _uiState.update {
                            it.copy(
                                error = e.message,
                                isStreaming = false,
                                currentStreamingMessage = ""
                            )
                        }
                    }
                    .collect { chunk ->
                        // 检测到完成标记时,立即清空流式状态
                        if (chunk == ProcessConversationUseCase.STREAM_END_MARKER) {
                            Timber.d("🎯 收到完成标记,立即清空流式状态")
                            Timber.d("📊 清空前状态 - messages数量: ${_uiState.value.messages.size}, streamingMsg长度: ${_uiState.value.currentStreamingMessage.length}")
                            _uiState.update {
                                it.copy(
                                    isStreaming = false,
                                    currentStreamingMessage = ""
                                )
                            }
                            Timber.d("🧹 已清空流式状态,等待数据库更新")
                        } else {
                            // 普通内容,添加到流式消息
                            _uiState.update { state ->
                                state.copy(
                                    currentStreamingMessage = state.currentStreamingMessage + chunk
                                )
                            }
                        }
                    }

                Timber.d("✅ Flow collect 完成")
                Timber.d("📊 最终状态 - messages数量: ${_uiState.value.messages.size}")
            } catch (e: Exception) {
                Timber.e(e, "Error sending message")
                _uiState.update {
                    it.copy(
                        error = e.message,
                        isStreaming = false,
                        currentStreamingMessage = ""
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                Timber.d("🗑️ 清除对话历史")
                conversationRepository.deleteAllMessages()
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        currentStreamingMessage = "",
                        isStreaming = false
                    )
                }
                Timber.d("✅ 对话历史已清除")
            } catch (e: Exception) {
                Timber.e(e, "清除对话历史失败")
                _uiState.update { it.copy(error = "清除失败: ${e.message}") }
            }
        }
    }

    /**
     * 从最近的对话中提取待办事项
     * 分析最近N条消息,自动创建待办
     */
    fun extractTodosFromConversation(messageCount: Int = 10) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // 获取最近的消息
                val recentMessages = _uiState.value.messages.takeLast(messageCount)
                if (recentMessages.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "没有对话可以提取"
                        )
                    }
                    return@launch
                }

                // 构建对话文本
                val conversationText = recentMessages.joinToString("\n") { message ->
                    "${if (message.role == MessageRole.USER) "用户" else "助手"}: ${message.content}"
                }

                Timber.d("正在从对话提取待办: $conversationText")

                // 获取置信度阈值
                val threshold = appPreferences.confidenceThreshold.first()

                // 调用信息提取UseCase
                val result = informationExtractorUseCase.extractFromConversation(
                    conversationText = conversationText,
                    confidenceThreshold = threshold
                )

                val totalExtracted = result.tasks.size + result.events.size

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        extractedTasksCount = totalExtracted,
                        showExtractionSuccess = totalExtracted > 0
                    )
                }

                Timber.d("成功提取 $totalExtracted 个待办/事件")
            } catch (e: Exception) {
                Timber.e(e, "从对话提取待办失败")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "提取待办失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 清除提取成功的提示
     */
    fun clearExtractionSuccess() {
        _uiState.update {
            it.copy(
                showExtractionSuccess = false,
                extractedTasksCount = 0
            )
        }
    }
}
