package com.xiaoguang.assistant.presentation.ui.debug

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
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * 网络调试界面
 *
 * 功能：
 * - 查看网络请求日志
 * - 显示请求/响应详情
 * - 测试API连接
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDebugScreen(
    viewModel: NetworkDebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // 转换为旧的NetworkLog格式（临时兼容）
    val networkLogs = remember(uiState.requests) {
        uiState.requests.map { req ->
            NetworkLog(
                id = req.id.toLongOrNull() ?: 0L,
                method = req.method,
                url = req.url,
                status = req.statusCode,
                duration = req.duration,
                timestamp = req.timestamp,
                requestSize = req.requestBody?.length?.toLong() ?: 0,
                responseSize = req.responseBody?.length?.toLong() ?: 0
            )
        }
    }

    // 原有的模拟数据逻辑移除，使用ViewModel数据
    /*
    val networkLogs = remember {
        listOf(
            NetworkLog(
                id = 1,
                method = "POST",
                url = "/api/chat/completions",
                status = 200,
                duration = 1234,
                timestamp = System.currentTimeMillis() - 60000,
                requestSize = 512,
                responseSize = 2048
            ),
            NetworkLog(
                id = 2,
                method = "POST",
                url = "/api/embeddings",
                status = 200,
                duration = 456,
                timestamp = System.currentTimeMillis() - 120000,
                requestSize = 256,
                responseSize = 128
            ),
            NetworkLog(
                id = 3,
                method = "GET",
                url = "/api/models",
                status = 200,
                duration = 89,
                timestamp = System.currentTimeMillis() - 300000,
                requestSize = 0,
                responseSize = 1024
            )
        )
    }
    */

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
                        text = "网络调试",
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
            // 统计信息卡片
            item {
                NetworkStatsCard(logs = networkLogs)
            }

            // 请求日志列表
            item {
                Text(
                    text = "请求日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(networkLogs) { log ->
                NetworkLogItem(log = log)
            }
        }
    }
}

/**
 * 网络统计卡片
 */
@Composable
private fun NetworkStatsCard(logs: List<NetworkLog>) {
    val totalRequests = logs.size
    val successRate = logs.count { it.status in 200..299 }.toFloat() / totalRequests * 100
    val avgDuration = logs.map { it.duration }.average()

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
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "网络统计",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("总请求数", "$totalRequests 次")
                StatItem("成功率", String.format("%.1f%%", successRate))
                StatItem("平均耗时", "${avgDuration.toInt()}ms")
            }
        }
    }
}

/**
 * 单个网络日志项
 */
@Composable
private fun NetworkLogItem(log: NetworkLog) {
    val statusColor = when (log.status) {
        in 200..299 -> Color(0xFF4CAF50)
        in 400..499 -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：方法、URL、状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 方法标签
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(0.2f)
                    ) {
                        Text(
                            text = log.method,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // URL
                    Text(
                        text = log.url,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // 状态码
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(0.2f)
                ) {
                    Text(
                        text = "${log.status}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 第二行：耗时、大小、时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoChip(Icons.Default.Timer, "${log.duration}ms")
                    InfoChip(Icons.Default.UploadFile, formatBytes(log.requestSize))
                    InfoChip(Icons.Default.Download, formatBytes(log.responseSize))
                }

                Text(
                    text = formatTime(log.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
 * 信息芯片
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
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 格式化字节数
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fMB", bytes / 1024f / 1024f)
    }
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
        else -> {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

/**
 * 网络日志数据模型
 */
private data class NetworkLog(
    val id: Long,
    val method: String,
    val url: String,
    val status: Int,
    val duration: Long,
    val timestamp: Long,
    val requestSize: Long,
    val responseSize: Long
)
