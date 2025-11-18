package com.xiaoguang.assistant.presentation.ui.voiceprint

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * 声纹库管理界面
 *
 * 功能：
 * - 查看所有已注册的声纹
 * - 显示声纹质量评级
 * - 删除声纹（主人声纹不可删除）
 * - 显示主人标记
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceprintManagementScreen(
    viewModel: VoiceprintManagementViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // 删除确认对话框
    if (uiState.deleteConfirmation != null) {
        DeleteConfirmationDialog(
            voiceprintName = uiState.deleteConfirmation!!.voiceprintName,
            isMaster = uiState.deleteConfirmation!!.isMaster,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
                            )
                        )
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "声纹库管理",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.isLoading) {
                // 加载中
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.voiceprints.isEmpty()) {
                // 空状态
                EmptyStateView(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // 声纹列表
                VoiceprintList(
                    voiceprints = uiState.voiceprints,
                    onDeleteClick = { voiceprint ->
                        viewModel.showDeleteConfirmation(voiceprint)
                    }
                )
            }
        }
    }
}

/**
 * 声纹列表
 */
@Composable
private fun VoiceprintList(
    voiceprints: List<VoiceprintDisplayInfo>,
    onDeleteClick: (VoiceprintDisplayInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(voiceprints) { voiceprint ->
            VoiceprintItem(
                voiceprint = voiceprint,
                onDeleteClick = { onDeleteClick(voiceprint) }
            )
        }
    }
}

/**
 * 单个声纹卡片
 */
@Composable
private fun VoiceprintItem(
    voiceprint: VoiceprintDisplayInfo,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (voiceprint.isMaster) {
                MaterialTheme.colorScheme.primary.copy(0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：声纹信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 姓名 + 主人标记
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = voiceprint.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (voiceprint.isMaster) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (voiceprint.isMaster) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "主人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 质量指示器
                QualityIndicator(quality = voiceprint.quality)

                // 详细信息
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 样本数
                    InfoChip(
                        icon = Icons.Default.GraphicEq,
                        text = "${voiceprint.sampleCount} 样本"
                    )

                    // 更新时间
                    InfoChip(
                        icon = Icons.Default.Schedule,
                        text = formatTime(voiceprint.lastRecognized)
                    )
                }
            }

            // 右侧：删除按钮
            if (!voiceprint.isMaster) {
                IconButton(
                    onClick = onDeleteClick
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // 主人声纹显示锁定图标（不可删除）
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "已锁定",
                    tint = MaterialTheme.colorScheme.primary.copy(0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * 质量指示器
 */
@Composable
private fun QualityIndicator(quality: VoiceprintQuality) {
    val (icon, text, color) = when (quality) {
        VoiceprintQuality.EXCELLENT -> Triple(
            Icons.Default.Stars,
            "优秀",
            Color(0xFF4CAF50)
        )
        VoiceprintQuality.GOOD -> Triple(
            Icons.Default.CheckCircle,
            "良好",
            Color(0xFF2196F3)
        )
        VoiceprintQuality.FAIR -> Triple(
            Icons.Default.Circle,
            "一般",
            Color(0xFFFFC107)
        )
        VoiceprintQuality.POOR -> Triple(
            Icons.Default.Warning,
            "较差",
            Color(0xFFFF5722)
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "质量: $text",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 信息标签
 */
@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 空状态视图
 */
@Composable
private fun EmptyStateView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "暂无声纹",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "在聊天中发送语音消息后\n系统会自动建立声纹档案",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
        )
    }
}

/**
 * 删除确认对话框
 */
@Composable
private fun DeleteConfirmationDialog(
    voiceprintName: String,
    isMaster: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isMaster) Icons.Default.Lock else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isMaster) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (isMaster) "无法删除" else "确认删除",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = if (isMaster) {
                    "主人声纹已锁定，无法删除"
                } else {
                    "确定要删除 \"$voiceprintName\" 的声纹吗？\n\n删除后将无法恢复，该用户需要重新录制声纹。"
                }
            )
        },
        confirmButton = {
            if (!isMaster) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isMaster) "知道了" else "取消")
            }
        }
    )
}

/**
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 86400_000 -> "${diff / 3600_000} 小时前"
        diff < 2592000_000 -> "${diff / 86400_000} 天前"
        else -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
