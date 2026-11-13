package com.fcc.claude

import org.json.JSONObject
import java.io.File

internal data class DeviceToolResult(val text: String, val isError: Boolean)

internal class DeviceToolExecutor(
    private val root: File,
    private val storageAccessGranted: () -> Boolean,
    private val commandRunner: (File, String) -> DeviceToolResult = { _, _ ->
        DeviceToolResult("A execução de comandos precisa ser encaminhada ao Termux.", true)
    },
) {
    private val termuxFileTools by lazy { TermuxFileToolExecutor(root, commandRunner) }

    fun execute(tool: String, input: JSONObject): DeviceToolResult = try {
        if (tool != "run_command" && !storageAccessGranted()) {
            error("Conceda ao FCC o acesso a todos os arquivos nas configurações do Android.")
        }
        val result = when (tool) {
            "read_file" -> {
                val file = resolve(input.getString("path")); require(file.isFile) { "Arquivo não encontrado." }
                require(file.length() <= MAX_BYTES) { "Arquivo excede o limite de 512 KiB." }
                require(file.length() <= MAX_BYTES) { "Arquivo excede o limite de 512 KiB." }
                DeviceToolResult(file.readText(Charsets.UTF_8), false)
            }
            "write_file" -> {
                termuxFileTools.writeFile(input.getString("path"), input.getString("content"))
            }
            "edit_file" -> {
                termuxFileTools.editFile(
                    input.getString("path"), input.getString("old_string"), input.getString("new_string"),
                )
            }
            "list_directory" -> {
                val dir = resolve(input.optString("path", ".")); require(dir.isDirectory) { "Diretório não encontrado." }
                DeviceToolResult(dir.listFiles().orEmpty().sortedBy { it.name }.take(MAX_ENTRIES).joinToString("\n") { (if (it.isDirectory) "d " else "f ") + it.name }, false)
            }
            "find_files" -> {
                val base = resolve(input.optString("path", "."))
                val glob = input.optString("pattern", "*").map { char -> when (char) {
                    '*' -> ".*"
                    '?' -> "."
                    else -> Regex.escape(char.toString())
                } }.joinToString("")
                val pattern = Regex("^$glob$")
                DeviceToolResult(walk(base).filter { pattern.matches(it.name) }.take(MAX_ENTRIES).joinToString("\n") { it.relativeTo(root.canonicalFile).path }, false)
            }
            "search_files" -> {
                val base = resolve(input.optString("path", ".")); val query = input.getString("query"); val matches = mutableListOf<String>()
                for (file in walk(base).filter { it.isFile }.take(MAX_SCAN_FILES)) {
                    if (file.length() > MAX_BYTES) continue
                    file.useLines(Charsets.UTF_8) { lines -> lines.forEachIndexed { i, line -> if (line.contains(query)) matches.add("${file.relativeTo(root.canonicalFile).path}:${i + 1}:$line") } }
                    if (matches.size >= MAX_ENTRIES) break
                }
                DeviceToolResult(matches.joinToString("\n"), false)
            }
            "run_command" -> {
                val cwd = resolve(input.optString("cwd", ".")); require(cwd.isDirectory) { "Diretório de trabalho não encontrado." }
                commandRunner(cwd, input.getString("command"))
            }
            else -> error("Ferramenta local desconhecida: $tool")
        }
        result.copy(text = result.text.take(MAX_BYTES))
    } catch (failure: Exception) {
        DeviceToolResult((failure.message ?: "Falha ao executar ferramenta local.").take(MAX_BYTES), true)
    }

    private fun resolve(path: String): File {
        val base = root.canonicalFile
        val candidate = File(base, path).canonicalFile
        require(candidate.toPath().startsWith(base.toPath())) { "O caminho precisa permanecer dentro da pasta do projeto." }
        return candidate
    }

    private fun walk(base: File): Sequence<File> = sequence {
        if (base.isFile) yield(base) else if (base.isDirectory) {
            val stack = ArrayDeque<File>(); stack.add(base); var count = 0
            while (stack.isNotEmpty() && count < MAX_SCAN_FILES) {
                val current = stack.removeLast()
                for (child in current.listFiles().orEmpty()) {
                    if (child.isSymbolicLink()) continue
                    yield(child); count++
                    if (child.isDirectory) stack.add(child)
                    if (count >= MAX_SCAN_FILES) break
                }
            }
        }
    }

    private fun File.isSymbolicLink() = runCatching { toPath() != canonicalFile.toPath() }.getOrDefault(true)

    companion object {
        private const val MAX_BYTES = 512 * 1024
        private const val MAX_ENTRIES = 500
        private const val MAX_SCAN_FILES = 5000
    }
}
