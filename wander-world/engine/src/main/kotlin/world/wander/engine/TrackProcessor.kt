package world.wander.engine

import java.time.Instant
import java.time.ZoneId

data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
)

data class Trail(val day: Int, val points: List<LatLng>)

data class TrackResult(
    val coverage: List<EdgeCoverage>,
    /** The path actually walked (usable fixes, simplified), one polyline per run, for clearing fog. */
    val trails: List<Trail>,
    /** Stretches walked where there is no mapped path: your own paths. */
    val wildTrails: List<Trail>,
    /** Fine cells along those stretches, per day, for wear. */
    val trodden: List<TroddenCell>,
    /** Time spent inside named areas (parks, woods, stores…). */
    val landmarkVisits: List<LandmarkVisit>,
    /** Where walking resumed after vehicle-speed travel within this track. */
    val arrivals: List<Arrival> = emptyList(),
    /** Water routes (paddling): no roads or settlements, but they clear fog and wear in. */
    val waterTrails: List<Trail> = emptyList(),
    val wake: List<TroddenCell> = emptyList(),
    /** Fine cells walked along a shore (water's edge, coastline, beach): food, gathered like stone from roads. */
    val shore: List<TroddenCell> = emptyList(),
    /** Fixes in stretches that never went anywhere (moving around a building). */
    val stationaryPoints: Int = 0,
    val presence: List<CellPresence>,
    val movement: List<DayMovement>,
    val keptPoints: Int,
    val droppedPoints: Int,
    val matchedPoints: Int,
)

/**
 * Turns a raw GPS track into observations: filters noisy and vehicle-speed fixes, splits on gaps,
 * map-matches each run, and credits time to the H3 cells it passed through.
 */
