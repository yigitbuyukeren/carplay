package dev.ygtlab.carplay.ui

import dev.ygtlab.carplay.R
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
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

    val themeMode by vm.theme.mode.collectAsStateWithLifecycle()
    val isDay by vm.theme.isDay.collectAsStateWithLifecycle()
    var showMap by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    val navUi by vm.nav.ui.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(!locationGranted(context)) }
    var overlayEnabled by remember { mutableStateOf(Prefs.overlayEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onResume()
    }

    // Re-evaluated whenever the activity resumes (e.g. coming back from a system settings page).
    val settingItems = remember(resume) {
        listOf(
            SettingItem(context.getString(R.string.perm_location), context.getString(R.string.perm_location_desc), locationGranted(context), context.getString(R.string.action_allow)) {
                permissionLauncher.launch(runtimePermissions())
            },
            SettingItem(context.getString(R.string.perm_phone), context.getString(R.string.perm_phone_desc), phoneGranted(context), context.getString(R.string.action_allow)) {
                permissionLauncher.launch(runtimePermissions())
            },
            SettingItem(context.getString(R.string.perm_notif), context.getString(R.string.perm_notif_desc), notificationAccessGranted(context), context.getString(R.string.action_open)) {
                context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            },
            SettingItem(context.getString(R.string.perm_overlay), context.getString(R.string.perm_overlay_desc), overlayGranted(context), context.getString(R.string.action_open)) { context.openOverlaySettings() },
            SettingItem(context.getString(R.string.perm_home), context.getString(R.string.perm_home_desc), false, context.getString(R.string.action_set)) {
                context.openSettingsAction(Settings.ACTION_HOME_SETTINGS)
            },
            SettingItem(context.getString(R.string.perm_battery), context.getString(R.string.perm_battery_desc), false, context.getString(R.string.action_open)) {
                context.openSettingsAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            },
        )
    }

    BackHandler(enabled = showMap || showNowPlaying) {
        showMap = false
        showNowPlaying = false
    }

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().background(LocalMotoPalette.current.wallpaper)) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(modifier = Modifier.fillMaxSize().padding(end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                Dock(
                    battery = battery,
                    onNavigation = { showMap = !showMap; showNowPlaying = false; showSettings = false },
                    onMusic = { showNowPlaying = !showNowPlaying; showMap = false; showSettings = false },
                    onPhone = { context.openDialer() },
                    onSettings = { showSettings = true },
                )
                if (showNowPlaying) {
                    NowPlayingScreen(
                        state = mediaState,
                        media = vm.media,
                        onOpenMusicApp = { context.openMusicApp(mediaState.packageName) },
                        onGrantAccess = { context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                } else if (showMap) {
                    MapScreen(
                        nav = vm.nav,
                        locationState = vm.locationState,
                        speedKmh = gps.speedKmh,
                        isDay = isDay,
                        onClose = { showMap = false },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                } else Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SpeedCard(gps, modifier = Modifier.weight(1f).fillMaxSize())
                        Column(
                            modifier = Modifier.weight(1.15f).fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NavCard(context, navUi, onOpenMap = { showMap = true }, modifier = Modifier.weight(1f).fillMaxSize())
                            MediaCard(
                                state = mediaState,
                                media = vm.media,
                                onGrantAccess = { context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                                onOpenMusicApp = { context.openMusicApp(null) },
                                onOpenNowPlaying = { showNowPlaying = true },
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
                    themeMode = themeMode,
                    onThemeMode = vm.theme::setMode,
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
