package com.fcc.claude

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxFileToolExecutorTest {
    @Test
    fun writesUtf8ContentThroughTermuxShellCommands() {
        withWorkspace { root ->
            val executor = executor(root)

            val result = executor.writeFile("src/nested/hello.txt", "olá Termux\nsegunda linha\n")

            assertFalse(result.isError)
            assertEquals("olá Termux\nsegunda linha\n", File(root, "src/nested/hello.txt").readText())
        }
    }

    @Test
    fun replacesOneLiteralMultilineMatchAndPreservesTrailingNewline() {
        withWorkspace { root ->
            val target = File(root, "notes.txt").apply { writeText("antes\nold 'texto'\ncontinua\nfim\n") }
            val executor = executor(root)

            val result = executor.editFile("notes.txt", "old 'texto'\ncontinua", "novo & texto\nsegunda")

            assertFalse(result.text, result.isError)
            assertEquals("antes\nnovo & texto\nsegunda\nfim\n", target.readText())
        }
    }

    @Test
    fun rejectsMissingOrAmbiguousTextWithoutChangingTheFile() {
        withWorkspace { root ->
            val target = File(root, "notes.txt").apply { writeText("same same") }
            val executor = executor(root)

            val missing = executor.editFile("notes.txt", "absent", "replacement")
            val ambiguous = executor.editFile("notes.txt", "same", "replacement")

            assertTrue(missing.isError)
            assertTrue(ambiguous.isError)
            assertEquals("same same", target.readText())
        }
    }

    @Test
    fun refusesPathsOutsideTheWorkspaceBeforeRunningTermux() {
        withWorkspace { root ->
            var ranCommand = false
            val executor = TermuxFileToolExecutor(root) { _, _ ->
                ranCommand = true
                DeviceToolResult("unexpected", false)
            }

            val result = executor.writeFile("../outside.txt", "no")

            assertTrue(result.isError)
            assertFalse(ranCommand)
        }
    }

    private fun executor(root: File) = TermuxFileToolExecutor(root, ::runShell)

    private fun runShell(cwd: File, command: String): DeviceToolResult {
        val process = ProcessBuilder("bash", "-c", command).directory(cwd).start()
        val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return DeviceToolResult("shell command timed out", true)
        }
        val code = process.exitValue()
        return DeviceToolResult(listOf(stdout, stderr, "(exit $code)").filter(String::isNotEmpty).joinToString("\n"), code != 0)
    }

    private fun withWorkspace(block: (File) -> Unit) {
        val root = Files.createTempDirectory("termux-file-tools").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
