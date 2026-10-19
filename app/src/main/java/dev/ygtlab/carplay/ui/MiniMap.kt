package dev.ygtlab.carplay.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.ygtlab.carplay.data.LocationState
import dev.ygtlab.carplay.data.Place
import dev.ygtlab.carplay.data.Route
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val MINI_FULL_FPS = 30
private const val MINI_LOW_POWER_FPS = 15

/**
 * The dashboard card's own small live map: route line and position, camera following the heading.
 * It is only composed while guidance runs and the full map screen is closed, and runs at a low frame
 * rate (lower still when the phone is hot), so it costs little battery.
 */
@Composable
fun MiniMap(
    route: Route?,
    destination: Place?,
    me: LocationState,
    isDay: Boolean,
    lowPower: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    LaunchedEffect(lowPower) { mapView.setMaximumFps(if (lowPower) MINI_LOW_POWER_FPS else MINI_FULL_FPS) }

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
        // Composed after the activity started, so the events it already missed are replayed by hand.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) mapView.onCreate(null)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            if (!destroyed) {
                destroyed = true
                mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            }
        }
    }

    LaunchedEffect(map, isDay) {
        val m = map ?: return@LaunchedEffect
        styleReady = false
        m.setStyle(Style.Builder().fromUri(styleUrl(isDay))) { style ->
            addMapLayers(style)
            styleReady = true
        }
    }

    LaunchedEffect(styleReady, route, destination) {
        val style = map?.style?.takeIf { styleReady } ?: return@LaunchedEffect
        val line = route?.let { r -> LineString.fromLngLats(r.shape.map { Point.fromLngLat(it.lon, it.lat) }) }
        val routeSource = style.getSourceAs<GeoJsonSource>(SRC_ROUTE)
        if (line != null) routeSource?.setGeoJson(Feature.fromGeometry(line))
        else routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        val destSource = style.getSourceAs<GeoJsonSource>(SRC_DEST)
        if (destination != null) destSource?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(destination.point.lon, destination.point.lat)))
        else destSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
    }

    LaunchedEffect(styleReady, me.fixAtMs) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady || me.fixAtMs == 0L) return@LaunchedEffect
        m.style?.getSourceAs<GeoJsonSource>(SRC_ME)?.setGeoJson(Point.fromLngLat(me.lon, me.lat))
        val camera = CameraPosition.Builder()
            .target(LatLng(me.lat, me.lon))
            .zoom(16.0)
            .tilt(50.0)
            .bearing((me.bearing ?: m.cameraPosition.bearing.toFloat()).toDouble())
            .padding(0.0, mapView.height * 0.3, 0.0, 0.0)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 900)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            mapView.also { mv ->
                mv.getMapAsync { m ->
                    // A glance, not a control: taps and drags go to the card, which opens the full map.
                    m.uiSettings.setAllGesturesEnabled(false)
                    // MapLibre's own logo and (i) button look out of place on a dark card; the required credit is drawn by the card instead.
                    m.uiSettings.isLogoEnabled = false
                    m.uiSettings.isAttributionEnabled = false
                    map = m
                }
            }
        },
    )
}

/** The map data credit that OpenFreeMap and OpenStreetMap ask for, small and quiet. */
@Composable
fun MapCredit(modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        "OpenFreeMap © OpenMapTiles · © OpenStreetMap",
        fontSize = 9.sp,
        color = androidx.compose.ui.graphics.Color(0x99FFFFFF),
        modifier = modifier,
    )
}
