package com.fcc.claude

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal class TranscriptStore(private val directory: File) {
    private fun file(scope: String, sessionId: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$scope\u0000$sessionId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$digest.json")
    }

    fun load(scope: String, sessionId: String): String? =
        file(scope, sessionId).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    @Synchronized
    fun save(scope: String, sessionId: String, content: String) {
        directory.mkdirs()
        val destination = file(scope, sessionId)
        val temporary = File(directory, destination.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    @Synchronized
    fun remove(scope: String, sessionId: String) {
        file(scope, sessionId).delete()
    }
}
