package com.ba.aiagent.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ba.aiagent.ui.theme.DiffGreen
import com.ba.aiagent.ui.theme.DiffRed
import com.ba.aiagent.ui.theme.MonoFamily
import com.ba.aiagent.ui.theme.TerminalGreen
import com.ba.aiagent.ui.theme.WarningAmber

/** Very small Markdown renderer: code blocks, headers, bold, plain text. No extra dependency. */
@Composable
fun SimpleMarkdown(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val codeStart = remaining.indexOf("```")
            if (codeStart < 0) {
                TextBlocks(remaining)
                break
            }
            if (codeStart > 0) TextBlocks(remaining.substring(0, codeStart))
            val afterFence = remaining.substring(codeStart + 3)
            val langEnd = afterFence.indexOf('\n')
            val lang = if (langEnd >= 0) afterFence.substring(0, langEnd).trim() else ""
            val body = if (langEnd >= 0) afterFence.substring(langEnd + 1) else ""
            val codeEnd = body.indexOf("```")
            if (codeEnd < 0) {
                CodeBlock(code = body, language = lang)
                break
            }
            CodeBlock(code = body.substring(0, codeEnd), language = lang)
            remaining = body.substring(codeEnd + 3)
        }
    }
}

@Composable
private fun TextBlocks(text: String) {
    text.lines().forEach { line ->
        when {
            line.startsWith("### ") -> Text(line.removePrefix("### "), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
            line.startsWith("## ") -> Text(line.removePrefix("## "), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(vertical = 2.dp))
            line.startsWith("# ") -> Text(line.removePrefix("# "), fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(vertical = 2.dp))
            line.startsWith("- ") || line.startsWith("* ") -> Text("• " + line.substring(2), modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp))
            line.isBlank() -> Spacer(Modifier.height(6.dp))
            else -> Text(stripBold(line), modifier = Modifier.padding(vertical = 1.dp))
        }
    }
}

private fun stripBold(s: String): String = s.replace("**", "").replace("__", "")

@Composable
fun CodeBlock(code: String, language: String) {
    val clipboard = LocalClipboardManager.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E13)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { "code" }, color = TerminalGreen, fontFamily = MonoFamily, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.padding(end = 4.dp))
                    Text("Copy")
                }
            }
            SelectionContainer {
                Text(
                    code.trimEnd(), fontFamily = MonoFamily, fontSize = 13.sp,
                    color = Color(0xFFE6EDF3),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
fun MessageBubble(content: String, isUser: Boolean, isStreaming: Boolean = false) {
    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 2 }) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                SimpleMarkdown(content)
                if (isStreaming) Text("▍", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ToolCallCard(tool: String, summary: String, status: String, result: String) {
    val color = when (status) {
        "Done" -> DiffGreen
        "Failed" -> DiffRed
        else -> WarningAmber
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(8.dp).height(8.dp).background(color, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Text("⚙ $tool", fontWeight = FontWeight.Bold, fontFamily = MonoFamily, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (summary.isNotBlank()) Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            if (result.isNotBlank()) Text(result.take(500), fontFamily = MonoFamily, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("BA", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 12.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ApiKeyField(value: String, onChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Show/hide")
            }
        }
    )
}
