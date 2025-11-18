package com.xiaoguang.assistant.presentation.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoguang.assistant.domain.usecase.ProcessConversationUseCase
import com.xiaoguang.assistant.service.speech.SpeechRecognitionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 语音助手ViewModel
 * 管理全屏语音助手界面的状态和交互
 */
@HiltViewModel
class VoiceAssistantViewModel @Inject constructor(
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val processConversationUseCase: ProcessConversationUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceAssistantState())
    val uiState: StateFlow<VoiceAssistantState> = _uiState.asStateFlow()

    private var recognitionJob: kotlinx.coroutines.Job? = null

    /**
     * 显示语音助手界面并开始识别
     */
    fun show() {
        _uiState.update { it.copy(isVisible = true, isListening = true, recognizedText = "", aiResponse = "") }
        startRecognition()
    }

    /**
     * 隐藏语音助手界面
     */
    fun hide() {
        stopRecognition()
        _uiState.update { it.copy(isVisible = false, isListening = false) }
    }

    /**
     * 开始语音识别
     */
    private fun startRecognition() {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            try {
                Timber.d("开始语音识别")

                // 启动识别
                speechRecognitionManager.startRecognition()

                // 监听识别结果
                speechRecognitionManager.recognitionResults
                    .catch { e ->
                        Timber.e(e, "语音识别失败")
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                aiResponse = "抱歉，语音识别出错了"
                            )
                        }
                    }
                    .collect { result ->
                        when (result) {
                            is com.xiaoguang.assistant.service.speech.SpeechRecognitionResult.Partial -> {
                                // 部分结果，更新显示
                                _uiState.update { it.copy(recognizedText = result.text) }
                            }
                            is com.xiaoguang.assistant.service.speech.SpeechRecognitionResult.Final -> {
                                // 最终结果，发送给AI处理
                                _uiState.update { it.copy(recognizedText = result.text) }
                                if (result.text.isNotEmpty()) {
                                    processUserInput(result.text)
                                }
                            }
                            is com.xiaoguang.assistant.service.speech.SpeechRecognitionResult.Error -> {
                                Timber.e("识别错误: ${result.message}")
                                _uiState.update {
                                    it.copy(
                                        isListening = false,
                                        aiResponse = "识别错误: ${result.message}"
                                    )
                                }
                            }
                            is com.xiaoguang.assistant.service.speech.SpeechRecognitionResult.Started -> {
                                Timber.d("识别已开始")
                            }
                            is com.xiaoguang.assistant.service.speech.SpeechRecognitionResult.Stopped -> {
                                Timber.d("识别已停止")
                                _uiState.update { it.copy(isListening = false) }
                            }
                        }
                    }

            } catch (e: Exception) {
                Timber.e(e, "语音识别异常")
                _uiState.update {
                    it.copy(
                        isListening = false,
                        aiResponse = "语音识别出错: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 停止语音识别
     */
    private fun stopRecognition() {
        recognitionJob?.cancel()
        viewModelScope.launch {
            speechRecognitionManager.stopRecognition()
        }
        _uiState.update { it.copy(isListening = false) }
    }

    /**
     * 处理用户输入，获取AI回复
     */
    private fun processUserInput(text: String) {
        viewModelScope.launch {
            try {
                Timber.d("处理用户输入: $text")

                // 停止监听，开始处理
                _uiState.update { it.copy(isListening = false, aiResponse = "") }

                var fullResponse = ""

                processConversationUseCase.processUserInput(text, includeContext = true)
                    .catch { e ->
                        Timber.e(e, "处理输入失败")
                        _uiState.update {
                            it.copy(aiResponse = "处理失败: ${e.message}")
                        }
                    }
                    .collect { chunk ->
                        // 检测结束标记
                        if (chunk == ProcessConversationUseCase.STREAM_END_MARKER) {
                            Timber.d("AI回复完成")
                            // 可以在这里播放TTS
                        } else {
                            fullResponse += chunk
                            _uiState.update { it.copy(aiResponse = fullResponse) }
                        }
                    }

            } catch (e: Exception) {
                Timber.e(e, "处理用户输入异常")
                _uiState.update {
                    it.copy(aiResponse = "处理出错: ${e.message}")
                }
            }
        }
    }

    /**
     * 更新音频级别（用于波形动画）
     */
    fun updateAudioLevel(level: Float) {
        _uiState.update { it.copy(audioLevel = level.coerceIn(0f, 1f)) }
    }

    /**
     * 重新开始监听
     */
    fun restartListening() {
        _uiState.update {
            it.copy(
                isListening = true,
                recognizedText = "",
                aiResponse = ""
            )
        }
        startRecognition()
    }

    override fun onCleared() {
        super.onCleared()
        stopRecognition()
    }
}
