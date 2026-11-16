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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.expressions.Expression.gte
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.layers.PropertyFactory.lineOffset
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.expressions.Expression.lt
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
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.linePattern
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
import org.maplibre.android.style.layers.PropertyFactory.textFont
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
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                    val problems = mutableListOf<String>()
                    try { addSprites(s, context) } catch (e: Exception) { problems += "sprites: $e" }
                    try { restyleBasemap(s) } catch (e: Exception) { problems += "basemap: $e" }
                    try { addWorldLayers(s, problems) } catch (e: Exception) { problems += "layers: $e" }
                    val missingImages = SPRITES.keys.filter { s.getImage(it) == null }
                    val missingLayers = OUR_LAYERS.filter { s.getLayer(it) == null }
                    repo.log("map", "Style ready; missing images: ${missingImages.ifEmpty { "none" }}; missing layers: ${missingLayers.ifEmpty { "none" }}" +
                        if (problems.isEmpty()) "" else "; problems: " + problems.joinToString("; "))
                    style = s
                }
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

    // World layers (fog is computed off the main thread). Each source updates independently, so one
    // failure can't hide the rest; failures and counts go to the diagnostics log.
    var lastSummary by remember { mutableStateOf("") }
    LaunchedEffect(style, layers, world) {
        val s = style ?: return@LaunchedEffect
        val errors = mutableListOf<String>()
        fun update(id: String, build: () -> FeatureCollection): Int = try {
            val fc = build()
            val src = s.source(id) ?: throw IllegalStateException("source missing")
            src.setGeoJson(fc)
            fc.features()?.size ?: 0
        } catch (e: Exception) { errors += "$id: ${e.javaClass.simpleName} ${e.message}"; -1 }
        val fe = try { withContext(Dispatchers.Default) { MapLayersBuilder.fogAndExplored(layers) } } catch (e: Exception) { errors += "fog: $e"; null }
        if (fe != null) { update("fog") { fe.first }; update("explored") { fe.second } }
        val kinds = world?.landmarks.orEmpty().associate { it.name to it.kind }
        val counts = linkedMapOf(
            "green" to update("green-places") { MapLayersBuilder.greenPlaces(layers, kinds) },
            "roads" to update("roads") { MapLayersBuilder.roads(layers) },
            "peek" to update("peek") { MapLayersBuilder.peeks(layers) },
            "wild" to update("wild") { MapLayersBuilder.wild(layers) },
            "places" to update("landmarks") { MapLayersBuilder.landmarkLabels(layers, kinds) },
            "settlements" to update("settlements") { MapLayersBuilder.settlements(world) },
            "waypoints" to update("waypoints") { MapLayersBuilder.waypoints(world) },
            "waystones" to update("waystones") { MapLayersBuilder.waystones(world) },
        )
        val summary = counts.entries.joinToString { "${it.key}=${it.value}" } + if (errors.isEmpty()) "" else " | errors: " + errors.joinToString("; ")
        if (summary != lastSummary) { lastSummary = summary; repo.log("map", summary) }
    }
    // Fly to whatever the Atlas or search picked.
    val focus by repo.focus.collectAsState()
    LaunchedEffect(map, focus) {
        val f = focus ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(MlLatLng(f.lat, f.lng), 15.5), 1200)
        centered = true
        repo.focus.value = null
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

        var showLegend by remember { mutableStateOf(false) }
        if (showLegend) LegendDialog(onDismiss = { showLegend = false })
        SmallFloatingActionButton(
            onClick = { showLegend = true },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 76.dp),
        ) { Text("?") }
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

private val OUR_LAYERS = listOf(
    "explored", "green-places", "fog", "fog-edge", "peek", "roads-edge", "roads-trail", "roads-dirt", "roads-cobble",
    "roads-trees-l", "roads-trees-r", "wild", "capital-badge",
    "landmark-icons", "landmark-names", "road-names", "track", "waypoints", "waypoint-labels", "waystones", "waystone-labels",
    "settlement-dots", "settlements", "settlement-labels", "me",
)

