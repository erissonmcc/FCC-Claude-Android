package com.fcc.claude

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import kotlin.system.exitProcess

class CrashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            getSystemService(ActivityManager::class.java).runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        if (!shouldInstallCrashHandler(processName, packageName)) return
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(filesDir, CRASH_REPORT_FILE).writeText(formatCrashReport(thread.name, throwable))
                startActivity(
                    Intent(this, CrashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
            } catch (error: Exception) {
                if (previousHandler != null) previousHandler.uncaughtException(thread, throwable)
                else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}

internal const val CRASH_REPORT_FILE = "last_crash.txt"
