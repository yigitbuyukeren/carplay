package dev.ygtlab.carplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ygtlab.carplay.data.GeoPoint
import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.NavUi
import dev.ygtlab.carplay.data.NavigationController
import dev.ygtlab.carplay.data.Place
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
private const val SRC_ROUTE = "route"
private const val SRC_DEST = "dest"
private const val SRC_ME = "me"

@Composable
fun MapScreen(
    nav: NavigationController,
    locationState: StateFlow<LocationState>,
    speedKmh: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by nav.ui.collectAsStateWithLifecycle()
    val me by locationState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var centeredOnce by remember { mutableStateOf(false) }

    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) { destroyed = true; mapView.onDestroy() }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                destroyed = true
                mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            }
        }
    }

    // My position marker + camera following.
    LaunchedEffect(styleReady, me.fixAtMs, ui.navigating) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady || me.fixAtMs == 0L) return@LaunchedEffect
        m.style?.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(Point.fromLngLat(me.lon, me.lat))
        val target = LatLng(me.lat, me.lon)
        when {
            ui.navigating -> {
                val camera = CameraPosition.Builder()
                    .target(target)
                    .zoom(17.0)
                    .tilt(55.0)
                    .bearing((me.bearing ?: m.cameraPosition.bearing.toFloat()).toDouble())
                    .padding(0.0, mapView.height * 0.45, 0.0, 0.0)
                    .build()
                m.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 900)
            }
            !centeredOnce -> {
                centeredOnce = true
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 15.0))
            }
        }
    }

    // Route line and destination pin.
    LaunchedEffect(styleReady, ui.route, ui.destination) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect
        val route = ui.route
        val line = route?.let { r -> LineString.fromLngLats(r.shape.map { Point.fromLngLat(it.lon, it.lat) }) }
        val routeSource = style.getSourceAs<GeoJsonSource>(SRC_ROUTE)
        if (line != null) routeSource?.setGeoJson(Feature.fromGeometry(line))
        else routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        val dest = ui.destination
        val destSource = style.getSourceAs<GeoJsonSource>(SRC_DEST)
        if (dest != null) destSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(dest.point.lon, dest.point.lat)))
        else destSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        if (route != null && !ui.navigating) {
            val bounds = LatLngBounds.Builder().apply {
                route.shape.forEach { include(LatLng(it.lat, it.lon)) }
            }.build()
            m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 520, 140, 140, 140), 800
            )
        }
    }

    // Leaving navigation: flatten the camera back to a north-up overview.
    LaunchedEffect(ui.navigating) {
        val m = map ?: return@LaunchedEffect
        if (!ui.navigating && me.fixAtMs > 0 && centeredOnce) {
            val camera = CameraPosition.Builder()
                .target(LatLng(me.lat, me.lon)).zoom(15.0).tilt(0.0).bearing(0.0)
                .padding(0.0, 0.0, 0.0, 0.0).build()
            m.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 700)
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(28.dp)).border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(28.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.also { mv ->
                    mv.getMapAsync { m ->
                        map = m
                        m.uiSettings.isRotateGesturesEnabled = true
                        m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                            addMapLayers(style)
                            styleReady = true
                        }
                        m.addOnMapLongClickListener { latLng ->
                            nav.selectPlace(Place("Seçilen konum", "", GeoPoint(latLng.latitude, latLng.longitude)))
                            true
                        }
                    }
                }
            },
        )

        // Left side panel, CarPlay style.
        Column(
            modifier = Modifier.fillMaxHeight().width(380.dp).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                ui.navigating -> ManeuverBanner(ui)
                ui.arrived -> ArrivedCard(onDone = nav::stopNavigation)
                ui.route != null -> RouteCard(ui, onStart = nav::startNavigation, onCancel = nav::stopNavigation)
                else -> SearchPanel(ui, nav::onQueryChange, nav::search, nav::selectPlace)
            }
            if (ui.rerouting) Chip("Rota yeniden hesaplanıyor…", MotoColors.Orange)
        }

        if (ui.navigating) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).width(356.dp),
            ) { TripBar(ui, onStop = nav::stopNavigation) }
        }

        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            RoundIconButton(Icons.Rounded.Close, onClick = onClose)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (!ui.navigating) {
                RoundIconButton(Icons.Rounded.MyLocation, onClick = {
                    val m = map
                    if (m != null && me.fixAtMs > 0) {
                        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(me.lat, me.lon), 16.0), 600)
                    }
                })
            }
            SpeedChip(speedKmh)
        }
    }
}

