package dev.ygtlab.carplay.util

import android.content.Context

object Prefs {
    private const val FILE = "motodash"
    private const val KEY_OVERLAY = "overlay_enabled"

    fun overlayEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_OVERLAY, false)

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_OVERLAY, enabled).apply()
    }
}
