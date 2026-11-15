package world.wander.engine

import java.time.Instant
import java.time.ZoneId

data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
)

data class TrackResult(
    val coverage: List<EdgeCoverage>,
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
) {
    fun dayOf(timeMs: Long): Int = Instant.ofEpochMilli(timeMs).atZone(zone).toLocalDate().toEpochDay().toInt()

    /** The fixes that survive filtering (no vehicle-speed legs, no inaccurate fixes): the only ones needing map data. */
    fun usablePoints(points: List<TrackPoint>): List<TrackPoint> = runs(points).first.flatten()

    private fun runs(points: List<TrackPoint>): Pair<List<List<TrackPoint>>, Int> {
        val sorted = points.filter { it.accuracyM <= maxAccuracyM }.sortedBy { it.timeMs }
        // Split into runs on gaps and vehicle-speed legs.
        val runs = mutableListOf<MutableList<TrackPoint>>()
        var current = mutableListOf<TrackPoint>()
        var dropped = points.size - sorted.size
        for (p in sorted) {
            val prev = current.lastOrNull()
            if (prev == null) { current.add(p); continue }
            val dt = (p.timeMs - prev.timeMs) / 1000.0
            if (dt <= 0) { dropped++; continue }
            val d = Geo.distanceM(prev.lat, prev.lng, p.lat, p.lng)
            val speed = d / dt
            when {
                speed > vehicleSpeedMps -> {
                    // Vehicle movement or a GPS jump: never counts. Start fresh after it.
                    dropped++
                    if (current.size > 1) runs.add(current)
                    current = mutableListOf()
                }
                dt > maxGapSeconds || d > maxGapMeters -> {
                    if (current.size > 1) runs.add(current)
                    current = mutableListOf(p)
                }
                else -> current.add(p)
            }
        }
        if (current.size > 1) runs.add(current)
        return runs to dropped
    }

    fun process(points: List<TrackPoint>, modeHint: Mode?, edges: List<GraphEdge>): TrackResult {
        val (runs, dropped) = runs(points)
        val matcher = if (edges.isNotEmpty()) MapMatcher(edges) else null
        val coverage = mutableListOf<EdgeCoverage>()
        val presence = HashMap<Triple<String, Int, Mode>, Double>()
        val movement = HashMap<Int, DoubleArray>()
        var kept = 0
        var matched = 0

        for (run in runs) {
            kept += run.size
            val mode = modeHint ?: inferMode(run)
            // Cell time and distance, per leg.
            for (k in 1 until run.size) {
                val a = run[k - 1]; val b = run[k]
                val dt = (b.timeMs - a.timeMs) / 1000.0
                val day = dayOf(a.timeMs)
                val cell = Cells.cell(a.lat, a.lng)
                presence.merge(Triple(cell, day, mode), dt, Double::plus)
                val m = movement.getOrPut(day) { DoubleArray(2) }
                m[if (mode == Mode.FOOT) 0 else 1] += Geo.distanceM(a.lat, a.lng, b.lat, b.lng)
            }
            if (matcher != null) {
                val m = matcher.match(run.map { LatLng(it.lat, it.lng) })
                matched += m.count { it != null }
                for (iv in matcher.coverage(m)) {
                    coverage += EdgeCoverage(iv.edgeId, dayOf(run[iv.legIndex].timeMs), iv.fromM, iv.toM, mode)
                }
            }
        }
        return TrackResult(
            coverage = coverage,
            presence = presence.map { (k, v) -> CellPresence(k.first, k.second, v, k.third) },
            movement = movement.map { (d, v) -> DayMovement(d, v[0], v[1]) },
            keptPoints = kept,
            droppedPoints = dropped,
            matchedPoints = matched,
        )
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
