package com.xiaoguang.assistant.presentation.screens.center

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.domain.state.*
import com.xiaoguang.assistant.presentation.components.*
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.design.animateEnter

/**
 * 小光中心页面
 *
 * 应用主页，显示小光的核心状态：
 * - 大头像 + 情绪可视化
 * - 当前想法
 * - 心流冲动
 * - 说话人识别
 * - 快捷操作
 */
@Composable
fun XiaoguangCenterScreen(
    viewModel: XiaoguangCenterViewModel = hiltViewModel(),
    onNavigateToConversation: () -> Unit = {}
) {
    val coreState by viewModel.coreState.collectAsState()
    val showThoughtDetail by viewModel.showThoughtDetail.collectAsState()

    XiaoguangCenterContent(
        coreState = coreState,
        onAvatarTapped = viewModel::onAvatarTapped,
        onThoughtClicked = viewModel::onThoughtClicked,
        onStartConversation = {
            viewModel.onStartConversation()
            onNavigateToConversation()
        }
    )

    // 想法详情对话框
    if (showThoughtDetail && coreState.flow.currentThought != null) {
        ThoughtDetailDialog(
            thought = coreState.flow.currentThought!!,
            onDismiss = viewModel::dismissThoughtDetail
        )
    }
}

@Composable
private fun XiaoguangCenterContent(
    coreState: XiaoguangCoreState,
    onAvatarTapped: () -> Unit,
    onThoughtClicked: () -> Unit,
    onStartConversation: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        emotionColors.background.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(XiaoguangDesignSystem.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.lg)
    ) {
        Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xl))

        // 大头像区域 - 第一个出现
        AvatarSection(
            modifier = Modifier.animateEnter(delayMillis = 0),
            emotion = coreState.emotion.currentEmotion,
            showPulse = coreState.flow.wantsToSpeak || coreState.speaker.isListening,
            onAvatarTapped = onAvatarTapped
        )

        // 情绪描述卡片 - 错落100ms
        EmotionCard(
            modifier = Modifier.animateEnter(delayMillis = 100),
            emotionState = coreState.emotion
        )

        // 当前想法气泡
        AnimatedVisibility(
            visible = coreState.flow.currentThought != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            coreState.flow.currentThought?.let { thought ->
                ThoughtBubble(
                    modifier = Modifier.animateEnter(delayMillis = 200),
                    thought = thought,
                    onClick = onThoughtClicked
                )
            }
        }

        // 心流冲动指示器 - 错落300ms
        FlowImpulseCard(
            modifier = Modifier.animateEnter(delayMillis = 300),
            flowState = coreState.flow
        )

        // 说话人识别状态
        AnimatedVisibility(
            visible = coreState.speaker.isListening || coreState.speaker.currentSpeakerName != null
        ) {
            SpeakerCard(
                modifier = Modifier.animateEnter(delayMillis = 400),
                speakerState = coreState.speaker
            )
        }

        // 快捷操作按钮 - 最后出现
        QuickActions(
            modifier = Modifier.animateEnter(delayMillis = 500),
            isInConversation = coreState.conversation.isInConversation,
            onStartConversation = onStartConversation
        )

        Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xl))
    }
}

/**
 * 大头像区域
 */
@Composable
private fun AvatarSection(
    emotion: EmotionType,
    showPulse: Boolean,
    onAvatarTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        XiaoguangAvatar(
            emotion = emotion,
            size = XiaoguangDesignSystem.AvatarSize.xxl,
            showPulse = showPulse,
            enableBreathing = true,
            modifier = Modifier.clickable(onClick = onAvatarTapped)
        )
    }
}

/**
 * 情绪描述卡片
 */
@Composable
private fun EmotionCard(
    emotionState: EmotionState,
    modifier: Modifier = Modifier
) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = emotionColors.background
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
        ) {
            // 情绪指示器
            EmotionIndicator(
                emotion = emotionState.currentEmotion,
                compact = false
            )

            // 情绪描述
            Text(
                text = emotionState.reason ?: getEmotionDescription(emotionState.currentEmotion),
                style = MaterialTheme.typography.bodyMedium,
                color = emotionColors.primary,
                textAlign = TextAlign.Center
            )

            // 情绪强度
            if (emotionState.intensity > 0.3f) {
                LinearProgressIndicator(
                    progress = { emotionState.intensity },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = emotionColors.primary,
                    trackColor = emotionColors.background,
                )
            }
        }
    }
}

/**
 * 想法气泡
 */
