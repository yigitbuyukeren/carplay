package dev.ygtlab.carplay.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

data class NavApp(val label: String, val packageName: String)

val NAV_APPS = listOf(
    NavApp("Google Haritalar", "com.google.android.apps.maps"),
    NavApp("Waze", "com.waze"),
    NavApp("Yandex Navi", "ru.yandex.yandexnavi"),
    NavApp("Yandex Haritalar", "ru.yandex.yandexmaps"),
)

data class MusicApp(val label: String, val packageName: String)

val MUSIC_APPS = listOf(
    MusicApp("Spotify", "com.spotify.music"),
    MusicApp("YouTube Music", "com.google.android.apps.youtube.music"),
)

private val MUSIC_PACKAGES = MUSIC_APPS.map { it.packageName }

fun Context.isInstalled(pkg: String): Boolean =
    packageManager.getLaunchIntentForPackage(pkg) != null

fun Context.launchPackage(pkg: String): Boolean {
    val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
    return true
}

fun Context.openNavigation(): Boolean =
    NAV_APPS.firstOrNull { isInstalled(it.packageName) }?.let { launchPackage(it.packageName) } ?: false

fun Context.openMusicApp(preferred: String?): Boolean {
    val candidates = listOfNotNull(preferred) + MUSIC_PACKAGES
    return candidates.any { launchPackage(it) }
}

fun Context.openDialer() {
    startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.openSettingsAction(action: String) {
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.openOverlaySettings() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
