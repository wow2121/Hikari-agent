package com.xiaoguang.assistant.presentation.ui.debug

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.presentation.ui.conversation.ConversationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据管理界面
 *
 * 功能：
 * - 查看数据库统计
 * - 清除各类数据
 * - 数据导入/导出
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementScreen(
    dataManagementViewModel: DataManagementViewModel = hiltViewModel(),
    conversationViewModel: ConversationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uiState by dataManagementViewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var clearTarget by remember { mutableStateOf("") }

    // 导出文件选择器
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                val result = dataManagementViewModel.exportDataAsJson()
                if (result.isSuccess) {
                    val jsonData = result.getOrNull() ?: ""
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonData.toByteArray())
                        }
                        Toast.makeText(context, "数据导出成功!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "文件写入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "导出失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val jsonData = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.readBytes().toString(Charsets.UTF_8)
                    } ?: ""

                    val result = dataManagementViewModel.importDataFromJson(jsonData)
                    if (result.isSuccess) {
                        Toast.makeText(context, result.getOrNull() ?: "导入成功!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "导入失败: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primary
                                )
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
                        text = "数据管理",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 数据统计卡片
            item {
                DataStatsCard(uiState = uiState)
            }

            // 数据清除操作
            item {
                DataActionSection(
                    title = "数据清除",
                    icon = Icons.Default.Delete,
                    actions = listOf(
                        DataAction(
                            title = "清除对话历史",
                            description = "删除所有对话记录",
                            icon = Icons.Default.Chat,
                            color = MaterialTheme.colorScheme.error,
                            onClick = {
                                clearTarget = "conversation"
                                showClearDialog = true
                            }
                        ),
                        DataAction(
                            title = "清除声纹数据",
                            description = "删除所有声纹档案（不含主人）",
                            icon = Icons.Default.GraphicEq,
                            color = MaterialTheme.colorScheme.error,
                            onClick = {
                                clearTarget = "voiceprint"
                                showClearDialog = true
                            }
                        ),
                        DataAction(
                            title = "清除记忆数据",
                            description = "删除所有长期记忆",
                            icon = Icons.Default.Psychology,
                            color = MaterialTheme.colorScheme.error,
                            onClick = {
                                clearTarget = "memory"
                                showClearDialog = true
                            }
                        )
                    )
                )
            }

            // 数据导入/导出
            item {
                DataActionSection(
                    title = "数据备份",
                    icon = Icons.Default.Backup,
                    actions = listOf(
                        DataAction(
                            title = "导出所有数据",
                            description = "导出对话、声纹、记忆等数据",
                            icon = Icons.Default.UploadFile,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                // 生成文件名: xiaoguang_backup_20250117_143025.json
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    .format(Date())
                                val fileName = "xiaoguang_backup_$timestamp.json"
                                exportLauncher.launch(fileName)
                            }
                        ),
                        DataAction(
                            title = "导入数据",
                            description = "从备份文件恢复数据",
                            icon = Icons.Default.Download,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                importLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    )
                )
            }

            // 危险操作
            item {
                DangerZoneCard(
                    onResetAll = {
                        clearTarget = "all"
                        showClearDialog = true
                    }
                )
            }
        }
    }

    // 确认对话框
    if (showClearDialog) {
        ClearConfirmationDialog(
            target = clearTarget,
            onConfirm = {
                scope.launch {
                    when (clearTarget) {
                        "conversation" -> {
                            conversationViewModel.clearHistory()
                            dataManagementViewModel.loadStatistics()
                        }
                        "voiceprint" -> {
                            dataManagementViewModel.clearVoiceprints()
                        }
                        "memory" -> {
                            dataManagementViewModel.clearMemories()
                        }
                        "all" -> {
                            // 清除所有数据
                            conversationViewModel.clearHistory()
                            dataManagementViewModel.clearAllData()
                        }
                    }
                }
                showClearDialog = false
            },
            onDismiss = {
                showClearDialog = false
            }
        )
    }
}

/**
 * 数据统计卡片
 */
@Composable
private fun DataStatsCard(uiState: DataManagementUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "数据统计",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("对话记录", "${uiState.conversationCount} 条")
                    StatItem("声纹档案", "${uiState.voiceprintCount} 个")
                    StatItem("长期记忆", "${uiState.memoryCount} 条")
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "数据库大小",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.2f MB".format(uiState.databaseSizeMB),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 数据操作分组
 */
@Composable
private fun DataActionSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actions: List<DataAction>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
                DataActionItem(action)
            }
        }
    }
}

/**
 * 数据操作项
 */
@Composable
private fun DataActionItem(action: DataAction) {
    Surface(
        onClick = action.onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = action.color,
                modifier = Modifier.size(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 危险区域卡片
 */
@Composable
private fun DangerZoneCard(onResetAll: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "危险操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedButton(
                onClick = onResetAll,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("清除所有数据并重置")
            }

            Text(
                text = "⚠️ 此操作将删除所有数据，包括对话、声纹、记忆等，且无法恢复！",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 确认对话框
 */
@Composable
private fun ClearConfirmationDialog(
    target: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message) = when (target) {
        "conversation" -> "清除对话历史" to "确定要删除所有对话记录吗？此操作无法撤销。"
        "voiceprint" -> "清除声纹数据" to "确定要删除所有声纹档案（除主人外）吗？此操作无法撤销。"
        "memory" -> "清除记忆数据" to "确定要删除所有长期记忆吗？此操作无法撤销。"
        "all" -> "清除所有数据" to "⚠️ 确定要删除所有数据并重置吗？这将删除对话、声纹、记忆等所有内容，且无法恢复！"
        else -> "确认操作" to "确定要执行此操作吗？"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 数据操作数据模型
 */
private data class DataAction(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val onClick: () -> Unit
)
