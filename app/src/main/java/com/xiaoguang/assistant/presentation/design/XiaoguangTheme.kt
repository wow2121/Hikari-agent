package com.xiaoguang.assistant.presentation.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.xiaoguang.assistant.domain.emotion.EmotionType

/**
 * 小光主题
 * 提供明暗两种配色方案，并支持根据情绪动态调整主色调
 */

/**
 * 亮色主题配色
 */
private val LightColorScheme = lightColorScheme(
    primary = XiaoguangDesignSystem.CoreColors.primary,
    onPrimary = XiaoguangDesignSystem.CoreColors.onPrimary,
    primaryContainer = XiaoguangDesignSystem.EmotionColors.Happy.background,
    onPrimaryContainer = Color(0xFF3E2723),

    secondary = XiaoguangDesignSystem.CoreColors.secondary,
    onSecondary = XiaoguangDesignSystem.CoreColors.onSecondary,
    secondaryContainer = XiaoguangDesignSystem.EmotionColors.Thinking.background,
    onSecondaryContainer = Color(0xFF1B5E20),

    tertiary = XiaoguangDesignSystem.EmotionColors.Surprised.primary,
    onTertiary = Color.White,
    tertiaryContainer = XiaoguangDesignSystem.EmotionColors.Surprised.background,
    onTertiaryContainer = Color(0xFF4A148C),

    background = XiaoguangDesignSystem.CoreColors.background,
    onBackground = XiaoguangDesignSystem.CoreColors.onBackground,

    surface = XiaoguangDesignSystem.CoreColors.surface,
    onSurface = XiaoguangDesignSystem.CoreColors.onSurface,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF616161),

    error = XiaoguangDesignSystem.CoreColors.error,
    onError = XiaoguangDesignSystem.CoreColors.onError,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),

    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
)

/**
 * 暗色主题配色
 */
private val DarkColorScheme = darkColorScheme(
    primary = XiaoguangDesignSystem.EmotionColors.Happy.primary,
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Color(0xFF5D4037),
    onPrimaryContainer = XiaoguangDesignSystem.EmotionColors.Happy.accent,

    secondary = XiaoguangDesignSystem.EmotionColors.Thinking.primary,
    onSecondary = Color(0xFF00251A),
    secondaryContainer = Color(0xFF00513A),
    onSecondaryContainer = XiaoguangDesignSystem.EmotionColors.Thinking.accent,

    tertiary = XiaoguangDesignSystem.EmotionColors.Surprised.primary,
    onTertiary = Color(0xFF4A148C),
    tertiaryContainer = Color(0xFF6A1B9A),
    onTertiaryContainer = XiaoguangDesignSystem.EmotionColors.Surprised.accent,

    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),

    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),

    error = XiaoguangDesignSystem.CoreColors.error,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
)

/**
 * 情绪主题颜色
 * 根据小光当前情绪提供对应的配色
 */
data class EmotionThemeColors(
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val accent: Color
)

/**
 * 将 EmotionType 映射到对应的颜色主题
 * @param isDark 是否为暗色模式
 */
