package world.wander.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import world.wander.app.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng as MlLatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillPattern
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import world.wander.app.data.Repository
import world.wander.app.tracking.TrackingService
import world.wander.engine.LatLng as WLatLng

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

// Placeholder palette: parchment fog, inked roads. Final art comes later.
private const val FOG = "#E9DFC7"
private const val INK = "#5B4636"

@Composable
fun MapScreen(repo: Repository, onOpenDispatch: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val world by repo.world.collectAsState()
    val layers by repo.layers.collectAsState()
    val track by repo.liveTrack.collectAsState()
    val me by repo.lastLocation.collectAsState()
    val status by repo.trackingStatus.collectAsState()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var centered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Show where you are as soon as the map opens, not only while recording.
    LaunchedEffect(Unit) { if (repo.lastLocation.value == null) currentLocation(context)?.let { repo.lastLocation.value = it } }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                map = m
                m.uiSettings.isCompassEnabled = true
                m.uiSettings.isAttributionEnabled = true
                m.cameraPosition = CameraPosition.Builder().target(MlLatLng(40.73, -74.17)).zoom(13.0).build()
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s -> addSprites(s, context); restyleBasemap(s); addWorldLayers(s); style = s }
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); mapView.onDestroy() }
    }

    // World layers (fog is computed off the main thread).
    LaunchedEffect(style, layers, world) {
        val s = style ?: return@LaunchedEffect
        val fog = withContext(Dispatchers.Default) { MapLayersBuilder.fog(layers) }
        s.source("fog")?.setGeoJson(fog)
        s.source("roads")?.setGeoJson(MapLayersBuilder.roads(layers))
        s.source("peek")?.setGeoJson(MapLayersBuilder.peeks(layers))
        s.source("wild")?.setGeoJson(MapLayersBuilder.wild(layers))
        val kinds = world?.landmarks.orEmpty().associate { it.name to it.kind }
        s.source("landmarks")?.setGeoJson(MapLayersBuilder.landmarkLabels(layers, kinds))
        s.source("settlements")?.setGeoJson(MapLayersBuilder.settlements(world))
    }
    LaunchedEffect(style, track) { style?.source("track")?.setGeoJson(MapLayersBuilder.track(track)) }
    LaunchedEffect(style, me) {
        style?.source("me")?.setGeoJson(MapLayersBuilder.point(me))
        val target = me ?: repo.prefs.homeLat?.let { lat -> repo.prefs.homeLng?.let { WLatLng(lat, it) } }
        if (!centered && target != null && map != null) {
            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(MlLatLng(target.lat, target.lng), 15.0))
            centered = true
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth()) {
            val today = world?.let { repo.dispatchFor(it, it.today) }
            Card(
                onClick = onOpenDispatch,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text(today?.lines?.firstOrNull() ?: "Your world is waiting. Go for a walk.", style = MaterialTheme.typography.bodyMedium)
                    world?.goal?.let { g ->
                        Text("Week: %.0f / %d min · %s".format(g.trailing7Minutes, g.goal, g.protection.label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        SmallFloatingActionButton(
            onClick = {
                scope.launch {
                    val t = currentLocation(context) ?: me
                    if (t == null) { repo.log("map", "No location fix available"); return@launch }
                    repo.lastLocation.value = t
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(MlLatLng(t.lat, t.lng), 16.0))
                }
            },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        ) { Text("◎") }

        val walking = status.startsWith("Charting")
        ExtendedFloatingActionButton(
            onClick = {
                TrackingService.start(context, if (walking) TrackingService.ACTION_STOP_MANUAL else TrackingService.ACTION_START_MANUAL)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Text(if (walking) "Stop walk" else "Start a walk") }
    }
}

/** A fresh location fix (falls back to the last known one). Null without permission or a fix. */
@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): WLatLng? = try {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() ?: client.lastLocation.await()
    loc?.let { WLatLng(it.latitude, it.longitude) }
} catch (_: Exception) { null }

private fun Style.source(id: String): GeoJsonSource? = getSourceAs(id)

private fun addWorldLayers(s: Style) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    for (id in listOf("fog", "peek", "wild", "roads", "landmarks", "track", "settlements", "me")) s.addSource(GeoJsonSource(id, empty))

    s.addLayer(FillLayer("fog", "fog").withProperties(fillPattern("fog_parchment"), fillOpacity(0.96f)))
    // Soft edge: a wide blurred line along the fog boundary.
    s.addLayer(LineLayer("fog-edge", "fog").withProperties(lineColor(FOG), lineWidth(16f), lineBlur(14f), lineOpacity(0.9f)))
    s.addLayer(
        LineLayer("peek", "peek").withProperties(
            lineColor(INK), lineOpacity(0.35f), lineWidth(1.5f), lineDasharray(arrayOf(1.5f, 2.5f)),
        ),
    )
    s.addLayer(
        LineLayer("roads", "roads").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            // Faint trail → path → cobblestone → road → lit avenue.
            lineColor(match(get("level"), literal("#A1887F"),
                stop(1, "#BCAAA4"), stop(2, "#A1887F"), stop(3, "#8D6E63"), stop(4, "#6D4C41"), stop(5, "#E0A100"))),
            lineWidth(interpolate(linear(), get("level"), stop(1, 1.5f), stop(3, 3.5f), stop(5, 6f))),
        ),
    )
    // Paths you made yourself: dashed footpaths that darken as they wear in.
    s.addLayer(
        LineLayer("wild", "wild").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#8D6E63"), lineOpacity(0.85f),
            lineDasharray(arrayOf(2f, 1.5f)),
            lineWidth(interpolate(linear(), get("level"), stop(1, 1.5f), stop(5, 4.5f))),
        ),
    )
    s.addLayer(
        SymbolLayer("landmark-names", "landmarks").withProperties(
            iconImage(get("icon")), iconSize(0.5f), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            textField(get("name")), textSize(12f), textColor("#2E5E3A"), textHaloColor("#F4ECD8"), textHaloWidth(2f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 0.2f)),
        ),
    )
    s.addLayer(
        SymbolLayer("road-names", "roads").withProperties(
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), textField(get("name")), textSize(11f),
            textColor(INK), textHaloColor("#FFFFFF"), textHaloWidth(1.2f),
        ).also { it.minZoom = 14f },
    )
    s.addLayer(LineLayer("track", "track").withProperties(lineColor("#C0392B"), lineWidth(3f), lineOpacity(0.8f)))
    s.addLayer(
        SymbolLayer("settlements", "settlements").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.35f), stop(14, 0.6f), stop(17, 1.0f))),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )
    s.addLayer(
        SymbolLayer("settlement-labels", "settlements").withProperties(
            textField(get("label")), textSize(interpolate(linear(), zoom(), stop(10, 11f), stop(16, 15f))),
            textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 0.3f)),
        ),
    )
    s.addLayer(
        SymbolLayer("me", "me").withProperties(
            iconImage("wanderer"), iconSize(0.6f), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )
}

