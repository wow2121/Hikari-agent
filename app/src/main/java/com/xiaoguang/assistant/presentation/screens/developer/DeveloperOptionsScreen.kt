package com.xiaoguang.assistant.presentation.screens.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.ui.conversation.ConversationViewModel
import com.xiaoguang.assistant.presentation.ui.debug.DataManagementViewModel
import kotlinx.coroutines.launch
import com.xiaoguang.assistant.presentation.components.XiaoguangAvatar
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.navigation.DeveloperScreen

/**
 * 调试工具项
 */
data class DebugToolItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit
)

/**
 * 开发者选项页面
 *
 * 整合所有调试和开发工具
 */
@Composable
fun DeveloperOptionsScreen(
    dataManagementViewModel: DataManagementViewModel = hiltViewModel(),
    conversationViewModel: ConversationViewModel = hiltViewModel(),
    onNavigateToFlowDebug: () -> Unit = {},
    onNavigateToFlowSettings: () -> Unit = {},
    onNavigateToNetworkDebug: () -> Unit = {},
    onNavigateToDataManagement: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit = {}
) {
    DeveloperOptionsContent(
        dataManagementViewModel = dataManagementViewModel,
        conversationViewModel = conversationViewModel,
        onNavigateToFlowDebug = onNavigateToFlowDebug,
        onNavigateToFlowSettings = onNavigateToFlowSettings,
        onNavigateToNetworkDebug = onNavigateToNetworkDebug,
        onNavigateToDataManagement = onNavigateToDataManagement,
        onNavigateToLogViewer = onNavigateToLogViewer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperOptionsContent(
    dataManagementViewModel: DataManagementViewModel,
    conversationViewModel: ConversationViewModel,
    onNavigateToFlowDebug: () -> Unit,
    onNavigateToFlowSettings: () -> Unit,
    onNavigateToNetworkDebug: () -> Unit,
    onNavigateToDataManagement: () -> Unit,
    onNavigateToLogViewer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(XiaoguangPhrases.Developer.DEBUG_MODE) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
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
            // 警告卡片
            item {
                WarningCard()
            }

            // 调试工具分组
            item {
                Text(
                    text = "调试工具",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.xs)
                )
            }

            val debugTools = listOf(
                DebugToolItem(
                    icon = Icons.Default.BugReport,
                    title = XiaoguangPhrases.Developer.FLOW_DEBUG,
                    description = "查看心流运行详情、日志",
                    onClick = onNavigateToFlowDebug
                ),
                DebugToolItem(
                    icon = Icons.Default.Settings,
                    title = "心流设置",
                    description = "调整心流参数、阈值",
                    onClick = onNavigateToFlowSettings
                ),
                DebugToolItem(
                    icon = Icons.Default.NetworkCheck,
                    title = "网络调试",
                    description = "API调用、网络请求监控",
                    onClick = onNavigateToNetworkDebug
                ),
                DebugToolItem(
                    icon = Icons.Default.Article,
                    title = XiaoguangPhrases.Developer.VIEW_LOGS,
                    description = "查看应用日志、错误日志",
                    onClick = onNavigateToLogViewer
                )
            )

            items(debugTools) { item ->
                DebugToolCard(item = item)
            }

            // 数据管理分组
            item {
                Text(
                    text = "数据管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.xs)
                )
            }

            val dataTools = listOf(
                DebugToolItem(
                    icon = Icons.Default.Storage,
                    title = "数据管理",
                    description = "查看、导出、清空数据",
                    onClick = onNavigateToDataManagement
                ),
                DebugToolItem(
                    icon = Icons.Default.CloudUpload,
                    title = XiaoguangPhrases.Developer.EXPORT_DATA,
                    description = "导出所有数据到文件",
                    onClick = onNavigateToDataManagement  // 导航到数据管理页面
                ),
                DebugToolItem(
                    icon = Icons.Default.CloudDownload,
                    title = XiaoguangPhrases.Developer.IMPORT_DATA,
                    description = "从文件导入数据",
                    onClick = onNavigateToDataManagement  // 导航到数据管理页面
                )
            )

            items(dataTools) { item ->
                DebugToolCard(item = item)
            }

            // 危险操作
            item {
                DangerZoneCard(
                    onClearAllData = {
                        scope.launch {
                            conversationViewModel.clearHistory()
                            dataManagementViewModel.clearAllData()
                        }
                    }
                )
            }
        }
    }
}

/**
 * 警告卡片
 */
@Composable
private fun WarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "开发者模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xxs))
                Text(
                    text = XiaoguangPhrases.Developer.DEVELOPER_WARNING,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * 调试工具卡片
 */
@Composable
private fun DebugToolCard(item: DebugToolItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.xs
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
            )

            // 文字
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 箭头
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm)
            )
        }
    }
}

/**
 * 危险操作区域
 */
@Composable
private fun DangerZoneCard(onClearAllData: () -> Unit) {
    var showClearDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm)
                )
                Text(
                    text = "危险操作",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = XiaoguangPhrases.Developer.DATA_LOSS_WARNING,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(XiaoguangDesignSystem.Spacing.xs))
                Text(XiaoguangPhrases.Developer.CLEAR_DATA)
            }
        }
    }

    // 清空数据确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("确认清空所有数据？") },
            text = { Text("此操作将删除所有记忆、对话历史、人物信息等数据，且无法恢复！") },
            confirmButton = {
                Button(
                    onClick = {
                        onClearAllData()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun DeveloperOptionsScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.CURIOUS) {
        DeveloperOptionsScreen()
    }
}
