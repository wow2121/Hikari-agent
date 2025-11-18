package com.xiaoguang.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangPhrases
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme
import com.xiaoguang.assistant.presentation.design.clickableScale

/**
 * 人物卡片
 *
 * 显示一个人物的基本信息
 *
 * @param name 姓名
 * @param relationship 关系描述（例如：朋友、同事）
 * @param lastSeenTime 最后见面时间描述
 * @param isMaster 是否为主人
 * @param onClick 点击回调
 */
@Composable
fun PersonCard(
    name: String,
    modifier: Modifier = Modifier,
    relationship: String? = null,
    lastSeenTime: String? = null,
    isMaster: Boolean = false,
    onClick: () -> Unit = {}
) {
    val emotionColors = LocalEmotionColors.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickableScale(onClick = onClick),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像占位符
            Box(
                modifier = Modifier
                    .size(XiaoguangDesignSystem.AvatarSize.md)
                    .clip(CircleShape)
                    .background(
                        if (isMaster) emotionColors.primary
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isMaster) Color.White
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
                )
            }

            // 信息列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs)
            ) {
                // 姓名
                Row(
                    horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isMaster) {
                        Surface(
                            shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.xs),
                            color = emotionColors.primary,
                            modifier = Modifier.padding(horizontal = XiaoguangDesignSystem.Spacing.xxs)
                        ) {
                            Text(
                                text = XiaoguangPhrases.Social.MASTER,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(
                                    horizontal = XiaoguangDesignSystem.Spacing.xxs,
                                    vertical = 2.dp
                                )
                            )
                        }
                    }
                }

                // 关系
                if (relationship != null) {
                    Text(
                        text = relationship,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 最后见面时间
                if (lastSeenTime != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(XiaoguangDesignSystem.IconSize.xs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastSeenTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 记忆卡片
 *
 * 显示一条记忆的摘要
 *
 * @param content 记忆内容
 * @param timestamp 时间戳描述
 * @param category 记忆类别（例如：日常、重要事件）
 * @param importance 重要性（0.0 - 1.0）
 * @param onClick 点击回调
 */
@Composable
fun MemoryCard(
    content: String,
    modifier: Modifier = Modifier,
    timestamp: String? = null,
    category: String? = null,
    importance: Float = 0.5f,
    onClick: () -> Unit = {}
) {
    val emotionColors = LocalEmotionColors.current

    // 重要性颜色
    val importanceColor = when {
        importance > 0.7f -> XiaoguangDesignSystem.CoreColors.error
        importance > 0.4f -> XiaoguangDesignSystem.CoreColors.warning
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickableScale(onClick = onClick),
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(XiaoguangDesignSystem.Spacing.md),
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.sm)
        ) {
            // 头部：类别 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 类别标签
                if (category != null) {
                    Surface(
                        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.xs),
                        color = emotionColors.background
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = emotionColors.primary,
                            modifier = Modifier.padding(
                                horizontal = XiaoguangDesignSystem.Spacing.xs,
                                vertical = XiaoguangDesignSystem.Spacing.xxs
                            )
                        )
                    }
                }

                // 时间
                if (timestamp != null) {
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 内容
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // 重要性指示器
            Row(
                horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm),
                    tint = importanceColor
                )
                // 重要性进度条
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(40.dp)
                        .clip(RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(importance.coerceIn(0f, 1f))
                            .background(importanceColor)
                    )
                }
            }
        }
    }
}

/**
 * 对话消息卡片
 *
 * 显示一条对话消息（小光或用户）
 *
 * @param message 消息内容
 * @param isFromXiaoguang 是否来自小光
 * @param speakerName 说话人姓名（用户消息时显示）
 * @param timestamp 时间戳
 * @param emotion 小光说话时的情绪（仅小光消息）
 */
@Composable
fun ConversationMessageCard(
    message: String,
    isFromXiaoguang: Boolean,
    modifier: Modifier = Modifier,
    speakerName: String? = null,
    timestamp: String? = null,
    emotion: EmotionType? = null
) {
    val emotionColors = LocalEmotionColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = XiaoguangDesignSystem.Spacing.md),
        horizontalArrangement = if (isFromXiaoguang) Arrangement.Start else Arrangement.End
    ) {
        Column(
            horizontalAlignment = if (isFromXiaoguang) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xxs)
        ) {
            // 说话人/时间
            if (isFromXiaoguang || speakerName != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(XiaoguangDesignSystem.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFromXiaoguang) {
                        // 小光头像（小尺寸）
                        if (emotion != null) {
                            XiaoguangAvatar(
                                emotion = emotion,
                                size = XiaoguangDesignSystem.AvatarSize.xs,
                                enableBreathing = false
                            )
                        }
                        Text(
                            text = "小光",
                            style = MaterialTheme.typography.labelSmall,
                            color = emotionColors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else if (speakerName != null) {
                        Text(
                            text = speakerName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (timestamp != null) {
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 消息气泡
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isFromXiaoguang) XiaoguangDesignSystem.CornerRadius.xs
                    else XiaoguangDesignSystem.CornerRadius.md,
                    topEnd = if (isFromXiaoguang) XiaoguangDesignSystem.CornerRadius.md
                    else XiaoguangDesignSystem.CornerRadius.xs,
                    bottomStart = XiaoguangDesignSystem.CornerRadius.md,
                    bottomEnd = XiaoguangDesignSystem.CornerRadius.md
                ),
                color = if (isFromXiaoguang)
                    emotionColors.background
                else
                    MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = XiaoguangDesignSystem.Elevation.xs
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFromXiaoguang)
                        emotionColors.primary
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(
                        horizontal = XiaoguangDesignSystem.Spacing.md,
                        vertical = XiaoguangDesignSystem.Spacing.sm
                    )
                )
            }
        }
    }
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun XiaoguangCardsPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("人物卡片", style = MaterialTheme.typography.titleSmall)
            PersonCard(
                name = "主人",
                relationship = XiaoguangPhrases.Social.CLOSE_FRIEND,
                lastSeenTime = XiaoguangPhrases.Common.JUST_NOW,
                isMaster = true
            )
            PersonCard(
                name = "张三",
                relationship = XiaoguangPhrases.Social.FRIEND,
                lastSeenTime = "2小时前"
            )

            Text("记忆卡片", style = MaterialTheme.typography.titleSmall)
            MemoryCard(
                content = "今天主人说要去参加一个重要会议，看起来有点紧张。我提醒了他带上资料。",
                category = "重要事件",
                timestamp = "今天 09:30",
                importance = 0.8f
            )

            Text("对话消息", style = MaterialTheme.typography.titleSmall)
            ConversationMessageCard(
                message = "你好呀！今天心情怎么样？",
                isFromXiaoguang = true,
                emotion = EmotionType.HAPPY,
                timestamp = "10:30"
            )
            Spacer(modifier = Modifier.height(4.dp))
            ConversationMessageCard(
                message = "还不错！",
                isFromXiaoguang = false,
                speakerName = "主人",
                timestamp = "10:31"
            )
        }
    }
}
