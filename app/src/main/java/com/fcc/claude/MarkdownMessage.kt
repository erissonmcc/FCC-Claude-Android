package com.fcc.claude

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import io.getstream.chat.android.ai.compose.ui.component.StreamingText

@Composable
internal fun StreamMessageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
internal fun MarkdownMessage(text: String, animate: Boolean) {
    StreamMessageTheme {
        StreamingText(text = text, animate = animate)
    }
}
