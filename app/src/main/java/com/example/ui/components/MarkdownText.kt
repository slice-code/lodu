package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownNode {
    data class Header(val level: Int, val text: String) : MarkdownNode()
    data class BulletItem(val text: String) : MarkdownNode()
    data class CodeBlock(val language: String?, val code: String) : MarkdownNode()
    data class Paragraph(val text: String) : MarkdownNode()
}

/**
 * Super lightweight, performant, and elegant Native Jetpack Compose Markdown parser.
 * Renders bold text (**text**), bullet points (- item), headers (# header), multiline code blocks, and inline code.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val nodes = parseMarkdown(text)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node ->
            when (node) {
                is MarkdownNode.Header -> {
                    val style = when (node.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    }
                    Text(
                        text = node.text,
                        style = style,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                is MarkdownNode.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = parseInlineStyles(node.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MarkdownNode.CodeBlock -> {
                    CodeBlockView(language = node.language, code = node.code)
                }
                is MarkdownNode.Paragraph -> {
                    if (node.text.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Text(
                            text = parseInlineStyles(node.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(language: String?, code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(12.dp))
    ) {
        // Language Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language?.uppercase() ?: "CODE",
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "⚡ Offline",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Code content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = code,
                color = Color(0xFFE0E0E0),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
        }
    }
}

private fun parseMarkdown(text: String): List<MarkdownNode> {
    // Unescape literal \n into real newlines
    val normalized = text.replace("\\n", "\n")
    val lines = normalized.split("\n")
    val nodes = mutableListOf<MarkdownNode>()
    var inCodeBlock = false
    var codeLanguage: String? = null
    val currentCode = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                nodes.add(MarkdownNode.CodeBlock(codeLanguage, currentCode.toString().trimEnd()))
                currentCode.clear()
                inCodeBlock = false
                codeLanguage = null
            } else {
                inCodeBlock = true
                codeLanguage = trimmed.removePrefix("```").trim().ifEmpty { null }
            }
        } else if (inCodeBlock) {
            currentCode.append(line).append("\n")
        } else {
            when {
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length
                    val headerText = line.substring(level).trim()
                    nodes.add(MarkdownNode.Header(level, headerText))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletText = trimmed.substring(2).trim()
                    nodes.add(MarkdownNode.BulletItem(bulletText))
                }
                else -> {
                    nodes.add(MarkdownNode.Paragraph(line))
                }
            }
        }
    }
    if (inCodeBlock && currentCode.isNotEmpty()) {
        nodes.add(MarkdownNode.CodeBlock(codeLanguage, currentCode.toString().trimEnd()))
    }
    return nodes
}

@Composable
private fun parseInlineStyles(input: String): androidx.compose.ui.text.AnnotatedString {
    val codeColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    
    return buildAnnotatedString {
        var i = 0
        while (i < input.length) {
            if (input.startsWith("**", i)) {
                val nextIndex = input.indexOf("**", i + 2)
                if (nextIndex != -1) {
                    val boldText = input.substring(i + 2, nextIndex)
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                    i = nextIndex + 2
                } else {
                    append("**")
                    i += 2
                }
            } else if (input[i] == '`') {
                val nextIndex = input.indexOf('`', i + 1)
                if (nextIndex != -1) {
                    val codeText = input.substring(i + 1, nextIndex)
                    withStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBg,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(codeText)
                    }
                    i = nextIndex + 1
                } else {
                    append("`")
                    i += 1
                }
            } else {
                append(input[i])
                i += 1
            }
        }
    }
}
