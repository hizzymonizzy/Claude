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
import world.wander.app.work.ProcessWorker
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
import world.wander.engine.Landmark
import world.wander.engine.LandmarkInfo
import world.wander.engine.LandmarkVisit
import world.wander.engine.TroddenCell
import world.wander.engine.Arrival
import world.wander.engine.ProjectDecision
import world.wander.engine.Proposal
import world.wander.engine.Geo
import world.wander.engine.Mode
import world.wander.engine.Place
import world.wander.engine.TrackPoint
import world.wander.engine.TrackProcessor
import world.wander.engine.WorldEngine
import world.wander.engine.WorldInput
import world.wander.engine.WorldState
import world.wander.engine.Yields
import world.wander.engine.ResourceFinder
import world.wander.engine.ResourceTarget
import world.wander.engine.Street
import java.time.LocalDate
import java.time.ZoneId

data class MapLayers(
    val discovered: List<Pair<EdgeEntity, EdgeState>>,
    val peek: List<EdgeEntity>,
    val trails: List<List<LatLng>> = emptyList(),
    /** Your own paths, with a wear level 1–5. */
    val wild: List<Pair<List<LatLng>, Int>> = emptyList(),
    /** Discovered named areas: name, rings, area. */
    val landmarks: List<Triple<String, List<List<LatLng>>, Double>> = emptyList(),
    /** Water routes you've paddled, with a wear level 1–5. */
    val water: List<Pair<List<LatLng>, Int>> = emptyList(),
    /** Sand areas (rings) near where you've been; drawn under the fog. */
    val sand: List<List<List<LatLng>>> = emptyList(),
    /** Coastlines (lines) near where you've been; a wet-sand shore is drawn along them, under the fog. */
    val coast: List<List<LatLng>> = emptyList(),
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
    /** Whether the map shows pointers to the nearest resources; Settings changes it, the map follows at once. */
    val resourcePointers = MutableStateFlow(prefs.resourcePointers)
    fun setResourcePointers(on: Boolean) { prefs.resourcePointers = on; resourcePointers.value = on }
    /** Set by the Atlas / search to fly the map somewhere; the map clears it after moving. */
    val focus = MutableStateFlow<LatLng?>(null)
    val trackingStatus = MutableStateFlow("Idle")
    /** The mode of the recording in progress ("FOOT", "CYCLE", "WATER"), or null when not recording. */
    val captureMode = MutableStateFlow<String?>(null)

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

    /** Tiles whose download failed recently, and when to try again (backoff doubles each failure). */
    private val tileRetry = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    private suspend fun ensureTiles(tiles: Collection<String>): Boolean {
        for (tile in tiles) {
            if (dao.tile(tile) != null) continue
            val retry = tileRetry[tile]
            if (retry != null && System.currentTimeMillis() < retry.first) return false
            try {
                val data = Overpass.fetchTile(tile)
                dao.insertEdges(data.edges)
                dao.upsertPlaces(data.places)
                dao.upsertTile(GeoTileEntity(tile, System.currentTimeMillis(), data.edges.size))
                log("geo", "Fetched tile $tile: ${data.edges.size} road edges, ${data.places.size} places")
                tileRetry.remove(tile)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val wait = ((retry?.second ?: 30_000L) * 2).coerceAtMost(30 * 60_000L)
                tileRetry[tile] = (System.currentTimeMillis() + wait) to wait
                log("geo", "Tile $tile fetch failed (${e.message?.lineSequence()?.firstOrNull()}); retrying in ${wait / 60_000} min")
                return false
            }
            delay(1_000) // be polite to the public Overpass server
        }
        return true
    }

    /**
     * Named places are a bonus layer: they never hold up a walk. When a tile's places arrive later,
     * walks in that tile are re-processed so their visits count.
     */
    private suspend fun ensureLandmarks(tiles: Collection<String>) {
        for (tile in tiles) {
            if (dao.tile(tile) == null || dao.landmarkTile(tile) != null) continue
            val key = "lm:$tile"
            val retry = tileRetry[key]
            if (retry != null && System.currentTimeMillis() < retry.first) continue
            try {
                val extras = Overpass.fetchLandmarks(tile)
                val lms = extras.landmarks
                dao.upsertLandmarks(lms)
                dao.upsertWaterAreas(extras.water)
                dao.upsertTerrain(extras.terrain)
                dao.upsertWaterTile(WaterTileEntity(tile, extras.coastal))
                dao.upsertLandmarkTile(LandmarkTileEntity(tile, System.currentTimeMillis(), lms.size))
                tileRetry.remove(key)
                val again = dao.allPresenceCells().filter { Cells.parent(it.cell, Cells.TILE_RES) == tile }.map { it.segmentId }.toSet()
                again.forEach { dao.markProcessed(it, false, "re-processing for named places") }
                log("geo", "Fetched ${lms.size} named places, ${extras.water.size} water areas, ${extras.terrain.size} sand/coast for tile $tile" +
                    (if (extras.skippedLarge > 0) " (${extras.skippedLarge} very large areas skipped)" else "") + "; re-processing ${again.size} walk(s)")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val wait = ((retry?.second ?: 30_000L) * 2).coerceAtMost(30 * 60_000L)
                tileRetry[key] = (System.currentTimeMillis() + wait) to wait
                log("geo", "Named places for $tile unavailable (${e.message?.lineSequence()?.firstOrNull()}); walks processed without them, retry in ${wait / 60_000} min")
            }
            delay(1_000)
        }
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
        if (prefs.processingVersion < PROCESSING_VERSION) {
            val n = dao.resetProcessed()
            prefs.processingVersion = PROCESSING_VERSION
            log("process", "Processing rules updated (v$PROCESSING_VERSION): re-processing $n walk(s)")
        }
        var done = processOnce()
        // Places for tiles we have roads for; if that re-queued walks, process them again.
        val tiles = dao.allPresenceCells().map { Cells.parent(it.cell, Cells.TILE_RES) }.toSet()
        ensureLandmarks(tiles)
        if (dao.pendingSegments().isNotEmpty()) done += processOnce()
        done
    }

    private suspend fun processOnce(): Int = withContext(Dispatchers.IO) {
        processLock.withLock {
            var done = 0
            for (seg in dao.pendingSegments()) {
                val pts = dao.points(seg.id)
                if (pts.size < 2) { dao.markProcessed(seg.id, true, "too few points"); continue }
                val processor = TrackProcessor(zone)
                val usable = processor.usablePoints(pts.map { TrackPoint(it.timeMs, it.lat, it.lng, it.accuracyM) })
                if (usable.isEmpty()) {
                    // Still record nothing for it, but clear anything an older version credited.
                    db.runInTransaction {
                        dao.deleteCoverage(seg.id); dao.deletePresence(seg.id); dao.deleteMovement(seg.id); dao.deleteTrails(seg.id)
                        dao.deleteWild(seg.id); dao.deleteTrodden(seg.id); dao.deleteVisits(seg.id); dao.deleteArrivals(seg.id)
                        dao.deleteWaterTrails(seg.id); dao.deleteWake(seg.id)
                        // Even a short stop marks where travel set you down.
                        arrivalSincePrevious(seg, pts)?.let { dao.insertArrivals(listOf(ArrivalEntity(segmentId = seg.id, day = it.day, lat = it.lat, lng = it.lng))) }
                        dao.markProcessed(seg.id, true, "no walk: moving around a building, in a vehicle, or poor GPS")
                    }
                    continue
                }
                val tiles = usable.map { Cells.tile(it.lat, it.lng) }.toSet()
                if (!ensureTiles(tiles)) continue // map data not available yet; this walk waits, others carry on
                val graph = dao.edgesInTiles(tiles.toList()).map {
                    GraphEdge(it.id, it.name, it.startNode, it.endNode, Overpass.decode(it.geometry))
                }
                // A vehicle probe only counts if it turned out to be on the water.
                val probe = seg.modeHint == "PROBE"
                val hint = seg.modeHint?.takeIf { !probe }?.let { Mode.valueOf(it) }
                val landmarks = dao.landmarksInTiles(tiles.toList()).distinctBy { it.id }.mapNotNull {
                    try { Landmark(it.id, it.name, it.kind, Overpass.decodeRings(it.geometry)) } catch (_: Exception) { null }
                }
                val water = dao.waterAreasInTiles(tiles.toList()).distinctBy { it.id }.mapNotNull {
                    try { Landmark(it.id, "", "water", Overpass.decodeRings(it.geometry)) } catch (_: Exception) { null }
                }
                val coastal = dao.waterTiles(tiles.toList()).any { it.coastal }
                val r0 = processor.process(pts.map { TrackPoint(it.timeMs, it.lat, it.lng, it.accuracyM) }, hint, graph, landmarks, water, coastal)
                val r = if (!probe) r0 else r0.copy(
                    coverage = emptyList(), presence = emptyList(), trails = emptyList(), wildTrails = emptyList(),
                    trodden = emptyList(), arrivals = emptyList(),
                    movement = r0.movement.map { it.copy(footMeters = 0.0, cycleMeters = 0.0) },
                )
                db.runInTransaction {
                    dao.deleteCoverage(seg.id); dao.deletePresence(seg.id); dao.deleteMovement(seg.id); dao.deleteTrails(seg.id)
                    dao.deleteWild(seg.id); dao.deleteTrodden(seg.id); dao.deleteVisits(seg.id); dao.deleteArrivals(seg.id)
                    dao.deleteWaterTrails(seg.id); dao.deleteWake(seg.id)
                    dao.insertWaterTrails(r.waterTrails.filter { it.points.size >= 2 }.map { WaterTrailEntity(segmentId = seg.id, day = it.day, geometry = Overpass.encode(it.points)) })
                    dao.insertWake(r.wake.map { WakeEntity(segmentId = seg.id, cell = it.cell, day = it.day) })
                    dao.insertArrivals((r.arrivals + listOfNotNull(arrivalSincePrevious(seg, pts))).map {
                        ArrivalEntity(segmentId = seg.id, day = it.day, lat = it.lat, lng = it.lng)
                    })
                    dao.insertWild(r.wildTrails.map { WildTrailEntity(segmentId = seg.id, day = it.day, geometry = Overpass.encode(it.points)) })
                    dao.insertTrodden(r.trodden.map { TroddenEntity(segmentId = seg.id, cell = it.cell, day = it.day) })
                    dao.insertVisits(r.landmarkVisits.map { LandmarkVisitEntity(segmentId = seg.id, landmarkId = it.landmarkId, day = it.day, seconds = it.seconds) })
                    dao.insertTrails(r.trails.filter { it.points.size >= 2 }.map { TrailEntity(segmentId = seg.id, day = it.day, geometry = Overpass.encode(it.points)) })
                    dao.insertCoverage(r.coverage.map { EdgeCoverageEntity(segmentId = seg.id, edgeId = it.edgeId, day = it.day, fromM = it.fromM, toM = it.toM, mode = it.mode.name) })
                    dao.insertPresence(r.presence.map { CellPresenceEntity(segmentId = seg.id, cell = it.cell, day = it.day, seconds = it.seconds, mode = it.mode.name) })
                    dao.insertMovement(r.movement.map { DayMovementEntity(segmentId = seg.id, day = it.day, footMeters = it.footMeters, cycleMeters = it.cycleMeters, waterMeters = it.waterMeters) })
                    assignAreas(r.presence.map { it.cell }.toSet())
                    dao.markProcessed(
                        seg.id, true,
                        if (r.keptPoints == 0 && r.stationaryPoints > 0) "moving around a building (${r.stationaryPoints} pts), not a walk"
                        else "kept ${r.keptPoints}, dropped ${r.droppedPoints}, still ${r.stationaryPoints}, matched ${r.matchedPoints}",
                    )
                }
                log("process", "Segment ${seg.id} (${seg.source}): ${pts.size} pts → ${r.coverage.size} road stretches, ${r.presence.size} cell visits, matched ${r.matchedPoints}/${r.keptPoints}")
                done++
            }
            if (dao.pendingSegments().isNotEmpty()) ProcessWorker.enqueue(app)
            done
        }
    }

    /**
     * Did this recording start far (> 2 km) from where the previous one ended? Then you drove or flew in
     * between, and its first decent fix is an arrival. Uses raw fixes on both sides, so a short or
     * "moving around a building" recording still marks where you were.
     */
    private fun arrivalSincePrevious(seg: SegmentEntity, pts: List<TrackPointEntity>): Arrival? {
        val good = pts.filter { it.accuracyM <= 50f }
        val first = good.firstOrNull() ?: return null
        val prev = dao.previousSegment(seg.startMs, seg.id) ?: return null
        val last = dao.points(prev.id).lastOrNull { it.accuracyM <= 50f } ?: return null
        if (Geo.distanceM(last.lat, last.lng, first.lat, first.lng) < 2_000) return null
        // Where you set off on foot: the middle of the first couple of minutes, so one stray fix can't move it.
        val start = good.takeWhile { it.timeMs - first.timeMs <= 120_000 }.take(8)
        val lat = start.map { it.lat }.sorted()[start.size / 2]
        val lng = start.map { it.lng }.sorted()[start.size / 2]
        return Arrival(TrackProcessor(zone).dayOf(first.timeMs), lat, lng)
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
        val visits = dao.allVisits()
        val landmarkEntities = visits.map { it.landmarkId }.distinct().chunked(500).flatMap { dao.landmarksById(it) }.associateBy { it.id }
        // Unnamed lakes and ponds you gathered food at: sources without a name (never "discovered").
        val waterInfos = visits.map { it.landmarkId }.distinct().filter { it !in landmarkEntities }.chunked(500)
            .flatMap { dao.waterAreasById(it) }.associate { w ->
                val ring = Overpass.decodeRings(w.geometry).maxByOrNull { it.size }
                w.id to LandmarkInfo(w.id, "", "water", 0.0, center = ring?.let { r -> LatLng(r.sumOf { it.lat } / r.size, r.sumOf { it.lng } / r.size) })
            }
        val rest = prefs.restPeriods.map { (a, b) -> a..(if (b < 0) today else b) }
        val input = WorldInput(
            startDay = prefs.startDay,
            homeAreaId = prefs.homeAreaId,
            homePoint = prefs.homeLat?.let { lat -> prefs.homeLng?.let { LatLng(lat, it) } },
            edges = edgeInfos,
            coverage = coverage.map { EdgeCoverage(it.edgeId, it.day, it.fromM, it.toM, Mode.valueOf(it.mode)) },
            presence = dao.allPresence().map { CellPresence(it.cell, it.day, it.seconds, Mode.valueOf(it.mode)) },
            intensity = dao.allIntensity().map { DayIntensity(it.day, it.moderateMin, it.vigorousMin) },
            movement = dao.allMovement().map { DayMovement(it.day, it.footMeters, it.cycleMeters, it.waterMeters) },
            cellArea = dao.allCellAreas().associate { it.cell to it.areaId },
            areas = dao.allAreas().associate { it.id to AreaInfo(it.id, it.name, it.placeType) },
            restPeriods = rest,
            goalLocked = prefs.goalLocked,
            landmarks = landmarkEntities.mapValues { (_, l) ->
                val ring = Overpass.decodeRings(l.geometry).maxByOrNull { it.size }
                LandmarkInfo(
                    l.id, l.name, l.kind, l.areaM2, pointLike = l.id.startsWith("n"),
                    center = ring?.let { r -> LatLng(r.sumOf { it.lat } / r.size, r.sumOf { it.lng } / r.size) },
                )
            } + waterInfos,
            decisions = dao.allDecisions().map { ProjectDecision(it.day, it.areaId, it.projectId, it.approve) },
            landmarkVisits = visits.map { LandmarkVisit(it.landmarkId, it.day, it.seconds) },
            trodden = dao.allTrodden().map { TroddenCell(it.cell, it.day) },
            arrivals = dao.allArrivals().map { Arrival(it.day, it.lat, it.lng) },
            wake = dao.allWake().map { TroddenCell(it.cell, it.day) },
        )
        val state = engine.build(input, today)
        // Freeze fallback names the first time an unnamed area earns one.
        for ((areaId, name) in state.areaNames) if (name != "Unnamed wilds") dao.freezeAreaName(areaId, name)

        val discovered = state.edges.values.filter { it.discovered }
        val discoveredEntities = discovered.mapNotNull { s -> edges[s.id]?.let { it to s } }
        val nodes = discoveredEntities.flatMap { listOf(it.first.startNode, it.first.endNode) }.distinct()
        val discoveredIds = discovered.map { it.id }.toSet()
        val peek = nodes.chunked(400).flatMap { dao.edgesTouching(it) }.filter { it.id !in discoveredIds }.distinctBy { it.id }
        val trails = dao.allTrails().map { Overpass.decode(it.geometry) }
        val wild = dao.allWild().map { w ->
            val pts = Overpass.decode(w.geometry)
            val wear = pts.map { state.troddenWear[Cells.cellAt(it.lat, it.lng, Cells.TRODDEN_RES)] ?: 1.0 }.average()
            pts to state.levelFor(wear)
        }
        val shownLandmarks = state.landmarks.mapNotNull { l ->
            landmarkEntities[l.id]?.let { Triple(l.name, Overpass.decodeRings(it.geometry), l.areaM2) }
        }
        val water = dao.allWaterTrails().map { w ->
            val pts = Overpass.decode(w.geometry)
            val wear = pts.map { state.wakeWear[Cells.cellAt(it.lat, it.lng, Cells.TRODDEN_RES)] ?: 1.0 }.average()
            pts to state.levelFor(wear)
        }
        val nearTiles = dao.allPresenceCells().map { Cells.parent(it.cell, Cells.TILE_RES) }.toSet()
        val terrain = dao.terrainInTiles(nearTiles.toList()).distinctBy { it.id }
        val sand = terrain.filter { it.kind == "sand" }.mapNotNull { try { Overpass.decodeRings(it.geometry) } catch (_: Exception) { null } }
        val coast = terrain.filter { it.kind == "coast" }.flatMap { try { Overpass.decodeRings(it.geometry) } catch (_: Exception) { emptyList() } }
        _layers.value = MapLayers(discoveredEntities, peek, trails, wild, shownLandmarks, water, sand, coast)
        _world.value = state
        state
    }

    /**
     * The best nearby source of each resource (yield per distance), from map data already on the phone (nothing is fetched):
     * wood from woods and parks, food from water and beaches, metal from places you haven't discovered yet
     * (then ones you have), stone from the nearest street you haven't walked. Null where none is near.
     */
    /** Whether places (wood, food, metal sources) have been downloaded for the tile at this point. */
    suspend fun placesLoadedAt(p: LatLng): Boolean = withContext(Dispatchers.IO) { dao.landmarkTile(Cells.tile(p.lat, p.lng)) != null }

    suspend fun nearestSources(from: LatLng): List<ResourceTarget> = withContext(Dispatchers.IO) {
        // Map data for where you are now, even if you haven't walked here yet (one tile, same request a walk makes).
        val here = Cells.tile(from.lat, from.lng)
        if (ensureTiles(listOf(here))) ensureLandmarks(listOf(here))
        val tiles = Cells.disk(here, 2)
        val places = dao.landmarksInTiles(tiles).distinctBy { it.id }.mapNotNull {
            try { Landmark(it.id, it.name, it.kind, Overpass.decodeRings(it.geometry)) } catch (_: Exception) { null }
        }
        val water = dao.waterAreasInTiles(tiles).distinctBy { it.id }.mapNotNull {
            try { Landmark(it.id, "", "water", Overpass.decodeRings(it.geometry)) } catch (_: Exception) { null }
        }.filter { w -> places.none { it.id == w.id } } // a named lake is already a place
        val streets = dao.edgesInTiles(Cells.disk(here, 1)).map { Street(it.id, it.name, Overpass.decode(it.geometry)) }
        ResourceFinder.find(from, places, water, streets, _world.value)
    }

    fun dispatchFor(state: WorldState, day: Int): Dispatch = DispatchWriter.write(state, day)

    /** Approve or decline the current project proposal. Recorded as a player decision and replayed. */
    suspend fun decide(proposal: Proposal, approve: Boolean) = withContext(Dispatchers.IO) {
        dao.insertDecision(ProjectDecisionEntity(day = today(), areaId = proposal.areaId, projectId = proposal.projectId, approve = approve, madeMs = System.currentTimeMillis()))
        log("economy", "${if (approve) "Approved" else "Declined"} ${proposal.projectId} for ${proposal.areaId}")
        rebuild()
    }

    /** The daily routine: sync health, process captures, rebuild, and write yesterday's dispatch. */
    suspend fun dailyRun(): DispatchEntity? = withContext(Dispatchers.IO) {
        try { health.sync(prefs.startDay) } catch (e: Exception) { log("health", "Sync failed: ${e.message}") }
        prefs.lastHealthSyncMs = System.currentTimeMillis()
        rebuild()
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
        val w = world.value ?: try { rebuild() } catch (e: Exception) { log("world", "Rebuild failed: $e"); null }
        buildString {
            appendLine("Wander World diagnostics · ${fmt.format(java.time.Instant.now())}")
            appendLine("App ${pkg.versionName} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("World start: ${java.time.LocalDate.ofEpochDay(prefs.startDay.toLong())} · home area: ${prefs.homeAreaId} · tracking: ${if (prefs.autoTrackingEnabled) prefs.trackingMode else "off"} · resting: ${prefs.resting} · goal locked: ${prefs.goalLocked} · resource pointers: ${if (prefs.resourcePointers) "on" else "off"}")
            extra.forEach { appendLine(it) }
            appendLine()
            appendLine("== World ==")
            if (w != null) {
                appendLine("Roads charted: ${w.discoveredCount} · cells known: ${w.cellFamiliarity.size} · own-path cells: ${w.troddenWear.size}")
                appendLine("Regions: " + w.regions.joinToString { "${it.name}${if (it.isHome) " (home)" else ""}${if (it.dormant) " (dormant)" else ""}" })
                appendLine("Treasury: ${w.treasury.toString().ifEmpty { "empty" }} · proposal: ${w.proposal?.let { "${it.projectId} for ${it.areaId}" } ?: "none"} · monuments: ${w.monuments.joinToString { it.id }.ifEmpty { "none" }}")
                appendLine("Waystones: ${w.waystones.size} (${w.waystones.count { it.great }} great) · portals: ${w.portals.size}")
                appendLine("Places discovered: " + w.landmarks.joinToString { "${it.name} (${it.kind}, ${it.visitDays}d)" }.ifEmpty { "none" })
                appendLine("Goal: %.0f / %d min (ratio %.2f, %s)".format(w.goal.trailing7Minutes, w.goal.goal, w.goal.ratio, w.goal.protection))
                for (st in w.settlements) appendLine("  ${st.name} [${st.areaId}] ${st.tier} (peak ${st.peakTier}) D=%.1f days=${st.areaDays} cells=${st.exploredCells} at %.4f,%.4f".format(st.development, st.position.lat, st.position.lng))
                appendLine("Waypoints: ${w.exploredAreas.count { !it.settled }} · map layers: see [map] lines in the log")
                appendLine("Last 14 days (intensity min · walked km):")
                for (d in w.today - 13..w.today) {
                    val s = w.days[d] ?: continue
                    appendLine("  ${java.time.LocalDate.ofEpochDay(d.toLong())}  %5.1f min · %.2f km · %s".format(s.intensityMinutes, s.footMeters / 1000, s.protection))
                }
            } else appendLine("(could not be built; see log)")
            val waiting = dao.pendingSegments()
            if (waiting.isNotEmpty()) {
                val proc = TrackProcessor(zone)
                val missing = waiting.flatMap { seg -> proc.usablePoints(dao.points(seg.id).map { TrackPoint(it.timeMs, it.lat, it.lng, it.accuracyM) }).map { Cells.tile(it.lat, it.lng) } }.toSet().filter { dao.tile(it) == null }
                appendLine("Waiting walks: ${waiting.size} · missing map tiles: ${missing.joinToString().ifEmpty { "none" }}")
            }
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
        /**
         * v2: stretches that stay within 45 m (moving around a building) are no longer walks.
         * v3: arrivals compare raw fixes, so short stops still mark where travel set you down.
         * v4: paddling (water routes) and wider place categories.
         * v5: paddling detected from being on water (water areas, coast).
         */
        const val PROCESSING_VERSION = 8
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) { instance ?: Repository(context).also { instance = it } }
    }
}
