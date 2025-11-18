package com.xiaoguang.assistant.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * 小光头像组件
 *
 * 显示小光的卡通形象，支持：
 * - 6种情绪表情（开心、平静、思考、惊讶、难过、尴尬）
 * - 呼吸动画（空闲时的微妙缩放）
 * - 脉冲光晕（说话或思考时）
 * - 表情切换动画
 *
 * @param emotion 当前情绪类型
 * @param size 头像尺寸
 * @param showPulse 是否显示脉冲光晕
 * @param modifier Modifier
 */
@Composable
fun XiaoguangAvatar(
    emotion: EmotionType,
    modifier: Modifier = Modifier,
    size: Dp = XiaoguangDesignSystem.AvatarSize.lg,
    showPulse: Boolean = false,
    enableBreathing: Boolean = true
) {
    val emotionColors = LocalEmotionColors.current

    // 呼吸动画 - 微妙的缩放效果
    val breathingScale by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = XiaoguangDesignSystem.AnimationDurations.AVATAR_PULSE,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // 脉冲光晕动画
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = XiaoguangDesignSystem.AnimationDurations.FLOW_IMPULSE,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val pulseScale by rememberInfiniteTransition(label = "pulseScale").animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = XiaoguangDesignSystem.AnimationDurations.FLOW_IMPULSE,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 脉冲光晕
        if (showPulse) {
            Surface(
                modifier = Modifier
                    .size(size)
                    .scale(pulseScale)
                    .alpha(pulseAlpha),
                shape = CircleShape,
                color = emotionColors.primary.copy(alpha = 0.3f)
            ) {}
        }

        // 主头像
        Box(
            modifier = Modifier
                .size(size)
                .scale(if (enableBreathing) breathingScale else 1f)
        ) {
            // 背景圆圈
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = emotionColors.background
            ) {}

            // 表情绘制
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawXiaoguangFace(emotion, emotionColors.primary, emotionColors.secondary)
            }
        }
    }
}

/**
 * 绘制小光的脸部表情
 */
private fun DrawScope.drawXiaoguangFace(
    emotion: EmotionType,
    primaryColor: Color,
    secondaryColor: Color
) {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val faceRadius = size.width / 2

    // 眼睛位置
    val eyeY = centerY - faceRadius * 0.15f
    val leftEyeX = centerX - faceRadius * 0.25f
    val rightEyeX = centerX + faceRadius * 0.25f

    when (emotion) {
        EmotionType.HAPPY, EmotionType.EXCITED -> {
            // 😊 开心 - 弯弯的眼睛 + 微笑的嘴
            drawHappyEyes(leftEyeX, eyeY, faceRadius, primaryColor)
            drawHappyEyes(rightEyeX, eyeY, faceRadius, primaryColor)
            drawSmile(centerX, centerY + faceRadius * 0.2f, faceRadius, primaryColor)
        }

        EmotionType.CALM, EmotionType.TIRED -> {
            // 😴 平静/困倦 - 半闭的眼睛 + 平静的嘴
            drawSleepyEyes(leftEyeX, eyeY, faceRadius, primaryColor)
            drawSleepyEyes(rightEyeX, eyeY, faceRadius, primaryColor)
            drawNeutralMouth(centerX, centerY + faceRadius * 0.25f, faceRadius, primaryColor)
        }

        EmotionType.CURIOUS -> {
            // 🤔 思考 - 好奇的眼睛 + 思考的嘴
            drawCuriousEyes(leftEyeX, eyeY, faceRadius, primaryColor)
            drawCuriousEyes(rightEyeX, eyeY, faceRadius, primaryColor)
            drawThinkingMouth(centerX, centerY + faceRadius * 0.2f, faceRadius, primaryColor)
        }

        EmotionType.SURPRISED -> {
            // 😮 惊讶 - 大大的眼睛 + O型嘴
            drawSurprisedEyes(leftEyeX, eyeY, faceRadius, primaryColor)
            drawSurprisedEyes(rightEyeX, eyeY, faceRadius, primaryColor)
            drawSurprisedMouth(centerX, centerY + faceRadius * 0.3f, faceRadius, primaryColor)
        }

        EmotionType.SAD, EmotionType.ANXIOUS -> {
            // 😢 难过 - 向下的眼睛 + 难过的嘴
            drawSadEyes(leftEyeX, eyeY, faceRadius, primaryColor)
            drawSadEyes(rightEyeX, eyeY, faceRadius, primaryColor)
            drawSadMouth(centerX, centerY + faceRadius * 0.25f, faceRadius, primaryColor)
        }

        EmotionType.CONFUSED, EmotionType.FRUSTRATED -> {
            // 😅 尴尬/困惑 - 不对称的眼睛 + 尴尬的嘴
            drawConfusedEyes(leftEyeX, rightEyeX, eyeY, faceRadius, primaryColor)
            drawEmbarrassedMouth(centerX, centerY + faceRadius * 0.2f, faceRadius, primaryColor)
        }

        else -> {
            // 默认中性表情
            drawNeutralEyes(leftEyeX, eyeY, faceRadius, primaryColor)
            drawNeutralEyes(rightEyeX, eyeY, faceRadius, primaryColor)
            drawNeutralMouth(centerX, centerY + faceRadius * 0.25f, faceRadius, primaryColor)
        }
    }
}

