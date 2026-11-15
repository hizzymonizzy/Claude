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

    private fun assignAreas(cells: Collection<String>) {
        val missing = cells.filter { dao.cellArea(it) == null }
        if (missing.isEmpty()) return
        val places = dao.allPlaces().map { Place(it.id, it.name, it.type, it.lat, it.lng) }
        for (cell in missing) {
            val area = AreaAssigner.assign(cell, places)
            dao.insertArea(AreaEntity(area.id, area.name, area.placeType, System.currentTimeMillis()))
            dao.insertCellArea(CellAreaEntity(cell, area.id))
        }
    }

    suspend fun setHome(lat: Double, lng: Double) = withContext(Dispatchers.IO) {
        val cell = Cells.cell(lat, lng)
        ensureTiles(listOf(Cells.tile(lat, lng)))
        assignAreas(listOf(cell))
        val areaId = dao.cellArea(cell)?.areaId
        prefs.homeLat = lat; prefs.homeLng = lng; prefs.homeAreaId = areaId
        log("home", "Home set in area $areaId")
        rebuild()
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
        try { health.sync() } catch (e: Exception) { log("health", "Sync failed: ${e.message}") }
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

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