private fun addMapLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_ROUTE))
    style.addLayer(
        LineLayer("route-casing", SRC_ROUTE).withProperties(
            lineColor("#06306B"), lineWidth(11f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer("route-line", SRC_ROUTE).withProperties(
            lineColor("#0A84FF"), lineWidth(7f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addSource(GeoJsonSource(SRC_DEST))
    style.addLayer(
        CircleLayer("dest-pin", SRC_DEST).withProperties(
            circleColor("#FF453A"), circleRadius(9f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)
        )
    )
    style.addSource(GeoJsonSource(SRC_ME))
    style.addLayer(CircleLayer("me-halo", SRC_ME).withProperties(circleColor("#0A84FF"), circleRadius(18f), circleOpacity(0.25f)))
    style.addLayer(
        CircleLayer("me-dot", SRC_ME).withProperties(
            circleColor("#0A84FF"), circleRadius(8f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f)
        )
    )
}

// ---- panel pieces -------------------------------------------------------------------------

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xE6101114))
            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun SearchPanel(ui: NavUi, onQuery: (String) -> Unit, onSearch: () -> Unit, onSelect: (Place) -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); focus.clearFocus(); onSearch() }
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MotoColors.CardHigh).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = MotoColors.Gray, modifier = Modifier.size(24.dp))
                Box(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                    if (ui.query.isEmpty()) Text("Nereye gidiyoruz?", fontSize = 18.sp, color = MotoColors.Gray)
                    BasicTextField(
                        value = ui.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(12.dp)).background(MotoColors.Blue)
                        .clickable { submit() }.padding(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("Ara", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
            }
            when {
                ui.loading -> Text("Rota hesaplanıyor…", fontSize = 15.sp, color = MotoColors.Gray)
                ui.searching -> Text("Aranıyor…", fontSize = 15.sp, color = MotoColors.Gray)
                ui.error != null -> Text(ui.error, fontSize = 15.sp, color = MotoColors.Orange)
                ui.results.isEmpty() && ui.query.isEmpty() ->
                    Text("İpucu: haritaya uzun basarak da hedef seçebilirsin.", fontSize = 14.sp, color = MotoColors.Gray)
            }
            if (ui.results.isNotEmpty()) {
                LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(ui.results) { place ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { keyboard?.hide(); focus.clearFocus(); onSelect(place) }.padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(place.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (place.detail.isNotBlank()) {
                                Text(place.detail, fontSize = 14.sp, color = MotoColors.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteCard(ui: NavUi, onStart: () -> Unit, onCancel: () -> Unit) {
    val route = ui.route ?: return
    val eta = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + route.durationS.toLong() * 1000))
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(ui.destination?.name.orEmpty(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${formatDistance(route.distanceM.toInt())} · ${formatDuration(route.durationS.toInt())} · Varış $eta",
                fontSize = 16.sp, color = MotoColors.Gray,
            )
            PillButton("Başlat", MotoColors.Green, onStart, Modifier.fillMaxWidth())
            PillButton("Vazgeç", MotoColors.CardHigh, onCancel, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ArrivedCard(onDone: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hedefe ulaştınız", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            PillButton("Tamam", MotoColors.Blue, onDone, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ManeuverBanner(ui: NavUi) {
    val route = ui.route ?: return
    val maneuver = route.maneuvers.getOrNull(ui.maneuverIndex) ?: return
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1B6FE0), Color(0xFF0B4FB3))))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(maneuverIcon(maneuver.type), null, tint = Color.White, modifier = Modifier.size(64.dp))
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(formatDistance(ui.distToNextM), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(maneuver.instruction, fontSize = 16.sp, color = Color(0xE6FFFFFF), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TripBar(ui: NavUi, onStop: () -> Unit) {
    val eta = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + ui.remainingS * 1000L))
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(eta, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${formatDuration(ui.remainingS)} · ${formatDistance(ui.remainingM)}", fontSize = 15.sp, color = MotoColors.Gray)
            }
            PillButton("Bitir", MotoColors.Red, onStop)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xE6101114)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, fontSize = 15.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xE6101114))
            .border(1.dp, Color(0x26FFFFFF), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun SpeedChip(speedKmh: Int) {
    Column(
        modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xE6101114)).border(1.dp, Color(0x26FFFFFF), CircleShape),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(speedKmh.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("km/sa", fontSize = 11.sp, color = MotoColors.Gray)
    }
}

fun formatDistance(meters: Int): String = when {
    meters < 1000 -> "${(meters / 10 * 10).coerceAtLeast(10)} m"
    else -> "%.1f km".format(Locale.US, meters / 1000.0)
}

fun formatDuration(seconds: Int): String {
    val minutes = (seconds + 30) / 60
    return if (minutes >= 60) "${minutes / 60} sa ${"%02d".format(minutes % 60)} dk" else "${maxOf(minutes, 1)} dk"
}
