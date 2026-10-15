package dev.ygtlab.carplay.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.util.NAV_APPS
import dev.ygtlab.carplay.util.isInstalled
import dev.ygtlab.carplay.util.launchPackage

@Composable
fun NavCard(context: Context, modifier: Modifier = Modifier) {
    val installed = remember { NAV_APPS.filter { context.isInstalled(it.packageName) } }
    Column(
        modifier = modifier.card().padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Navigasyon", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (installed.isEmpty()) {
            Text("Yüklü harita uygulaması bulunamadı", fontSize = 16.sp, color = MotoColors.Gray)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize().padding(top = 10.dp)) {
                installed.take(2).forEach { app ->
                    PillButton(
                        app.label,
                        if (app.packageName == "com.waze") MotoColors.Cyan.copy(alpha = 0.9f) else MotoColors.Green,
                        onClick = { context.launchPackage(app.packageName) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
