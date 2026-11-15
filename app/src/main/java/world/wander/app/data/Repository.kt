package world.wander.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import world.wander.app.geo.Overpass
import world.wander.app.health.HealthSync
import world.wander.engine.AreaAssigner
import world.wander.engine.AreaInfo
import world.wander.engine.CellPresence
import world.wander.engine.Cells
import world.wander.engine.DayIntensity
import world.wander.engine.DayMovement
import world.wander.engine.Dispatch
import world.wander.engine.DispatchWriter
import world.wander.engine.EdgeCoverage
import world.wander.engine.EdgeState
import world.wander.engine.GraphEdge
import world.wander.engine.LatLng
import world.wander.engine.Mode
import world.wander.engine.Place
import world.wander.engine.TrackPoint
import world.wander.engine.TrackProcessor
import world.wander.engine.WorldEngine
import world.wander.engine.WorldInput
import world.wander.engine.WorldState
import java.time.LocalDate
import java.time.ZoneId

data class MapLayers(
    val discovered: List<Pair<EdgeEntity, EdgeState>>,
    val peek: List<EdgeEntity>,
)

/** Everything the UI, the tracking service and the daily worker share. */
class Repository(context: Context) {
    private val app = context.applicationContext
    val db = WanderDb.create(app)
    val dao = db.dao()
    val prefs = Prefs(app)
    val health = HealthSync(app, dao) { log("health", it) }
    private val engine = WorldEngine()
    private val processLock = Mutex()

    private val _world = MutableStateFlow<WorldState?>(null)
    val world: StateFlow<WorldState?> = _world

    private val _layers = MutableStateFlow(MapLayers(emptyList(), emptyList()))
    val layers: StateFlow<MapLayers> = _layers

    /** Live track of the current capture, for the map. */
    val liveTrack = MutableStateFlow<List<LatLng>>(emptyList())
    val lastLocation = MutableStateFlow<LatLng?>(null)
    val trackingStatus = MutableStateFlow("Idle")

    val zone: ZoneId get() = ZoneId.systemDefault()
    fun today(): Int = LocalDate.now(zone).toEpochDay().toInt()

    private val logExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Diagnostics log, visible in Settings. Safe to call from any thread. */
    fun log(tag: String, message: String) {
        android.util.Log.i("Wander/$tag", message)
        val entry = DiagEntity(timeMs = System.currentTimeMillis(), tag = tag, message = message)
        logExecutor.execute { try { dao.log(entry) } catch (_: Exception) {} }
    }

    suspend fun logAsync(tag: String, message: String) = withContext(Dispatchers.IO) { log(tag, message) }

    // ---- Map data ----

    private suspend fun ensureTiles(tiles: Collection<String>): Boolean {
        for (tile in tiles) {
            if (dao.tile(tile) != null) continue
            try {
                val data = Overpass.fetchTile(tile)
                dao.insertEdges(data.edges)
                dao.upsertPlaces(data.places)
                dao.upsertTile(GeoTileEntity(tile, System.currentTimeMillis(), data.edges.size))
                log("geo", "Fetched tile $tile: ${data.edges.size} road edges, ${data.places.size} places")
                delay(1_000) // be polite to the public Overpass server
            } catch (e: Exception) {
                log("geo", "Tile $tile fetch failed: ${e.message}")
                return false
            }
        }
        return true
    }

    /** Assigns cells to neighborhoods. Cells whose map tile isn't downloaded yet wait until it is. */
    private fun assignAreas(cells: Collection<String>) {
        val missing = cells.filter { dao.cellArea(it) == null && dao.tile(Cells.parent(it, Cells.TILE_RES)) != null }
        if (missing.isEmpty()) return
        val places = dao.allPlaces().map { Place(it.id, it.name, it.type, it.lat, it.lng) }
        for (cell in missing) {
            val area = AreaAssigner.assign(cell, places)
            dao.insertArea(AreaEntity(area.id, area.name, area.placeType, System.currentTimeMillis()))
            dao.insertCellArea(CellAreaEntity(cell, area.id))
        }
    }

    /**
     * Cells that fell back to a plain hex area (no named place known at the time, e.g. because the
     * map download failed) move to their real neighborhood once place data is available.
     */
    private fun repairFallbackAreas() {
        val fallback = dao.fallbackCellAreas()
        if (fallback.isEmpty()) return
        val places = dao.allPlaces().map { Place(it.id, it.name, it.type, it.lat, it.lng) }
        if (places.isEmpty()) return
        var moved = 0
        for (ca in fallback) {
            val area = AreaAssigner.assign(ca.cell, places)
            if (area.id == ca.areaId || area.id.startsWith("r7:")) continue
            dao.insertArea(AreaEntity(area.id, area.name, area.placeType, System.currentTimeMillis()))
            dao.reassignCell(ca.cell, area.id)
            moved++
        }
        if (moved > 0) log("geo", "Moved $moved block(s) into their named neighborhoods")
    }

