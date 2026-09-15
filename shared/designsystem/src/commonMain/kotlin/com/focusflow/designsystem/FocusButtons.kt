package com.focusflow.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// ============================================================================
// 柔和高级按钮系统
// 设计原则：微妙渐变 + 柔和阴影 + 弹性动效 + 充足留白
// ============================================================================

/**
 * 主按钮 - 用于最重要的操作（开始专注、保存等）
 * 特征：渐变背景 + 柔和阴影 + 按下缩放动效
 */
@Composable
fun FocusPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val prefs by LocalFocusFeedback.current.prefs.collectAsState()
    var isPressed by mutableStateOf(false)

    val scale by animateFloatAsState(
        targetValue = if (isPressed && prefs.reducedMotion.not()) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    // 渐变背景：从主色到主色深
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            FocusColors.Primary,
            FocusColors.PrimaryDeep
        )
    )

    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (enabled) 12.dp else 0.dp,
                shape = FocusShapes.button,
                ambientColor = FocusColors.Primary.copy(alpha = 0.3f),
                spotColor = FocusColors.Primary.copy(alpha = 0.2f)
            )
            .background(
                brush = if (enabled) gradientBrush else Brush.verticalGradient(
                    listOf(Color(0xFFD0D0D0), Color(0xFFB0B0B0))
                ),
                shape = FocusShapes.button
            )
            .height(56.dp)
            .padding(horizontal = 32.dp)
            .clip(FocusShapes.button)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * 次要按钮 - 用于次要操作（取消、跳过等）
 * 特征：描边 + 透明背景 + 柔和动效
 */
@Composable
fun FocusSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val prefs by LocalFocusFeedback.current.prefs.collectAsState()
    var isPressed by mutableStateOf(false)

    val scale by animateFloatAsState(
        targetValue = if (isPressed && prefs.reducedMotion.not()) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "button_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) FocusColors.Primary.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "button_background"
    )

    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundColor, shape = FocusShapes.button)
            .border(
                width = 1.5.dp,
                color = if (enabled) FocusColors.Primary else FocusColors.Muted.copy(alpha = 0.5f),
                shape = FocusShapes.button
            )
            .height(56.dp)
            .padding(horizontal = 32.dp)
            .clip(FocusShapes.button)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = if (enabled) FocusColors.Primary else FocusColors.Muted,
                    modifier = Modifier.size(24.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * 文字按钮 - 用于轻量操作（返回、取消等）
 * 特征：纯文字 + 微妙背景 + 快速动效
 */
@Composable
fun FocusTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val prefs by LocalFocusFeedback.current.prefs.collectAsState()
    var isPressed by mutableStateOf(false)

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) FocusColors.Primary.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "button_background"
    )

    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .background(backgroundColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
