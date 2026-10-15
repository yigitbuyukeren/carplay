package dev.ygtlab.carplay.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ygtlab.carplay.DashboardViewModel
import dev.ygtlab.carplay.data.CallPhase
import dev.ygtlab.carplay.service.OverlayService
import dev.ygtlab.carplay.util.Prefs
import dev.ygtlab.carplay.util.openDialer
import dev.ygtlab.carplay.util.openMusicApp
import dev.ygtlab.carplay.util.openNavigation
import dev.ygtlab.carplay.util.openOverlaySettings
import dev.ygtlab.carplay.util.openSettingsAction

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val context = LocalContext.current
    val gps by vm.gps.collectAsStateWithLifecycle()
    val battery by vm.battery.state.collectAsStateWithLifecycle()
    val mediaState by vm.media.state.collectAsStateWithLifecycle()
    val callState by vm.call.state.collectAsStateWithLifecycle()
    val callerName by vm.callerName.collectAsStateWithLifecycle()
    val resume by vm.resumeTick.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(!locationGranted(context)) }
    var overlayEnabled by remember { mutableStateOf(Prefs.overlayEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onResume()
    }

    // Re-evaluated whenever the activity resumes (e.g. coming back from a system settings page).
    val settingItems = remember(resume) {
        listOf(
            SettingItem("Konum", "Hız göstergesi için GPS", locationGranted(context), "İzin ver") {
                permissionLauncher.launch(runtimePermissions())
            },
            SettingItem("Telefon", "Gelen aramayı cevaplamak/reddetmek için", phoneGranted(context), "İzin ver") {
                permissionLauncher.launch(runtimePermissions())
            },
            SettingItem("Bildirim erişimi", "Müzik kontrolü ve arayan adı için", notificationAccessGranted(context), "Aç") {
                context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            },
            SettingItem("Üstte göster", "Hız şeridi için", overlayGranted(context), "Aç") { context.openOverlaySettings() },
            SettingItem("Ana ekran olarak ayarla", "Home tuşu MotoDash'i açsın", false, "Ayarla") {
                context.openSettingsAction(Settings.ACTION_HOME_SETTINGS)
            },
            SettingItem("Pil kısıtlaması", "Uygulamayı \"Kısıtlanmamış\" yap", false, "Aç") {
                context.openSettingsAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            },
        )
    }

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().background(WallpaperBrush)) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(modifier = Modifier.fillMaxSize().padding(end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                Dock(
                    battery = battery,
                    onNavigation = { if (!context.openNavigation()) showSettings = false },
                    onMusic = { context.openMusicApp(mediaState.packageName) },
                    onPhone = { context.openDialer() },
                    onSettings = { showSettings = true },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SpeedCard(gps, modifier = Modifier.weight(1f).fillMaxSize())
                        Column(
                            modifier = Modifier.weight(1.15f).fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NavCard(context, modifier = Modifier.weight(1f).fillMaxSize())
                            MediaCard(
                                state = mediaState,
                                media = vm.media,
                                onGrantAccess = { context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                                onOpenMusicApp = { context.openMusicApp(null) },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                            )
                        }
                    }
                }
            }

            if (showSettings) {
                SettingsScreen(
                    items = settingItems,
                    overlayEnabled = overlayEnabled,
                    onOverlayToggle = { enabled -> overlayEnabled = toggleOverlay(context, enabled) },
                    onClose = { showSettings = false },
                )
            }

            if (callState.phase != CallPhase.IDLE) {
                CallScreen(callState, callerName, onAnswer = vm.call::answer, onHangUp = vm.call::hangUp)
            }
        }
    }
}

private fun toggleOverlay(context: Context, enabled: Boolean): Boolean {
    if (enabled && !overlayGranted(context)) {
        context.openOverlaySettings()
        return false
    }
    Prefs.setOverlayEnabled(context, enabled)
    if (enabled) OverlayService.start(context) else OverlayService.stop(context)
    return enabled
}
