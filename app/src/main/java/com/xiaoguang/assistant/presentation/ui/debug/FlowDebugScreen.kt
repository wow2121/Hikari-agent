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
import com.xiaoguang.assistant.domain.flow.model.DecisionRecord
import com.xiaoguang.assistant.domain.flow.model.InnerThought
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * 心流调试界面
 *
 * 功能：
 * - 显示心流系统实时状态
 * - 显示内在状态和冲动值
 * - 显示决策历史
 * - 显示内心想法
 * - 手动触发发言测试
 * - 调整心流配置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowDebugScreen(
    viewModel: FlowDebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val config by viewModel.config.collectAsState()

    var showTriggerDialog by remember { mutableStateOf(false) }

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
                        text = "心流调试",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // 刷新按钮
                    IconButton(
                        onClick = { viewModel.refreshState() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showTriggerDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "手动触发发言",
                    tint = Color.White
                )
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
            // 系统状态卡片
            item {
                SystemStatusCard(
                    isRunning = isRunning,
                    cycleCount = uiState.cycleCount,
                    lastUpdateTime = uiState.lastUpdateTime
                )
            }

            // ✅ 环境监听卡片
            item {
                EnvironmentMonitorCard(
                    recentUtterances = uiState.recentUtterances,
                    currentSpeaker = uiState.currentSpeaker,
                    presentPeople = uiState.presentPeople,
                    audioLevel = uiState.audioLevel,
                    isVoiceActive = uiState.isVoiceActive
                )
            }

            // 内在状态卡片
            item {
                InternalStateCard(
                    impulse = uiState.currentImpulse,
                    stateDescription = uiState.getStateDescription(),
                    emotion = uiState.internalState?.currentEmotion?.displayName ?: "未知",
                    emotionIntensity = uiState.internalState?.emotionIntensity ?: 0f,
                    speakRatio = uiState.recentSpeakRatio,
                    ignoredCount = uiState.ignoredCount
                )
            }

            // 待处理想法
            item {
                ThoughtsCard(
                    thoughts = uiState.getPendingThoughts(),
                    thoughtsCount = uiState.pendingThoughtsCount
                )
            }

            // 决策历史
            item {
                DecisionHistoryCard(
                    decisions = uiState.getRecentDecisions(),
                    decisionsCount = uiState.recentDecisionsCount
                )
            }

            // 听觉诊断日志（新增）
            item {
                VoiceDiagnosticsCard()
            }

            // 配置调整
            item {
                ConfigCard(
                    talkativeLevel = config.talkativeLevel,
                    enableInnerThoughts = config.enableInnerThoughts,
                    enableCuriosity = config.enableCuriosity,
                    enableProactiveCare = config.enableProactiveCare,
                    onTalkativeLevelChange = { viewModel.adjustTalkativeLevel(it) },
                    onInnerThoughtsToggle = { viewModel.toggleInnerThoughts(it) },
                    onCuriosityToggle = { viewModel.toggleCuriosity(it) },
                    onProactiveCareToggle = { viewModel.toggleProactiveCare(it) }
                )
            }
        }
    }

    // 手动触发对话框
    if (showTriggerDialog) {
        ManualTriggerDialog(
            onDismiss = { showTriggerDialog = false },
            onTrigger = { message ->
                viewModel.triggerManualSpeak(message)
                showTriggerDialog = false
            }
        )
    }
}

/**
 * 系统状态卡片
 */