    /** The home area follows the home cell, in case that cell was re-assigned. */
    private fun resolveHome() {
        val lat = prefs.homeLat ?: return
        val lng = prefs.homeLng ?: return
        val cell = Cells.cell(lat, lng)
        if (dao.cellArea(cell) == null) {
            assignAreas(listOf(cell))
        }
        val areaId = dao.cellArea(cell)?.areaId ?: return
        if (areaId != prefs.homeAreaId) {
            prefs.homeAreaId = areaId
            log("home", "Home is in area $areaId")
        }
    }

    suspend fun setHome(lat: Double, lng: Double) = withContext(Dispatchers.IO) {
        prefs.homeLat = lat; prefs.homeLng = lng; prefs.homeAreaId = null
        val ok = ensureTiles(listOf(Cells.tile(lat, lng)))
        if (!ok) log("home", "Home saved; its neighborhood will be named once map data downloads")
        rebuild()
        prefs.homeAreaId != null
    }

    // ---- Processing captured tracks into observations ----

    suspend fun processPending(): Int = withContext(Dispatchers.IO) {
        processLock.withLock {
            var done = 0
            for (seg in dao.pendingSegments()) {
                val pts = dao.points(seg.id)
                if (pts.size < 2) { dao.markProcessed(seg.id, true, "too few points"); continue }
                val tiles = pts.map { Cells.tile(it.lat, it.lng) }.toSet()
                if (!ensureTiles(tiles)) break // offline; try again later
                val graph = dao.edgesInTiles(tiles.toList()).map {
                    GraphEdge(it.id, it.name, it.startNode, it.endNode, Overpass.decode(it.geometry))
                }
                val hint = seg.modeHint?.let { Mode.valueOf(it) }
                val r = TrackProcessor(zone).process(pts.map { TrackPoint(it.timeMs, it.lat, it.lng, it.accuracyM) }, hint, graph)
                db.runInTransaction {
                    dao.deleteCoverage(seg.id); dao.deletePresence(seg.id); dao.deleteMovement(seg.id)
                    dao.insertCoverage(r.coverage.map { EdgeCoverageEntity(segmentId = seg.id, edgeId = it.edgeId, day = it.day, fromM = it.fromM, toM = it.toM, mode = it.mode.name) })
                    dao.insertPresence(r.presence.map { CellPresenceEntity(segmentId = seg.id, cell = it.cell, day = it.day, seconds = it.seconds, mode = it.mode.name) })
                    dao.insertMovement(r.movement.map { DayMovementEntity(segmentId = seg.id, day = it.day, footMeters = it.footMeters, cycleMeters = it.cycleMeters) })
                    assignAreas(r.presence.map { it.cell }.toSet())
                    dao.markProcessed(seg.id, true, "kept ${r.keptPoints}, dropped ${r.droppedPoints}, matched ${r.matchedPoints}")
                }
                log("process", "Segment ${seg.id} (${seg.source}): ${pts.size} pts → ${r.coverage.size} road stretches, ${r.presence.size} cell visits, matched ${r.matchedPoints}/${r.keptPoints}")
                done++
            }
            done
        }
    }

    // ---- Building the world ----

    suspend fun rebuild(): WorldState = withContext(Dispatchers.IO) {
        val today = today()
        repairFallbackAreas()
        resolveHome()
        val coverage = dao.allCoverage()
        val edgeIds = coverage.map { it.edgeId }.distinct()
        val edges = edgeIds.chunked(500).flatMap { dao.edgesById(it) }.associateBy { it.id }
        val edgeInfos = edges.mapValues { (_, e) ->
            GraphEdge(e.id, e.name, e.startNode, e.endNode, Overpass.decode(e.geometry)).toInfo()
        }
        val rest = prefs.restPeriods.map { (a, b) -> a..(if (b < 0) today else b) }
        val input = WorldInput(
            startDay = prefs.startDay,
            homeAreaId = prefs.homeAreaId,
            edges = edgeInfos,
            coverage = coverage.map { EdgeCoverage(it.edgeId, it.day, it.fromM, it.toM, Mode.valueOf(it.mode)) },
            presence = dao.allPresence().map { CellPresence(it.cell, it.day, it.seconds, Mode.valueOf(it.mode)) },
            intensity = dao.allIntensity().map { DayIntensity(it.day, it.moderateMin, it.vigorousMin) },
            movement = dao.allMovement().map { DayMovement(it.day, it.footMeters, it.cycleMeters) },
            cellArea = dao.allCellAreas().associate { it.cell to it.areaId },
            areas = dao.allAreas().associate { it.id to AreaInfo(it.id, it.name, it.placeType) },
            restPeriods = rest,
            goalLocked = prefs.goalLocked,
        )
        val state = engine.build(input, today)
        // Freeze fallback names the first time an unnamed area earns one.
        for ((areaId, name) in state.areaNames) if (name != "Unnamed wilds") dao.freezeAreaName(areaId, name)

        val discovered = state.edges.values.filter { it.discovered }
        val discoveredEntities = discovered.mapNotNull { s -> edges[s.id]?.let { it to s } }
        val nodes = discoveredEntities.flatMap { listOf(it.first.startNode, it.first.endNode) }.distinct()
        val discoveredIds = discovered.map { it.id }.toSet()
        val peek = nodes.chunked(400).flatMap { dao.edgesTouching(it) }.filter { it.id !in discoveredIds }.distinctBy { it.id }
        _layers.value = MapLayers(discoveredEntities, peek)
        _world.value = state
        state
    }

