package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NUMBERED_LIST_REGEX = Regex("^\\d+\\.\\s.*")
private val NUMBERED_PREFIX_REGEX = Regex("^(\\d+\\.)\\s")
private val MARKDOWN_INLINE_REGEX = Regex("(\\*\\*\\*(.*?)\\*\\*\\*|\\*\\*(.*?)\\*\\*|\\*([^*\\n]+?)\\*|`(.*?)`|__([^_\\n]+?)__|(?<=\\s|^)_([^_\\n]+?)_(?=\\s|$))")

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class BulletItem(val text: String, val indentDp: Dp) : MarkdownBlock()
    data class NumberedItem(val prefix: String, val text: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    object EmptyLine : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

/**
 * Phân tích cấu trúc khối Markdown trước khi vẽ giao diện (tránh lỗi Composable context)
 */
fun parseMarkdownToBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = markdown.split("\n")
    val currentQuoteLines = mutableListOf<String>()

    fun flushQuote() {
        if (currentQuoteLines.isNotEmpty()) {
            blocks.add(MarkdownBlock.Quote(currentQuoteLines.joinToString("\n")))
            currentQuoteLines.clear()
        }
    }

    for (rawLine in lines) {
        val line = rawLine.trimEnd()
        val trimmed = line.trimStart()

        if (trimmed.startsWith(">")) {
            currentQuoteLines.add(trimmed.removePrefix(">").trimStart())
            continue
        } else if (currentQuoteLines.isNotEmpty()) {
            flushQuote()
        }

        when {
            trimmed.isEmpty() -> {
                blocks.add(MarkdownBlock.EmptyLine)
            }
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks.add(MarkdownBlock.Divider)
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("• ") || trimmed.startsWith("- ") ||
            (trimmed.startsWith("* ") && !trimmed.startsWith("**")) || trimmed.startsWith("+ ") -> {
                val itemText = trimmed.substring(2).trim()
                val indent = (line.length - trimmed.length).coerceAtLeast(0) / 2
                val indentDp = (indent * 12 + 4).dp
                blocks.add(MarkdownBlock.BulletItem(itemText, indentDp))
            }
            trimmed.matches(NUMBERED_LIST_REGEX) -> {
                val prefixMatch = NUMBERED_PREFIX_REGEX.find(trimmed)
                val numPrefix = prefixMatch?.groupValues?.get(1) ?: "•"
                val itemText = trimmed.substring(numPrefix.length).trim()
                blocks.add(MarkdownBlock.NumberedItem(numPrefix, itemText))
            }
            else -> {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
    }

    flushQuote()
    return blocks
}

/**
 * Trình dựng văn bản Markdown phong cách Word:
 * Hiển thị chữ đậm, nghiêng, đậm nghiêng, tiêu đề, danh sách có bullet, code pill, blockquote...
 */
@Composable
fun RichMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    isUser: Boolean = false
) {
    val blocks = parseMarkdownToBlocks(markdown)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MarkdownBlock.EmptyLine -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = if (isUser) color.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }

                is MarkdownBlock.Heading -> {
                    val typography = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    val headingColor = if (isUser) color else {
                        if (block.level <= 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    }
                    Text(
                        text = parseMarkdownInline(block.text, isUser),
                        style = typography.copy(
                            fontWeight = FontWeight.Bold,
                            color = headingColor
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = block.indentDp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = baseTextStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) color else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownInline(block.text, isUser),
                            style = baseTextStyle.copy(
                                color = color,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.prefix,
                            style = baseTextStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) color else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownInline(block.text, isUser),
                            style = baseTextStyle.copy(
                                color = color,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Quote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = if (isUser) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(
                                    color = if (isUser) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseMarkdownInline(block.text, isUser),
                            style = baseTextStyle.copy(
                                fontStyle = FontStyle.Italic,
                                color = color
                            )
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseMarkdownInline(block.text, isUser),
                        style = baseTextStyle.copy(
                            color = color,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Phân tích các cú pháp Markdown nội dòng:
 * - ***đậm nghiêng***
 * - **đậm**
 * - __đậm__
 * - *nghiêng*
 * - `mã/phiên âm`
 */
fun parseMarkdownInline(text: String, isUser: Boolean = false): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0

        MARKDOWN_INLINE_REGEX.findAll(text).forEach { matchResult ->
            val range = matchResult.range
            if (range.first > lastIndex) {
                append(text.substring(lastIndex, range.first))
            }

            val fullMatch = matchResult.value
            when {
                // ***Bold Italic***
                fullMatch.startsWith("***") && fullMatch.endsWith("***") && fullMatch.length >= 6 -> {
                    val inner = fullMatch.substring(3, fullMatch.length - 3)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                }

                // **Bold**
                fullMatch.startsWith("**") && fullMatch.endsWith("**") && fullMatch.length >= 4 -> {
                    val inner = fullMatch.substring(2, fullMatch.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(inner)
                    }
                }

                // __Bold__
                fullMatch.startsWith("__") && fullMatch.endsWith("__") && fullMatch.length >= 4 -> {
                    val inner = fullMatch.substring(2, fullMatch.length - 2)
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(inner)
                    }
                }

                // `inline code / pronunciation`
                fullMatch.startsWith("`") && fullMatch.endsWith("`") && fullMatch.length >= 2 -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            background = if (isUser) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.08f)
                        )
                    ) {
                        append(" $inner ")
                    }
                }

                // *Italic*
                fullMatch.startsWith("*") && fullMatch.endsWith("*") && fullMatch.length >= 2 -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                }

                // _Italic_
                fullMatch.startsWith("_") && fullMatch.endsWith("_") && fullMatch.length >= 2 -> {
                    val inner = fullMatch.substring(1, fullMatch.length - 1)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                }

                else -> append(fullMatch)
            }

            lastIndex = range.last + 1
        }

        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