@Composable
private fun SystemStatusCard(
    isRunning: Boolean,
    cycleCount: Long,
    lastUpdateTime: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) Color(0xFF4CAF50).copy(0.1f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
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
                    imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "系统状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(label = "运行状态", value = if (isRunning) "运行中 ✓" else "已停止 ✗")
                InfoItem(label = "循环次数", value = "$cycleCount 次")
            }

            if (lastUpdateTime > 0) {
                Text(
                    text = "最后更新: ${formatTime(lastUpdateTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 内在状态卡片
 */
@Composable
private fun InternalStateCard(
    impulse: Float,
    stateDescription: String,
    emotion: String,
    emotionIntensity: Float,
    speakRatio: Float,
    ignoredCount: Int
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
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "内在状态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 冲动值
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("冲动值", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${(impulse * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = impulse,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = when {
                        impulse > 0.7f -> MaterialTheme.colorScheme.error
                        impulse > 0.4f -> Color(0xFFFFC107)
                        else -> Color(0xFF4CAF50)
                    }
                )
                Text(
                    stateDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider()

            // 情绪状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(label = "当前情绪", value = emotion)
                InfoItem(label = "情绪强度", value = "${(emotionIntensity * 100).toInt()}%")
            }

            // 互动统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoItem(label = "发言比例", value = "${(speakRatio * 100).toInt()}%")
                InfoItem(label = "被忽视次数", value = "$ignoredCount 次")
            }
        }
    }
}

/**
 * 待处理想法卡片
 */
@Composable
private fun ThoughtsCard(
    thoughts: List<InnerThought>,
    thoughtsCount: Int
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "内心想法",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.1f)
                ) {
                    Text(
                        text = "$thoughtsCount 个",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (thoughts.isEmpty()) {
                Text(
                    text = "暂无待处理想法",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                thoughts.forEach { thought ->
                    ThoughtItem(thought)
                }
            }
        }
    }
}

/**
 * 单个想法项
 */
