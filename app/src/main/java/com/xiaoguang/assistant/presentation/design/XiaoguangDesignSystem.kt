package com.xiaoguang.assistant.presentation.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 小光设计系统 - 核心设计规范
 *
 * 定义小光AI助手的视觉风格，包括：
 * - 情绪色彩系统（6种核心情绪）
 * - 动画时长规范
 * - 间距系统
 * - 形状规范
 */
object XiaoguangDesignSystem {

    /**
     * 情绪色彩系统
     * 每种情绪对应一组渐变色，用于表现小光的情感状态
     */
    object EmotionColors {
        /** 😊 开心 - 温暖的橙黄色 */
        object Happy {
            val primary = Color(0xFFFFB84D)      // 明亮橙黄
            val secondary = Color(0xFFFFA726)    // 深橙黄
            val background = Color(0xFFFFF3E0)   // 浅黄底色
            val accent = Color(0xFFFFE082)       // 高光黄
        }

        /** 😴 平静/困倦 - 柔和的蓝紫色 */
        object Calm {
            val primary = Color(0xFF9FA8DA)      // 淡蓝紫
            val secondary = Color(0xFF7986CB)    // 中蓝紫
            val background = Color(0xFFE8EAF6)   // 浅蓝紫底色
            val accent = Color(0xFFC5CAE9)       // 高光蓝紫
        }

        /** 🤔 思考 - 智慧的青绿色 */
        object Thinking {
            val primary = Color(0xFF4DB6AC)      // 青绿色
            val secondary = Color(0xFF26A69A)    // 深青绿
            val background = Color(0xFFE0F2F1)   // 浅青绿底色
            val accent = Color(0xFF80CBC4)       // 高光青绿
        }

        /** 😮 惊讶 - 活泼的粉紫色 */
        object Surprised {
            val primary = Color(0xFFBA68C8)      // 粉紫色
            val secondary = Color(0xFFAB47BC)    // 深粉紫
            val background = Color(0xFFF3E5F5)   // 浅粉紫底色
            val accent = Color(0xFFCE93D8)       // 高光粉紫
        }

        /** 😢 难过 - 柔和的灰蓝色 */
        object Sad {
            val primary = Color(0xFF90A4AE)      // 灰蓝色
            val secondary = Color(0xFF78909C)    // 深灰蓝
            val background = Color(0xFFECEFF1)   // 浅灰蓝底色
            val accent = Color(0xFFB0BEC5)       // 高光灰蓝
        }

        /** 😅 尴尬/紧张 - 温和的玫瑰粉 */
        object Embarrassed {
            val primary = Color(0xFFF06292)      // 玫瑰粉
            val secondary = Color(0xFFEC407A)    // 深玫瑰粉
            val background = Color(0xFFFCE4EC)   // 浅粉底色
            val accent = Color(0xFFF48FB1)       // 高光粉
        }

        /** 中性/默认 - 柔和灰色 */
        object Neutral {
            val primary = Color(0xFFBDBDBD)      // 中灰
            val secondary = Color(0xFF9E9E9E)    // 深灰
            val background = Color(0xFFFAFAFA)   // 浅灰底色
            val accent = Color(0xFFE0E0E0)       // 高光灰
        }
    }

    /**
     * 核心色彩
     * 应用的基础配色方案
     */
    object CoreColors {
        // 主色调 - 温暖橙色（代表小光的温暖性格）
        val primary = Color(0xFFFFB84D)
        val primaryVariant = Color(0xFFFFA726)
        val onPrimary = Color(0xFFFFFFFF)

        // 次要色 - 柔和青绿（代表智慧和成长）
        val secondary = Color(0xFF4DB6AC)
        val secondaryVariant = Color(0xFF26A69A)
        val onSecondary = Color(0xFFFFFFFF)

        // 背景色
        val background = Color(0xFFFFFBF7)       // 温暖的米白色
        val surface = Color(0xFFFFFFFF)
        val onBackground = Color(0xFF2C2C2C)
        val onSurface = Color(0xFF2C2C2C)

