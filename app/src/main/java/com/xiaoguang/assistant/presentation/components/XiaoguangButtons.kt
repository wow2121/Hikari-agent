package com.xiaoguang.assistant.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xiaoguang.assistant.domain.emotion.EmotionType
import com.xiaoguang.assistant.presentation.design.LocalEmotionColors
import com.xiaoguang.assistant.presentation.design.XiaoguangDesignSystem
import com.xiaoguang.assistant.presentation.design.XiaoguangTheme

/**
 * 小光主按钮
 *
 * 使用情绪主题色的主要按钮
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param enabled 是否启用
 * @param icon 可选图标
 */
@Composable
fun XiaoguangPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val emotionColors = LocalEmotionColors.current

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = emotionColors.primary,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.sm,
            pressedElevation = XiaoguangDesignSystem.Elevation.xs
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm)
            )
            Spacer(modifier = Modifier.width(XiaoguangDesignSystem.Spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 小光次要按钮
 *
 * 使用轮廓样式的次要按钮
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param enabled 是否启用
 * @param icon 可选图标
 */
@Composable
fun XiaoguangSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val emotionColors = LocalEmotionColors.current

    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.sm),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = emotionColors.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = XiaoguangDesignSystem.StrokeWidth.normal
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm)
            )
            Spacer(modifier = Modifier.width(XiaoguangDesignSystem.Spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 小光文本按钮
 *
 * 无背景的文本按钮
 *
 * @param text 按钮文字
 * @param onClick 点击回调
 * @param enabled 是否启用
 * @param icon 可选图标
 */
@Composable
fun XiaoguangTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val emotionColors = LocalEmotionColors.current

    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = emotionColors.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(XiaoguangDesignSystem.IconSize.sm)
            )
            Spacer(modifier = Modifier.width(XiaoguangDesignSystem.Spacing.xs))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 小光图标按钮
 *
 * 圆形图标按钮
 *
 * @param icon 图标
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 * @param enabled 是否启用
 */
@Composable
fun XiaoguangIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    enabled: Boolean = true
) {
    val emotionColors = LocalEmotionColors.current

    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) emotionColors.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 小光悬浮操作按钮 (FAB)
 *
 * @param icon 图标
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 */
@Composable
fun XiaoguangFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val emotionColors = LocalEmotionColors.current

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = emotionColors.primary,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = XiaoguangDesignSystem.Elevation.md,
            pressedElevation = XiaoguangDesignSystem.Elevation.sm
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(XiaoguangDesignSystem.IconSize.lg)
        )
    }
}

/**
 * 小光输入框
 *
 * 单行文本输入框
 *
 * @param value 当前值
 * @param onValueChange 值变化回调
 * @param placeholder 占位符文字
 * @param leadingIcon 前置图标
 * @param trailingIcon 后置图标
 * @param enabled 是否启用
 * @param singleLine 是否单行
 */
@Composable
fun XiaoguangTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true
) {
    val emotionColors = LocalEmotionColors.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium) }
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = emotionColors.primary
                )
            }
        },
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(XiaoguangDesignSystem.CornerRadius.sm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = emotionColors.primary,
            focusedLabelColor = emotionColors.primary,
            cursorColor = emotionColors.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        ),
        textStyle = MaterialTheme.typography.bodyMedium
    )
}

// ========== 预览 ==========

@Preview(showBackground = true)
@Composable
private fun XiaoguangButtonsPreview() {
    XiaoguangTheme(emotion = EmotionType.HAPPY) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("主按钮", style = MaterialTheme.typography.titleSmall)
            XiaoguangPrimaryButton(text = "确认", onClick = {})
            XiaoguangPrimaryButton(text = "已禁用", onClick = {}, enabled = false)

            Text("次要按钮", style = MaterialTheme.typography.titleSmall)
            XiaoguangSecondaryButton(text = "取消", onClick = {})
            XiaoguangSecondaryButton(text = "已禁用", onClick = {}, enabled = false)

            Text("文本按钮", style = MaterialTheme.typography.titleSmall)
            XiaoguangTextButton(text = "了解更多", onClick = {})

            Text("输入框", style = MaterialTheme.typography.titleSmall)
            XiaoguangTextField(
                value = "",
                onValueChange = {},
                placeholder = "请输入..."
            )
        }
    }
}