private val SPRITES = mapOf(
    "tier_0" to R.drawable.sprite_tier_0, "tier_1" to R.drawable.sprite_tier_1, "tier_2" to R.drawable.sprite_tier_2,
    "tier_3" to R.drawable.sprite_tier_3, "tier_4" to R.drawable.sprite_tier_4, "tier_5" to R.drawable.sprite_tier_5,
    "tier_6" to R.drawable.sprite_tier_6, "place_park" to R.drawable.sprite_place_park,
    "place_store" to R.drawable.sprite_place_store, "place_generic" to R.drawable.sprite_place_generic,
    "wanderer" to R.drawable.sprite_wanderer, "fog_parchment" to R.drawable.fog_parchment,
)

private fun addSprites(s: Style, context: Context) {
    val opts = BitmapFactory.Options().apply { inScaled = false }
    for ((name, res) in SPRITES) BitmapFactory.decodeResource(context.resources, res, opts)?.let { s.addImage(name, it) }
}

/** Recolors the base map toward parchment and hides its labels: names appear only once you discover them. */
private fun restyleBasemap(s: Style) {
    for (layer in s.layers) {
        val id = layer.id.lowercase()
        when (layer) {
            is BackgroundLayer -> layer.setProperties(backgroundColor("#EFE6CF"))
            is FillLayer -> when {
                "water" in id -> layer.setProperties(fillColor("#A9C4C7"))
                listOf("park", "wood", "grass", "landcover", "forest", "garden").any { it in id } -> layer.setProperties(fillColor("#D3DEB0"))
                "building" in id -> layer.setProperties(fillColor("#E0CFAE"))
                "landuse" in id -> layer.setProperties(fillColor("#E9DEC2"))
            }
            is LineLayer -> if (listOf("road", "highway", "street", "path", "bridge", "tunnel", "rail").any { it in id }) {
                layer.setProperties(lineColor("#E6D8B8"))
            }
            is SymbolLayer -> layer.setProperties(visibility(Property.NONE))
        }
    }
}