private fun addWorldLayers(s: Style, problems: MutableList<String>) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    for (id in listOf("explored", "green-places", "fog", "peek", "wild", "roads", "landmarks", "track", "waypoints", "waystones", "settlements", "me")) s.addSource(GeoJsonSource(id, empty))

    // Explored ground: real terrain texture (0 A.D. grass / forest floor), slipped under the base map's
    // water, roads and buildings so those still show where you've been.
    val ground = FillLayer("explored", "explored").withProperties(fillPattern("ground_grass"), fillOpacity(0.92f))
    val woods = FillLayer("green-places", "green-places").withProperties(fillPattern("ground_forest"), fillOpacity(0.85f))
    val waterId = s.layers.firstOrNull { it is FillLayer && "water" in it.id.lowercase() }?.id
    if (waterId != null) { s.addLayerBelow(ground, waterId); s.addLayerAbove(woods, "explored") }
    else { s.addLayer(ground); s.addLayer(woods) }

    // Fog: parchment you can see the land through, thicker when zoomed out so explored areas pop.
    s.addLayer(
        FillLayer("fog", "fog").withProperties(
            fillPattern("fog_parchment"),
            fillOpacity(interpolate(linear(), zoom(), stop(8, 0.85f), stop(13, 0.75f), stop(17, 0.62f))),
        ),
    )
    // Soft edge: a wide blurred line along the fog boundary.
    s.addLayer(LineLayer("fog-edge", "fog").withProperties(lineColor(FOG), lineWidth(12f), lineBlur(10f), lineOpacity(0.6f)))
    s.addLayer(
        LineLayer("peek", "peek").withProperties(
            lineColor(INK), lineOpacity(0.35f), lineWidth(1.5f), lineDasharray(arrayOf(1.5f, 2.5f)),
        ),
    )
    // Charted roads, five stages by distinct days walked (see the Legend):
    // 1 Trail (dashed sand) · 2 Footpath (dirt) · 3 Lane (dirt + edge) · 4 Cobbled road · 5 Avenue (cobbles, tree-lined).
    fun level(n: Int) = eq(get("level"), literal(n))
    guard(problems, "roads-edge") {
        s.addLayer(
            LineLayer("roads-edge", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineColor(match(get("level"), literal("#5E5A52"), stop(3, "#4A3A28"))), lineOpacity(0.75f),
                lineWidth(match(get("level"), literal(11f), stop(3, 7.5f), stop(4, 9.5f))),
            ).also { it.setFilter(gte(get("level"), literal(3))) },
        )
    }
    guard(problems, "roads-trail") {
        s.addLayer(
            LineLayer("roads-trail", "roads").withProperties(
                lineCap(Property.LINE_CAP_BUTT), lineColor("#E2CDA0"), lineWidth(2.5f), lineDasharray(arrayOf(1.6f, 1.2f)),
            ).also { it.setFilter(level(1)) },
        )
    }
    guard(problems, "roads-dirt") {
        s.addLayer(
            LineLayer("roads-dirt", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineColor("#D2B88A"), linePattern("road_dirt"), lineWidth(match(get("level"), literal(5f), stop(2, 3.5f))),
            ).also { it.setFilter(all(gte(get("level"), literal(2)), lt(get("level"), literal(4)))) },
        )
    }
    guard(problems, "roads-cobble") {
        s.addLayer(
            LineLayer("roads-cobble", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineColor("#8E8A80"), linePattern("road_cobble"), lineWidth(match(get("level"), literal(8.5f), stop(4, 6.5f))),
            ).also { it.setFilter(gte(get("level"), literal(4))) },
        )
    }
    for ((id, offset) in listOf("roads-trees-l" to -9f, "roads-trees-r" to 9f)) guard(problems, id) {
        s.addLayer(
            LineLayer(id, "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineColor("#3A6832"), lineWidth(6f), lineOffset(offset),
                lineDasharray(arrayOf(0.01f, 3.2f)),
            ).also { it.setFilter(level(5)) },
        )
    }
    // Paths you made yourself: dashed footpaths that darken as they wear in.
    s.addLayer(
        LineLayer("wild", "wild").withProperties(
            lineCap(Property.LINE_CAP_BUTT), lineColor("#ECDEBA"), lineOpacity(0.95f),
            lineDasharray(arrayOf(1.2f, 1.0f)),
            lineWidth(interpolate(linear(), get("level"), stop(1, 1.5f), stop(5, 4.5f))),
        ),
    )
    s.addLayer(
        SymbolLayer("landmark-icons", "landmarks").withProperties(
            iconImage(get("icon")), iconSize(interpolate(linear(), zoom(), stop(10, 0.3f), stop(16, 0.5f))),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM), iconAllowOverlap(true), iconIgnorePlacement(true),
        ),
    )
    guard(problems, "landmark-names") {
        s.addLayer(
            SymbolLayer("landmark-names", "landmarks").withProperties(
                textField(get("name")), textFont(FONT), textSize(12f), textColor("#2E5E3A"), textHaloColor("#F4ECD8"), textHaloWidth(2f),
                textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 0.2f)),
            ),
        )
    }
    guard(problems, "road-names") { s.addLayer(
        SymbolLayer("road-names", "roads").withProperties(
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), textField(get("name")), textFont(FONT), textSize(11f),
            textColor(INK), textHaloColor("#FFFFFF"), textHaloWidth(1.2f),
        ).also { it.minZoom = 14f },
    ) }
    s.addLayer(LineLayer("track", "track").withProperties(lineColor("#C0392B"), lineWidth(3f), lineOpacity(0.8f)))
    // Waypoints: a signpost for every area you've walked in, readable from far out.
    s.addLayer(
        SymbolLayer("waypoints", "waypoints").withProperties(
            iconImage("medal_waypoint"), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(8, 0.25f), stop(13, 0.35f), stop(17, 0.5f))),
        ),
    )
    guard(problems, "waypoint-labels") {
        s.addLayer(
            SymbolLayer("waypoint-labels", "waypoints").withProperties(
                textField(get("name")), textFont(FONT), textSize(interpolate(linear(), zoom(), stop(8, 10f), stop(15, 13f))),
                textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f),
                textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 0.2f)),
            ),
        )
    }
    s.addLayer(
        SymbolLayer("waystones", "waystones").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(9, 0.3f), stop(14, 0.45f), stop(17, 0.6f))),
        ).also { it.minZoom = 9f },
    )
    guard(problems, "waystone-labels") {
        s.addLayer(
            SymbolLayer("waystone-labels", "waystones").withProperties(
                textField(get("label")), textFont(FONT), textSize(10f), textColor("#2E4A6B"), textHaloColor("#F4ECD8"), textHaloWidth(2f),
                textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 1.6f)),
            ).also { it.minZoom = 12f },
        )
    }
    // A gold dot under each medallion, so a settlement always shows even if its image fails.
    s.addLayer(
        CircleLayer("settlement-dots", "settlements").withProperties(
            circleColor("#C4A052"), circleStrokeColor("#4A3A28"), circleStrokeWidth(2f),
            circleRadius(interpolate(linear(), zoom(), stop(8, 3f), stop(16, 5f))),
        ),
    )
    s.addLayer(
        SymbolLayer("settlements", "settlements").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconSize(interpolate(linear(), zoom(), stop(8, 0.3f), stop(14, 0.45f), stop(17, 0.65f))),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
        ),
    )
    guard(problems, "capital-badge") {
        s.addLayer(
            SymbolLayer("capital-badge", "settlements").withProperties(
                iconImage("badge_capital"), iconAllowOverlap(true), iconIgnorePlacement(true),
                iconSize(interpolate(linear(), zoom(), stop(8, 0.35f), stop(14, 0.5f), stop(17, 0.7f))),
                iconOffset(arrayOf(42f, -42f)),
            ).also { it.setFilter(eq(get("capital"), literal(true))) },
        )
    }
    guard(problems, "settlement-labels") { s.addLayer(
        SymbolLayer("settlement-labels", "settlements").withProperties(
            textField(get("label")), textFont(FONT), textSize(interpolate(linear(), zoom(), stop(10, 11f), stop(16, 15f))),
            textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 2.2f)),
        ),
    ) }
    s.addLayer(
        SymbolLayer("me", "me").withProperties(
            iconImage("wanderer"), iconSize(0.6f), iconAllowOverlap(true), iconIgnorePlacement(true),
            iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )
}

