package com.xiaoguang.assistant.presentation.screens.profile

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
import com.xiaoguang.assistant.domain.state.XiaoguangCoreState
import com.xiaoguang.assistant.presentation.components.XiaoguangAvatar
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme

/**
 * 设置项
 */
data class SettingItem(
    val icon: ImageVector,
    val title: String,
    val description: String? = null,
    val onClick: () -> Unit
)

/**
 * 我的页面
 *
 * 包含：
 * - 小光信息卡片
 * - 设置入口
 * - 关于信息
 */
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    coreState: XiaoguangCoreState? = null,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDeveloperOptions: () -> Unit = {}
) {
    val profileUiState by viewModel.uiState.collectAsState()

    ProfileContent(
        coreState = coreState,
        profileUiState = profileUiState,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToDeveloperOptions = onNavigateToDeveloperOptions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    coreState: XiaoguangCoreState?,
    profileUiState: ProfileUiState,
    onNavigateToSettings: () -> Unit,
    onNavigateToDeveloperOptions: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的") }
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
            // 小光信息卡片
            item {
                XiaoguangInfoCard(
                    coreState = coreState,
                    profileUiState = profileUiState
                )
            }

            // 功能设置
            item {
                Text(
                    text = "功能设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.xs)
                )
            }

            val functionSettings = listOf(
                SettingItem(
                    icon = Icons.Default.Palette,
                    title = XiaoguangPhrases.Settings.APPEARANCE,
                    description = "主题、颜色、字体",
                    onClick = onNavigateToSettings
                ),
                SettingItem(
                    icon = Icons.Default.Psychology,
                    title = XiaoguangPhrases.Settings.BEHAVIOR,
                    description = "心流、情绪、性格",
                    onClick = onNavigateToSettings
                ),
                SettingItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = XiaoguangPhrases.Settings.VOICE,
                    description = "语音识别、声纹管理",
                    onClick = onNavigateToSettings
                ),
                SettingItem(
                    icon = Icons.Default.Notifications,
                    title = XiaoguangPhrases.Settings.NOTIFICATION,
                    description = "提醒、通知偏好",
                    onClick = onNavigateToSettings
                )
            )

            items(functionSettings) { item ->
                SettingItemCard(item = item)
            }

            // 高级选项
            item {
                Text(
                    text = "高级选项",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.xs)
                )
            }

            val advancedSettings = listOf(
                SettingItem(
                    icon = Icons.Default.DeveloperMode,
                    title = XiaoguangPhrases.Developer.DEBUG_MODE,
                    description = "调试工具、日志查看",
                    onClick = onNavigateToDeveloperOptions
                ),
                SettingItem(
                    icon = Icons.Default.Security,
                    title = XiaoguangPhrases.Settings.PRIVACY,
                    description = "数据隐私、权限管理",
                    onClick = onNavigateToSettings
                ),
                SettingItem(
                    icon = Icons.Default.Info,
                    title = XiaoguangPhrases.Settings.ABOUT,
                    description = "版本信息、开源协议",
                    onClick = { /* TODO */ }
                )
            )

            items(advancedSettings) { item ->
                SettingItemCard(item = item)
            }

            // 版本信息
            item {
                VersionInfoCard()
            }
        }
    }
}

/**
 * 小光信息卡片
 */
@Composable
private fun XiaoguangInfoCard(
    coreState: XiaoguangCoreState?,
    profileUiState: ProfileUiState
) {
    val emotionColors = LocalEmotionColors.current
    val emotion = coreState?.emotion?.currentEmotion ?: EmotionType.HAPPY

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
                emotion = emotion,
                size = XiaoguangDesignSystem.AvatarSize.xl
            )

            // 名字
            Text(
                text = "小光 AI 助手",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = emotionColors.primary
            )

            // 签名
            Text(
                text = "你温暖贴心的AI伙伴~",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 加载状态或统计数据
            if (profileUiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.md),
                    color = emotionColors.primary
                )
            } else {
                // 状态统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(label = "运行天数", value = profileUiState.runningDays.toString())
                    StatItem(label = "对话次数", value = profileUiState.conversationCount.toString())
                    StatItem(label = "记忆数量", value = profileUiState.memoryCount.toString())
                }
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 设置项卡片
 */
@Composable
private fun SettingItemCard(item: SettingItem) {
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
                if (item.description != null) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
 * 版本信息卡片
 */
@Composable
private fun VersionInfoCard() {
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs)
        ) {
            Text(
                text = "小光 AI 助手",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "版本 1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "由 Claude Code 生成",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        ProfileContent(
            coreState = null,
            profileUiState = ProfileUiState(
                runningDays = 7,
                conversationCount = 42,
                memoryCount = 128,
                isLoading = false
            ),
            onNavigateToSettings = {},
            onNavigateToDeveloperOptions = {}
        )
    }
}
