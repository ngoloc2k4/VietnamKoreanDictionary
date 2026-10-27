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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Trình dựng văn bản Markdown phong cách Word:
 * Hỗ trợ chuyển đổi tự động các định dạng Markdown của AI Model thành văn bản giàu định dạng:
 * - Tiêu đề (Headings #, ##, ###) với kích thước và màu sắc phân cấp chuẩn
 * - In đậm (**text**), in nghiêng (*text*), đậm nghiêng (***text***)
 * - Khối mã hoặc phiên âm inline (`code`) dạng pill chuyên nghiệp
 * - Danh sách gạch đầu dòng (•, -, *) và danh sách số (1., 2.) với thụt lề chuẩn
 * - Khối trích dẫn / ghi chú (> quote)
 * - Đường kẻ phân cách (---)
 */
@Composable
fun RichMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    isUser: Boolean = false
) {
    val lines = markdown.split("\n")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var inQuoteBlock = false
        val currentQuoteLines = mutableListOf<String>()

        fun flushQuote() {
            if (currentQuoteLines.isNotEmpty()) {
                val quoteText = currentQuoteLines.joinToString("\n")
                QuoteBlock(
                    text = quoteText,
                    color = color,
                    baseTextStyle = baseTextStyle,
                    isUser = isUser
                )
                currentQuoteLines.clear()
            }
            inQuoteBlock = false
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            val trimmed = line.trimStart()

            // Blockquote handling
            if (trimmed.startsWith(">")) {
                inQuoteBlock = true
                currentQuoteLines.add(trimmed.removePrefix(">").trimStart())
                continue
            } else if (inQuoteBlock) {
                flushQuote()
            }

            when {
                // Empty line / paragraph break
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Horizontal Rule
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = if (isUser) color.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }

                // Heading 1
                trimmed.startsWith("# ") -> {
                    val headingText = trimmed.removePrefix("# ").trim()
                    Text(
                        text = parseMarkdownInline(headingText, isUser),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) color else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                // Heading 2
                trimmed.startsWith("## ") -> {
                    val headingText = trimmed.removePrefix("## ").trim()
                    Text(
                        text = parseMarkdownInline(headingText, isUser),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) color else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // Heading 3
                trimmed.startsWith("### ") -> {
                    val headingText = trimmed.removePrefix("### ").trim()
                    Text(
                        text = parseMarkdownInline(headingText, isUser),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) color else MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                // Bullet List (•, -, *, +)
                trimmed.startsWith("• ") || trimmed.startsWith("- ") ||
                (trimmed.startsWith("* ") && !trimmed.startsWith("**")) || trimmed.startsWith("+ ") -> {
                    val bulletMarker = "•"
                    val itemText = trimmed.substring(2).trim()
                    val indent = (line.length - trimmed.length).coerceAtLeast(0) / 2
                    val indentDp = (indent * 12 + 4).dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentDp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = bulletMarker,
                            style = baseTextStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) color else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownInline(itemText, isUser),
                            style = baseTextStyle.copy(
                                color = color,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Numbered List (1., 2., 3., etc.)
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val prefixMatch = Regex("^(\\d+\\.)\\s").find(trimmed)
                    val numPrefix = prefixMatch?.groupValues?.get(1) ?: "•"
                    val itemText = trimmed.substring(numPrefix.length).trim()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 1.dp, bottom = 1.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = numPrefix,
                            style = baseTextStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) color else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseMarkdownInline(itemText, isUser),
                            style = baseTextStyle.copy(
                                color = color,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Normal paragraph text
                else -> {
                    Text(
                        text = parseMarkdownInline(line, isUser),
                        style = baseTextStyle.copy(
                            color = color,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
        }

        if (inQuoteBlock) {
            flushQuote()
        }
    }
}

@Composable
private fun QuoteBlock(
    text: String,
    color: Color,
    baseTextStyle: TextStyle,
    isUser: Boolean
) {
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
            text = parseMarkdownInline(text, isUser),
            style = baseTextStyle.copy(
                fontStyle = FontStyle.Italic,
                color = color
            )
        )
    }
}

/**
 * Phân tích các cú pháp Markdown nội dòng:
 * - ***đậm nghiêng***
 * - **đậm**
 * - *nghiêng*
 * - `mã/phiên âm`
 */
fun parseMarkdownInline(text: String, isUser: Boolean = false): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Regex("(\\*\\*\\*(.*?)\\*\\*\\*|\\*\\*(.*?)\\*\\*|\\*([^*\\n]+?)\\*|`(.*?)`|__([^_\\n]+?)__|(?<=\\s|^)_([^_\\n]+?)_(?=\\s|$))")
        var lastIndex = 0

        pattern.findAll(text).forEach { matchResult ->
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
