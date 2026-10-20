package dev.ygtlab.carplay.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.WidgetBridge
import dev.ygtlab.carplay.data.WidgetHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Color as AndroidColor

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Dashboard card showing the chosen system widget; the pencil (when stopped) picks another. */
@Composable
fun WidgetCard(widgetId: Int, canEdit: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember(widgetId) { if (widgetId >= 0) WidgetHost.manager(context).getAppWidgetInfo(widgetId) else null }
    val density = LocalDensity.current
    Box(modifier = modifier.clip(RoundedCornerShape(28.dp)).background(MotoColors.Card), contentAlignment = Alignment.Center) {
        if (info == null) {
            PillButton(stringResource(R.string.widget_pick), MotoColors.Blue, onPick, singleLine = true)
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                factory = { ctx -> WidgetHost.host(ctx).createView(ctx, widgetId, info).apply { setBackgroundColor(AndroidColor.TRANSPARENT) } },
                update = { view ->
                    val w = with(density) { view.width.toDp().value.toInt() }
                    val h = with(density) { view.height.toDp().value.toInt() }
                    if (w > 0 && h > 0) view.updateAppWidgetSize(null, w, h, w, h)
                },
            )
            if (canEdit) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(56.dp).clip(CircleShape)
                        .background(MotoColors.PanelHigh).clickable(onClick = onPick),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Edit, stringResource(R.string.widget_pick), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp)) }
            }
        }
    }
}

/** Starts and stops widget updates with the screen. */
@Composable
fun WidgetUpdates() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        WidgetHost.start(context)
        onDispose { WidgetHost.stop(context) }
    }
}

/**
 * Full-screen list of the phone's widgets. Picking one binds it (asking the system when it needs to), runs the
 * widget's own setup screen if it has one, and reports the new id through [onChosen].
 */
@Composable
fun WidgetPicker(currentId: Int, onChosen: (Int) -> Unit, onClose: () -> Unit) {
    val palette = LocalMotoPalette.current
    val context = LocalContext.current
    val providers by produceState(emptyList<AppWidgetProviderInfo>()) {
        value = withContext(Dispatchers.IO) { WidgetHost.providers(context) }
    }
    // The id being set up: kept across the system dialogs that come in between.
    var pendingId by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var pendingProvider by remember { androidx.compose.runtime.mutableStateOf<AppWidgetProviderInfo?>(null) }

    fun finish(id: Int) {
        if (currentId >= 0 && currentId != id) WidgetHost.delete(context, currentId)
        onChosen(id)
    }

    fun configureOrFinish(id: Int, info: AppWidgetProviderInfo) {
        val activity = context.findActivity()
        if (info.configure != null && activity != null) {
            WidgetBridge.onConfigured = { ok ->
                WidgetBridge.onConfigured = null
                if (ok) finish(id) else { WidgetHost.delete(context, id); pendingId = -1 }
            }
            WidgetHost.host(context).startAppWidgetConfigureActivityForResult(activity, id, 0, WidgetHost.REQUEST_CONFIGURE, null)
        } else finish(id)
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val info = pendingProvider
        if (result.resultCode == Activity.RESULT_OK && info != null && pendingId >= 0) configureOrFinish(pendingId, info)
        else { WidgetHost.delete(context, pendingId); pendingId = -1 }
    }

    fun pick(info: AppWidgetProviderInfo) {
        val id = WidgetHost.host(context).allocateAppWidgetId()
        pendingId = id
        pendingProvider = info
        if (WidgetHost.manager(context).bindAppWidgetIdIfAllowed(id, info.provider)) configureOrFinish(id, info)
        else bindLauncher.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.scrim).clickable(enabled = false) {}) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_pick), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = palette.text, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(palette.cardHigh).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = palette.text, modifier = Modifier.size(32.dp)) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(providers, key = { it.provider.flattenToString() }) { info ->
                    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, info) {
                        value = withContext(Dispatchers.Default) {
                            runCatching { info.loadIcon(context, 0)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card)
                            .clickable { pick(info) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        icon?.let { Image(it, null, modifier = Modifier.size(48.dp)) } ?: Box(Modifier.size(48.dp))
                        Text(
                            info.loadLabel(context.packageManager), fontSize = 20.sp, color = palette.text,
                            fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
