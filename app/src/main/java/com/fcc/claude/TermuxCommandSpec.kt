package com.fcc.claude

internal object TermuxCommandSpec {
    const val COMMAND_PATH = "\$PREFIX/bin/bash"

    fun arguments(command: String): Array<String> = arrayOf(
        "-c",
        "exec timeout 60s bash -c ${shellQuote(command)}",
    )

    fun result(stdout: String, stderr: String, exitCode: Int, errorCode: Int, errorMessage: String?): DeviceToolResult {
        if (errorCode != -1) {
            return DeviceToolResult(errorMessage?.takeIf(String::isNotBlank) ?: "Termux não conseguiu iniciar o comando.", true)
        }
        val output = buildList {
            stdout.takeIf(String::isNotEmpty)?.let(::add)
            stderr.takeIf(String::isNotEmpty)?.let(::add)
            add("(exit $exitCode)")
        }.joinToString("\n")
        return DeviceToolResult(output, exitCode != 0)
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