@Composable
private fun ThoughtBubble(
    thought: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emotionColors = LocalEmotionColors.current

    // 浮动动画
    val infiniteTransition = rememberInfiniteTransition(label = "thoughtFloat")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = offsetY.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.md
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = emotionColors.primary,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = XiaoguangPhrases.Flow.THINKING,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = thought,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * 心流冲动卡片
 */
@Composable
private fun FlowImpulseCard(
    flowState: FlowState,
    modifier: Modifier = Modifier
) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.xs
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
        ) {
            // 标题
            Text(
                text = "心流状态",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 冲动指示器
            FlowImpulseIndicator(
                impulse = flowState.impulse,
                showLabel = true
            )

            // 心流阶段
            Row(
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "当前阶段：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.xs),
                    color = emotionColors.background
                ) {
                    Text(
                        text = getFlowPhaseDescription(flowState.currentPhase),
                        style = MaterialTheme.typography.labelSmall,
                        color = emotionColors.primary,
                        modifier = Modifier.padding(
                            horizontal = XiaoguangDesignSystem.Spacing.xs,
                            vertical = XiaoguangDesignSystem.Spacing.xxs
                        )
                    )
                }
            }
        }
    }
}

/**
 * 说话人识别卡片
 */
@Composable
private fun SpeakerCard(
    speakerState: SpeakerState,
    modifier: Modifier = Modifier
) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (speakerState.isListening)
                emotionColors.background
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
        ) {
            SpeakerIndicator(
                speakerName = speakerState.currentSpeakerName,
                isListening = speakerState.isListening
            )

            // 识别置信度
            if (speakerState.currentSpeakerName != null && speakerState.confidence > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "识别度",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { speakerState.confidence },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp),
                        color = emotionColors.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        text = "${(speakerState.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = emotionColors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 快捷操作按钮
 */
@Composable
private fun QuickActions(
    isInConversation: Boolean,
    onStartConversation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
    ) {
        XiaoguangPrimaryButton(
            text = if (isInConversation) "继续对话" else "开始对话",
            onClick = onStartConversation,
            icon = Icons.Default.Chat,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 获取情绪描述
 */
private fun getEmotionDescription(emotion: EmotionType): String {
    return when (emotion) {
        EmotionType.HAPPY -> "我现在${XiaoguangPhrases.Emotion.HAPPY}"
        EmotionType.EXCITED -> "我${XiaoguangPhrases.Emotion.EXCITED}"
        EmotionType.CALM -> "我${XiaoguangPhrases.Emotion.CALM}"
        EmotionType.TIRED -> "我${XiaoguangPhrases.Emotion.TIRED}"
        EmotionType.CURIOUS -> "我${XiaoguangPhrases.Emotion.CURIOUS}"
        EmotionType.CONFUSED -> "我${XiaoguangPhrases.Emotion.CONFUSED}"
        EmotionType.SURPRISED -> "${XiaoguangPhrases.Emotion.SURPRISED}"
        EmotionType.SAD -> "我${XiaoguangPhrases.Emotion.SAD}"
        EmotionType.ANXIOUS -> "我${XiaoguangPhrases.Emotion.ANXIOUS}"
        EmotionType.FRUSTRATED -> "${XiaoguangPhrases.Emotion.FRUSTRATED}"
        else -> "我在这里~"
    }
}

/**
 * 获取心流阶段描述
 */
private fun getFlowPhaseDescription(phase: FlowPhase): String {
    return when (phase) {
        FlowPhase.PERCEIVING -> "感知环境"
        FlowPhase.THINKING -> "思考分析"
        FlowPhase.DECIDING -> "决策判断"
        FlowPhase.ACTING -> "执行行动"
        FlowPhase.IDLE -> "空闲等待"
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun XiaoguangCenterScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        val mockState = XiaoguangCoreState(
            emotion = EmotionState(
                currentEmotion = EmotionType.HAPPY,
                intensity = 0.7f,
                reason = "刚认识了一个新朋友！"
            ),
            flow = FlowState(
                isRunning = true,
                impulse = 0.6f,
                currentThought = "主人今天看起来心情不错，要不要主动打个招呼呢？",
                wantsToSpeak = true,
                currentPhase = FlowPhase.THINKING
            ),
            speaker = SpeakerState(
                currentSpeakerId = "master",
                currentSpeakerName = "主人",
                isMaster = true,
                isListening = true,
                confidence = 0.95f
            )
        )

        XiaoguangCenterContent(
            coreState = mockState,
            onAvatarTapped = {},
            onThoughtClicked = {},
            onStartConversation = {}
        )
    }
}

/**
 * 想法详情对话框
 */
@Composable
private fun ThoughtDetailDialog(
    thought: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "我在想...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = thought,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}
