package dev.ygtlab.carplay.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** Hosts one system widget on the dashboard, the way a home-screen launcher does. */
object WidgetHost {
    private const val HOST_ID = 0x4D44
    const val REQUEST_CONFIGURE = 4401

    private var host: AppWidgetHost? = null

    fun host(context: Context): AppWidgetHost =
        host ?: AppWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    fun start(context: Context) = host(context).startListening()

    fun stop(context: Context) {
        try { host(context).stopListening() } catch (_: RuntimeException) {}
    }

    /** Widgets meant for a home screen, by name. */
    fun providers(context: Context): List<AppWidgetProviderInfo> {
        val pm = context.packageManager
        return manager(context).installedProviders
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .sortedBy { it.loadLabel(pm).lowercase() }
    }

    fun delete(context: Context, id: Int) {
        if (id >= 0) host(context).deleteAppWidgetId(id)
    }
}

/** Carries the result of a widget's own configuration screen back from the activity. */
object WidgetBridge {
    var onConfigured: ((Boolean) -> Unit)? = null
}
