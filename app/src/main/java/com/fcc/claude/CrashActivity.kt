package com.fcc.claude

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Activity
import java.io.File

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 17, 19)
        window.navigationBarColor = Color.rgb(16, 17, 19)
        val report = File(filesDir, CRASH_REPORT_FILE).takeIf(File::exists)?.readText()
            ?: "Não foi possível carregar os detalhes do erro."
        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(16, 17, 19))
        }
        root.addView(TextView(this).apply {
            text = "O aplicativo parou"
            textSize = 24f
            setTextColor(Color.rgb(233, 233, 231))
        })
        root.addView(TextView(this).apply {
            text = "Detalhes completos do erro"
            textSize = 14f
            setTextColor(Color.rgb(153, 155, 159))
            setPadding(0, padding / 4, 0, padding / 2)
        })
        val reportView = TextView(this).apply {
            text = report
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(Color.rgb(233, 233, 231))
            setTextIsSelectable(true)
            setPadding(padding / 2, padding / 2, padding / 2, padding / 2)
        }
        root.addView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(27, 28, 31))
            addView(reportView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
            ).apply { bottomMargin = padding / 2 }
        })
        root.addView(Button(this).apply {
            text = "Copiar erro completo"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Erro do aplicativo", report))
                text = "Erro copiado"
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.gravity = Gravity.CENTER_VERTICAL
        setContentView(root)
    }
}
