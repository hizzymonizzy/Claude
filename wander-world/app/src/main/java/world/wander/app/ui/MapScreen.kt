package world.wander.app.ui

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import world.wander.engine.Geo
import world.wander.app.data.ResourceTarget
import world.wander.app.data.MapLayers
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
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
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.FeatureCollection
import world.wander.app.data.Repository
import world.wander.app.tracking.TrackingService
import world.wander.engine.LatLng as WLatLng

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

// Placeholder palette: parchment fog, inked roads. Final art comes later.

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
    var cameraTick by remember { mutableIntStateOf(0) }
    var topInsetPx by remember { mutableFloatStateOf(0f) }
    var targets by remember { mutableStateOf<List<ResourceTarget>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val showPointers by repo.resourcePointers.collectAsState()
    val scope = rememberCoroutineScope()
    // Show where you are as soon as the map opens, not only while recording.
    LaunchedEffect(Unit) { if (repo.lastLocation.value == null) currentLocation(context)?.let { repo.lastLocation.value = it } }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                map = m
                m.addOnCameraMoveListener { cameraTick++ }
                m.addOnCameraIdleListener { cameraTick++ }
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
        val fe = try { withContext(Dispatchers.Default) { MapLayersBuilder.fogAndExplored(layers, world?.waystones.orEmpty().map { it.center }) } } catch (e: Exception) { errors += "fog: $e"; null }
        if (fe != null) { update("fog") { fe.first }; update("explored") { fe.second } }
        val kinds = world?.landmarks.orEmpty().associate { it.name to it.kind }
        val counts = linkedMapOf(
            "green" to update("green-places") { MapLayersBuilder.greenPlaces(layers, kinds) },
            "sand" to update("sand-places") { MapLayersBuilder.sandPlaces(layers, kinds) },
            "water" to update("water") { MapLayersBuilder.water(layers) },
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
    // The wanderer paddles while you're on the water.
    val captureMode by repo.captureMode.collectAsState()
    LaunchedEffect(style, captureMode) {
        style?.getLayerAs<SymbolLayer>("me")?.setProperties(iconImage(if (captureMode == "WATER") "wanderer_paddle" else "wanderer"))
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

    // Nearest source of each resource, from where you are (or home), refreshed as you move or the world changes.
    var targetsFrom by remember { mutableStateOf<Pair<WLatLng, MapLayers>?>(null) }
    LaunchedEffect(me, layers) {
        val from = me ?: repo.prefs.homeLat?.let { lat -> repo.prefs.homeLng?.let { WLatLng(lat, it) } } ?: return@LaunchedEffect
        val last = targetsFrom
        if (last != null && last.second === layers && Geo.distanceM(last.first, from) < 40) return@LaunchedEffect
        targetsFrom = from to layers
        searching = true
        // A newer search may cancel this one (the world just rebuilt); let it, rather than blanking the pointers.
        targets = try { repo.nearestSources(from) } catch (e: CancellationException) { targetsFrom = null; throw e }
            catch (e: Exception) { repo.log("map", "Resource search failed: ${e.message}"); targets }
        searching = false
        repo.log("map", "Resource pointers: " + targets.joinToString { "${it.resource} ${formatDistance(it.distanceM)}" }.ifEmpty { "none nearby yet" })
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (showPointers && targets.isNotEmpty()) {
            ResourcePointers(
                targets = targets,
                project = { p -> map?.projection?.toScreenLocation(MlLatLng(p.lat, p.lng))?.let { it.x to it.y } },
                topInsetPx = topInsetPx,
                onPick = { repo.focus.value = it.point },
                cameraTick = cameraTick,
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth()
                .onGloballyPositioned { topInsetPx = it.boundsInParent().bottom },
        ) {
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
            world?.let { w ->
                Spacer(Modifier.height(6.dp))
                InventoryBar(w.treasury, targets) { repo.focus.value = it.point }
                if (showPointers && targets.isEmpty()) {
                    Text(
                        if (searching) "Looking for resources nearby…" else "No wood, food, stone or metal mapped near here yet",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
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

        val recording = captureMode != null
        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = {
                    TrackingService.start(context, if (recording) TrackingService.ACTION_STOP_MANUAL else TrackingService.ACTION_START_MANUAL)
                },
            ) { Text(if (recording) "Stop" else "Start") }
        }
    }
}

/** A fresh location fix (falls back to the last known one). Null without permission or a fix. */
@SuppressLint("MissingPermission")
private suspend fun currentLocation(context: Context): WLatLng? = try {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val loc = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await() ?: client.lastLocation.await()
    loc?.let { WLatLng(it.latitude, it.longitude) }
} catch (_: Exception) { null }
