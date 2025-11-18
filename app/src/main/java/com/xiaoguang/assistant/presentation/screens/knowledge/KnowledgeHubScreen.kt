package com.xiaoguang.assistant.presentation.screens.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.components.XiaoguangAvatar
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.navigation.KnowledgeScreen

/**
 * 知识中心页面
 *
 * 统一入口，访问：
 * - 世界书（世界观知识）
 * - 角色书（人物档案）
 * - 向量数据库（语义检索）
 * - 图数据库（关系可视化）
 */
@Composable
fun KnowledgeHubScreen(
    onNavigateToWorldBook: () -> Unit = {},
    onNavigateToCharacterBook: () -> Unit = {},
    onNavigateToVectorDatabase: () -> Unit = {},
    onNavigateToGraphDatabase: () -> Unit = {}
) {
    KnowledgeHubContent(
        onNavigateToWorldBook = onNavigateToWorldBook,
        onNavigateToCharacterBook = onNavigateToCharacterBook,
        onNavigateToVectorDatabase = onNavigateToVectorDatabase,
        onNavigateToGraphDatabase = onNavigateToGraphDatabase
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeHubContent(
    onNavigateToWorldBook: () -> Unit,
    onNavigateToCharacterBook: () -> Unit,
    onNavigateToVectorDatabase: () -> Unit,
    onNavigateToGraphDatabase: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识中心") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 欢迎卡片
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(XiaoguangDesignSystem.Spacing.lg),
                shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.lg),
                color = emotionColors.background
            ) {
                Row(
                    modifier = Modifier.padding(XiaoguangDesignSystem.Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    XiaoguangAvatar(
                        emotion = EmotionType.CURIOUS,
                        size = XiaoguangDesignSystem.AvatarSize.lg
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "我的知识宝库",
                            style = MaterialTheme.typography.titleMedium,
                            color = emotionColors.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xxs))
                        Text(
                            text = "这里存储着我所学到的一切~",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 知识模块网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = XiaoguangDesignSystem.Spacing.lg),
                contentPadding = PaddingValues(vertical = XiaoguangDesignSystem.Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md)
            ) {
                // 世界书
                item {
                    KnowledgeModuleCard(
                        icon = Icons.Default.Public,
                        title = "世界书",
                        description = "世界观知识",
                        onClick = onNavigateToWorldBook
                    )
                }

                // 角色书
                item {
                    KnowledgeModuleCard(
                        icon = Icons.Default.People,
                        title = "角色书",
                        description = "人物档案",
                        onClick = onNavigateToCharacterBook
                    )
                }

                // 向量数据库
                item {
                    KnowledgeModuleCard(
                        icon = Icons.Default.Dns,
                        title = "向量数据库",
                        description = "语义检索",
                        onClick = onNavigateToVectorDatabase
                    )
                }

                // 图数据库
                item {
                    KnowledgeModuleCard(
                        icon = Icons.Default.Hub,
                        title = "图数据库",
                        description = "关系可视化",
                        onClick = onNavigateToGraphDatabase
                    )
                }
            }
        }
    }
}

/**
 * 知识模块卡片
 */
@Composable
private fun KnowledgeModuleCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(XiaoguangDesignSystem.Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
                color = emotionColors.background,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = emotionColors.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(XiaoguangDesignSystem.Spacing.md)
                )
            }

            Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.md))

            // 标题
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(XiaoguangDesignSystem.Spacing.xxs))

            // 描述
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun KnowledgeHubScreenPreview() {
    XiaoguangTheme(emotion = EmotionType.CURIOUS) {
        KnowledgeHubContent(
            onNavigateToWorldBook = {},
            onNavigateToCharacterBook = {},
            onNavigateToVectorDatabase = {},
            onNavigateToGraphDatabase = {}
        )
    }
}
