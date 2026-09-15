package com.focusflow.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 柔和高级输入框
 * 特征：聚焦时边框变色 + 微妙阴影 + 充足留白
 */
@Composable
fun FocusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = if (label != null) {{ Text(label) }} else null,
        placeholder = if (placeholder != null) {{ Text(placeholder, color = Color(0xFF9CA3AF)) }} else null,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = FocusColors.Primary,
            unfocusedIndicatorColor = Color(0xFFE8E8F0),
            disabledIndicatorColor = Color(0xFFE0E0E0),
            focusedTextColor = Color(0xFF1A1A2E),
            unfocusedTextColor = Color(0xFF1A1A2E),
            disabledTextColor = Color(0xFF9CA3AF),
            cursorColor = FocusColors.Primary,
            focusedLabelColor = FocusColors.Primary,
            unfocusedLabelColor = Color(0xFF6B7280),
            disabledLabelColor = Color(0xFF9CA3AF),
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

/**
 * 数字输入框 - 用于专注时长、休息时长等
 */
@Composable
fun FocusNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "0",
    suffix: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FocusTextField(
            value = value,
            onValueChange = { newValue ->
                // 只允许数字
                if (newValue.all { it.isDigit() }) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
        )
        
        if (suffix != null) {
            Text(
                text = suffix,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF6B7280),
                modifier = Modifier.padding(top = if (label != null) 28.dp else 0.dp)
            )
        }
    }
}
