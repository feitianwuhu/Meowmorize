package com.meowmorize.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meowmorize.core.designsystem.motion.MeowMotion
import com.meowmorize.core.designsystem.motion.meowPressScale
import com.meowmorize.core.designsystem.motion.meowTween
import com.meowmorize.core.designsystem.motion.motionMs
import kotlinx.coroutines.delay
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.model.AppStyle

/** 底部导航项。 */
data class MeowNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * 悬浮胶囊式底部导航。
 * 不做传统的全宽导航栏，改成「浮在内容上的一块」，
 * 让列表可以一直滑到底部而不会被硬切一刀。
 */
@Composable
fun MeowBottomBar(
    items: List<MeowNavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminal = LocalAppStyle.current == AppStyle.TERMINAL
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = if (terminal) 12.dp else 24.dp, vertical = 10.dp),
        shape = if (terminal) RoundedCornerShape(6.dp) else RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (terminal) 1f else 0.8f),
        ),
        // 终端风格不用投影：层次靠 1px 描边
        shadowElevation = if (terminal) 0.dp else 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == currentRoute
                val interaction = remember { MutableInteractionSource() }

                // 底栏一天要被点几十次：颜色过渡压到 150ms，只求「跟手」不求好看
                val container by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
                    label = "navContainer",
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
                    label = "navTint",
                )
                Row(
                    modifier = Modifier
                        .meowPressScale(interactionSource = interaction, pressedScale = 0.95f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(container)
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                        ) { onSelect(item.route) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(21.dp),
                    )
                    // 文字展开而不是「突然出现」：胶囊宽度变化才有连续感
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn(meowTween(MeowMotion.FAST)) +
                            expandHorizontally(
                                animationSpec = meowTween(MeowMotion.FAST, MeowMotion.easeOut),
                                expandFrom = Alignment.Start,
                            ),
                        exit = fadeOut(meowTween(MeowMotion.PRESS)) +
                            shrinkHorizontally(
                                animationSpec = meowTween(MeowMotion.PRESS, MeowMotion.easeOut),
                                shrinkTowards = Alignment.Start,
                            ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = tint,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 自绘底部弹层。
 *
 * 没有用 Material 的 ModalBottomSheet：一是它仍带实验注解，
 * 二是 HyperOS 的底部弹层需要「顶部把手 + 大圆角 + 半透明蒙层」这套固定结构，
 * 自己画反而更可控。
 */
@Composable
fun MeowBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 关闭瞬间不能立刻移除 Dialog，否则没有宿主承载退场动画
    var present by remember { mutableStateOf(visible) }
    val sheetMs = motionMs(MeowMotion.SHEET)
    LaunchedEffect(visible) {
        if (visible) {
            present = true
        } else if (present) {
            delay((sheetMs + 40).toLong())
            present = false
        }
    }
    if (!present) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        val scrim by animateFloatAsState(
            targetValue = if (visible) 0.45f else 0f,
            animationSpec = meowTween(MeowMotion.SHEET, MeowMotion.easeOut),
            label = "sheetScrim",
        )
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                // 上滑用抽屉曲线，退下更快：进入要「被托起来」，退出要「立刻让开」
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = meowTween(MeowMotion.SHEET, MeowMotion.drawer),
                ) + fadeIn(meowTween(MeowMotion.BASE)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = meowTween(MeowMotion.BASE, MeowMotion.easeOut),
                ) + fadeOut(meowTween(MeowMotion.FAST)),
            ) {
                Surface(
                    modifier = modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = if (LocalAppStyle.current == AppStyle.TERMINAL) {
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    } else {
                        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                    },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 22.dp)
                            .padding(top = 12.dp, bottom = 20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline),
                        )
                        Spacer(Modifier.height(18.dp))
                        content()
                    }
                }
            }
        }
    }
}