// ========== 眼睛绘制函数 ==========

private fun DrawScope.drawHappyEyes(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 弯弯的笑眼（向上的弧线）
    val path = Path().apply {
        moveTo(x - faceRadius * 0.1f, y)
        quadraticBezierTo(
            x, y - faceRadius * 0.08f,
            x + faceRadius * 0.1f, y
        )
    }
    drawPath(path, color, style = Stroke(width = faceRadius * 0.05f))
}

private fun DrawScope.drawSleepyEyes(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 半闭的眼睛（短横线）
    drawLine(
        color = color,
        start = Offset(x - faceRadius * 0.08f, y),
        end = Offset(x + faceRadius * 0.08f, y),
        strokeWidth = faceRadius * 0.04f
    )
}

private fun DrawScope.drawCuriousEyes(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 好奇的圆眼睛
    drawCircle(
        color = color,
        radius = faceRadius * 0.08f,
        center = Offset(x, y)
    )
    // 高光
    drawCircle(
        color = Color.White,
        radius = faceRadius * 0.03f,
        center = Offset(x - faceRadius * 0.03f, y - faceRadius * 0.03f)
    )
}

private fun DrawScope.drawSurprisedEyes(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 大大的惊讶眼睛
    drawCircle(
        color = color,
        radius = faceRadius * 0.1f,
        center = Offset(x, y)
    )
    // 高光
    drawCircle(
        color = Color.White,
        radius = faceRadius * 0.04f,
        center = Offset(x - faceRadius * 0.04f, y - faceRadius * 0.04f)
    )
}

private fun DrawScope.drawSadEyes(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 难过的向下弧线眼睛
    val path = Path().apply {
        moveTo(x - faceRadius * 0.1f, y - faceRadius * 0.05f)
        quadraticBezierTo(
            x, y + faceRadius * 0.03f,
            x + faceRadius * 0.1f, y - faceRadius * 0.05f
        )
    }
    drawPath(path, color, style = Stroke(width = faceRadius * 0.05f))
}

private fun DrawScope.drawConfusedEyes(
    leftX: Float,
    rightX: Float,
    y: Float,
    faceRadius: Float,
    color: Color
) {
    // 左眼正常
    drawCircle(
        color = color,
        radius = faceRadius * 0.06f,
        center = Offset(leftX, y)
    )
    // 右眼半闭（表示困惑）
    drawLine(
        color = color,
        start = Offset(rightX - faceRadius * 0.08f, y),
        end = Offset(rightX + faceRadius * 0.08f, y),
        strokeWidth = faceRadius * 0.04f
    )
}

