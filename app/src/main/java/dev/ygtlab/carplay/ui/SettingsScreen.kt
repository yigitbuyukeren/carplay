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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private enum class SettingsTab(val label: Int) {
    SETUP(R.string.settings_tab_setup),
    RIDE(R.string.settings_tab_ride),
    SAFETY(R.string.settings_tab_safety),
    ENGINE(R.string.settings_tab_engine),
}

/**
 * Settings in four tabs down the left side, so each page is a short list even on a 360dp-high phone:
 * permissions, ride options, safety (SOS) and engine (OBD).
 */
@Composable
fun SettingsScreen(
    items: List<SettingItem>,
    toggles: List<ToggleItem>,
    onOpenTrips: () -> Unit,
    lean: LeanState,
    leanEnabled: Boolean,
    onLeanEnabled: (Boolean) -> Unit,
    onLeanCalibrate: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onClose: () -> Unit,
    safetyCard: @Composable () -> Unit = {},
    engineCard: @Composable () -> Unit = {},
) {
    val palette = LocalMotoPalette.current
    // Start on the setup page while a permission is missing, otherwise on the everyday options.
    var tab by rememberSaveable { mutableStateOf(if (items.any { !it.granted }) SettingsTab.SETUP else SettingsTab.RIDE) }
    Box(modifier = Modifier.fillMaxSize().background(palette.scrim).clickable(enabled = false) {}) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_title), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = palette.text)
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(MotoColors.CardHigh).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = palette.text, modifier = Modifier.size(32.dp)) }
            }
            Row(modifier = Modifier.fillMaxSize().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(modifier = Modifier.width(190.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsTab.entries.forEach { t ->
                        PillButton(
                            stringResource(t.label),
                            if (t == tab) MotoColors.Blue else palette.cardHigh,
                            { tab = t },
                            Modifier.fillMaxWidth(),
                            textColor = if (t == tab) Color.White else palette.text,
                            horizontalPadding = 8.dp,
                        )
                    }
                }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (tab) {
                        SettingsTab.SETUP -> items(items) { item -> SettingRow(item) }
                        SettingsTab.RIDE -> {
                            item {
                                SettingRow(
                                    SettingItem(
                                        stringResource(R.string.trips_title), stringResource(R.string.trips_desc), false,
                                        stringResource(R.string.action_open), onOpenTrips,
                                    )
                                )
                            }
                            items(toggles) { t -> ToggleRow(t) }
                            item { LeanRow(lean, leanEnabled, onLeanEnabled, onLeanCalibrate) }
                            item { ThemeRow(themeMode, onThemeMode) }
                        }
                        SettingsTab.SAFETY -> item { safetyCard() }
                        SettingsTab.ENGINE -> item { engineCard() }
                    }
                }
            }
        }
    }
}

@Composable
private fun LeanRow(lean: LeanState, enabled: Boolean, onEnabled: (Boolean) -> Unit, onCalibrate: () -> Unit) {
    val palette = LocalMotoPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.lean_title), fontSize = 20.sp, color = palette.text, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (lean.supported) R.string.lean_desc else R.string.lean_unsupported),
                    fontSize = 15.sp, color = palette.muted,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled, enabled = lean.supported)
        }
        if (enabled && lean.supported) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(stringResource(R.string.lean_calibrate), MotoColors.Blue, onCalibrate)
                if (lean.calibrated) Text(stringResource(R.string.lean_calibrated), fontSize = 15.sp, color = MotoColors.Green)
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