        // 功能性颜色
        val error = Color(0xFFE57373)            // 柔和红色（避免过于刺眼）
        val onError = Color(0xFFFFFFFF)
        val success = Color(0xFF81C784)          // 柔和绿色
        val warning = Color(0xFFFFB74D)          // 柔和橙色
        val info = Color(0xFF64B5F6)             // 柔和蓝色
    }

    /**
     * 动画时长规范
     * 所有时长单位为毫秒(ms)
     */
    object AnimationDurations {
        // 快速动画 - 用于小元素的响应
        const val FAST = 150

        // 正常动画 - 用于大多数UI交互
        const val NORMAL = 300

        // 慢速动画 - 用于复杂转场和状态变化
        const val SLOW = 500

        // 小光表情变化
        const val EMOTION_CHANGE = 600

        // 思考气泡动画
        const val THOUGHT_BUBBLE = 400

        // 小光头像呼吸/脉搏效果
        const val AVATAR_PULSE = 2000

        // 说话人指示器
        const val SPEAKER_INDICATOR = 300

        // 心流脉冲
        const val FLOW_IMPULSE = 800
    }

    /**
     * 间距系统
     * 基于8dp网格系统
     */
    object Spacing {
        val xxxs = 2.dp   // 极小间距
        val xxs = 4.dp    // 超小间距
        val xs = 8.dp     // 小间距
        val sm = 12.dp    // 中小间距
        val md = 16.dp    // 中等间距（默认）
        val lg = 24.dp    // 大间距
        val xl = 32.dp    // 超大间距
        val xxl = 48.dp   // 极大间距
        val xxxl = 64.dp  // 超极大间距
    }

    /**
     * 圆角规范
     */
    object CornerRadius {
        val xs = 4.dp     // 小圆角 - 用于按钮、输入框
        val sm = 8.dp     // 中小圆角 - 用于卡片
        val md = 12.dp    // 中等圆角 - 用于对话气泡
        val lg = 16.dp    // 大圆角 - 用于容器
        val xl = 20.dp    // 超大圆角 - 用于底部导航栏
        val full = 999.dp // 完全圆形 - 用于头像、徽章
    }

    /**
     * 阴影高度
     */
    object Elevation {
        val none = 0.dp
        val xs = 1.dp     // 细微阴影 - 分隔线
        val sm = 2.dp     // 小阴影 - 卡片悬停
        val md = 4.dp     // 中等阴影 - 卡片
        val lg = 8.dp     // 大阴影 - 弹出层
        val xl = 12.dp    // 超大阴影 - 模态框
    }

    /**
     * 字体规范
     */
    object Typography {
        // 显示文字 - 用于大标题
        val displayLarge = 32.sp
        val displayMedium = 28.sp
        val displaySmall = 24.sp

        // 标题文字
        val headlineLarge = 22.sp
        val headlineMedium = 20.sp
        val headlineSmall = 18.sp

        // 正文文字
        val bodyLarge = 16.sp
        val bodyMedium = 14.sp
        val bodySmall = 12.sp

        // 标签文字
        val labelLarge = 14.sp
        val labelMedium = 12.sp
        val labelSmall = 10.sp
    }

    /**
     * 图标尺寸
     */
    object IconSize {
        val xs = 12.dp
        val sm = 16.dp
        val md = 24.dp
        val lg = 32.dp
        val xl = 48.dp
        val xxl = 64.dp
    }

    /**
     * 小光头像尺寸
     */
    object AvatarSize {
        val xs = 24.dp    // 极小 - 用于列表项
        val sm = 32.dp    // 小 - 用于对话气泡
        val md = 48.dp    // 中 - 用于通知
        val lg = 64.dp    // 大 - 用于个人资料
        val xl = 96.dp    // 超大 - 用于主页中心
        val xxl = 128.dp  // 巨大 - 用于全屏展示
    }

    /**
     * 透明度
     */
    object Alpha {
        const val DISABLED = 0.38f
        const val MEDIUM = 0.60f
        const val HIGH = 0.87f
        const val FULL = 1.0f
    }

    /**
     * 线宽
     */
    object StrokeWidth {
        val thin = 1.dp
        val normal = 2.dp
        val thick = 3.dp
        val bold = 4.dp
    }
}
