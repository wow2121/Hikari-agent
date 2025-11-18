package com.xiaoguang.assistant.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.design.toThemeColors

/**
 * 情绪状态指示器
 *
 * 显示小光当前的情绪状态，包括：
 * - 情绪颜色
 * - 情绪名称
 *
 * @param emotion 当前情绪
 * @param compact 是否使用紧凑模式（只显示颜色圆点）
 */
@Composable
fun EmotionIndicator(
    emotion: EmotionType,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val emotionColors = emotion.toThemeColors()
    val emotionText = emotion.toDisplayText()

    if (compact) {
        // 紧凑模式 - 只显示彩色圆点
        Box(
            modifier = modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(emotionColors.primary)
        )
    } else {
        // 完整模式 - 显示圆点 + 文字
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.full))
                .background(emotionColors.background)
                .padding(
                    horizontal = XiaoguangDesignSystem.Spacing.sm,
                    vertical = XiaoguangDesignSystem.Spacing.xxs
                ),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(emotionColors.primary)
            )
            Text(
                text = emotionText,
                style = MaterialTheme.typography.labelSmall,
                color = emotionColors.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 心流冲动指示器
 *
 * 显示小光想要说话的冲动强度（0.0 - 1.0）
 *
 * @param impulse 冲动强度 (0.0 - 1.0)
 * @param showLabel 是否显示文字标签
 */
@Composable
fun FlowImpulseIndicator(
    impulse: Float,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val emotionColors = LocalEmotionColors.current

    // 冲动等级颜色
    val impulseColor = when {
        impulse > 0.7f -> XiaoguangDesignSystem.CoreColors.error  // 强烈冲动 - 红色
        impulse > 0.4f -> XiaoguangDesignSystem.CoreColors.warning  // 中等冲动 - 橙色
        else -> XiaoguangDesignSystem.CoreColors.success  // 弱冲动 - 绿色
    }

    // 脉动动画
    val infiniteTransition = rememberInfiniteTransition(label = "impulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "impulseAlpha"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLabel) {
            Text(
                text = "冲动",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 进度条
        Box(
            modifier = Modifier
                .height(8.dp)
                .width(60.dp)
                .clip(RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.xs))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(impulse.coerceIn(0f, 1f))
                    .alpha(if (impulse > 0.5f) alpha else 1f)  // 高冲动时脉动
                    .background(impulseColor)
            )
        }

        // 百分比（可选）
        if (showLabel) {
            Text(
                text = "${(impulse * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = impulseColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 说话人指示器
 *
 * 显示当前识别到的说话人
 *
 * @param speakerName 说话人姓名，null表示未识别
 * @param isListening 是否正在监听
 */
@Composable
fun SpeakerIndicator(
    speakerName: String?,
    modifier: Modifier = Modifier,
    isListening: Boolean = false
) {
    val emotionColors = LocalEmotionColors.current

    // 监听动画
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    val listeningAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = XiaoguangDesignSystem.AnimationDurations.SPEAKER_INDICATOR,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "listeningAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.sm))
            .background(
                if (isListening)
                    emotionColors.background.copy(alpha = listeningAlpha)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(
                horizontal = XiaoguangDesignSystem.Spacing.sm,
                vertical = XiaoguangDesignSystem.Spacing.xxs
            ),
        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm),
            tint = if (isListening) emotionColors.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = when {
                isListening && speakerName != null -> speakerName
                isListening -> XiaoguangPhrases.VoiceRecognition.LISTENING
                speakerName != null -> speakerName
                else -> "未知"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isListening) emotionColors.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isListening) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * 综合状态栏
 *
 * 在一行中显示情绪、心流冲动、说话人信息
 *
 * @param emotion 当前情绪
 * @param flowImpulse 心流冲动强度
 * @param speakerName 说话人姓名
 * @param isListening 是否正在监听
 */
@Composable
fun XiaoguangStatusBar(
    emotion: EmotionType,
    flowImpulse: Float,
    modifier: Modifier = Modifier,
    speakerName: String? = null,
    isListening: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(XiaoguangDesignSystem.Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 情绪指示器
        EmotionIndicator(emotion = emotion)

        // 心流冲动
        FlowImpulseIndicator(
            impulse = flowImpulse,
            showLabel = false,
            modifier = Modifier.weight(1f)
        )

        // 说话人
        if (isListening || speakerName != null) {
            SpeakerIndicator(
                speakerName = speakerName,
                isListening = isListening
            )
        }
    }
}

/**
 * 将 EmotionType 转换为显示文本
 */
private fun EmotionType.toDisplayText(): String {
    return when (this) {
        EmotionType.HAPPY -> XiaoguangPhrases.Emotion.HAPPY
        EmotionType.EXCITED -> XiaoguangPhrases.Emotion.EXCITED
        EmotionType.CALM -> XiaoguangPhrases.Emotion.CALM
        EmotionType.TIRED -> XiaoguangPhrases.Emotion.TIRED
        EmotionType.CURIOUS -> XiaoguangPhrases.Emotion.CURIOUS
        EmotionType.CONFUSED -> XiaoguangPhrases.Emotion.CONFUSED
        EmotionType.SURPRISED -> XiaoguangPhrases.Emotion.SURPRISED
        EmotionType.SAD -> XiaoguangPhrases.Emotion.SAD
        EmotionType.ANXIOUS -> XiaoguangPhrases.Emotion.ANXIOUS
        EmotionType.FRUSTRATED -> XiaoguangPhrases.Emotion.FRUSTRATED
        else -> "中性"
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun StatusIndicatorPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("情绪指示器", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmotionIndicator(emotion = EmotionType.HAPPY)
                EmotionIndicator(emotion = EmotionType.CALM)
                EmotionIndicator(emotion = EmotionType.SAD)
            }

            Text("紧凑模式", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmotionIndicator(emotion = EmotionType.HAPPY, compact = true)
                EmotionIndicator(emotion = EmotionType.CALM, compact = true)
                EmotionIndicator(emotion = EmotionType.SAD, compact = true)
            }

            Text("心流冲动指示器", style = MaterialTheme.typography.titleSmall)
            FlowImpulseIndicator(impulse = 0.2f)
            FlowImpulseIndicator(impulse = 0.5f)
            FlowImpulseIndicator(impulse = 0.9f)

            Text("说话人指示器", style = MaterialTheme.typography.titleSmall)
            SpeakerIndicator(speakerName = "主人", isListening = false)
            SpeakerIndicator(speakerName = "张三", isListening = true)
            SpeakerIndicator(speakerName = null, isListening = true)

            Text("综合状态栏", style = MaterialTheme.typography.titleSmall)
            XiaoguangStatusBar(
                emotion = EmotionType.HAPPY,
                flowImpulse = 0.6f,
                speakerName = "主人",
                isListening = true
            )
        }
    }
}
