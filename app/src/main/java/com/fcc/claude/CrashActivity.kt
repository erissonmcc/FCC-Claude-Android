package com.fcc.claude

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 17, 19)
        window.navigationBarColor = android.graphics.Color.rgb(16, 17, 19)
        val report = File(filesDir, CRASH_REPORT_FILE).takeIf(File::exists)?.readText()
            ?: "Não foi possível carregar os detalhes do erro."
        setContent {
            val context = LocalContext.current
            var copied by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxSize().background(Backdrop)
                    .statusBarsPadding().navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("O aplicativo parou", color = MainText, fontSize = 24.sp)
                Text("Detalhes completos do erro", color = MutedText, fontSize = 14.sp)
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    SelectionContainer {
                        Text(
                            report,
                            style = TextStyle(color = MainText, fontSize = 12.sp, lineHeight = 18.sp,
                                fontFamily = FontFamily.Monospace),
                        )
                    }
                }
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Erro do aplicativo", report))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (copied) "Erro copiado" else "Copiar erro completo")
                }
            }
        }
    }
}
