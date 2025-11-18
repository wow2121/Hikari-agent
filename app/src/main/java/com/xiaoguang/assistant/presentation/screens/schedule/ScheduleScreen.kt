package com.xiaoguang.assistant.presentation.screens.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.components.*
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import java.text.SimpleDateFormat
import java.util.*

/**
 * 日程项目
 */
data class ScheduleItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val dueTime: Long,
    val isCompleted: Boolean = false,
    val priority: SchedulePriority = SchedulePriority.NORMAL,
    val type: ScheduleType = ScheduleType.TODO
)

/**
 * 日程类型
 */
enum class ScheduleType {
    TODO,       // 待办事项
    REMINDER,   // 提醒
    EVENT       // 事件
}

/**
 * 优先级
 */
enum class SchedulePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * 日程页面
 *
 * 整合待办事项和提醒功能
 */
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // 显示错误提示
    if (uiState.error != null) {
        LaunchedEffect(uiState.error) {
            // 错误会在 UI 中显示，这里可以添加 Snackbar 等提示
            viewModel.clearError()
        }
    }

    ScheduleContent(
        scheduleItems = uiState.schedules,
        isLoading = uiState.isLoading,
        onItemClick = { /* TODO: 导航到详情页 */ },
        onItemToggle = { id, isCompleted ->
            viewModel.toggleComplete(id, isCompleted)
        },
        onAddItem = { /* TODO: 显示添加对话框 */ }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleContent(
    scheduleItems: List<ScheduleItem>,
    isLoading: Boolean = false,
    onItemClick: (String) -> Unit,
    onItemToggle: (String, Boolean) -> Unit,
    onAddItem: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日程") }
            )
        },
        floatingActionButton = {
            XiaoguangFab(
                icon = Icons.Default.Add,
                onClick = onAddItem,
                contentDescription = "添加日程"
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 加载状态
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 今日概览卡片
                TodaySummaryCard(
                    totalItems = scheduleItems.size,
                    completedItems = scheduleItems.count { it.isCompleted },
                    upcomingItems = scheduleItems.count { !it.isCompleted && it.dueTime > System.currentTimeMillis() }
                )

                // 日程列表
                if (scheduleItems.isEmpty()) {
                    EmptyState()
                } else {
                    ScheduleList(
                        items = scheduleItems,
                        onItemClick = onItemClick,
                        onItemToggle = onItemToggle
                    )
                }
            }
        }
    }
}

/**
 * 今日概览卡片
 */
@Composable
private fun TodaySummaryCard(
    totalItems: Int,
    completedItems: Int,
    upcomingItems: Int
) {
    val emotionColors = LocalEmotionColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(XiaoguangDesignSystem.Spacing.md),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.lg),
        color = emotionColors.background
    ) {
        Row(
            modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.lg)
        ) {
            // 总数
            SummaryItem(
                icon = Icons.Default.CalendarToday,
                label = "总数",
                value = totalItems.toString(),
                color = emotionColors.primary
            )

            // 已完成
            SummaryItem(
                icon = Icons.Default.CheckCircle,
                label = "已完成",
                value = completedItems.toString(),
                color = XiaoguangDesignSystem.CoreColors.success
            )

            // 待处理
            SummaryItem(
                icon = Icons.Default.Pending,
                label = "待处理",
                value = upcomingItems.toString(),
                color = XiaoguangDesignSystem.CoreColors.warning
            )
        }
    }
}

/**
 * 概览项
 */
@Composable
private fun RowScope.SummaryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 日程列表
 */
@Composable
private fun ScheduleList(
    items: List<ScheduleItem>,
    onItemClick: (String) -> Unit,
    onItemToggle: (String, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(XiaoguangDesignSystem.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
    ) {
        items(
            items = items,
            key = { it.id }
        ) { item ->
            ScheduleItemCard(
                item = item,
                onClick = { onItemClick(item.id) },
                onToggle = { onItemToggle(item.id, !item.isCompleted) }
            )
        }
    }
}

/**
 * 日程项卡片
 */
@Composable
private fun ScheduleItemCard(
    item: ScheduleItem,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current
    val priorityColor = when (item.priority) {
        SchedulePriority.URGENT -> XiaoguangDesignSystem.CoreColors.error
        SchedulePriority.HIGH -> XiaoguangDesignSystem.CoreColors.warning
        SchedulePriority.NORMAL -> emotionColors.primary
        SchedulePriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val dueTimeText = remember(item.dueTime) {
        val now = System.currentTimeMillis()
        val diff = item.dueTime - now

        when {
            diff < 0 -> "已过期"
            diff < 3600_000 -> "${diff / 60_000}分钟后"
            diff < 86400_000 -> "${diff / 3600_000}小时后"
            else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.dueTime))
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.isCompleted)
                XiaoguangDesignSystem.Elevation.none
            else
                XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成复选框
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = { onToggle() }
            )

            // 内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs)
            ) {
                // 标题
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.isCompleted)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isCompleted)
                        TextDecoration.LineThrough
                    else
                        null
                )

                // 描述
                if (item.description != null) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 时间 + 优先级
                Row(
                    horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(XiaoguangDesignSystem.IconSize.xs),
                        tint = priorityColor
                    )
                    Text(
                        text = dueTimeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor,
                        fontWeight = FontWeight.Medium
                    )

                    // 类型标签
                    Surface(
                        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.xs),
                        color = emotionColors.background
                    ) {
                        Text(
                            text = getTypeLabel(item.type),
                            style = MaterialTheme.typography.labelSmall,
                            color = emotionColors.primary,
                            modifier = Modifier.padding(
                                horizontal = XiaoguangDesignSystem.Spacing.xs,
                                vertical = 2.dp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            XiaoguangAvatar(
                emotion = EmotionType.CALM,
                size = XiaoguangDesignSystem.AvatarSize.xl
            )
            Text(
                text = "今天没有安排哦~",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "好好休息吧！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 获取类型标签
 */
private fun getTypeLabel(type: ScheduleType): String {
    return when (type) {
        ScheduleType.TODO -> "待办"
        ScheduleType.REMINDER -> "提醒"
        ScheduleType.EVENT -> "事件"
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun ScheduleScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        ScheduleScreen()
    }
}
