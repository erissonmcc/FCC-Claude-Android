package com.fcc.claude

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolExecutorTest {
    @Test
    fun writesAndReadsFilesInsideThePhoneWorkspace() {
        val root = Files.createTempDirectory("device-tool").toFile()
        try {
            val executor = DeviceToolExecutor(root, storageAccessGranted = { true })
            val written = executor.execute("write_file", JSONObject()
                .put("path", "src/hello.txt")
                .put("content", "hello phone"))
            val read = executor.execute("read_file", JSONObject().put("path", "src/hello.txt"))
            assertFalse(written.isError)
            assertEquals("hello phone", read.text)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsPathsThatEscapeTheWorkspace() {
        val root = Files.createTempDirectory("device-tool-root").toFile()
        val outside = Files.createTempFile("device-tool-outside", ".txt").toFile()
        try {
            val executor = DeviceToolExecutor(root, storageAccessGranted = { true })
            val result = executor.execute("read_file", JSONObject().put("path", "../${outside.name}"))
            assertTrue(result.isError)
            assertFalse(result.text.contains(outside.readText()))
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun commandRunsFromWorkspaceAndReturnsItsOutput() {
        val root = Files.createTempDirectory("device-tool-command").toFile()
        try {
            var commandDirectory: File? = null
            val executor = DeviceToolExecutor(
                root,
                storageAccessGranted = { true },
                commandRunner = { directory, command ->
                    commandDirectory = directory
                    DeviceToolResult("ran:$command", false)
                },
            )
            val result = executor.execute("run_command", JSONObject()
                .put("command", "pwd")
                .put("cwd", "."))
            assertFalse(result.isError)
            assertEquals(root.canonicalFile, commandDirectory)
            assertEquals("ran:pwd", result.text)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesLocalToolsUntilAllFilesAccessIsGranted() {
        val root = Files.createTempDirectory("device-tool-no-access").toFile()
        try {
            val executor = DeviceToolExecutor(root, storageAccessGranted = { false })
            val result = executor.execute("list_directory", JSONObject().put("path", "."))
            assertTrue(result.isError)
            assertTrue(result.text.contains("acesso a todos os arquivos", ignoreCase = true))
        } finally {
            root.deleteRecursively()
        }
    }
}
