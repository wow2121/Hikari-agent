package com.xiaoguang.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.domain.flow.model.PersonalityType

/**
 * 心流系统设置界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDebug: (() -> Unit)? = null,
    viewModel: FlowSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("心流系统设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 运行状态
            StatusCard(
                isRunning = uiState.isRunning,
                statistics = uiState.statistics,
                onRefresh = { viewModel.updateStatistics() }
            )

            // 人格设置
            PersonalitySettingsCard(
                talkativeLevel = uiState.talkativeLevel,
                personalityType = uiState.personalityType,
                onTalkativeLevelChange = { viewModel.adjustTalkativeLevel(it) },
                onPersonalityTypeChange = { viewModel.setPersonalityType(it) }
            )

            // 功能开关
            FeatureSwitchesCard(
                enableInnerThoughts = uiState.enableInnerThoughts,
                enableCuriosity = uiState.enableCuriosity,
                enableProactiveCare = uiState.enableProactiveCare,
                debugMode = uiState.debugMode,
                onInnerThoughtsToggle = { viewModel.toggleInnerThoughts(it) },
                onCuriosityToggle = { viewModel.toggleCuriosity(it) },
                onProactiveCareToggle = { viewModel.toggleProactiveCare(it) },
                onDebugModeToggle = { viewModel.toggleDebugMode(it) }
            )

            // 控制按钮
            ControlButtonsCard(
                isRunning = uiState.isRunning,
                onStart = { viewModel.startFlowSystem() },
                onStop = { viewModel.stopFlowSystem() },
                onPause = { viewModel.pauseFlowSystem() },
                onResume = { viewModel.resumeFlowSystem() }
            )

            // 调试按钮
            if (onNavigateToDebug != null) {
                OutlinedButton(
                    onClick = onNavigateToDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("打开心流调试界面")
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    isRunning: Boolean,
    statistics: com.xiaoguang.assistant.domain.flow.FlowStatistics?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("运行状态", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (isRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(if (isRunning) "运行中" else "已停止")
            }

            if (statistics != null) {
                Divider()
                Text("循环次数: ${statistics.cycleCount}")
                Text("当前冲动值: ${String.format("%.2f", statistics.currentImpulse)}")
                Text("最近决策: ${statistics.recentDecisions} 条")
                Text("待处理想法: ${statistics.pendingThoughts} 个")
                Text("发言占比: ${String.format("%.1f%%", statistics.recentSpeakRatio * 100)}")
            }
        }
    }
}

@Composable
fun PersonalitySettingsCard(
    talkativeLevel: Float,
    personalityType: PersonalityType,
    onTalkativeLevelChange: (Float) -> Unit,
    onPersonalityTypeChange: (PersonalityType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("人格设置", style = MaterialTheme.typography.titleMedium)

            // 话痨度滑块
            Column {
                Text("话痨度: ${String.format("%.1f", talkativeLevel)}")
                Slider(
                    value = talkativeLevel,
                    onValueChange = onTalkativeLevelChange,
                    valueRange = 0.5f..1.5f,
                    steps = 9
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("安静", style = MaterialTheme.typography.bodySmall)
                    Text("正常", style = MaterialTheme.typography.bodySmall)
                    Text("活泼", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 人格类型选择
            Text("人格类型")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PersonalityType.values().forEach { type ->
                    FilterChip(
                        selected = type == personalityType,
                        onClick = { onPersonalityTypeChange(type) },
                        label = { Text(type.displayName) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureSwitchesCard(
    enableInnerThoughts: Boolean,
    enableCuriosity: Boolean,
    enableProactiveCare: Boolean,
    debugMode: Boolean,
    onInnerThoughtsToggle: (Boolean) -> Unit,
    onCuriosityToggle: (Boolean) -> Unit,
    onProactiveCareToggle: (Boolean) -> Unit,
    onDebugModeToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("功能开关", style = MaterialTheme.typography.titleMedium)

            SwitchRow(
                title = "内心想法",
                subtitle = "让小光有内心活动",
                checked = enableInnerThoughts,
                onCheckedChange = onInnerThoughtsToggle
            )

            SwitchRow(
                title = "好奇心",
                subtitle = "检测矛盾并主动提问",
                checked = enableCuriosity,
                onCheckedChange = onCuriosityToggle
            )

            SwitchRow(
                title = "主动关心",
                subtitle = "主动打招呼和关心",
                checked = enableProactiveCare,
                onCheckedChange = onProactiveCareToggle
            )

            Divider()

            SwitchRow(
                title = "调试模式",
                subtitle = "显示详细日志",
                checked = debugMode,
                onCheckedChange = onDebugModeToggle
            )
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun ControlButtonsCard(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("控制", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isRunning) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("启动")
                    }
                } else {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, null)
                        Spacer(Modifier.width(4.dp))
                        Text("暂停")
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text("停止")
                    }
                }
            }
        }
    }
}
