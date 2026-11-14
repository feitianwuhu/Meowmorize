package com.meowmorize.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 轻量 Markdown 渲染（**子集**）。
 *
 * 支持：标题、无序列表、引用、分隔线、表格（用于「两栏对照」）、粗体 / 斜体 / 行内代码。
 * 刻意不引第三方渲染库：背诵场景只需要结构化，不需要完整 CommonMark 兼容；
 * 自己实现可以精确控制字号、行距与暗色对比度，也不增加包体和版本维护成本。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            if (index > 0) Spacer(Modifier.height(blockSpacing(blocks[index - 1], block)))
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor,
                )
                is MdBlock.Paragraph -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                )
                is MdBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 9.dp, end = 10.dp)
                            .size(4.dp)
                            .background(textColor.copy(alpha = 0.6f), RoundedCornerShape(1.dp)),
                    )
                    Text(
                        text = inline(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                    )
                }
                is MdBlock.Quote -> Row {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = inline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = mutedColor,
                    )
                }
                MdBlock.Divider -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
                is MdBlock.Table -> MarkdownTable(
                    rows = block.rows,
                    textColor = textColor,
                    mutedColor = mutedColor,
                )
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    rows: List<List<String>>,
    textColor: Color,
    mutedColor: Color,
) {
    val columnCount = rows.maxOfOrNull { it.size } ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
    ) {
        rows.forEachIndexed { rowIndex, cells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until columnCount) {
                    val cell = cells.getOrElse(column) { "" }
                    Text(
                        text = inline(cell),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (rowIndex == 0) FontWeight.Medium else FontWeight.Normal,
                        ),
                        color = if (rowIndex == 0) mutedColor else textColor,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
            }
            if (rowIndex < rows.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                )
            }
        }
    }
}

private fun blockSpacing(previous: MdBlock, current: MdBlock): androidx.compose.ui.unit.Dp = when {
    previous is MdBlock.Heading -> 6.dp
    current is MdBlock.Heading -> 14.dp
    current is MdBlock.Table -> 10.dp
    else -> 8.dp
}

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Table(val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

private fun parseMarkdown(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    var index = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    while (index < lines.size) {
        val raw = lines[index]
        val line = raw.trim()
        when {
            line.isEmpty() -> {
                flushParagraph()
                index++
            }
            line.startsWith("|") -> {
                flushParagraph()
                val tableLines = mutableListOf<String>()
                while (index < lines.size && lines[index].trim().startsWith("|")) {
                    tableLines += lines[index].trim()
                    index++
                }
                val rows = tableLines
                    .filterNot { it.matches(Regex("^\\|[\\s:|-]+\\|?$")) }
                    .map { row ->
                        row.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
                    }
                if (rows.isNotEmpty()) blocks += MdBlock.Table(rows)
            }
            line.startsWith("### ") -> {
                flushParagraph()
                blocks += MdBlock.Heading(3, line.removePrefix("### ").trim())
                index++
            }
            line.startsWith("## ") -> {
                flushParagraph()
                blocks += MdBlock.Heading(2, line.removePrefix("## ").trim())
                index++
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks += MdBlock.Heading(1, line.removePrefix("# ").trim())
                index++
            }
            line == "---" || line == "***" -> {
                flushParagraph()
                blocks += MdBlock.Divider
                index++
            }
            line.startsWith("> ") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.removePrefix("> ").trim())
                index++
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                flushParagraph()
                blocks += MdBlock.Bullet(line.drop(2).trim())
                index++
            }
            line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                flushParagraph()
                blocks += MdBlock.Bullet(line.replaceFirst(Regex("^\\d+\\.\\s+"), ""))
                index++
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
                index++
            }
        }
    }
    flushParagraph()
    return blocks
}

/** 行内格式：**粗体**、*斜体*、`代码`。 */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0x1A888888),
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            text[i] == '*' && i + 1 < text.length -> {
                val end = text.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
