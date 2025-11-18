package com.xiaoguang.assistant.presentation.design

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * 小光动画系统
 *
 * 提供统一的动画规范和工具函数
 */
object XiaoguangAnimations {

    /**
     * 默认缓动曲线
     */
    object Easing {
        val Standard = FastOutSlowInEasing
        val Accelerate = FastOutLinearInEasing
        val Decelerate = LinearOutSlowInEasing
        val Emphasized = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
        internal val OverShoot = OvershootInterpolator(1.2f)
    }

    /**
     * 页面转场动画规范
     */
    object Transitions {
        /**
         * 淡入淡出 + 缩放
         */
        fun fadeInOut(durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.NORMAL): Pair<EnterTransition, ExitTransition> {
            return fadeIn(
                animationSpec = tween(durationMillis, easing = Easing.Standard)
            ) + scaleIn(
                initialScale = 0.95f,
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) to fadeOut(
                animationSpec = tween(durationMillis, easing = Easing.Standard)
            ) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            )
        }

        /**
         * 从右侧滑入/滑出
         */
        fun slideInFromRight(durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.NORMAL): Pair<EnterTransition, ExitTransition> {
            return slideInHorizontally(
                initialOffsetX = { it / 3 },
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) + fadeIn(
                animationSpec = tween(durationMillis)
            ) to slideOutHorizontally(
                targetOffsetX = { it / 3 },
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) + fadeOut(
                animationSpec = tween(durationMillis)
            )
        }

        /**
         * 从底部滑入/滑出
         */
        fun slideInFromBottom(durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.NORMAL): Pair<EnterTransition, ExitTransition> {
            return slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) + fadeIn(
                animationSpec = tween(durationMillis)
            ) to slideOutVertically(
                targetOffsetY = { it / 4 },
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) + fadeOut(
                animationSpec = tween(durationMillis)
            )
        }

        /**
         * 展开/收起（用于垂直内容）
         */
        fun expandVertically(durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.NORMAL): Pair<EnterTransition, ExitTransition> {
            return expandVertically(
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) + fadeIn(
                animationSpec = tween(durationMillis)
            ) to shrinkVertically(
                animationSpec = tween(durationMillis, easing = Easing.Emphasized)
            ) + fadeOut(
                animationSpec = tween(durationMillis)
            )
        }
    }

    /**
     * 创建无限循环的脉冲动画
     */
    @Composable
    fun rememberPulseAnimation(
        minScale: Float = 0.95f,
        maxScale: Float = 1.05f,
        durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.AVATAR_PULSE
    ): Float {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        return infiniteTransition.animateFloat(
            initialValue = minScale,
            targetValue = maxScale,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = Easing.Standard),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        ).value
    }

    /**
     * 创建无限循环的呼吸动画（透明度）
     */
    @Composable
    fun rememberBreathingAnimation(
        minAlpha: Float = 0.6f,
        maxAlpha: Float = 1.0f,
        durationMillis: Int = 2000
    ): Float {
        val infiniteTransition = rememberInfiniteTransition(label = "breathing")
        return infiniteTransition.animateFloat(
            initialValue = minAlpha,
            targetValue = maxAlpha,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = Easing.Standard),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathing_alpha"
        ).value
    }

    /**
     * 创建波动动画（用于思考气泡等）
     */
    @Composable
    fun rememberWaveAnimation(
        durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.THOUGHT_BUBBLE
    ): Float {
        val infiniteTransition = rememberInfiniteTransition(label = "wave")
        return infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_rotation"
        ).value
    }

    /**
     * 创建震动动画（用于提示等）
     */
    @Composable
    fun rememberShakeAnimation(trigger: Boolean): Float {
        val shakeOffset = remember { Animatable(0f) }

        LaunchedEffect(trigger) {
            if (trigger) {
                // 左右震动3次
                repeat(3) {
                    shakeOffset.animateTo(10f, tween(50))
                    shakeOffset.animateTo(-10f, tween(50))
                }
                shakeOffset.animateTo(0f, tween(50))
            }
        }

        return shakeOffset.value
    }
}

/**
 * Modifier扩展 - 点击缩放效果
 */
fun Modifier.clickableScale(
    enabled: Boolean = true,
    minScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled) {
            if (enabled) {
                detectTapGestures(
                    onPress = {
                        coroutineScope.launch {
                            scale.animateTo(
                                minScale,
                                tween(100, easing = XiaoguangAnimations.Easing.Standard)
                            )
                        }
                        val released = tryAwaitRelease()
                        coroutineScope.launch {
                            scale.animateTo(
                                1f,
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                        }
                        if (released) {
                            onClick()
                        }
                    }
                )
            }
        }
}

/**
 * Modifier扩展 - 悬停效果（放大）
 */
fun Modifier.hoverScale(
    scale: Float = 1.05f,
    durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.FAST
): Modifier = composed {
    val animatedScale = remember { Animatable(1f) }
    var isHovered by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        animatedScale.animateTo(
            if (isHovered) scale else 1f,
            tween(durationMillis, easing = XiaoguangAnimations.Easing.Standard)
        )
    }

    this.graphicsLayer {
        scaleX = animatedScale.value
        scaleY = animatedScale.value
    }
}

/**
 * Modifier扩展 - 脉冲效果
 */
fun Modifier.pulse(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    val scale = XiaoguangAnimations.rememberPulseAnimation()

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Modifier扩展 - 呼吸效果（透明度）
 */
fun Modifier.breathing(enabled: Boolean = true): Modifier = composed {
    if (!enabled) return@composed this

    val alpha = XiaoguangAnimations.rememberBreathingAnimation()

    this.graphicsLayer {
        this.alpha = alpha
    }
}

/**
 * Modifier扩展 - 进入动画（淡入 + 上移）
 */
fun Modifier.animateEnter(
    delayMillis: Int = 0,
    durationMillis: Int = XiaoguangDesignSystem.AnimationDurations.NORMAL
): Modifier = composed {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(50f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMillis.toLong())
        launch {
            alpha.animateTo(
                1f,
                tween(durationMillis, easing = XiaoguangAnimations.Easing.Standard)
            )
        }
        launch {
            offsetY.animateTo(
                0f,
                tween(durationMillis, easing = XiaoguangAnimations.Easing.Emphasized)
            )
        }
    }

    this.graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}

/**
 * 自定义插值器 - OverShoot效果
 */
internal class OvershootInterpolator(private val tension: Float = 2.0f) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction - 1.0f
        return t * t * ((tension + 1.0f) * t + tension) + 1.0f
    }
}
