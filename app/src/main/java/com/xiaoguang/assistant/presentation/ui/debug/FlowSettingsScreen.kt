package com.xiaoguang.assistant.presentation.ui.debug

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors

/**
 * 心流设置界面
 *
 * 功能：
 * - 调整心流配置参数
 * - 修改话痨度、人格类型
 * - 调整决策阈值
 * - 修改循环参数
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowSettingsScreen(
    viewModel: FlowDebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val config by viewModel.config.collectAsState()

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
                        text = "心流设置",
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
            // 基础参数
            item {
                SettingsSection(
                    title = "基础参数",
                    icon = Icons.Default.Settings
                ) {
                    // 话痨度
                    SettingSlider(
                        title = "话痨度",
                        value = config.talkativeLevel,
                        valueRange = 0.5f..1.5f,
                        steps = 9,
                        onValueChange = { viewModel.adjustTalkativeLevel(it) },
                        valueFormatter = { String.format("%.1f", it) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    // 人格类型
                    SettingChips(
                        title = "人格类型",
                        options = listOf("内向" to "RESERVED", "平衡" to "BALANCED", "外向" to "OUTGOING"),
                        selectedValue = config.personalityType.name,
                        onValueChange = { /* TODO: 实现人格类型切换 */ }
                    )
                }
            }

            // 功能开关
            item {
                SettingsSection(
                    title = "功能开关",
                    icon = Icons.Default.ToggleOn
                ) {
                    SettingSwitch(
                        title = "内心想法",
                        description = "小光会产生内心想法并可能说出来",
                        checked = config.enableInnerThoughts,
                        onCheckedChange = { viewModel.toggleInnerThoughts(it) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingSwitch(
                        title = "好奇心",
                        description = "小光会对新事物产生好奇并主动询问",
                        checked = config.enableCuriosity,
                        onCheckedChange = { viewModel.toggleCuriosity(it) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    SettingSwitch(
                        title = "主动关心",
                        description = "小光会主动关心主人的状态",
                        checked = config.enableProactiveCare,
                        onCheckedChange = { viewModel.toggleProactiveCare(it) }
                    )
                }
            }

            // 决策阈值
            item {
                SettingsSection(
                    title = "决策阈值",
                    icon = Icons.Default.Speed
                ) {
                    InfoText("冲动值超过阈值时，小光会主动发言")

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingInfoRow("主人", String.format("%.0f%%", config.speakThreshold * 100))
                    SettingInfoRow("普通人", String.format("%.0f%%", config.speakThresholdNormal * 100))
                    SettingInfoRow("陌生人", String.format("%.0f%%", config.speakThresholdStranger * 100))
                }
            }

            // 循环参数
            item {
                SettingsSection(
                    title = "循环参数",
                    icon = Icons.Default.Loop
                ) {
                    SettingInfoRow("基础间隔", "${config.baseLoopInterval}ms")
                    SettingInfoRow("最小间隔", "${config.minLoopInterval}ms")
                    SettingInfoRow("最大间隔", "${config.maxLoopInterval}ms")
                }
            }

            // 权重配置
            item {
                SettingsSection(
                    title = "评分权重",
                    icon = Icons.Default.Scale
                ) {
                    WeightBar("时间权重", config.timeWeight)
                    WeightBar("情绪权重", config.emotionWeight)
                    WeightBar("关系权重", config.relationWeight)
                    WeightBar("上下文权重", config.contextWeight)
                    WeightBar("好奇心权重", config.curiosityWeight)
                    WeightBar("紧急度权重", config.urgencyWeight)
                }
            }

            // 频率控制
            item {
                SettingsSection(
                    title = "频率控制",
                    icon = Icons.Default.Timer
                ) {
                    SettingInfoRow(
                        "最大发言比例",
                        String.format("%.0f%%", config.maxSpeakRatio * 100)
                    )
                    SettingInfoRow(
                        "最小沉默时间",
                        "${config.minSilenceDuration / 1000}秒"
                    )
                }
            }

            // 调试选项
            item {
                SettingsSection(
                    title = "调试选项",
                    icon = Icons.Default.BugReport
                ) {
                    SettingInfoRow("调试模式", if (config.debugMode) "开启" else "关闭")
                    SettingInfoRow("记录决策", if (config.logDecisions) "开启" else "关闭")
                }
            }
        }
    }
}

/**
 * 设置分组卡片
 */
@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

            content()
        }
    }
}

/**
 * 滑块设置项
 */
@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { String.format("%.2f", it) }
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueFormatter(value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

/**
 * 开关设置项
 */
@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
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

/**
 * 信息行
 */
@Composable
private fun SettingInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
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
 * 芯片选择项
 */
@Composable
private fun SettingChips(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, value) ->
                FilterChip(
                    selected = selectedValue == value,
                    onClick = { onValueChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

/**
 * 权重条
 */
@Composable
private fun WeightBar(label: String, weight: Float) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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
                text = String.format("%.0f%%", weight * 100),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = weight,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 信息文本
 */
@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
