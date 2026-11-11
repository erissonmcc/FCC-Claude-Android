package com.fcc.claude

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate
import androidx.core.view.WindowCompat
import org.json.JSONArray

class MainActivity : ReactActivity() {
    override fun getMainComponentName() = "FccClaude"

    internal val launchCwd: String
        get() = intent.getStringExtra("fcc_cwd") ?: ChatRepository.defaultWorkingDirectory()

    internal val launchOptions: LaunchOptions
        get() = parseLaunchOptions(intent)

    internal val launchPrompt: String?
        get() = intent.getStringExtra("fcc_prompt") ?: launchOptions.prompt

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, BuildConfig.IS_NEW_ARCHITECTURE_ENABLED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

private fun parseLaunchOptions(intent: Intent): LaunchOptions {
    val raw = intent.getStringExtra("fcc_args_json") ?: return LaunchOptions()
    val args = runCatching { JSONArray(raw) }.getOrNull() ?: return LaunchOptions()
    var prompt: String? = null
    var resume: String? = null
    var model: String? = null
    var mode: String? = null
    var continueSession = false
    var index = 0
    while (index < args.length()) {
        when (args.optString(index++)) {
            "-p", "--print" -> {
                val parts = mutableListOf<String>()
                while (index < args.length() && !args.optString(index).startsWith("-")) parts += args.optString(index++)
                prompt = parts.joinToString(" ")
            }
            "-c", "--continue" -> continueSession = true
            "--resume", "-r" -> if (index < args.length() && !args.optString(index).startsWith("-")) resume = args.optString(index++)
            "--model", "-m" -> if (index < args.length()) model = args.optString(index++)
            "--permission-mode" -> if (index < args.length()) mode = args.optString(index++)
            "--dangerously-skip-permissions" -> mode = "full_access"
        }
    }
    val normalizedMode = when (mode) {
        "default", "acceptEdits" -> "config"
        "plan", "dontAsk" -> "ask"
        "bypassPermissions" -> "full_access"
        else -> mode
    }
    return LaunchOptions(prompt, continueSession, resume, model, normalizedMode)
}
