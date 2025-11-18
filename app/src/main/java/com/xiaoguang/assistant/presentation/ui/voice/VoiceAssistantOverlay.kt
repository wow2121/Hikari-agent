package com.xiaoguang.assistant.presentation.ui.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

/**
 * 全屏语音助手界面
 * 类似Siri/小爱同学的沉浸式体验
 */
@Composable
fun VoiceAssistantOverlay(
    isListening: Boolean,
    recognizedText: String,
    aiResponse: String,
    audioLevel: Float = 0f,  // 0-1的音量级别
    onDismiss: () -> Unit
) {
    // 渐变背景颜色
    val gradientColors = listOf(
        Color(0xFF1A1A2E),  // 深蓝黑
        Color(0xFF16213E),  // 深蓝
        Color(0xFF0F3460)   // 中蓝
    )

    // 拖动偏移量
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(gradientColors)
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            // 向下拖动超过200dp关闭
                            if (offsetY > 200) {
                                onDismiss()
                            }
                            offsetY = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            // 只允许向下拖动
                            offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                        }
                    )
                }
        ) {
            // 关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color.White.copy(0.7f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 语音波形动画
                VoiceWaveAnimation(
                    isActive = isListening,
                    audioLevel = audioLevel,
                    modifier = Modifier.size(200.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 状态提示
                Text(
                    text = if (isListening) "我在听..." else "已停止",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 识别的文字
                if (recognizedText.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(0.1f)
                        )
                    ) {
                        Text(
                            text = recognizedText,
                            color = Color.White,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(20.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // AI回复
                if (aiResponse.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF4ECDC4).copy(0.2f)
                        )
                    ) {
                        Text(
                            text = aiResponse,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 拖动提示
                Text(
                    text = "向下滑动关闭",
                    color = Color.White.copy(0.5f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 语音波形动画组件
 */
@Composable
fun VoiceWaveAnimation(
    isActive: Boolean,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    // 脉冲动画
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 根据音量调整缩放
    val dynamicScale = 1f + (audioLevel * 0.5f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 外圈波纹（3层）
        repeat(3) { index ->
            val delay = index * 300
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing, delayMillis = delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing, delayMillis = delay),
                    repeatMode = RepeatMode.Restart
                ),
                label = "alpha_$index"
            )

            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Color(0xFF4ECDC4).copy(alpha * 0.3f))
                )
            }
        }

        // 中心圆
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale * dynamicScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4ECDC4),
                            Color(0xFF44A08D)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // 小光图标或文字
            Text(
                text = "小光",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 语音助手ViewModel状态
 */
data class VoiceAssistantState(
    val isVisible: Boolean = false,
    val isListening: Boolean = false,
    val recognizedText: String = "",
    val aiResponse: String = "",
    val audioLevel: Float = 0f
)
