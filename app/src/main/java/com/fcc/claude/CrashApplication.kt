package com.fcc.claude

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import java.io.File
import kotlin.system.exitProcess

class CrashApplication : Application(), ReactApplication {
    override val reactNativeHost: ReactNativeHost = object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> = PackageList(this).packages.apply {
            add(FccChatPackage())
        }

        override fun getJSMainModuleName() = "index"
        override fun getUseDeveloperSupport() = BuildConfig.DEBUG && BuildConfig.REACT_NATIVE_DEV_SUPPORT
        override val isNewArchEnabled = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled = BuildConfig.IS_HERMES_ENABLED
    }

    override val reactHost: ReactHost
        get() = getDefaultReactHost(applicationContext, reactNativeHost)

    override fun onCreate() {
        super.onCreate()
        SoLoader.init(this, OpenSourceMergedSoMapping)
        if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) load()
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