/** Map images. oad_* are adapted from 0 A.D. (© Wildfire Games, CC BY-SA 3.0); see Settings → Credits. */
private inline fun guard(problems: MutableList<String>, what: String, block: () -> Unit) {
    try { block() } catch (e: Exception) { problems += "$what: ${e.javaClass.simpleName} ${e.message}" }
}

private val SPRITES = mapOf(
    "medal_tier_0" to R.drawable.oad_medal_tier_0, "medal_tier_1" to R.drawable.oad_medal_tier_1,
    "medal_tier_2" to R.drawable.oad_medal_tier_2, "medal_tier_3" to R.drawable.oad_medal_tier_3,
    "medal_tier_4" to R.drawable.oad_medal_tier_4, "medal_tier_5" to R.drawable.oad_medal_tier_5,
    "medal_tier_6" to R.drawable.oad_medal_tier_6, "medal_waypoint" to R.drawable.oad_medal_waypoint,
    "medal_place_park" to R.drawable.oad_medal_place_park, "medal_place_wood" to R.drawable.oad_medal_place_wood,
    "medal_place_store" to R.drawable.oad_medal_place_store, "medal_place_generic" to R.drawable.oad_medal_place_generic,
    "ground_grass" to R.drawable.oad_ground_grass, "ground_forest" to R.drawable.oad_ground_forest,
    "road_dirt" to R.drawable.oad_road_dirt, "road_cobble" to R.drawable.oad_road_cobble,
    "wanderer" to R.drawable.sprite_wanderer, "fog_parchment" to R.drawable.fog_parchment,
    "waystone" to R.drawable.sprite_waystone,
    "medal_waystone" to R.drawable.medal_waystone, "medal_waystone_great" to R.drawable.medal_waystone_great,
    "badge_capital" to R.drawable.badge_capital,
)

private fun addSprites(s: Style, context: Context) {
    val opts = BitmapFactory.Options().apply { inScaled = false }
    for ((name, res) in SPRITES) BitmapFactory.decodeResource(context.resources, res, opts)?.let { s.addImage(name, it) }
}

/** The base map's own font stack; text in any other font fails to load and hides the whole layer. */
private var FONT: Array<String> = arrayOf("Noto Sans Regular")

/** Recolors the base map toward parchment and hides its labels: names appear only once you discover them. */
private fun restyleBasemap(s: Style) {
    s.layers.filterIsInstance<SymbolLayer>().firstNotNullOfOrNull { it.textFont.value?.takeIf { f -> f.isNotEmpty() } }?.let { FONT = it }
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
