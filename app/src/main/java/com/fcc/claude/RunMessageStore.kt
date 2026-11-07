package com.fcc.claude

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private journal. A completed write replaces the previous snapshot atomically. */
internal class RunMessageStore(private val file: File) {
    @Synchronized
    fun load(): List<RunMessage> {
        if (!file.exists()) return emptyList()
        return DataInputStream(file.inputStream().buffered()).use { input ->
            check(input.readInt() == 1) { "Versão desconhecida do registro de mensagens." }
            val count = input.readInt()
            check(count in 0..100000) { "Registro de mensagens inválido." }
            fun readString(): String {
                val size = input.readInt()
                check(size in 0..4_000_000) { "Registro de mensagens inválido." }
                return ByteArray(size).also { input.readFully(it) }.toString(Charsets.UTF_8)
            }
            List(count) {
                val message = RunMessage(readString(), readString(), readString(), readString(), readString(), readString())
                if (message.status == "sending") message.copy(status = "uncertain") else message
            }
        }
    }

    @Synchronized
    fun save(messages: List<RunMessage>) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                val output = DataOutputStream(stream.buffered())
                fun writeString(value: String) {
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    require(bytes.size <= 4_000_000)
                    output.writeInt(bytes.size)
                    output.write(bytes)
                }
                output.writeInt(1)
                output.writeInt(messages.size)
                messages.forEach { message ->
                    listOf(message.id, message.sessionId, message.runId, message.epoch, message.text, message.status).forEach(::writeString)
                }
                output.flush()
                stream.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