    fun dispatchFor(state: WorldState, day: Int): Dispatch = DispatchWriter.write(state, day)

    /** The daily routine: sync health, process captures, rebuild, and write yesterday's dispatch. */
    suspend fun dailyRun(): DispatchEntity? = withContext(Dispatchers.IO) {
        try { health.sync(prefs.startDay) } catch (e: Exception) { log("health", "Sync failed: ${e.message}") }
        prefs.lastHealthSyncMs = System.currentTimeMillis()
        processPending()
        val state = rebuild()
        dao.prunePoints(System.currentTimeMillis() - 90L * 24 * 3600 * 1000)
        dao.pruneDiagnostics(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
        val yesterday = today() - 1
        if (yesterday < prefs.startDay || dao.dispatch(yesterday) != null) return@withContext null
        val d = dispatchFor(state, yesterday)
        val entity = DispatchEntity(yesterday, d.title, d.lines.joinToString("\n"), System.currentTimeMillis())
        dao.upsertDispatch(entity)
        prefs.lastDispatchDay = yesterday
        entity
    }

    /** A plain-text report of everything useful for debugging, for sharing. */
    suspend fun diagnosticsReport(extra: List<String>): String = withContext(Dispatchers.IO) {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
        val pkg = app.packageManager.getPackageInfo(app.packageName, 0)
        val w = world.value
        buildString {
            appendLine("Wander World diagnostics · ${fmt.format(java.time.Instant.now())}")
            appendLine("App ${pkg.versionName} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("World start: ${java.time.LocalDate.ofEpochDay(prefs.startDay.toLong())} · home area: ${prefs.homeAreaId} · tracking: ${if (prefs.autoTrackingEnabled) prefs.trackingMode else "off"} · resting: ${prefs.resting} · goal locked: ${prefs.goalLocked}")
            extra.forEach { appendLine(it) }
            appendLine()
            appendLine("== World ==")
            if (w != null) {
                appendLine("Roads charted: ${w.discoveredCount} · cells known: ${w.cellFamiliarity.size}")
                appendLine("Goal: %.0f / %d min (ratio %.2f, %s)".format(w.goal.trailing7Minutes, w.goal.goal, w.goal.ratio, w.goal.protection))
                for (st in w.settlements) appendLine("  ${st.name} [${st.areaId}] ${st.tier} (peak ${st.peakTier}) D=%.1f days=${st.areaDays} cells=${st.exploredCells}".format(st.development))
                appendLine("Last 14 days (intensity min · walked km):")
                for (d in w.today - 13..w.today) {
                    val s = w.days[d] ?: continue
                    appendLine("  ${java.time.LocalDate.ofEpochDay(d.toLong())}  %5.1f min · %.2f km · %s".format(s.intensityMinutes, s.footMeters / 1000, s.protection))
                }
            } else appendLine("(not built yet)")
            appendLine()
            appendLine("== Intensity from Health Connect ==")
            dao.allIntensity().sortedByDescending { it.day }.take(28).forEach {
                appendLine("  ${java.time.LocalDate.ofEpochDay(it.day.toLong())} moderate %.0f vigorous %.0f (${it.source})".format(it.moderateMin, it.vigorousMin))
            }
            appendLine()
            appendLine("== Captures ==")
            dao.recentSegments(40).forEach {
                appendLine("  ${fmt.format(java.time.Instant.ofEpochMilli(it.startMs))} → ${it.endMs?.let { e -> fmt.format(java.time.Instant.ofEpochMilli(e)) } ?: "recording"} · ${it.source} · ${it.modeHint ?: "?"} · ${dao.pointCount(it.id)} pts · ${it.note ?: if (it.processed) "processed" else "waiting"}")
            }
            appendLine()
            appendLine("== Log (newest first) ==")
            dao.recentLogs(500).forEach { appendLine("${fmt.format(java.time.Instant.ofEpochMilli(it.timeMs))} [${it.tag}] ${it.message}") }
        }
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
