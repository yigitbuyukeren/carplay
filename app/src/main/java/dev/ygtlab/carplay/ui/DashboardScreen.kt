package dev.ygtlab.carplay.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ygtlab.carplay.DashboardViewModel
import dev.ygtlab.carplay.R
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

    val leanEnabled by vm.leanEnabled.collectAsStateWithLifecycle()
    val obdEnabled by vm.obdEnabled.collectAsStateWithLifecycle()
    val obdDevice by vm.obdDevice.collectAsStateWithLifecycle()
    val obdState by vm.obd.state.collectAsStateWithLifecycle()
    val leanState by vm.lean.state.collectAsStateWithLifecycle()
    val speedLimitOn by vm.speedLimitEnabled.collectAsStateWithLifecycle()
    val speedLimit by vm.speedLimit.limit.collectAsStateWithLifecycle()
    val recordTrips by vm.recordTrips.collectAsStateWithLifecycle()
    var showTrips by remember { mutableStateOf(false) }
    val message by vm.messages.collectAsStateWithLifecycle()
    val autoRead by vm.readMessagesAloud.collectAsStateWithLifecycle()
    var dismissedMessageId by remember { mutableLongStateOf(0L) }
    val themeMode by vm.theme.mode.collectAsStateWithLifecycle()
    val isDay by vm.theme.isDay.collectAsStateWithLifecycle()
    var showMap by remember { mutableStateOf(false) }
    var showNowPlaying by remember { mutableStateOf(false) }
    val navUi by vm.nav.ui.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val thermal by vm.thermal.state.collectAsStateWithLifecycle()
    val thermalOn by vm.thermalEnabled.collectAsStateWithLifecycle()
    var heatDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(thermal.warn) { if (!thermal.warn) heatDismissed = false }
    val weather by vm.weather.snapshot.collectAsStateWithLifecycle()
    val weatherOn by vm.weatherEnabled.collectAsStateWithLifecycle()
    val rainVoice by vm.rainVoice.collectAsStateWithLifecycle()
    var rainDismissed by remember { mutableStateOf(false) }
    var iceDismissed by remember { mutableStateOf(false) }
    val raining = weather?.rainInMin != null
    val icy = weather?.iceRisk == true
    LaunchedEffect(raining) { if (!raining) rainDismissed = false }
    LaunchedEffect(icy) { if (!icy) iceDismissed = false }
    var showSettings by remember { mutableStateOf(!locationGranted(context)) }
    var overlayEnabled by remember { mutableStateOf(Prefs.overlayEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onResume()
    }

    // Re-evaluated whenever the activity resumes (e.g. coming back from a system settings page).
    val settingItems = remember(resume) {
        listOf(
            SettingItem(context.getString(R.string.trips_title), context.getString(R.string.trips_desc), false, context.getString(R.string.action_open)) {
                showTrips = true
            },
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

    BackHandler(enabled = showMap || showNowPlaying || showTrips) {
        showTrips = false
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
                        speedLimit = speedLimit,
                        isDay = isDay,
                        lowPower = thermal.lowPower,
                        places = places,
                        onSavePlace = vm::savePlace,
                        onRemovePlace = vm::removePlace,
                        onClose = { showMap = false },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                } else Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SpeedCard(gps, speedLimit, leanState.takeIf { leanEnabled }, vm.lean::resetPeaks, obd = obdState.takeIf { obdEnabled }, modifier = Modifier.weight(1f).fillMaxSize(), onClick = { showTrips = true })
                        Column(
                            modifier = Modifier.weight(1.15f).fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            NavCard(
                                context, navUi, places, weather.takeIf { weatherOn },
                                onOpenMap = { showMap = true },
                                onGoTo = { vm.nav.selectPlace(it); showMap = true },
                                modifier = Modifier.weight(1f).fillMaxSize())
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
                val toggles = listOf(
                    ToggleItem(stringResource(R.string.msg_auto_title), stringResource(R.string.msg_auto_desc), autoRead, vm::setReadMessagesAloud),
                    ToggleItem(stringResource(R.string.weather_title), stringResource(R.string.weather_desc), weatherOn, vm::setWeatherEnabled),
                    ToggleItem(stringResource(R.string.rain_voice_title), stringResource(R.string.rain_voice_desc), rainVoice, vm::setRainVoice),
                    ToggleItem(stringResource(R.string.thermal_title), stringResource(R.string.thermal_desc), thermalOn, vm::setThermalEnabled),
                    ToggleItem(stringResource(R.string.speed_limit_title), stringResource(R.string.speed_limit_desc), speedLimitOn, vm::setSpeedLimitEnabled),
                    ToggleItem(stringResource(R.string.record_trips_title), stringResource(R.string.record_trips_desc), recordTrips, vm::setRecordTrips),
                    ToggleItem(stringResource(R.string.overlay_title), stringResource(R.string.overlay_desc), overlayEnabled) { enabled ->
                        overlayEnabled = toggleOverlay(context, enabled)
                    },
                )
                SettingsScreen(
                    items = settingItems,
                    toggles = toggles,
                    lean = leanState,
                    leanEnabled = leanEnabled,
                    onLeanEnabled = vm::setLeanEnabled,
                    onLeanCalibrate = vm.lean::calibrate,
                    themeMode = themeMode,
                    onThemeMode = vm.theme::setMode,
                    onClose = { showSettings = false },
                    cards = {
                        ObdSettingsCard(
                            obd = vm.obd,
                            bluetooth = vm.bluetooth,
                            state = obdState,
                            enabled = obdEnabled,
                            onEnabled = vm::setObdEnabled,
                            device = obdDevice,
                            onDevice = vm::setObdDevice,
                            moving = gps.speedKmh > 2 || (obdState.data.speedKmh ?: 0) > 2,
                        )
                    },
                )
            }

            if (showTrips) {
                TripsScreen(trips = vm.trips, onClose = { showTrips = false })
            }

            val shownMessage = message?.takeIf { it.id != dismissedMessageId && callState.phase == CallPhase.IDLE }
            if (shownMessage != null && !showSettings) {
                MessageBanner(
                    message = shownMessage,
                    onRead = { vm.speakMessage(shownMessage) },
                    onDismiss = { dismissedMessageId = shownMessage.id },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }

            if (!showSettings && callState.phase == CallPhase.IDLE && shownMessage == null) {
                val w = weather
                val rainIn = w?.rainInMin
                when {
                    thermal.warn && !heatDismissed -> AlertStrip(
                        text = stringResource(R.string.thermal_banner),
                        accent = MotoColors.Red,
                        onDismiss = { heatDismissed = true },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                    weatherOn && w != null && rainIn != null && !rainDismissed -> AlertStrip(
                        text = if (rainIn > 0) stringResource(R.string.rain_banner_fmt, rainIn) else stringResource(R.string.rain_banner_now),
                        accent = MotoColors.Blue,
                        onDismiss = { rainDismissed = true },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                    weatherOn && w != null && w.iceRisk && !iceDismissed -> AlertStrip(
                        text = stringResource(R.string.ice_banner_fmt, w.tempC),
                        accent = MotoColors.Cyan,
                        onDismiss = { iceDismissed = true },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                }
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
