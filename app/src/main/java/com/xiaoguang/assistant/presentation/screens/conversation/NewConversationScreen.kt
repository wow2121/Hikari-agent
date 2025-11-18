package com.xiaoguang.assistant.presentation.screens.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.domain.model.Message
import com.xiaoguang.assistant.domain.model.MessageRole
import com.xiaoguang.assistant.domain.state.XiaoguangCoreState
import com.xiaoguang.assistant.domain.state.XiaoguangCoreStateManager
import com.xiaoguang.assistant.presentation.components.*
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.ui.conversation.ConversationUiState
import com.xiaoguang.assistant.presentation.ui.conversation.ConversationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * 新的对话页面
 *
 * 整合了所有对话功能，使用新的设计系统：
 * - 显示对话消息列表
 * - 情绪标签
 * - 说话人标签
 * - 思考状态指示
 * - 流式回复
 * - 语音输入
 */
@Composable
fun NewConversationScreen(
    viewModel: ConversationViewModel = hiltViewModel(),
    coreStateManager: XiaoguangCoreStateManager? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val coreState = coreStateManager?.coreState?.collectAsState()?.value

    NewConversationContent(
        uiState = uiState,
        coreState = coreState,
        onSendMessage = viewModel::sendMessage,
        onClearHistory = viewModel::clearHistory
    )
}

@Composable
private fun NewConversationContent(
    uiState: ConversationUiState,
    coreState: XiaoguangCoreState?,
    onSendMessage: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 自动滚动到最新消息
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 消息列表
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            // 空状态提示
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyStatePrompt()
                }
            }

            // 消息列表
            items(
                items = uiState.messages,
                key = { it.id }
            ) { message ->
                MessageItem(
                    message = message,
                    currentEmotion = coreState?.emotion?.currentEmotion ?: EmotionType.CALM,
                    currentSpeaker = if (message.role == MessageRole.USER)
                        coreState?.speaker?.currentSpeakerName
                    else null
                )
            }

            // 流式输入指示器
            if (uiState.isStreaming && uiState.currentStreamingMessage.isNotEmpty()) {
                item {
                    StreamingMessageIndicator(
                        partialMessage = uiState.currentStreamingMessage,
                        currentEmotion = coreState?.emotion?.currentEmotion ?: EmotionType.CALM
                    )
                }
            }

            // 思考中指示器
            if (uiState.isLoading) {
                item {
                    ThinkingIndicator()
                }
            }
        }

        // 错误提示
        AnimatedVisibility(visible = uiState.error != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
                ) {
                    Text(
                        text = uiState.error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 输入栏
        InputBar(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    onSendMessage(inputText)
                    inputText = ""
                }
            },
            onVoiceInput = {
                // TODO: 语音输入
            },
            onClearHistory = onClearHistory,
            enabled = !uiState.isLoading && !uiState.isStreaming
        )
    }
}

/**
 * 消息项
 */
@Composable
private fun MessageItem(
    message: Message,
    currentEmotion: EmotionType,
    currentSpeaker: String?
) {
    val isFromXiaoguang = message.role == MessageRole.ASSISTANT
    val timestamp = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    ConversationMessageCard(
        message = message.content,
        isFromXiaoguang = isFromXiaoguang,
        speakerName = if (!isFromXiaoguang) currentSpeaker else null,
        timestamp = timestamp,
        emotion = if (isFromXiaoguang) currentEmotion else null
    )
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyStatePrompt() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(XiaoguangDesignSystem.Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
    ) {
        XiaoguangAvatar(
            emotion = EmotionType.HAPPY,
            size = XiaoguangDesignSystem.AvatarSize.xl
        )

        Text(
            text = XiaoguangPhrases.Conversation.START_CONVERSATION,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "试试跟我说点什么吧~",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 流式消息指示器
 */
@Composable
private fun StreamingMessageIndicator(
    partialMessage: String,
    currentEmotion: EmotionType
) {
    ConversationMessageCard(
        message = partialMessage,
        isFromXiaoguang = true,
        emotion = currentEmotion,
        timestamp = null
    )
}

/**
 * 思考中指示器
 */
@Composable
private fun ThinkingIndicator() {
    val emotionColors = LocalEmotionColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = XiaoguangDesignSystem.Spacing.md),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
            color = emotionColors.background
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = XiaoguangDesignSystem.Spacing.md,
                    vertical = XiaoguangDesignSystem.Spacing.sm
                ),
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                XiaoguangAvatar(
                    emotion = EmotionType.CURIOUS,
                    size = XiaoguangDesignSystem.AvatarSize.xs,
                    enableBreathing = false
                )

                Text(
                    text = XiaoguangPhrases.Conversation.THINKING_RESPONSE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = emotionColors.primary,
                    fontWeight = FontWeight.Medium
                )

                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = emotionColors.primary
                )
            }
        }
    }
}

/**
 * 输入栏
 */
@Composable
private fun InputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceInput: () -> Unit,
    onClearHistory: () -> Unit,
    enabled: Boolean
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = XiaoguangDesignSystem.Elevation.sm,
        shadowElevation = XiaoguangDesignSystem.Elevation.md
    ) {
        Column {
            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(XiaoguangDesignSystem.Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm),
                verticalAlignment = Alignment.Bottom
            ) {
                // 语音输入按钮
                XiaoguangIconButton(
                    icon = Icons.Default.Mic,
                    onClick = onVoiceInput,
                    contentDescription = "语音输入",
                    enabled = enabled
                )

                // 文本输入框
                XiaoguangTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = if (enabled) "说点什么吧..." else XiaoguangPhrases.Conversation.THINKING_RESPONSE,
                    enabled = enabled,
                    singleLine = false
                )

                // 发送按钮
                XiaoguangIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    onClick = onSend,
                    contentDescription = "发送",
                    enabled = enabled && inputText.isNotBlank()
                )

                // 清空历史按钮
                XiaoguangIconButton(
                    icon = Icons.Default.Clear,
                    onClick = { showClearDialog = true },
                    contentDescription = "清空历史",
                    enabled = enabled
                )
            }
        }
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(XiaoguangPhrases.Conversation.CLEAR_HISTORY) },
            text = { Text(XiaoguangPhrases.Settings.CANNOT_UNDO) },
            confirmButton = {
                XiaoguangPrimaryButton(
                    text = XiaoguangPhrases.Common.CONFIRM,
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                    }
                )
            },
            dismissButton = {
                XiaoguangTextButton(
                    text = XiaoguangPhrases.Common.CANCEL,
                    onClick = { showClearDialog = false }
                )
            }
        )
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun NewConversationScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        val mockMessages = listOf(
            Message(
                id = "1",
                content = "你好呀！今天心情怎么样？",
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis() - 60000
            ),
            Message(
                id = "2",
                content = "还不错！",
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis() - 30000
            ),
            Message(
                id = "3",
                content = "那太好了！有什么我能帮你的吗？",
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis()
            )
        )

        NewConversationContent(
            uiState = ConversationUiState(
                messages = mockMessages,
                isLoading = false
            ),
            coreState = null,
            onSendMessage = {},
            onClearHistory = {}
        )
    }
}
