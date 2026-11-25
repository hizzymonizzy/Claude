package world.wander.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Own-path merge (0.14): a new stretch off the mapped network that runs alongside one of your own paths, in the same
 * direction, is the same path walked again with GPS drift. It's drawn on, and wears, the path you already have.
 *
 * How close counts: [nearM] (20 m) in general. On a beach it depends on the beach's depth (water to dunes): a narrow
 * beach (under 40 m) has room for one path, so anything on it merges; a deeper one merges within a third of its depth
 * (never less than [nearM]). Never beyond [maxM] (40 m).
 */
class OwnPaths(
    paths: List<List<LatLng>>,
    private val beaches: List<Landmark> = emptyList(),
    private val nearM: Double = 20.0,
    private val maxM: Double = 40.0,
    /** Paths cross at more than this angle (degrees) aren't the same path. */
    maxAngleDeg: Double = 30.0,
) {
    private val gridM = 50.0
    private val cosMax = kotlin.math.cos(maxAngleDeg * Math.PI / 180)
    private var proj: LocalProjection? = null
    private val segs = ArrayList<DoubleArray>() // ax, ay, bx, by
    private val grid = HashMap<Long, MutableList<Int>>()
    private val beachDepth = HashMap<String, Double>()

    init { paths.forEach { add(it) } }

    /** Remember a path, so later stretches (even later in the same walk) can merge onto it. */
    fun add(path: List<LatLng>) {
        if (path.size < 2) return
        val pr = proj ?: LocalProjection(path.first()).also { proj = it }
        for (i in 1 until path.size) {
            val s = doubleArrayOf(pr.x(path[i - 1]), pr.y(path[i - 1]), pr.x(path[i]), pr.y(path[i]))
            if (s[0] == s[2] && s[1] == s[3]) continue
            val idx = segs.size; segs += s
            for (gx in floor(min(s[0], s[2]) / gridM).toInt()..floor(max(s[0], s[2]) / gridM).toInt())
                for (gy in floor(min(s[1], s[3]) / gridM).toInt()..floor(max(s[1], s[3]) / gridM).toInt())
                    grid.getOrPut(key(gx, gy)) { mutableListOf() }.add(idx)
        }
    }

    val isEmpty get() = segs.isEmpty()

    private fun key(gx: Int, gy: Int): Long = (gx.toLong() shl 32) or (gy.toLong() and 0xffffffffL)

    /** How far a fix at [p] may be from an own path and still be on it. */
    fun limitAt(p: LatLng): Double {
        val beach = beaches.firstOrNull { it.contains(p.lat, p.lng) } ?: return nearM
        val depth = beachDepth.getOrPut(beach.id) { depthOf(beach) }
        return if (depth < 40.0) maxM else (depth / 3).coerceIn(nearM, maxM)
    }

    /**
     * [points] (a stretch, sampled about every 10 m) with every fix that lies alongside an own path moved onto it.
     * Only runs of at least [minRun] such fixes move, so a path that merely crosses or touches another stays its own.
     * Returns the new points and how many moved.
     */
    fun snap(points: List<LatLng>, minRun: Int = 3): Pair<List<LatLng>, Int> {
        val pr = proj
        if (pr == null || segs.isEmpty() || points.size < 2) return points to 0
        val moved = arrayOfNulls<LatLng>(points.size)
        for (i in points.indices) {
            val a = points[(i - 1).coerceAtLeast(0)]; val b = points[(i + 1).coerceAtMost(points.size - 1)]
            val hx = pr.x(b) - pr.x(a); val hy = pr.y(b) - pr.y(a)
            val hl = hypot(hx, hy)
            if (hl == 0.0) continue
            val px = pr.x(points[i]); val py = pr.y(points[i])
            val limit = limitAt(points[i])
            var bestD = Double.MAX_VALUE; var best: Pair<Double, Double>? = null
            val seen = HashSet<Int>()
            for (gx in floor((px - limit) / gridM).toInt()..floor((px + limit) / gridM).toInt())
                for (gy in floor((py - limit) / gridM).toInt()..floor((py + limit) / gridM).toInt())
                    for (idx in grid[key(gx, gy)].orEmpty()) {
                        if (!seen.add(idx)) continue
                        val s = segs[idx]
                        val dx = s[2] - s[0]; val dy = s[3] - s[1]
                        val len2 = dx * dx + dy * dy
                        // Same direction (either way along it): the angle between headings under the limit.
                        if (abs(hx * dx + hy * dy) / (hl * kotlin.math.sqrt(len2)) < cosMax) continue
                        val t = (((px - s[0]) * dx + (py - s[1]) * dy) / len2).coerceIn(0.0, 1.0)
                        val qx = s[0] + t * dx; val qy = s[1] + t * dy
                        val d = hypot(qx - px, qy - py)
                        if (d <= limit && d < bestD) { bestD = d; best = qx to qy }
                    }
            best?.let { moved[i] = pr.toLatLng(it.first, it.second) }
        }
        // Keep only runs long enough to be the same path, not a crossing.
        val out = points.toMutableList()
        var n = 0
        var i = 0
        while (i < points.size) {
            if (moved[i] == null) { i++; continue }
            var e = i
            while (e + 1 < points.size && moved[e + 1] != null) e++
            if (e - i + 1 >= minRun) for (k in i..e) { out[k] = moved[k]!!; n++ }
            i = e + 1
        }
        return out to n
    }

    companion object {
        /**
         * A beach's depth (water to dunes): the short side of the rectangle with the same area and perimeter as its
         * outline (exact for a rectangular strip; a fair guess for the long, narrow shapes beaches are).
         */
        fun depthOf(beach: Landmark): Double {
            val ring = beach.rings.maxBy { it.size }
            if (ring.size < 3) return 0.0
            val half = Geo.lengthM(if (ring.first() == ring.last()) ring else ring + ring.first()) / 2
            val a = beach.areaM2
            val disc = half * half - 4 * a
            return if (disc < 0) kotlin.math.sqrt(a) else (half - kotlin.math.sqrt(disc)) / 2
        }
    }
}
