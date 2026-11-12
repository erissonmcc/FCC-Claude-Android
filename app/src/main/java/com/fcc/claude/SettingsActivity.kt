package com.fcc.claude

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate

class SettingsActivity : ReactActivity() {
    override fun getMainComponentName() = "FccSettings"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        object : DefaultReactActivityDelegate(this, mainComponentName, BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
            override fun getLaunchOptions(): Bundle = Bundle().apply {
                putString("cwd", intent.getStringExtra("fcc_cwd"))
                putString("model", intent.getStringExtra("fcc_model"))
                putString("mode", intent.getStringExtra("fcc_mode"))
                putString("effort", intent.getStringExtra("fcc_effort"))
                putString("backendUrl", intent.getStringExtra("fcc_backend_url"))
                putString("backendToken", intent.getStringExtra("fcc_backend_token"))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightNavigationBars = false
        insetsController.isAppearanceLightStatusBars = false
    }
}