@Composable
private fun ThoughtItem(thought: InnerThought) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 类型标签
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(0.2f)
            ) {
                Text(
                    text = thought.type.displayName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thought.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "紧急度: ${(thought.urgency * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (thought.urgency > 0.7f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 决策历史卡片
 */
@Composable
private fun DecisionHistoryCard(
    decisions: List<DecisionRecord>,
    decisionsCount: Int
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "决策历史",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(0.1f)
                ) {
                    Text(
                        text = "最近 $decisionsCount 条",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (decisions.isEmpty()) {
                Text(
                    text = "暂无决策记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                decisions.forEach { decision ->
                    DecisionItem(decision)
                }
            }
        }
    }
}

/**
 * 单个决策项
 */
@Composable
private fun DecisionItem(decision: DecisionRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (decision.shouldSpeak) Color(0xFF4CAF50).copy(0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = if (decision.shouldSpeak) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (decision.shouldSpeak) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (decision.shouldSpeak) "应该发言" else "保持安静",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = formatTime(decision.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = decision.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "置信度: ${(decision.confidence * 100).toInt()}% | 实际发言: ${if (decision.actuallySpoke) "是" else "否"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 配置调整卡片
 */
@Composable
private fun ConfigCard(
    talkativeLevel: Float,
    enableInnerThoughts: Boolean,
    enableCuriosity: Boolean,
    enableProactiveCare: Boolean,
    onTalkativeLevelChange: (Float) -> Unit,
    onInnerThoughtsToggle: (Boolean) -> Unit,
    onCuriosityToggle: (Boolean) -> Unit,
    onProactiveCareToggle: (Boolean) -> Unit
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "配置调整",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 话痨度
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("话痨度", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        String.format("%.1f", talkativeLevel),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = talkativeLevel,
                    onValueChange = onTalkativeLevelChange,
                    valueRange = 0.5f..1.5f,
                    steps = 9
                )
            }

            Divider()

            // 功能开关
            SwitchItem(
                title = "内心想法",
                checked = enableInnerThoughts,
                onCheckedChange = onInnerThoughtsToggle
            )

            SwitchItem(
                title = "好奇心",
                checked = enableCuriosity,
                onCheckedChange = onCuriosityToggle
            )

            SwitchItem(
                title = "主动关心",
                checked = enableProactiveCare,
                onCheckedChange = onProactiveCareToggle
            )
        }
    }
}

/**
 * 开关项
 */
@Composable
private fun SwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 信息项
 */
@Composable
private fun InfoItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 手动触发对话框
 */
@Composable
private fun ManualTriggerDialog(
    onDismiss: () -> Unit,
    onTrigger: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("手动触发发言")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入小光要说的话：")
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例如：主人，你今天过得怎么样？") },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (message.isNotBlank()) onTrigger(message) },
                enabled = message.isNotBlank()
            ) {
                Text("触发")
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
 * 格式化时间
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * ✅ 环境监听卡片
 */
@Composable
private fun EnvironmentMonitorCard(
    recentUtterances: List<com.xiaoguang.assistant.domain.flow.model.Utterance>,
    currentSpeaker: com.xiaoguang.assistant.domain.flow.model.SpeakerData?,
    presentPeople: List<com.xiaoguang.assistant.domain.flow.model.SpeakerData>,
    audioLevel: Float,
    isVoiceActive: Boolean
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
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isVoiceActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "环境监听",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isVoiceActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50).copy(0.2f)
                    ) {
                        Text(
                            text = "正在说话",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // 音频级别
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("音频级别", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${(audioLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (audioLevel > 0.3f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = audioLevel,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = when {
                        audioLevel > 0.5f -> Color(0xFF4CAF50)
                        audioLevel > 0.2f -> Color(0xFFFFC107)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Divider()

            // 当前说话人
            if (currentSpeaker != null && currentSpeaker.speakerId != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "当前说话人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = currentSpeaker.speakerName ?: currentSpeaker.speakerId ?: "未知",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (currentSpeaker.isMaster) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (currentSpeaker.isMaster) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(0.2f)
                        ) {
                            Text(
                                text = "主人",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 在场人员
            if (presentPeople.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "在场人员 (${presentPeople.size}人)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    presentPeople.take(5).forEach { person ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (person.isMaster) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = person.speakerName ?: person.speakerId ?: "未知",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (person.isMaster) {
                                Text(
                                    text = "[主人]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Divider()

            // 最近识别的语句
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "最近识别的语句",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${recentUtterances.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (recentUtterances.isEmpty()) {
                    Text(
                        text = "暂无识别结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    recentUtterances.reversed().forEach { utterance ->
                        UtteranceItem(utterance)
                    }
                }
            }
        }
    }
}

/**
 * 单个语句项
 */
@Composable
private fun UtteranceItem(utterance: com.xiaoguang.assistant.domain.flow.model.Utterance) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = utterance.speakerName ?: utterance.speakerId ?: "未知说话人",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (utterance.speakerCount > 1) {
                        Text(
                            text = "(${utterance.speakerCount}人)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFC107)
                        )
                    }
                }
                Text(
                    text = "${utterance.getAgeSeconds()}秒前",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = utterance.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 听觉诊断日志卡片（新增）
 * 显示语音识别和档案创建的诊断信息
 */
@Composable
private fun VoiceDiagnosticsCard() {
    var diagnosticLogs by remember { mutableStateOf(listOf<DiagnosticLog>()) }
    var isExpanded by remember { mutableStateOf(true) }

    // TODO: 从LogCat实时读取诊断日志
    // 当前显示示例数据，实际应从Android Logcat过滤"[诊断]"标签
    LaunchedEffect(Unit) {
        diagnosticLogs = listOf(
            DiagnosticLog(
                timestamp = System.currentTimeMillis(),
                level = "DEBUG",
                tag = "SpeakerIdentification",
                message = "等待语音输入..."
            )
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "听觉诊断日志",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // 使用说明
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "查看语音识别和档案创建详情",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "在Android Studio Logcat中筛选 'SpeakerIdentification' 标签，查看完整的[诊断]日志",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 日志列表
                if (diagnosticLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无日志",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        diagnosticLogs.takeLast(5).forEach { log ->
                            DiagnosticLogItem(log)
                        }
                    }

                    if (diagnosticLogs.size > 5) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "显示最近 5 条，共 ${diagnosticLogs.size} 条日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* TODO: 清除日志 */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清除日志")
                    }

                    Button(
                        onClick = { /* TODO: 导出日志 */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出日志")
                    }
                }
            }
        }
    }
}

/**
 * 诊断日志项
 */
@Composable
private fun DiagnosticLogItem(log: DiagnosticLog) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = when (log.level) {
            "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(0.3f)
            "WARN" -> Color(0xFFFFA726).copy(0.2f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.message,
                style = MaterialTheme.typography.bodySmall,
                color = when (log.level) {
                    "ERROR" -> MaterialTheme.colorScheme.error
                    "WARN" -> Color(0xFFFFA726)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/**
 * 诊断日志数据模型
 */
private data class DiagnosticLog(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
)

