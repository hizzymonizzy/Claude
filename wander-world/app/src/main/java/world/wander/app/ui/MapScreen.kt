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
import world.wander.engine.Cells
import world.wander.engine.HeatSpread
import world.wander.engine.Geo
import world.wander.engine.ResourceTarget
import world.wander.engine.Yields
import world.wander.engine.GATHER_M
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
import kotlinx.coroutines.delay
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
import world.wander.app.data.nearestSources
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
    // The resource node whose details card is open (by source id), chosen by tapping it on the map.
    val selectedNode = remember { mutableStateOf<String?>(null) }
    // The shop that's open: "trader" or a settlement's area id (its market).
    val shopSeller = remember { mutableStateOf<String?>(null) }
    // Equipped looks (outfit, banner); only ones you own are worn.
    var looks by remember { mutableStateOf(repo.prefs.outfit to repo.prefs.banner) }
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
                m.addOnMapClickListener { ll ->
                    val shopHit = m.queryRenderedFeatures(m.projection.toScreenLocation(ll), "trader", "buildings")
                        .firstNotNullOfOrNull { f -> if (f.hasProperty("id")) f.getStringProperty("id") else null }
                        ?.let { if (it == "trader") it else it.removePrefix("market:").takeIf { _ -> it.startsWith("market:") } }
                    if (shopHit != null) { shopSeller.value = shopHit; selectedNode.value = null; return@addOnMapClickListener true }
                    val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(ll), "resource-icons", "resource-rings")
                        .firstNotNullOfOrNull { f -> if (f.hasProperty("id")) f.getStringProperty("id") else null }
                    selectedNode.value = hit
                    hit != null
                }
                m.addOnCameraIdleListener { cameraTick++ }
                m.uiSettings.isCompassEnabled = true
                m.uiSettings.isAttributionEnabled = true
                // A neutral whole-world view until your location (or home) arrives; no place is baked into the app.
                m.cameraPosition = CameraPosition.Builder().target(MlLatLng(20.0, 0.0)).zoom(1.5).build()
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

    // World layers. Each source updates independently, so one failure can't hide the rest; failures and counts go to
    // the diagnostics log. The fog (a union of every walked road, the heavy part) depends only on map data and
    // waystones, so it is recomputed only when those change, not on every purchase or decision (review H3).
    val wornBanner = looks.second?.takeIf { "banner_$it" in world?.owned.orEmpty() }
    val wornOutfit = looks.first?.takeIf { "outfit_$it" in world?.owned.orEmpty() }
    var lastSummary by remember { mutableStateOf("") }
    var exploredGeometry by remember { mutableStateOf<org.locationtech.jts.geom.Geometry?>(null) }
    var fogSummary by remember { mutableStateOf("") }
    val waystoneCenters = world?.waystones.orEmpty().map { it.center }
    // How far each walked hex's heat reaches (0.12.2): fog clears to these hexes, and they're what's drawn.
    var coverage by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val heat = world?.heat
    LaunchedEffect(style, layers, waystoneCenters, heat) {
        val s = style ?: return@LaunchedEffect
        val errors = mutableListOf<String>()
        val cov = withContext(Dispatchers.Default) { HeatSpread.coverage(heat.orEmpty()) }
        coverage = cov
        val fe = try { withContext(Dispatchers.Default) { MapLayersBuilder.fogExploredGeometry(layers, waystoneCenters, cov) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "fog: $e"; null }
        if (fe != null) {
            s.source("fog")?.setGeoJson(fe.first); s.source("explored")?.setGeoJson(fe.second)
            exploredGeometry = fe.third
        }
        val peek = try { withContext(Dispatchers.Default) { MapLayersBuilder.peeks(layers, fe?.third) } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "peek: $e"; null }
        peek?.let { s.source("peek")?.setGeoJson(it) }
        fogSummary = "peek=${peek?.features()?.size ?: -1}" + if (errors.isEmpty()) "" else " | errors: " + errors.joinToString("; ")
        repo.log("map", "Fog and unwalked streets: $fogSummary")
    }
    LaunchedEffect(style, layers, world, wornBanner, exploredGeometry, coverage) {
        val s = style ?: return@LaunchedEffect
        val errors = mutableListOf<String>()
        fun update(id: String, build: () -> FeatureCollection): Int = try {
            val fc = build()
            val src = s.source(id) ?: throw IllegalStateException("source missing")
            src.setGeoJson(fc)
            fc.features()?.size ?: 0
        } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "$id: ${e.javaClass.simpleName} ${e.message}"; -1 }
        val kinds = world?.landmarks.orEmpty().associate { it.name to it.kind }
        // The heat map at three hex sizes (built off the main thread: thousands of hexes for a year of walking).
        val heatCounts = try {
            val (block, hood, county) = withContext(Dispatchers.Default) {
                val grounds = HeatLayers.grounds(layers, kinds)
                val w = world
                // Neighbourhood and county hexes from the block coverage: only where enough of the hex is really covered.
                val hood = HeatSpread.aggregate(coverage, Cells.HOOD_RES, 49)
                val county = HeatSpread.aggregate(hood, Cells.COUNTY_RES, 49)
                Triple(
                    HeatLayers.hexes(coverage, Cells.HEAT_RES, grounds, w, exploredGeometry),
                    HeatLayers.hexes(hood, Cells.HOOD_RES, grounds, w, exploredGeometry),
                    HeatLayers.hexes(county, Cells.COUNTY_RES, grounds, w, exploredGeometry),
                )
            }
            "heat=${update("heat-block") { block }}/${update("heat-hood") { hood }}/${update("heat-county") { county }}"
        } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "heat: $e"; "heat=-1" }
        val counts = linkedMapOf(
            "green" to update("green-places") { MapLayersBuilder.greenPlaces(layers, kinds) },
            "sand" to update("sand-places") { MapLayersBuilder.sandPlaces(layers, kinds) },
            "shore" to update("shore") { MapLayersBuilder.shore(layers) },
            "water" to update("water") { MapLayersBuilder.water(layers) },
            "roads" to update("roads") { MapLayersBuilder.roads(layers) },
            "wild" to update("wild") { MapLayersBuilder.wild(layers) },
            "places" to update("landmarks") { MapLayersBuilder.landmarkLabels(layers, kinds) },
            "settlements" to update("settlements") { MapLayersBuilder.settlements(world, wornBanner) },
            "buildings" to update("buildings") { MapLayersBuilder.villageBuildings(world) },
            "trader" to update("trader") { MapLayersBuilder.trader(world) },
            "waypoints" to update("waypoints") { MapLayersBuilder.waypoints(world) },
            "waystones" to update("waystones") { MapLayersBuilder.waystones(world) },
            "regions" to update("region-nodes") { HeatLayers.regionNodes(world) }.also { update("region-links") { HeatLayers.regionLinks(world) } },
        )
        val summary = counts.entries.joinToString { "${it.key}=${it.value}" } + ", $heatCounts" + if (errors.isEmpty()) "" else " | errors: " + errors.joinToString("; ")
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
    LaunchedEffect(style, captureMode, wornOutfit) {
        val base = if (captureMode == "WATER") "wanderer_paddle" else "wanderer"
        style?.getLayerAs<SymbolLayer>("me")?.setProperties(iconImage(if (wornOutfit != null) "${base}_$wornOutfit" else base))
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

    // Live gathering: seconds spent in each node's zone during the current recording. Once a node passes the
    // visit threshold it shows as gathered right away; the resources themselves are credited when the walk ends.
    val dwell = remember { HashMap<String, Double>() }
    var pendingGathered by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastSearch by remember { mutableStateOf<Pair<WLatLng, Long>?>(null) }
    var lastNodesLine by remember { mutableStateOf("") }
    val recordingNow = captureMode != null
    LaunchedEffect(recordingNow) { if (!recordingNow) { dwell.clear(); pendingGathered = emptySet() } }
    // Resource nodes around you (or home), refreshed as you move, while recording every 20 s, and when the world changes.
    var targetsFrom by remember { mutableStateOf<Pair<WLatLng, MapLayers>?>(null) }
    LaunchedEffect(me, layers) {
        val from = me ?: repo.prefs.homeLat?.let { lat -> repo.prefs.homeLng?.let { WLatLng(lat, it) } } ?: return@LaunchedEffect
        val last = targetsFrom
        val now = System.currentTimeMillis()
        val stale = recordingNow && (lastSearch?.second?.let { now - it >= 20_000 } ?: true)
        if (last != null && last.second === layers && Geo.distanceM(last.first, from) < 15 && !stale) return@LaunchedEffect
        // Time since the previous search counts toward every node you were (and still are) in the zone of.
        lastSearch?.let { (_, t) ->
            val dt = ((now - t) / 1000.0).coerceAtMost(30.0)
            if (recordingNow) for (n in targets) if (n.distanceM <= GATHER_M) dwell.merge(n.sourceId, dt, Double::plus)
        }
        lastSearch = from to now
        targetsFrom = from to layers
        searching = true
        // A newer search may cancel this one (the world just rebuilt); let it, rather than blanking the nodes.
        // If this spot's places haven't downloaded yet (the server is often busy), try again a few times.
        for (attempt in 1..6) {
            targets = try { repo.nearestSources(from) } catch (e: CancellationException) { targetsFrom = null; searching = false; throw e }
                catch (e: Exception) { repo.log("map", "Resource search failed: ${e.message}"); targets }
            val newlyGathered = targets.filter { (dwell[it.sourceId] ?: 0.0) >= Yields.VISIT_SECONDS && it.sourceId !in pendingGathered }
            if (newlyGathered.isNotEmpty()) {
                pendingGathered = pendingGathered + newlyGathered.map { it.sourceId }
                newlyGathered.forEach { repo.log("gather", "Gathering ${it.resource} at ${it.name}; credited when this walk ends") }
            }
            // Logged only when the set of nodes changes (it used to fill half the log every 20 s while recording).
            val nodesLine = "Resource nodes: " + targets.groupBy { it.resource }.entries.joinToString { (r, ns) ->
                "$r ${ns.size}" + (ns.firstOrNull { it.best }?.let { " (best ${it.name}${if (it.note.isNotEmpty()) " ${it.note}" else ""})" } ?: "")
            }.ifEmpty { "none nearby yet" }
            if (nodesLine != lastNodesLine) { lastNodesLine = nodesLine; repo.log("map", nodesLine) }
            if (repo.placesLoadedAt(from)) break
            searching = false
            delay(90_000)
        }
        searching = false
    }
    // Nodes you've gathered at on this walk show the tick now; the arrow moves on to the nearest one you haven't.
    val shownNodes = targets.map { if (it.sourceId in pendingGathered && it.note != "gathered") it.copy(note = "gathered", best = false) else it }
        .let { ns ->
            val next = ns.groupBy { it.resource }.mapValues { (_, list) ->
                if (list.any { it.best }) null else list.filter { it.note != "gathered" }.minByOrNull { it.distanceM }?.sourceId
            }
            ns.map { n -> if (next[n.resource] == n.sourceId) n.copy(best = true) else n }
        }
    LaunchedEffect(shownNodes) { repo.resourceNodes.value = shownNodes }
    LaunchedEffect(style, shownNodes, showPointers) {
        style?.source("resources")?.setGeoJson(MapLayersBuilder.resources(if (showPointers) shownNodes else emptyList()))
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (showPointers && targets.isNotEmpty()) {
            ResourcePointers(
                targets = shownNodes.filter { it.best }.distinctBy { it.resource },
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
                    world?.let { w -> Text(weekLine(w), style = MaterialTheme.typography.labelSmall) }
                }
            }
            world?.let { w ->
                Spacer(Modifier.height(6.dp))
                InventoryBar(w.treasury, shownNodes.filter { it.best }.distinctBy { it.resource }, today = w.days[w.today]?.earned) { repo.focus.value = it.point }
                if (showPointers && targets.isEmpty()) {
                    Text(
                        if (searching) "Looking for resources nearby…" else "No wood, food, stone or metal mapped near here yet",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                    )
                }
            }
        }

        shownNodes.firstOrNull { it.sourceId == selectedNode.value }?.let { n ->
            Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 84.dp)) {
                NodeDetailsCard(n, onGo = { repo.focus.value = n.point; selectedNode.value = null }, onClose = { selectedNode.value = null })
            }
        }
        val w = world
        val seller = shopSeller.value
        if (w != null && seller != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 84.dp)) {
                ShopSheet(repo, w, seller, looks, onLooks = { l -> looks = l; repo.prefs.outfit = l.first; repo.prefs.banner = l.second },
                    onClose = { shopSeller.value = null })
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

/**
 * This week's active minutes and what next Tuesday's scoring will do if the week ended now (ADR 0008), so a missed
 * week is never a surprise.
 */
internal fun weekLine(w: world.wander.engine.WorldState): String {
    val g = w.goal
    val ratio = g.weekMinutes / g.goal
    val head = "This week: %.0f / %d active min".format(g.weekMinutes, g.goal)
    val daysLeft = 6 - (w.today - g.weekStartDay)
    val outlook = when {
        !g.coolingApplies -> if (g.protection == world.wander.engine.Protection.RESTING) "resting" else "new world grace"
        ratio > 1.0 -> "over the goal: faded places come back ¼ step"
        ratio >= 1.0 -> "on track: nothing fades"
        else -> {
            val need = "%.0f min to go".format(g.goal - g.weekMinutes)
            val loss = world.wander.engine.WorldEngine.weeklyLoss(ratio)
            if (daysLeft > 0) "$need, $daysLeft ${if (daysLeft == 1) "day" else "days"} left"
            else "$need; Tuesday places fade " + if (loss >= 0.2) "¼ step" else if (loss >= 0.1) "⅛ step" else "a little"
        }
    }
    return "$head · $outlook"
}
