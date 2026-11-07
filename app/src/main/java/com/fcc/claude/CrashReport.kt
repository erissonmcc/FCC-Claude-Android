package com.fcc.claude

import java.io.PrintWriter
import java.io.StringWriter

internal fun shouldInstallCrashHandler(processName: String?, packageName: String): Boolean =
    processName == packageName

internal fun formatCrashReport(threadName: String, throwable: Throwable): String {
    val stackTrace = StringWriter().also { writer ->
        throwable.printStackTrace(PrintWriter(writer))
    }
    return "Thread: $threadName\n\n$stackTrace"
}