private fun DrawScope.drawNeutralEyes(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 中性圆眼
    drawCircle(
        color = color,
        radius = faceRadius * 0.06f,
        center = Offset(x, y)
    )
}

// ========== 嘴巴绘制函数 ==========

private fun DrawScope.drawSmile(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 微笑的弧线
    val path = Path().apply {
        moveTo(x - faceRadius * 0.25f, y)
        quadraticBezierTo(
            x, y + faceRadius * 0.15f,
            x + faceRadius * 0.25f, y
        )
    }
    drawPath(path, color, style = Stroke(width = faceRadius * 0.05f))
}

private fun DrawScope.drawNeutralMouth(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 平直的嘴
    drawLine(
        color = color,
        start = Offset(x - faceRadius * 0.2f, y),
        end = Offset(x + faceRadius * 0.2f, y),
        strokeWidth = faceRadius * 0.04f
    )
}

private fun DrawScope.drawThinkingMouth(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 思考的小嘴（偏向一侧）
    val path = Path().apply {
        moveTo(x - faceRadius * 0.1f, y)
        lineTo(x + faceRadius * 0.15f, y - faceRadius * 0.05f)
    }
    drawPath(path, color, style = Stroke(width = faceRadius * 0.04f))
}

private fun DrawScope.drawSurprisedMouth(x: Float, y: Float, faceRadius: Float, color: Color) {
    // O型嘴
    drawCircle(
        color = color,
        radius = faceRadius * 0.12f,
        center = Offset(x, y),
        style = Stroke(width = faceRadius * 0.05f)
    )
}

private fun DrawScope.drawSadMouth(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 难过的向下弧线
    val path = Path().apply {
        moveTo(x - faceRadius * 0.2f, y)
        quadraticBezierTo(
            x, y - faceRadius * 0.1f,
            x + faceRadius * 0.2f, y
        )
    }
    drawPath(path, color, style = Stroke(width = faceRadius * 0.05f))
}

private fun DrawScope.drawEmbarrassedMouth(x: Float, y: Float, faceRadius: Float, color: Color) {
    // 尴尬的波浪嘴
    val path = Path().apply {
        moveTo(x - faceRadius * 0.2f, y)
        quadraticBezierTo(
            x - faceRadius * 0.1f, y + faceRadius * 0.05f,
            x, y
        )
        quadraticBezierTo(
            x + faceRadius * 0.1f, y - faceRadius * 0.05f,
            x + faceRadius * 0.2f, y
        )
    }
    drawPath(path, color, style = Stroke(width = faceRadius * 0.04f))
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun XiaoguangAvatarPreview() {
    XiaoguangTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 不同情绪的头像
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XiaoguangAvatar(
                    emotion = EmotionType.HAPPY,
                    size = XiaoguangDesignSystem.AvatarSize.md
                )
                XiaoguangAvatar(
                    emotion = EmotionType.CALM,
                    size = XiaoguangDesignSystem.AvatarSize.md
                )
                XiaoguangAvatar(
                    emotion = EmotionType.CURIOUS,
                    size = XiaoguangDesignSystem.AvatarSize.md
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                XiaoguangAvatar(
                    emotion = EmotionType.SURPRISED,
                    size = XiaoguangDesignSystem.AvatarSize.md
                )
                XiaoguangAvatar(
                    emotion = EmotionType.SAD,
                    size = XiaoguangDesignSystem.AvatarSize.md
                )
                XiaoguangAvatar(
                    emotion = EmotionType.CONFUSED,
                    size = XiaoguangDesignSystem.AvatarSize.md
                )
            }

            // 带脉冲的大头像
            XiaoguangAvatar(
                emotion = EmotionType.HAPPY,
                size = XiaoguangDesignSystem.AvatarSize.xl,
                showPulse = true
            )
        }
    }
}
