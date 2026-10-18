package dev.ygtlab.carplay.ui

import dev.ygtlab.carplay.data.LeanState
import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.ThemeMode
import dev.ygtlab.carplay.util.hasPermission

data class SettingItem(
    val title: String,
    val description: String,
    val granted: Boolean,
    val action: String,
    val onClick: () -> Unit,
)

fun locationGranted(c: Context) = c.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
fun phoneGranted(c: Context) =
    c.hasPermission(Manifest.permission.READ_PHONE_STATE) && c.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)
fun notificationAccessGranted(c: Context) =
    NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)
fun overlayGranted(c: Context) = Settings.canDrawOverlays(c)

fun runtimePermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
    return list.toTypedArray()
}

data class ToggleItem(val title: String, val description: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

@Composable
fun SettingsScreen(
    items: List<SettingItem>,
    toggles: List<ToggleItem>,
    lean: LeanState,
    leanEnabled: Boolean,
    onLeanEnabled: (Boolean) -> Unit,
    onLeanCalibrate: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onClose: () -> Unit,
    cards: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().background(LocalMotoPalette.current.scrim).clickable(enabled = false) {}) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = LocalMotoPalette.current.text)
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MotoColors.CardHigh).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = LocalMotoPalette.current.text, modifier = Modifier.size(32.dp)) }
            }
            LazyColumn(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items) { item -> SettingRow(item) }
                items(toggles) { t -> ToggleRow(t) }
                item { cards() }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.lean_title), fontSize = 20.sp, color = LocalMotoPalette.current.text, fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(if (lean.supported) R.string.lean_desc else R.string.lean_unsupported),
                                    fontSize = 15.sp, color = LocalMotoPalette.current.muted,
                                )
                            }
                            Switch(checked = leanEnabled, onCheckedChange = onLeanEnabled, enabled = lean.supported)
                        }
                        if (leanEnabled && lean.supported) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PillButton(stringResource(R.string.lean_calibrate), MotoColors.Blue, onLeanCalibrate)
                                if (lean.calibrated) {
                                    Text(stringResource(R.string.lean_calibrated), fontSize = 15.sp, color = MotoColors.Green)
                                }
                            }
                        }
                    }
                }
                item { ThemeRow(themeMode, onThemeMode) }
            }
        }
    }
}

@Composable
private fun ToggleRow(t: ToggleItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(t.title, fontSize = 20.sp, color = LocalMotoPalette.current.text, fontWeight = FontWeight.SemiBold)
            Text(t.description, fontSize = 15.sp, color = LocalMotoPalette.current.muted)
        }
        Switch(checked = t.checked, onCheckedChange = t.onChange)
    }
}

@Composable
private fun SettingRow(item: SettingItem) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontSize = 20.sp, color = LocalMotoPalette.current.text, fontWeight = FontWeight.SemiBold)
            Text(item.description, fontSize = 15.sp, color = LocalMotoPalette.current.muted)
        }
        if (item.granted) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MotoColors.Green, modifier = Modifier.size(36.dp))
        } else {
            PillButton(item.action, MotoColors.Blue, item.onClick)
        }
    }
}

@Composable
private fun ThemeRow(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val palette = LocalMotoPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.theme_title), fontSize = 20.sp, color = palette.text, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.theme_desc), fontSize = 15.sp, color = palette.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                ThemeMode.AUTO to R.string.theme_auto,
                ThemeMode.DAY to R.string.theme_day,
                ThemeMode.NIGHT to R.string.theme_night,
            ).forEach { (m, label) ->
                PillButton(
                    stringResource(label),
                    if (m == mode) MotoColors.Blue else palette.cardHigh,
                    { onSelect(m) },
                    Modifier.weight(1f),
                    textColor = if (m == mode) Color.White else palette.text,
                )
            }
        }
    }
}