class TrackProcessor(
    private val zone: ZoneId,
    private val maxAccuracyM: Float = 30f,
    private val vehicleSpeedMps: Double = 7.0,
    private val cycleSpeedMps: Double = 5.0,
    private val maxGapSeconds: Double = 60.0,
    private val maxGapMeters: Double = 100.0,
    /** A stretch whose fixes stay within this radius of their centre is pottering, not walking. */
    private val minSpreadM: Double = 45.0,
) {
    fun dayOf(timeMs: Long): Int = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate().toEpochDay().toInt()

    /** The fixes that survive filtering (no vehicle-speed legs, no inaccurate fixes): the only ones needing map data. */
    fun usablePoints(points: List<TrackPoint>): List<TrackPoint> = runs(points).runs.flatten()

    private class Split(val runs: List<List<TrackPoint>>, val dropped: Int, val arrivals: List<TrackPoint>, val stationary: Int)

    /** How far a stretch's fixes reach from their centre. Pottering around a house stays within a few dozen metres. */
    private fun spreadM(run: List<TrackPoint>): Double {
        val lat = run.sumOf { it.lat } / run.size
        val lng = run.sumOf { it.lng } / run.size
        return run.maxOf { Geo.distanceM(lat, lng, it.lat, it.lng) }
    }

    private fun runs(points: List<TrackPoint>): Split {
        val sorted = points.filter { it.accuracyM <= maxAccuracyM }.sortedBy { it.timeMs }
        // Split into runs on gaps and vehicle-speed legs.
        val runs = mutableListOf<MutableList<TrackPoint>>()
        var current = mutableListOf<TrackPoint>()
        var dropped = points.size - sorted.size
        // Arrival detection: walking resumes > 1 km from where it stopped, after vehicle-speed travel.
        var beforeVehicle: TrackPoint? = null
        var arrivalCandidate: TrackPoint? = null
        val arrivals = mutableListOf<TrackPoint>()
        fun close() {
            if (current.size > 1) {
                runs.add(current)
                if (arrivalCandidate != null && current.first() === arrivalCandidate) { arrivals += arrivalCandidate!!; arrivalCandidate = null }
            }
        }
        for (p in sorted) {
            val prev = current.lastOrNull()
            if (prev == null) {
                val bv = beforeVehicle
                if (bv != null && Geo.distanceM(bv.lat, bv.lng, p.lat, p.lng) > 1_000) arrivalCandidate = p
                current.add(p); continue
            }
            val dt = (p.timeMs - prev.timeMs) / 1000.0
            if (dt <= 0) { dropped++; continue }
            val d = Geo.distanceM(prev.lat, prev.lng, p.lat, p.lng)
            val speed = d / dt
            when {
                speed > vehicleSpeedMps -> {
                    // Vehicle movement or a GPS jump: never counts. Start fresh after it.
                    dropped++
                    if (beforeVehicle == null || current.size > 1) beforeVehicle = prev
                    close()
                    current = mutableListOf()
                }
                dt > maxGapSeconds || d > maxGapMeters -> {
                    close()
                    current = mutableListOf(p)
                }
                else -> current.add(p)
            }
        }
        close()
        // Moving around a building (or GPS drifting indoors) is not a walk: those stretches earn nothing.
        // Their raw points stay in the database, so a different threshold can re-process them later.
        val (moving, still) = runs.partition { spreadM(it) >= minSpreadM }
        val stillFirsts = still.map { it.first() }.toSet()
        return Split(moving, dropped, arrivals.filter { a -> a !in stillFirsts }, still.sumOf { it.size })
    }

    /**
     * Is this stretch on the water? Mostly inside mapped water (lakes, rivers, bays), or — where the coast is
     * mapped but the sea isn't an area — mostly far from any road at paddling speed and not on a beach.
     */
    private fun onWater(run: List<TrackPoint>, water: List<Landmark>, coastal: Boolean, matcher: MapMatcher?, beaches: List<Landmark>): Boolean {
        if (water.isNotEmpty() && run.count { p -> water.any { it.contains(p.lat, p.lng) } } >= 0.6 * run.size) return true
        if (!coastal || matcher == null) return false
        val farFromRoads = run.count { matcher.nearestRoadM(LatLng(it.lat, it.lng), 80.0) == null }
        val onBeach = run.count { p -> beaches.any { it.contains(p.lat, p.lng) } }
        val speeds = (1 until run.size).mapNotNull { k ->
            val dt = (run[k].timeMs - run[k - 1].timeMs) / 1000.0
            if (dt <= 0) null else Geo.distanceM(run[k - 1].lat, run[k - 1].lng, run[k].lat, run[k].lng) / dt
        }.sorted()
        val median = speeds.getOrNull(speeds.size / 2) ?: return false
        return farFromRoads >= 0.7 * run.size && onBeach < 0.3 * run.size && median in 0.4..3.5
    }

    /** Being this close to a place's edge counts as being at it (see [Yields.gatherMarginM]). */
    fun gatherMarginM(kind: String): Double = Yields.gatherMarginM(kind)

    fun process(
        points: List<TrackPoint>, modeHint: Mode?, edges: List<GraphEdge>, landmarks: List<Landmark> = emptyList(),
        water: List<Landmark> = emptyList(), coastal: Boolean = false,
        /** Coastlines near the walk (lines). Water outlines and beaches are shores too. */
        coast: List<List<LatLng>> = emptyList(),
    ): TrackResult {
        val shoreline = Shores(coast + water.flatMap { it.rings } + landmarks.filter { it.kind == "beach" }.flatMap { it.rings },
            landmarks.filter { it.kind == "beach" })
        val split = runs(points)
        // Places you can be "at": named places, plus unnamed lakes and ponds (food from their shores).
        val named = landmarks.map { it.id }.toSet()
        val gatherable = landmarks + water.filter { it.id !in named }
        val runs = split.runs
        val dropped = split.dropped
        val matcher = if (edges.isNotEmpty()) MapMatcher(edges) else null
        val coverage = mutableListOf<EdgeCoverage>()
        val presence = HashMap<Triple<String, Int, Mode>, Double>()
        val movement = HashMap<Int, DoubleArray>()
        var kept = 0
        var matched = 0
        val trails = mutableListOf<Trail>()
        val wild = mutableListOf<Trail>()
        val trodden = HashSet<TroddenCell>()
        val visits = HashMap<Pair<String, Int>, Double>()
        val waterTrails = mutableListOf<Trail>()
        val wake = HashSet<TroddenCell>()
        val shore = HashSet<TroddenCell>()

        for (run in runs) {
            kept += run.size
            val beaches = landmarks.filter { it.kind == "beach" }
            val mode = if (modeHint == Mode.WATER || onWater(run, water, coastal, matcher, beaches)) Mode.WATER else modeHint ?: inferMode(run)
            val line = run.map { LatLng(it.lat, it.lng) }
            if (mode == Mode.WATER) {
                // On the water: chart the route, clear fog, find beaches and lakes; no roads, no settlements.
                val day = dayOf(run.first().timeMs)
                waterTrails += Trail(day, simplify(line, 6.0))
                sample(line, 15.0).forEach { wake += TroddenCell(Cells.cellAt(it.lat, it.lng, Cells.TRODDEN_RES), day) }
                for (k in 1 until run.size) {
                    val a = run[k - 1]; val b = run[k]
                    val d = dayOf(a.timeMs)
                    for (lm in gatherable) if (lm.near(a.lat, a.lng, gatherMarginM(lm.kind))) visits.merge(lm.id to d, (b.timeMs - a.timeMs) / 1000.0, Double::plus)
                    movement.getOrPut(d) { DoubleArray(3) }[2] += Geo.distanceM(a.lat, a.lng, b.lat, b.lng)
                }
                continue
            }
            trails += Trail(dayOf(run.first().timeMs), simplify(line, 4.0))
            // Cell time and distance, per leg.
            for (k in 1 until run.size) {
                val a = run[k - 1]; val b = run[k]
                val dt = (b.timeMs - a.timeMs) / 1000.0
                val day = dayOf(a.timeMs)
                val cell = Cells.cell(a.lat, a.lng)
                for (lm in gatherable) if (lm.near(a.lat, a.lng, gatherMarginM(lm.kind))) visits.merge(lm.id to day, dt, Double::plus)
                presence.merge(Triple(cell, day, mode), dt, Double::plus)
                if (mode == Mode.FOOT) shoreline.shorePoint(a.lat, a.lng)?.let { sp -> shore += TroddenCell(Cells.cellAt(sp.lat, sp.lng, Cells.TRODDEN_RES), day) }
                val m = movement.getOrPut(day) { DoubleArray(3) }
                m[if (mode == Mode.FOOT) 0 else 1] += Geo.distanceM(a.lat, a.lng, b.lat, b.lng)
            }
            if (matcher != null) {
                val m = matcher.match(run.map { LatLng(it.lat, it.lng) })
                matched += m.count { it != null }
                // Off-network stretches: consecutive fixes with no mapped path nearby.
                var k = 0
                while (k < run.size) {
                    if (m[k] != null) { k++; continue }
                    var e = k
                    while (e + 1 < run.size && m[e + 1] == null) e++
                    // Include the fixes on either side so a wild path joins the road it leaves from.
                    val from = (k - 1).coerceAtLeast(0); val to = (e + 1).coerceAtMost(run.size - 1)
                    val pts = run.subList(from, to + 1).map { LatLng(it.lat, it.lng) }
                    if (pts.size >= 3 && Geo.lengthM(pts) >= 30) {
                        val day = dayOf(run[k].timeMs)
                        wild += Trail(day, simplify(pts, 4.0))
                        sample(pts, 10.0).forEach { trodden += TroddenCell(Cells.cellAt(it.lat, it.lng, Cells.TRODDEN_RES), day) }
                    }
                    k = e + 1
                }
                for (iv in matcher.coverage(m)) {
                    coverage += EdgeCoverage(iv.edgeId, dayOf(run[iv.legIndex].timeMs), iv.fromM, iv.toM, mode)
                }
            }
        }
        return TrackResult(
            coverage = coverage,
            trails = trails,
            wildTrails = wild,
            trodden = trodden.toList(),
            landmarkVisits = visits.map { (k, v) -> LandmarkVisit(k.first, k.second, v) },
            arrivals = split.arrivals.map { Arrival(dayOf(it.timeMs), it.lat, it.lng) },
            stationaryPoints = split.stationary,
            presence = presence.map { (k, v) -> CellPresence(k.first, k.second, v, k.third) },
            movement = movement.map { (d, v) -> DayMovement(d, v[0], v[1], v[2]) },
            waterTrails = waterTrails,
            wake = wake.toList(),
            shore = shore.toList(),
            keptPoints = kept,
            droppedPoints = dropped,
            matchedPoints = matched,
        )
    }

    /** Points every [stepM] meters along a polyline. */
    private fun sample(points: List<LatLng>, stepM: Double): List<LatLng> {
        val out = mutableListOf(points.first())
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val d = Geo.distanceM(a, b)
            val n = (d / stepM).toInt()
            for (s in 1..n) {
                val t = s * stepM / d
                out += LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
            }
            out += b
        }
        return out
    }

    /** Douglas–Peucker simplification in local meters. */
    private fun simplify(points: List<LatLng>, toleranceM: Double): List<LatLng> {
        if (points.size < 3) return points
        val proj = LocalProjection(points.first())
        val xs = points.map { proj.x(it) }; val ys = points.map { proj.y(it) }
        val keep = BooleanArray(points.size).also { it[0] = true; it[points.size - 1] = true }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.size - 1) }
        while (stack.isNotEmpty()) {
            val (a, b) = stack.removeLast()
            var maxD = 0.0; var idx = -1
            val dx = xs[b] - xs[a]; val dy = ys[b] - ys[a]
            val len = kotlin.math.hypot(dx, dy)
            for (i in a + 1 until b) {
                val d = if (len == 0.0) kotlin.math.hypot(xs[i] - xs[a], ys[i] - ys[a])
                else kotlin.math.abs(dy * xs[i] - dx * ys[i] + xs[b] * ys[a] - ys[b] * xs[a]) / len
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > toleranceM) { keep[idx] = true; stack.add(a to idx); stack.add(idx to b) }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun inferMode(run: List<TrackPoint>): Mode {
        val speeds = (1 until run.size).mapNotNull { k ->
            val dt = (run[k].timeMs - run[k - 1].timeMs) / 1000.0
            if (dt <= 0) null else Geo.distanceM(run[k - 1].lat, run[k - 1].lng, run[k].lat, run[k].lng) / dt
        }.sorted()
        if (speeds.isEmpty()) return Mode.FOOT
        return if (speeds[speeds.size / 2] > cycleSpeedMps) Mode.CYCLE else Mode.FOOT
    }
}
