package com.fcc.claude

import java.io.File
import java.util.Base64
import java.util.UUID

internal class TermuxFileToolExecutor(
    root: File,
    private val commandRunner: (File, String) -> DeviceToolResult,
) {
    private val root = root.canonicalFile

    fun writeFile(path: String, content: String): DeviceToolResult {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) return DeviceToolResult("Conteúdo excede o limite de 512 KiB.", true)
        val target = try { resolve(path) } catch (failure: Exception) { return failure(failure) }
        if (target.isDirectory) return DeviceToolResult("O caminho informado é um diretório.", true)
        val parent = target.parentFile ?: return DeviceToolResult("Caminho de arquivo inválido.", true)

        val staging = File(parent, ".fcc-write-${UUID.randomUUID()}")
        val prepared = run(
            "mkdir -p -- ${shellQuote(parent.path)} && " +
                "if [ -f ${shellQuote(target.path)} ]; then " +
                "cp -p -- ${shellQuote(target.path)} ${shellQuote(staging.path)} && : > ${shellQuote(staging.path)}; " +
                "else : > ${shellQuote(staging.path)}; fi",
        )
        if (prepared.isError) return prepared

        val encoded = Base64.getEncoder().encodeToString(bytes)
        for (chunk in encoded.chunked(BASE64_CHUNK_CHARS)) {
            val appended = run("printf '%s' '$chunk' | base64 -d >> ${shellQuote(staging.path)}")
            if (appended.isError) {
                run("rm -f -- ${shellQuote(staging.path)}")
                return appended
            }
        }

        val committed = run("mv -f -- ${shellQuote(staging.path)} ${shellQuote(target.path)}")
        if (committed.isError) {
            run("rm -f -- ${shellQuote(staging.path)}")
            return committed
        }
        return DeviceToolResult("Arquivo salvo: ${relativePath(target)}", false)
    }

    fun editFile(path: String, oldText: String, newText: String): DeviceToolResult {
        if (oldText.isEmpty()) return DeviceToolResult("O trecho a substituir não pode estar vazio.", true)
        val oldBytes = oldText.toByteArray(Charsets.UTF_8)
        val newBytes = newText.toByteArray(Charsets.UTF_8)
        if (oldBytes.size > MAX_BYTES || newBytes.size > MAX_BYTES) {
            return DeviceToolResult("Trecho excede o limite de 512 KiB.", true)
        }
        val target = try { resolve(path) } catch (failure: Exception) { return failure(failure) }
        if (!target.isFile) return DeviceToolResult("Arquivo não encontrado.", true)

        val staging = File(target.parentFile, ".fcc-edit-${UUID.randomUUID()}")
        val prepared = run("mkdir -- ${shellQuote(staging.path)}")
        if (prepared.isError) return prepared

        val oldPayload = File(staging, "old.b64")
        val newPayload = File(staging, "new.b64")
        for ((payload, bytes) in listOf(oldPayload to oldBytes, newPayload to newBytes)) {
            val created = run(": > ${shellQuote(payload.path)}")
            if (created.isError) {
                cleanup(staging)
                return created
            }
            val encoded = Base64.getEncoder().encodeToString(bytes)
            for (chunk in encoded.chunked(BASE64_CHUNK_CHARS)) {
                val appended = run("printf '%s' '$chunk' >> ${shellQuote(payload.path)}")
                if (appended.isError) {
                    cleanup(staging)
                    return appended
                }
            }
        }

        val updated = File(staging, "updated")
        val script = """
            set -e
            tmpdir=${shellQuote(staging.path)}
            trap 'rm -rf -- "§tmpdir"' EXIT
            base64 -d -- "§tmpdir/old.b64" > "§tmpdir/old"
            base64 -d -- "§tmpdir/new.b64" > "§tmpdir/new"
            old=§(cat -- "§tmpdir/old"; printf '\001')
            old=§{old%§'\001'}
            new=§(cat -- "§tmpdir/new"; printf '\001')
            new=§{new%§'\001'}
            content=§(cat -- ${shellQuote(target.path)}; printf '\001')
            content=§{content%§'\001'}
            rest=§content
            matches=0
            while [[ "§rest" == *"§old"* ]]; do
                prefix=§{rest%%"§old"*}
                matches=§((matches + 1))
                if (( matches > 1 )); then break; fi
                rest=§{rest:§(( §{#prefix} + 1 ))}
            done
            if (( matches == 0 )); then echo 'Trecho a substituir não encontrado.' >&2; exit 4; fi
            if (( matches > 1 )); then echo 'Trecho ambíguo; precisa ocorrer uma única vez.' >&2; exit 5; fi
            updated=§{content/"§old"/"§new"}
            size=§(printf '%s' "§updated" | wc -c)
            size=§{size//[[:space:]]/}
            if (( size > $MAX_BYTES )); then echo 'Arquivo excede o limite de 512 KiB.' >&2; exit 6; fi
            cp -p -- ${shellQuote(target.path)} ${shellQuote(updated.path)}
            printf '%s' "§updated" > ${shellQuote(updated.path)}
            mv -f -- ${shellQuote(updated.path)} ${shellQuote(target.path)}
            printf 'Arquivo atualizado.\n'
        """.trimIndent().replace('§', '$')

        val result = run(script)
        cleanup(staging)
        return if (result.isError) result else DeviceToolResult("Arquivo atualizado: ${relativePath(target)}", false)
    }

    private fun run(command: String) = commandRunner(root, command)

    private fun cleanup(directory: File) {
        run("rm -rf -- ${shellQuote(directory.path)}")
    }

    private fun resolve(path: String): File {
        val candidate = File(root, path).canonicalFile
        require(candidate.toPath().startsWith(root.toPath())) {
            "O caminho precisa permanecer dentro da pasta do projeto."
        }
        return candidate
    }

    private fun relativePath(file: File) = file.relativeTo(root).path

    private fun failure(exception: Exception) = DeviceToolResult(
        exception.message ?: "Falha ao acessar o arquivo no Termux.", true,
    )

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    companion object {
        private const val MAX_BYTES = 512 * 1024
        private const val BASE64_CHUNK_CHARS = 32 * 1024
    }
}
