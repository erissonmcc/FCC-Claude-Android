package com.fcc.claude

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {
    @Test
    fun markdownParserKeepsLatestStreamingSnapshotIncludingPartialSyntax() {
        val parser = MarkdownParser(GFMFlavourDescriptor())
        val snapshots = listOf(
            "Resposta parcial",
            "Resposta parcial\n\n**em andamento",
            "Resposta corrigida",
            "",
        )

        snapshots.forEach { snapshot ->
            val tree = parser.buildMarkdownTreeFromString(snapshot)
            assertEquals(MarkdownElementTypes.MARKDOWN_FILE, tree.type)
            assertEquals(snapshot.length, tree.endOffset)
            assertTrue(tree.startOffset <= tree.endOffset)
        }
    }

    @Test
    fun markdownParserRecognizesAnUnclosedCodeFenceDuringStreaming() {
        val snapshot = "```kotlin\nval answer = 42"
        val tree = MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(snapshot)

        assertEquals(MarkdownElementTypes.MARKDOWN_FILE, tree.type)
        assertTrue(tree.children.any { it.type == MarkdownElementTypes.CODE_FENCE || it.type == MarkdownElementTypes.CODE_BLOCK })
    }
}
