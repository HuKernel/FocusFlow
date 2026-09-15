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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 柔和高级芯片 - 用于筛选、选择、标签等
 * 特征：选中时渐变背景 + 微妙动效 + 充足留白
 */
@Composable
fun FocusChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    var isPressed by mutableStateOf(false)
    val interactionSource = remember { MutableInteractionSource() }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chip_scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFFF0F0F0)
            selected -> FocusColors.Primary
            else -> Color.White
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_background"
    )
    
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF9CA3AF)
            selected -> Color.White
            else -> Color(0xFF1A1A2E)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_text"
    )
    
    val borderColor = when {
        !enabled -> Color(0xFFE0E0E0)
        selected -> FocusColors.Primary
        else -> Color(0xFFE8E8F0)
    }
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor, shape = RoundedCornerShape(20.dp))
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.scale(0.9f)
                )
            }
            Text(
                text = label,
                color = textColor,
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * 紧凑芯片 - 用于标签、分类等
 */
@Composable
fun FocusChipCompact(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
) {
    var isPressed by mutableStateOf(false)
    val interactionSource = remember { MutableInteractionSource() }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chip_scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFFF0F0F0)
            selected -> FocusColors.Primary.copy(alpha = 0.15f)
            else -> Color(0xFFF5F5F7)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_background"
    )
    
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF9CA3AF)
            selected -> FocusColors.Primary
            else -> Color(0xFF6B7280)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_text"
    )
    
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                enabled = enabled
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
    }
}
