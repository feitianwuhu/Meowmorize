package com.meowmorize.core.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

/**
 * 全应用统一的动效令牌。
 *
 * 取值来自 Emil Kowalski 的动效规范，这里落成 Compose 常量：
 * - **进出场一律 ease-out**：起步就快，用户等待感最低；`ease-in` 在 UI 上是禁用项。
 * - **曲线要用「强」的自定义变体**，内置的 ease 系列太软，看不出意图。
 * - **UI 动效 < 300ms**；只有弹层（sheet/dialog）可以到 300ms 上下。
 * - 数字集中在这里，避免每个组件各写一套「差不多」的时长。
 */
object MeowMotion {

    /** 强 ease-out：进入/退出/按压回弹。cubic-bezier(0.23, 1, 0.32, 1) */
    val easeOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** 强 ease-in-out：元素在屏内位移/形变。cubic-bezier(0.77, 0, 0.175, 1) */
    val easeInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** 抽屉/弹层曲线。cubic-bezier(0.32, 0.72, 0, 1) */
    val drawer: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** 按压反馈：100–160ms 区间，取 120。 */
    const val PRESS = 120

    /** 高频、小范围的状态切换（底栏高亮、胶囊选中）。 */
    const val FAST = 150

    /** 通用状态过渡（进度、分段指示器）。 */
    const val BASE = 200

    /** 页面进入。 */
    const val ENTER = 220

    /** 页面退出：比进入略快，退回时更跟手。 */
    const val EXIT = 180

    /** 弹层上滑。 */
    const val SHEET = 280
}

/**
 * 系统「动画时长倍数」。
 *
 * 0 = 用户在系统里选择了「关闭动画」（无障碍里的减少动态效果）。
 * 由 App 层读取系统设置后注入；默认 1 不改变任何时长。
 */
val LocalMotionScale = staticCompositionLocalOf { 1f }

/** 按系统倍数缩放后的毫秒数；倍数为 0 时退化为「瞬间完成」。 */
@Composable
fun motionMs(baseMillis: Int): Int {
    val scale = LocalMotionScale.current
    if (scale <= 0f) return 0
    return (baseMillis * scale).roundToInt().coerceAtLeast(1)
}

/** 带令牌曲线的 tween。 */
@Composable
fun <T> meowTween(
    baseMillis: Int,
    easing: Easing = MeowMotion.easeOut,
    delayMillis: Int = 0,
): FiniteAnimationSpec<T> = tween(
    durationMillis = motionMs(baseMillis),
    delayMillis = motionMs(delayMillis),
    easing = easing,
)

/**
 * 按压缩放。
 *
 * 这是「界面听到了你的点击」最廉价也最有效的信号：只动 [graphicsLayer]，
 * 不触发重新布局，低端机也不掉帧。缩放幅度刻意压在小范围（0.95–0.98），
 * 大了会显得廉价。
 *
 * 依赖调用方把同一个 [interactionSource] 传给 `clickable(indication = null)`——
 * 那些组件本来就去掉了水波纹，反馈全靠这里。
 */
@Composable
fun Modifier.meowPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed = remember { mutableStateOf(false) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed.value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed.value = false
            }
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed.value) pressedScale else 1f,
        animationSpec = meowTween(MeowMotion.PRESS),
        label = "meowPressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
