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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
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
import dev.ygtlab.carplay.data.SosReason
import dev.ygtlab.carplay.service.OverlayService
import dev.ygtlab.carplay.util.Prefs
import dev.ygtlab.carplay.util.openMusicApp
import dev.ygtlab.carplay.util.openNavigation
import dev.ygtlab.carplay.util.openOverlaySettings
import dev.ygtlab.carplay.util.openSettingsAction

@Composable
fun DashboardScreen(vm: DashboardViewModel) {
    val context = LocalContext.current
    val gps by vm.gps.collectAsStateWithLifecycle()
    val locationState by vm.locationState.collectAsStateWithLifecycle()
    val battery by vm.battery.state.collectAsStateWithLifecycle()
    val mediaState by vm.media.state.collectAsStateWithLifecycle()
    val callState by vm.call.state.collectAsStateWithLifecycle()
    val callerName by vm.callerName.collectAsStateWithLifecycle()
    val resume by vm.resumeTick.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val vehicleChosen by vm.vehicleChosen.collectAsStateWithLifecycle()
    val routeOptions by vm.nav.options.collectAsStateWithLifecycle()
    val cfg = profile.config
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

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
    var showPhone by remember { mutableStateOf(false) }
    val dialRequest by vm.dialRequest.collectAsStateWithLifecycle()
    val recents by vm.phone.recents.collectAsStateWithLifecycle()
    val contacts by vm.phone.contacts.collectAsStateWithLifecycle()
    val navUi by vm.nav.ui.collectAsStateWithLifecycle()
    val places by vm.places.collectAsStateWithLifecycle()
    val thermal by vm.thermal.state.collectAsStateWithLifecycle()
    // Without a charger a low battery is treated like a hot phone: the maps drop their frame rate.
    val lowPower = thermal.lowPower || (cfg.assumesNoCharger && !battery.charging && battery.percent in 1..LOW_BATTERY_PERCENT)
    val thermalOn by vm.thermalEnabled.collectAsStateWithLifecycle()
    var heatDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(thermal.warn) { if (!thermal.warn) heatDismissed = false }
    val sosUi by vm.sos.ui.collectAsStateWithLifecycle()
    val sosContacts by vm.sosContacts.collectAsStateWithLifecycle()
    val sosCrash by vm.sosCrashEnabled.collectAsStateWithLifecycle()
    val sosButton by vm.sosButtonEnabled.collectAsStateWithLifecycle()
    val weather by vm.weather.snapshot.collectAsStateWithLifecycle()
    val weatherOn by vm.weatherEnabled.collectAsStateWithLifecycle()
    val rainVoice by vm.rainVoice.collectAsStateWithLifecycle()
    var rainDismissed by remember { mutableStateOf(false) }
    var iceDismissed by remember { mutableStateOf(false) }
    var windDismissed by remember { mutableStateOf(false) }
    val windy = weather?.let { w -> cfg.windAlertKmh?.let { w.windKmh >= it } } == true
    LaunchedEffect(windy) { if (!windy) windDismissed = false }
    val raining = weather?.rainInMin != null
    val icy = weather?.iceRisk == true
    LaunchedEffect(raining) { if (!raining) rainDismissed = false }
    LaunchedEffect(icy) { if (!icy) iceDismissed = false }
    var showSettings by remember { mutableStateOf(!locationGranted(context)) }
    LaunchedEffect(dialRequest) {
        if (dialRequest != null) { showPhone = true; showMap = false; showNowPlaying = false; showSettings = false; showTrips = false }
    }
    var overlayEnabled by remember { mutableStateOf(Prefs.overlayEnabled(context)) }

    var pendingDial by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val dialerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.onResume()
        pendingDial?.let { (number, name) -> vm.dial(number, name) }
        pendingDial = null
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onResume()
    }
    val phonebookLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.phone.refresh()
        vm.onResume()
    }
    // Recents change whenever a call ends, and the permissions may have been granted while away.
    LaunchedEffect(showPhone, resume, callState.phase) { if (showPhone && callState.phase == CallPhase.IDLE) vm.phone.refresh() }

    // Re-evaluated whenever the activity resumes (e.g. coming back from a system settings page).
    val settingItems = remember(resume) {
        listOf(
            SettingItem(context.getString(R.string.perm_location), context.getString(R.string.perm_location_desc), locationGranted(context), context.getString(R.string.action_allow)) {
                permissionLauncher.launch(runtimePermissions())
            },
            SettingItem(context.getString(R.string.perm_phone), context.getString(R.string.perm_phone_desc), phoneGranted(context), context.getString(R.string.action_allow)) {
                permissionLauncher.launch(runtimePermissions())
            },
            SettingItem(context.getString(R.string.perm_dialer), context.getString(R.string.perm_dialer_desc), isDefaultDialer(context), context.getString(R.string.action_set)) {
                dialerLauncher.launch(defaultDialerIntent(context))
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

    BackHandler(enabled = showMap || showNowPlaying || showPhone || showTrips) {
        showTrips = false
        showPhone = false
        showMap = false
        showNowPlaying = false
    }

    Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize().background(LocalMotoPalette.current.wallpaper)) {
        Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            val dock: @Composable () -> Unit = {
                Dock(
                    battery = battery,
                    onNavigation = { showMap = !showMap; showNowPlaying = false; showPhone = false; showSettings = false },
                    onMusic = { showNowPlaying = !showNowPlaying; showMap = false; showPhone = false; showSettings = false },
                    onPhone = { vm.dialRequest.value = null; showPhone = !showPhone; showMap = false; showNowPlaying = false; showSettings = false },
                    onSettings = { showSettings = true },
                    horizontal = portrait,
                )
            }
            val content: @Composable (Modifier) -> Unit = { m ->
                if (showPhone) {
                    PhoneScreen(
                        recents = recents,
                        contacts = contacts,
                        hasAccess = phonebookGranted(context),
                        keypadLocked = gps.speedKmh >= PHONE_LOCK_KMH,
                        initialNumber = dialRequest,
                        onGrantAccess = { phonebookLauncher.launch(phonebookPermissions()) },
                        dialerHint = !isDefaultDialer(context),
                        onSetDialer = { dialerLauncher.launch(defaultDialerIntent(context)) },
                        // Once, offer to make MotoDash the phone app; otherwise the phone's own call screen fights with ours.
                        onDial = { number, name ->
                            if (!isDefaultDialer(context) && !Prefs.dialerPrompted(context)) {
                                Prefs.setDialerPrompted(context)
                                pendingDial = number to name
                                dialerLauncher.launch(defaultDialerIntent(context))
                            } else vm.dial(number, name)
                        },
                        modifier = m,
                    )
                } else if (showNowPlaying) {
                    NowPlayingScreen(
                        state = mediaState,
                        media = vm.media,
                        onGrantAccess = { context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                        modifier = m,
                    )
                } else if (showMap) {
                    MapScreen(
                        nav = vm.nav,
                        locationState = vm.locationState,
                        speedKmh = gps.speedKmh,
                        speedLimit = speedLimit,
                        isDay = isDay,
                        lowPower = lowPower,
                        places = places,
                        onSavePlace = vm::savePlace,
                        onRemovePlace = vm::removePlace,
                        onClose = { showMap = false },
                        modifier = m,
                    )
                } else Column(modifier = m) {
                    val speedCard: @Composable (Modifier) -> Unit = { mod ->
                        SpeedCard(gps, speedLimit, maxKmh = cfg.gaugeMaxKmh, onSos = if (sosButton) ({ vm.sos.trigger(SosReason.MANUAL) }) else null, modifier = mod, onClick = { showTrips = true })
                    }
                    val navCard: @Composable (Modifier) -> Unit = { mod ->
                        NavCard(
                            navUi, places, weather.takeIf { weatherOn },
                            me = locationState, isDay = isDay, lowPower = lowPower,
                            onOpenMap = { showMap = true },
                            onGoTo = { vm.nav.selectPlace(it); showMap = true },
                            modifier = mod)
                    }
                    val mediaCard: @Composable (Modifier) -> Unit = { mod ->
                        MediaCard(
                            state = mediaState,
                            media = vm.media,
                            onGrantAccess = { context.openSettingsAction(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                            onOpenNowPlaying = { showNowPlaying = true },
                            modifier = mod,
                        )
                    }
                    if (portrait) {
                        // Upright phone: speed on top, the route in the middle, music at the bottom.
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            speedCard(Modifier.weight(1.1f).fillMaxWidth())
                            navCard(Modifier.weight(1.3f).fillMaxWidth())
                            mediaCard(Modifier.weight(0.9f).fillMaxWidth())
                        }
                    } else {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            speedCard(Modifier.weight(1f).fillMaxSize())
                            Column(
                                modifier = Modifier.weight(1.15f).fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                navCard(Modifier.weight(1f).fillMaxSize())
                                mediaCard(Modifier.weight(1f).fillMaxSize())
                            }
                        }
                    }
                }
            }
            if (portrait) {
                Column(modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
                    content(Modifier.weight(1f).fillMaxWidth())
                    dock()
                }
            } else {
                Row(modifier = Modifier.fillMaxSize().padding(end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                    dock()
                    content(Modifier.weight(1f).fillMaxSize())
                }
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

            // Not over the map: there the top left holds the route or manoeuvre card, and rain, ice and heat are also spoken.
            if (!showSettings && !showMap && !showPhone && callState.phase == CallPhase.IDLE && shownMessage == null) {
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
                    weatherOn && w != null && windy && !windDismissed -> AlertStrip(
                        text = stringResource(R.string.wind_banner_fmt, w.windKmh),
                        accent = MotoColors.Orange,
                        onDismiss = { windDismissed = true },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                }
            }

        }

        // Settings and trips cover the whole screen, cutout strip included; they pad their own content.
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
                    onOpenTrips = { showSettings = false; showTrips = true },
                    themeMode = themeMode,
                    onThemeMode = vm.theme::setMode,
                    onClose = { showSettings = false },
                    vehicle = profile,
                    onVehicle = vm::setVehicle,
                    bikeType = routeOptions.bikeType,
                    onBikeType = { vm.nav.setOptions(routeOptions.copy(bikeType = it)) },
                    safetyCard = {
                        SosSettingsCard(
                            contacts = sosContacts,
                            onAdd = vm::addSosContact,
                            onRemove = vm::removeSosContact,
                            crashEnabled = sosCrash,
                            onCrashEnabled = vm::setSosCrashEnabled,
                            buttonEnabled = sosButton,
                            onButtonEnabled = vm::setSosButtonEnabled,
                            onTest = { vm.sos.trigger(SosReason.TEST) },
                        )
                    },
                )
            }

            if (showTrips) {
                TripsScreen(trips = vm.trips, onClose = { showTrips = false })
            }


        // The call screen paints edge to edge (it pads its own content); inside the padded box it left a bare strip at the cutout.
        if (callState.phase != CallPhase.IDLE) {
            CallScreen(
                callState, callerName, gps.speedKmh, navUi,
                onAnswer = vm.call::answer, onHangUp = vm.call::hangUp, onMute = vm.call::toggleMute, onRoute = vm.call::setRoute,
                onVolumeUp = vm.call::volumeUp, onVolumeDown = vm.call::volumeDown, onDigit = vm.call::sendDigit,
            )
        }

        // The first-launch question sits above everything but the SOS countdown.
        if (vehicleChosen == null) VehicleChooser(onChoose = vm::setVehicle)

        // Last, so nothing can cover an SOS countdown.
        SosScreen(sosUi, sosContacts.map { it.name }, onCancel = vm.sos::cancel, onDismiss = vm.sos::dismiss)
        }
    }
}

/** Below this charge, a phone with no charger on board saves power. */
private const val LOW_BATTERY_PERCENT = 30

/** No number entry while riding, same limit as the map search box. */
private const val PHONE_LOCK_KMH = 5

private fun toggleOverlay(context: Context, enabled: Boolean): Boolean {
    if (enabled && !overlayGranted(context)) {
        context.openOverlaySettings()
        return false
    }
    Prefs.setOverlayEnabled(context, enabled)
    if (enabled) OverlayService.start(context) else OverlayService.stop(context)
    return enabled
}
