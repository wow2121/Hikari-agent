package com.xiaoguang.assistant.presentation.screens.flow

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.domain.state.*
import com.xiaoguang.assistant.presentation.components.FlowImpulseIndicator
import com.xiaoguang.assistant.presentation.components.XiaoguangAvatar
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme

/**
 * 心流状态页面
 *
 * 用户友好地展示小光的思维流程：
 * - 四层架构可视化（感知 → 思考 → 决策 → 行动）
 * - 当前心流阶段
 * - 冲动强度
 * - 当前想法
 * - 实时状态
 */
@Composable
fun FlowStatusScreen(
    viewModel: FlowStatusViewModel = hiltViewModel()
) {
    val coreState by viewModel.uiState.collectAsState()
    FlowStatusContent(coreState = coreState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowStatusContent(
    coreState: XiaoguangCoreState?
) {
    val emotionColors = LocalEmotionColors.current
    val flowState = coreState?.flow ?: FlowState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("心流状态") }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(XiaoguangDesignSystem.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.lg)
        ) {
            // 小光头像 + 运行状态
            item {
                FlowHeader(
                    isRunning = flowState.isRunning,
                    currentPhase = flowState.currentPhase
                )
            }

            // 当前想法
            if (flowState.currentThought != null) {
                item {
                    CurrentThoughtCard(thought = flowState.currentThought)
                }
            }

            // 心流冲动
            item {
                FlowImpulseCard(flowState = flowState)
            }

            // 四层架构可视化
            item {
                FourLayerArchitecture(currentPhase = flowState.currentPhase)
            }

            // 状态详情
            item {
                FlowStatusDetails(flowState = flowState)
            }
        }
    }
}

/**
 * 头部：小光头像 + 运行状态
 */
@Composable
private fun FlowHeader(
    isRunning: Boolean,
    currentPhase: FlowPhase
) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = emotionColors.background
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            // 头像
            XiaoguangAvatar(
                emotion = EmotionType.CURIOUS,
                size = XiaoguangDesignSystem.AvatarSize.xl,
                showPulse = isRunning
            )

            // 状态
            Row(
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(isActive = isRunning)
                Text(
                    text = if (isRunning) "心流运行中" else "心流已暂停",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRunning) emotionColors.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 当前阶段
            Text(
                text = getPhaseDescription(currentPhase),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 状态指示点
 */
@Composable
private fun StatusDot(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .alpha(if (isActive) alpha else 1f)
            .background(
                color = if (isActive) XiaoguangDesignSystem.CoreColors.success
                else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape
            )
    )
}

/**
 * 当前想法卡片
 */
@Composable
private fun CurrentThoughtCard(thought: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = XiaoguangPhrases.Flow.THINKING,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xxs))
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
private fun FlowImpulseCard(flowState: FlowState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
        ) {
            Text(
                text = "发言冲动",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowImpulseIndicator(
                impulse = flowState.impulse,
                showLabel = true
            )

            if (flowState.wantsToSpeak) {
                Text(
                    text = XiaoguangPhrases.Flow.WANT_TO_SPEAK,
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoguangDesignSystem.CoreColors.warning,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 四层架构可视化
 */
@Composable
private fun FourLayerArchitecture(currentPhase: FlowPhase) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            Text(
                text = "思维流程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // 四层
            FlowLayer(
                icon = Icons.Default.Visibility,
                title = "感知层",
                description = "观察环境和用户状态",
                isActive = currentPhase == FlowPhase.PERCEIVING
            )

            FlowLayer(
                icon = Icons.Default.Psychology,
                title = "思考层",
                description = "分析情况，生成想法",
                isActive = currentPhase == FlowPhase.THINKING
            )

            FlowLayer(
                icon = Icons.Default.CheckCircle,
                title = "决策层",
                description = "决定是否发言",
                isActive = currentPhase == FlowPhase.DECIDING
            )

            FlowLayer(
                icon = Icons.Default.Send,
                title = "行动层",
                description = "执行发言或其他行动",
                isActive = currentPhase == FlowPhase.ACTING
            )
        }
    }
}

/**
 * 心流层级
 */
@Composable
private fun FlowLayer(
    icon: ImageVector,
    title: String,
    description: String,
    isActive: Boolean
) {
    val emotionColors = LocalEmotionColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Surface(
            shape = CircleShape,
            color = if (isActive) emotionColors.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.sm)
            )
        }

        // 文字
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = if (isActive) emotionColors.primary
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 活跃指示
        if (isActive) {
            StatusDot(isActive = true)
        }
    }
}

/**
 * 状态详情
 */
@Composable
private fun FlowStatusDetails(flowState: FlowState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs)
        ) {
            Text(
                text = "详细信息",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DetailRow("循环间隔", "${flowState.flowDelay / 1000}秒")
            DetailRow("上次处理", formatTimestamp(flowState.lastFlowTimestamp))
        }
    }
}

/**
 * 详情行
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 获取阶段描述
 */
private fun getPhaseDescription(phase: FlowPhase): String {
    return when (phase) {
        FlowPhase.PERCEIVING -> "正在${XiaoguangPhrases.Flow.PERCEIVING}"
        FlowPhase.THINKING -> XiaoguangPhrases.Flow.ANALYZING
        FlowPhase.DECIDING -> XiaoguangPhrases.Flow.DECIDING
        FlowPhase.ACTING -> XiaoguangPhrases.Flow.ACTING
        FlowPhase.IDLE -> "空闲中"
    }
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "从未"
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 1000 -> "刚刚"
        diff < 60_000 -> "${diff / 1000}秒前"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        else -> "${diff / 3600_000}小时前"
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun FlowStatusScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.CURIOUS) {
        val mockState = XiaoguangCoreState(
            flow = FlowState(
                isRunning = true,
                impulse = 0.7f,
                currentThought = "主人看起来心情不错，要不要主动打个招呼呢？",
                wantsToSpeak = true,
                currentPhase = FlowPhase.THINKING,
                flowDelay = 3000,
                lastFlowTimestamp = System.currentTimeMillis() - 5000
            )
        )

        FlowStatusContent(coreState = mockState)
    }
}
