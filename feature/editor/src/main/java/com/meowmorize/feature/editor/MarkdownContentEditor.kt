package com.meowmorize.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.meowmorize.core.designsystem.component.MeowChip
import com.meowmorize.core.designsystem.theme.LocalAppStyle
import com.meowmorize.core.model.AppStyle

/**
 * Markdown 内容编辑器。
 *
 * 这里保留 [TextFieldValue] 才能在光标处插入语法，而不是把符号拼到末尾。
 * 快捷按钮覆盖背诵场景最常用的五种结构：标题、加粗、列表、引用、两栏对照。
 */
@Composable
fun MarkdownContentEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    minLines: Int = 9,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }

    // 外部内容变化（例如 OCR 追加）时同步，避免光标和文本脱节
    LaunchedEffect(value) {
        if (fieldValue.text != value) {
            fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    fun update(newValue: TextFieldValue) {
        fieldValue = newValue
        onValueChange(newValue.text)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MeowChip(
                text = "标题",
                selected = false,
                onClick = { update(insertLinePrefix(fieldValue, "## ")) },
            )
            MeowChip(
                text = "加粗",
                selected = false,
                onClick = { update(wrapSelection(fieldValue, "**", "**", "重点")) },
            )
            MeowChip(
                text = "列表",
                selected = false,
                onClick = { update(insertLinePrefix(fieldValue, "- ")) },
            )
            MeowChip(
                text = "引用",
                selected = false,
                onClick = { update(insertLinePrefix(fieldValue, "> ")) },
            )
            MeowChip(
                text = "两栏",
                selected = false,
                onClick = { update(insertBlock(fieldValue, TWO_COLUMN_TEMPLATE)) },
            )
        }

        OutlinedTextField(
            value = fieldValue,
            onValueChange = { update(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("背诵内容（Markdown）", style = MaterialTheme.typography.labelLarge) },
            placeholder = {
                Text(
                    "# 标题\n- 要点一\n- 要点二\n\n> 一句话记忆钩子",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            minLines = minLines,
            maxLines = 18,
            shape = if (LocalAppStyle.current == AppStyle.TERMINAL) {
                RoundedCornerShape(4.dp)
            } else {
                RoundedCornerShape(18.dp)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Text(
            text = "支持 # 标题、- 列表、> 引用、**加粗**；「两栏」会插入对照表",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        )
    }
}

private const val TWO_COLUMN_TEMPLATE = """| 原文 / 提示 | 释义 / 答案 |
| --- | --- |
|  |  |
|  |  |
"""

/** 在光标所在行的行首插入前缀（标题、列表、引用）。 */
private fun insertLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let {
        if (it == -1) 0 else it + 1
    }
    val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
    val newCursor = cursor + prefix.length
    return value.copy(text = newText, selection = TextRange(newCursor))
}

/** 用标记包裹选中内容；没有选区时插入占位词并选中它，方便直接替换。 */
private fun wrapSelection(
    value: TextFieldValue,
    prefix: String,
    suffix: String,
    placeholder: String,
): TextFieldValue {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)
    val selected = text.substring(start, end).ifEmpty { placeholder }
    val newText = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
    val selectionStart = start + prefix.length
    return value.copy(
        text = newText,
        selection = TextRange(selectionStart, selectionStart + selected.length),
    )
}

/** 插入整块模板，前后各留一个空行，避免和上一段粘在一起。 */
private fun insertBlock(value: TextFieldValue, block: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.start.coerceIn(0, text.length)
    val needsLeadingBreak = cursor > 0 && text[cursor - 1] != '\n'
    val insertion = (if (needsLeadingBreak) "\n\n" else "") + block
    val newText = text.substring(0, cursor) + insertion + text.substring(cursor)
    val newCursor = cursor + insertion.length
    return value.copy(text = newText, selection = TextRange(newCursor))
}
