package com.ridedeck.app.util

import android.appwidget.AppWidgetHost
import android.content.Context

/** One-time clean-up of things older builds left behind. */
object LegacyCleanup {
    private const val WIDGET_HOST_ID = 0x4D44
    private const val KEY_WIDGET_ID = "dash_widget_id"

    /** The dashboard once hosted a system widget; release the widget id it had bound. */
    fun run(context: Context) {
        val prefs = context.getSharedPreferences("ridedeck", Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_WIDGET_ID)) return
        try {
            AppWidgetHost(context.applicationContext, WIDGET_HOST_ID).deleteHost()
        } catch (_: RuntimeException) {
        }
        prefs.edit().remove(KEY_WIDGET_ID).apply()
    }
}