fun EmotionType.toThemeColors(isDark: Boolean = false): EmotionThemeColors {
    return when (this) {
        EmotionType.HAPPY, EmotionType.EXCITED -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFFFFD54F),  // 更亮的橙黄色
                secondary = Color(0xFFFFB74D),
                background = Color(0xFF3E2723),
                accent = Color(0xFFFFE082)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Happy.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Happy.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Happy.background,
                accent = XiaoguangDesignSystem.EmotionColors.Happy.accent
            )
        }

        EmotionType.CALM, EmotionType.TIRED -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFFB39DDB),  // 更亮的蓝紫色
                secondary = Color(0xFF9575CD),
                background = Color(0xFF1A237E),
                accent = Color(0xFFD1C4E9)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Calm.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Calm.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Calm.background,
                accent = XiaoguangDesignSystem.EmotionColors.Calm.accent
            )
        }

        EmotionType.CURIOUS -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFF80CBC4),  // 更亮的青绿色
                secondary = Color(0xFF4DB6AC),
                background = Color(0xFF004D40),
                accent = Color(0xFFB2DFDB)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Thinking.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Thinking.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Thinking.background,
                accent = XiaoguangDesignSystem.EmotionColors.Thinking.accent
            )
        }

        EmotionType.SURPRISED -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFFCE93D8),  // 更亮的粉紫色
                secondary = Color(0xFFBA68C8),
                background = Color(0xFF4A148C),
                accent = Color(0xFFE1BEE7)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Surprised.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Surprised.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Surprised.background,
                accent = XiaoguangDesignSystem.EmotionColors.Surprised.accent
            )
        }

        EmotionType.SAD, EmotionType.ANXIOUS -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFFB0BEC5),  // 更亮的灰蓝色
                secondary = Color(0xFF90A4AE),
                background = Color(0xFF263238),
                accent = Color(0xFFCFD8DC)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Sad.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Sad.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Sad.background,
                accent = XiaoguangDesignSystem.EmotionColors.Sad.accent
            )
        }

        EmotionType.CONFUSED, EmotionType.FRUSTRATED -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFFF48FB1),  // 更亮的玫瑰粉
                secondary = Color(0xFFF06292),
                background = Color(0xFF880E4F),
                accent = Color(0xFFF8BBD0)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Embarrassed.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Embarrassed.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Embarrassed.background,
                accent = XiaoguangDesignSystem.EmotionColors.Embarrassed.accent
            )
        }

        else -> if (isDark) {
            EmotionThemeColors(
                primary = Color(0xFFE0E0E0),
                secondary = Color(0xFFBDBDBD),
                background = Color(0xFF424242),
                accent = Color(0xFFF5F5F5)
            )
        } else {
            EmotionThemeColors(
                primary = XiaoguangDesignSystem.EmotionColors.Neutral.primary,
                secondary = XiaoguangDesignSystem.EmotionColors.Neutral.secondary,
                background = XiaoguangDesignSystem.EmotionColors.Neutral.background,
                accent = XiaoguangDesignSystem.EmotionColors.Neutral.accent
            )
        }
    }
}

/**
 * 本地组合：当前情绪主题颜色
 */
val LocalEmotionColors = staticCompositionLocalOf {
    EmotionThemeColors(
        primary = XiaoguangDesignSystem.EmotionColors.Happy.primary,
        secondary = XiaoguangDesignSystem.EmotionColors.Happy.secondary,
        background = XiaoguangDesignSystem.EmotionColors.Happy.background,
        accent = XiaoguangDesignSystem.EmotionColors.Happy.accent
    )
}

/**
 * 小光主题 Composable
 *
 * @param darkTheme 是否使用暗色主题（默认跟随系统）
 * @param emotion 当前情绪（用于动态调整配色）
 * @param content 内容
 */
@Composable
fun XiaoguangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    emotion: EmotionType? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val emotionColors = emotion?.toThemeColors(isDark = darkTheme) ?: LocalEmotionColors.current

    CompositionLocalProvider(
        LocalEmotionColors provides emotionColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = androidx.compose.material3.Typography(
                // Display styles
                displayLarge = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.displayLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    lineHeight = 40.sp
                ),
                displayMedium = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.displayMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    lineHeight = 36.sp
                ),
                displaySmall = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.displaySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    lineHeight = 32.sp
                ),

                // Headline styles
                headlineLarge = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.headlineLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    lineHeight = 28.sp
                ),
                headlineMedium = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    lineHeight = 26.sp
                ),
                headlineSmall = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.headlineSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    lineHeight = 24.sp
                ),

                // Body styles
                bodyLarge = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    lineHeight = 24.sp
                ),
                bodyMedium = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    lineHeight = 20.sp
                ),
                bodySmall = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    lineHeight = 16.sp
                ),

                // Label styles
                labelLarge = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    lineHeight = 20.sp
                ),
                labelMedium = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    lineHeight = 16.sp
                ),
                labelSmall = androidx.compose.ui.text.TextStyle(
                    fontSize = XiaoguangDesignSystem.Typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    lineHeight = 14.sp
                )
            ),
            content = content
        )
    }
}
