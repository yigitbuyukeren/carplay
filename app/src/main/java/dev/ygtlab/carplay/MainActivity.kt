package dev.ygtlab.carplay

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import dev.ygtlab.carplay.util.Units
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.ygtlab.carplay.data.SosPhase
import dev.ygtlab.carplay.service.OverlayService
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.ygtlab.carplay.ui.DashboardScreen
import dev.ygtlab.carplay.ui.MotoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Units.load(this)
        dev.ygtlab.carplay.util.LegacyCleanup.run(this)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) { handleDialIntent(intent); handleVoiceIntent(intent) }
        // A hot phone dims itself (see ThermalGuard); a cool one keeps the system brightness.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.thermal.state, viewModel.sos.ui, viewModel.brightnessChoice) { thermal, sos, chosen ->
                    // Never leave a dimmed screen during an SOS countdown; a hot phone stays dimmer than the rider asked.
                    when {
                        sos.phase == SosPhase.COUNTDOWN -> 1f
                        chosen == null -> thermal.brightness
                        thermal.brightness >= 0f -> minOf(thermal.brightness, chosen)
                        else -> chosen
                    }
                }.distinctUntilChanged().collect { brightness ->
                    window.attributes = window.attributes.also { it.screenBrightness = brightness }
                }
            }
        }
        // Only the bicycle may stand upright on the handlebar; the others keep the landscape dashboard.
        lifecycleScope.launch {
            viewModel.profile.collect { profile ->
                requestedOrientation = if (profile.config.allowsPortrait) ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        setContent {
            val isDay by viewModel.theme.isDay.collectAsStateWithLifecycle()
            MotoTheme(isDay) { DashboardScreen(viewModel) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDialIntent(intent)
        handleVoiceIntent(intent)
    }

    /** The helmet's voice button and the system assist gesture start listening. */
    private fun handleVoiceIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VOICE_COMMAND || intent?.action == Intent.ACTION_ASSIST) viewModel.requestVoice()
    }

    /** Another app's tel: link: the phone screen opens with the number on the keypad. */
    private fun handleDialIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL && intent.data?.scheme == "tel") {
            viewModel.dialRequest.value = intent.data?.schemeSpecificPart.orEmpty()
        }
    }

    override fun onResume() {
        super.onResume()
        OverlayService.appInForeground = true
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        OverlayService.appInForeground = false
        viewModel.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
