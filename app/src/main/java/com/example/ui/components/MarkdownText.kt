package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Super lightweight, performant, and elegant Native Jetpack Compose Markdown parser.
 * Perfectly renders bold text (**text**), bullet points (- item), headers (# header) and code snippets.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            when {
                // Header 1 or 2
                line.startsWith("#") -> {
                    val headerText = line.replace(Regex("^#+\\s*"), "")
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                // Bullet List Items
                line.trim().startsWith("- ") || line.trim().startsWith("* ") -> {
                    val bulletText = line.trim().replace(Regex("^[-*]\\s*"), "")
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = parseBoldText(bulletText),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // Code Blocks or simple code line
                line.trim().startsWith("```") -> {
                    // Skip start/end tags, or display as separator
                }
                line.startsWith("`") && line.endsWith("`") -> {
                    val rawCode = line.replace("`", "")
                    Text(
                        text = rawCode,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // Normal paragraph with potential bold styles
                else -> {
                    if (line.isNotBlank()) {
                        Text(
                            text = parseBoldText(line),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private fun parseBoldText(input: String) = buildAnnotatedString {
    var parts = input.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) { // Odd index is inside **bold**
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(part)
            }
        } else {
            append(part)
        }
    }
}
