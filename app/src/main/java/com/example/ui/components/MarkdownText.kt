package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString

sealed class MarkdownNode {
    data class Header(val level: Int, val text: String) : MarkdownNode()
    data class BulletItem(val text: String) : MarkdownNode()
    data class CodeBlock(val language: String?, val code: String) : MarkdownNode()
    data class MathBlock(val formula: String) : MarkdownNode()
    data class Paragraph(val text: String) : MarkdownNode()
}

/**
 * Super lightweight, performant, and elegant Native Jetpack Compose Markdown parser.
 * Renders bold text (**text**), bullet points (- item), headers (# header), multiline code blocks,
 * inline code, and mathematical formulas (LaTeX block & inline equations).
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val nodes = remember(text) { parseMarkdown(text) }
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
                is MarkdownNode.MathBlock -> {
                    MathBlockView(formula = node.formula)
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
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
            
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
                    android.widget.Toast.makeText(context, "Kode disalin ke clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = "Salin",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
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

@Composable
fun MathBlockView(formula: String) {
    val cleanedFormula = remember(formula) { cleanMathFormula(formula) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(
                text = "🧮",
                fontSize = 14.sp
            )
            Text(
                text = "Persamaan Matematika",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Text(
            text = cleanedFormula,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 26.sp
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

fun cleanMathFormula(formula: String): String {
    var result = formula
        .replace("\\[", "")
        .replace("\\]", "")
        .replace("\\(", "")
        .replace("\\)", "")
        
    // Greek Letters translation
    val greekLetters = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ",
        "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ",
        "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
        "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "omega",
        "\\Delta" to "Δ", "\\Sigma" to "Σ", "\\Omega" to "Ω", "\\Phi" to "Φ",
        "\\Pi" to "Π", "\\Lambda" to "Λ"
    )
    for ((latex, unicode) in greekLetters) {
        result = result.replace(latex, unicode)
    }

    // Mathematical Operators & Relations
    val mathSymbols = mapOf(
        "\\times" to "×", "\\div" to "÷", "\\pm" to "±", "\\mp" to "∓",
        "\\infty" to "∞", "\\approx" to "≈", "\\neq" to "≠", "\\equiv" to "≡",
        "\\leq" to "≤", "\\geq" to "≥", "\\le" to "≤", "\\ge" to "≥",
        "\\sum" to "∑", "\\prod" to "∏", "\\coprod" to "∐",
        "\\int" to "∫", "\\iint" to "∬", "\\iiint" to "∭",
        "\\partial" to "∂", "\\nabla" to "∇", "\\cdot" to "·",
        "\\forall" to "∀", "\\exists" to "exists", "\\in" to "∈", "\\notin" to "∉",
        "\\subset" to "⊂", "\\supset" to "⊃", "\\subseteq" to "⊆", "\\supseteq" to "⊇",
        "\\cap" to "∩", "\\cup" to "∪", "\\rightarrow" to "→", "\\leftarrow" to "←",
        "\\implies" to "⇒", "\\iff" to "⇔"
    )
    for ((latex, unicode) in mathSymbols) {
        result = result.replace(latex, unicode)
    }

    // Regex for fractions: \frac{a}{b} -> (a)/(b)
    val fracRegex = Regex("\\\\frac\\{([^}]+)\\}\\{([^}]+)\\}")
    result = fracRegex.replace(result) { matchResult ->
        val numerator = matchResult.groupValues[1]
        val denominator = matchResult.groupValues[2]
        "($numerator)/($denominator)"
    }

    // Regex for square root: \sqrt{a} -> √(a)
    val sqrtRegex = Regex("\\\\sqrt\\{([^}]+)\\}")
    result = sqrtRegex.replace(result) { matchResult ->
        val content = matchResult.groupValues[1]
        "√($content)"
    }
    
    // Superscripts
    val superscripts = mapOf(
        "^0" to "⁰", "^1" to "¹", "^2" to "²", "^3" to "³", "^4" to "⁴",
        "^5" to "⁵", "^6" to "⁶", "^7" to "⁷", "^8" to "⁸", "^9" to "⁹",
        "^+" to "⁺", "^-" to "⁻", "^=" to "⁼", "^(" to "⁽", "^)" to "⁾",
        "^n" to "ⁿ", "^x" to "ˣ", "^i" to "ⁱ"
    )
    for ((latex, unicode) in superscripts) {
        result = result.replace(latex, unicode)
    }

    // Subscripts
    val subscripts = mapOf(
        "_0" to "₀", "_1" to "₁", "_2" to "₂", "_3" to "₃", "_4" to "₄",
        "_5" to "₅", "_6" to "₆", "_7" to "₇", "_8" to "₈", "_9" to "₉",
        "_+" to "₊", "_-" to "₋", "_=" to "₌", "_(" to "₍", "_)" to "₎",
        "_n" to "ₙ", "_x" to "ₓ", "_y" to "ᵧ", "_i" to "ᵢ", "_j" to "ⱼ"
    )
    for ((latex, unicode) in subscripts) {
        result = result.replace(latex, unicode)
    }

    // Clean remaining braces from sub/superscripts e.g. ^{abc} -> ⁽abc⁾, _{abc} -> ₍abc₎
    result = result.replace("^{", "⁽").replace("_{", "₍").replace("}", "⁾").replace("}", "₎")

    return result
}

private fun parseMarkdown(text: String): List<MarkdownNode> {
    val normalized = text.replace("\\n", "\n")
    val lines = normalized.split("\n")
    val nodes = mutableListOf<MarkdownNode>()
    var inCodeBlock = false
    var codeLanguage: String? = null
    val currentCode = StringBuilder()

    var inMathBlock = false
    val currentMath = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()
        
        // 1. If in code block
        if (inCodeBlock) {
            if (trimmed.startsWith("```")) {
                nodes.add(MarkdownNode.CodeBlock(codeLanguage, currentCode.toString().trimEnd()))
                currentCode.clear()
                inCodeBlock = false
                codeLanguage = null
            } else {
                currentCode.append(line).append("\n")
            }
            continue
        }
        
        // 2. If in math block
        if (inMathBlock) {
            if (trimmed.endsWith("$$") || trimmed.endsWith("\\]")) {
                val endLen = if (trimmed.endsWith("$$")) 2 else 2
                val lineContent = trimmed.substring(0, trimmed.length - endLen).trim()
                if (lineContent.isNotEmpty()) {
                    currentMath.append(lineContent)
                }
                nodes.add(MarkdownNode.MathBlock(currentMath.toString().trimEnd()))
                currentMath.clear()
                inMathBlock = false
            } else {
                currentMath.append(line).append("\n")
            }
            continue
        }
        
        // 3. Normal parsing
        if (trimmed.startsWith("```")) {
            inCodeBlock = true
            codeLanguage = trimmed.removePrefix("```").trim().ifEmpty { null }
        } else if (trimmed.startsWith("$$") || trimmed.startsWith("\\[")) {
            val isBackslashSquare = trimmed.startsWith("\\[")
            val endDelimiter = if (isBackslashSquare) "\\]" else "$$"
            val startDelimiterLength = 2
            
            val withoutStart = trimmed.substring(startDelimiterLength).trim()
            if (withoutStart.endsWith(endDelimiter)) {
                val mathContent = withoutStart.substring(0, withoutStart.length - startDelimiterLength).trim()
                nodes.add(MarkdownNode.MathBlock(mathContent))
            } else {
                inMathBlock = true
                if (withoutStart.isNotEmpty()) {
                    currentMath.append(withoutStart).append("\n")
                }
            }
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
    
    // Handle unclosed blocks gracefully
    if (inCodeBlock && currentCode.isNotEmpty()) {
        nodes.add(MarkdownNode.CodeBlock(codeLanguage, currentCode.toString().trimEnd()))
    }
    if (inMathBlock && currentMath.isNotEmpty()) {
        nodes.add(MarkdownNode.MathBlock(currentMath.toString().trimEnd()))
    }
    
    return nodes
}

@Composable
private fun parseInlineStyles(input: String): AnnotatedString {
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
            } else if (input[i] == '$') {
                val nextIndex = input.indexOf('$', i + 1)
                if (nextIndex != -1) {
                    val mathText = input.substring(i + 1, nextIndex)
                    val cleanedMath = cleanMathFormula(mathText)
                    withStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(cleanedMath)
                    }
                    i = nextIndex + 1
                } else {
                    append("$")
                    i += 1
                }
            } else if (input.startsWith("\\(", i)) {
                val nextIndex = input.indexOf("\\)", i + 2)
                if (nextIndex != -1) {
                    val mathText = input.substring(i + 2, nextIndex)
                    val cleanedMath = cleanMathFormula(mathText)
                    withStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(cleanedMath)
                    }
                    i = nextIndex + 2
                } else {
                    append("\\(")
                    i += 2
                }
            } else {
                append(input[i])
                i += 1
            }
        }
    }
}
