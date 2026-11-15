package world.wander.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A road edge with full geometry, used for matching. */
class GraphEdge(
    val id: String,
    val name: String?,
    val startNode: Long,
    val endNode: Long,
    val points: List<LatLng>,
) {
    /** Cumulative distance (m) at each vertex. */
    val cumulative: DoubleArray = DoubleArray(points.size).also {
        for (i in 1 until points.size) it[i] = it[i - 1] + Geo.distanceM(points[i - 1], points[i])
    }
    val lengthM: Double get() = cumulative.last()

    fun midpoint(): LatLng {
        val half = lengthM / 2
        for (i in 1 until points.size) {
            if (cumulative[i] >= half) {
                val seg = cumulative[i] - cumulative[i - 1]
                val t = if (seg <= 0) 0.0 else (half - cumulative[i - 1]) / seg
                val a = points[i - 1]
                val b = points[i]
                return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
            }
        }
        return points.first()
    }

    fun toInfo() = EdgeInfo(id, name, lengthM, startNode, endNode, midpoint())
}

/** A GPS fix snapped onto an edge, [alongM] meters from the edge's start. */
data class Candidate(val edge: GraphEdge, val alongM: Double, val distM: Double)

/** A stretch of an edge covered between two consecutive matched fixes. */
data class EdgeInterval(val edgeId: String, val fromM: Double, val toM: Double, val legIndex: Int)

/**
 * Hidden-Markov map matching (after Newson & Krumm, 2009), simplified for walking:
 * emission = distance from the fix to the road; transition = how far the on-road distance
 * differs from the straight-line distance between fixes.
 */
class MapMatcher(
    private val edges: List<GraphEdge>,
    private val searchRadiusM: Double = 25.0,
    private val sigmaM: Double = 8.0,
    private val betaM: Double = 5.0,
    private val maxCandidates: Int = 6,
) {
    private val gridM = 50.0
    private val proj = LocalProjection(edges.firstOrNull()?.points?.firstOrNull() ?: LatLng(0.0, 0.0))
    private val grid = HashMap<Long, MutableList<Pair<GraphEdge, Int>>>()

    init {
        for (e in edges) {
            for (i in 1 until e.points.size) {
                val ax = proj.x(e.points[i - 1]); val ay = proj.y(e.points[i - 1])
                val bx = proj.x(e.points[i]); val by = proj.y(e.points[i])
                val gx0 = floor(min(ax, bx) / gridM).toInt(); val gx1 = floor(max(ax, bx) / gridM).toInt()
                val gy0 = floor(min(ay, by) / gridM).toInt(); val gy1 = floor(max(ay, by) / gridM).toInt()
                for (gx in gx0..gx1) for (gy in gy0..gy1) {
                    grid.getOrPut(key(gx, gy)) { mutableListOf() }.add(e to i)
                }
            }
        }
    }

    private fun key(gx: Int, gy: Int): Long = (gx.toLong() shl 32) or (gy.toLong() and 0xffffffffL)

    fun candidates(p: LatLng): List<Candidate> {
        val px = proj.x(p); val py = proj.y(p)
        val r = searchRadiusM
        val best = HashMap<String, Candidate>()
        for (gx in floor((px - r) / gridM).toInt()..floor((px + r) / gridM).toInt()) {
            for (gy in floor((py - r) / gridM).toInt()..floor((py + r) / gridM).toInt()) {
                val list = grid[key(gx, gy)] ?: continue
                for ((e, i) in list) {
                    val a = e.points[i - 1]; val b = e.points[i]
                    val ax = proj.x(a); val ay = proj.y(a); val bx = proj.x(b); val by = proj.y(b)
                    val dx = bx - ax; val dy = by - ay
                    val len2 = dx * dx + dy * dy
                    val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
                    val d = hypot(ax + t * dx - px, ay + t * dy - py)
                    if (d > r) continue
                    val along = e.cumulative[i - 1] + t * (e.cumulative[i] - e.cumulative[i - 1])
                    val prev = best[e.id]
                    if (prev == null || d < prev.distM) best[e.id] = Candidate(e, along, d)
                }
            }
        }
        return best.values.sortedBy { it.distM }.take(maxCandidates)
    }

    /** On-road distance between two candidates, or null if they aren't on the same or adjacent edges. */
    private fun routeDistance(a: Candidate, b: Candidate): Double? {
        if (a.edge === b.edge) return abs(b.alongM - a.alongM)
        return sharedNodeDistance(a, b)?.first
    }

    /** Distance via a node shared by both edges, and whether that node is the END of edge a. */
    private fun sharedNodeDistance(a: Candidate, b: Candidate): Pair<Double, Pair<Boolean, Boolean>>? {
        var best: Pair<Double, Pair<Boolean, Boolean>>? = null
        for (aEnd in listOf(false, true)) {
            val aNode = if (aEnd) a.edge.endNode else a.edge.startNode
            for (bStart in listOf(true, false)) {
                val bNode = if (bStart) b.edge.startNode else b.edge.endNode
                if (aNode != bNode) continue
                val da = if (aEnd) a.edge.lengthM - a.alongM else a.alongM
                val db = if (bStart) b.alongM else b.edge.lengthM - b.alongM
                val d = da + db
                if (best == null || d < best.first) best = d to (aEnd to bStart)
            }
        }
        return best
    }

    /**
     * Matches a run of fixes. Returns one candidate (or null) per fix. Runs break wherever a fix has no
     * nearby road or consecutive fixes are too far apart.
     */
    fun match(points: List<LatLng>): List<Candidate?> {
        val result = arrayOfNulls<Candidate>(points.size)
        var i = 0
        while (i < points.size) {
            val cands0 = candidates(points[i])
            if (cands0.isEmpty()) { i++; continue }
            // Build a run of fixes that all have candidates.
            val runIdx = mutableListOf(i)
            val runCands = mutableListOf(cands0)
            var j = i + 1
            while (j < points.size) {
                if (Geo.distanceM(points[j - 1], points[j]) > 200) break
                val c = candidates(points[j])
                if (c.isEmpty()) break
                runIdx.add(j); runCands.add(c)
                j++
            }
            viterbi(runIdx.map { points[it] }, runCands).forEachIndexed { k, c -> result[runIdx[k]] = c }
            i = j
        }
        return result.toList()
    }

    private fun viterbi(points: List<LatLng>, cands: List<List<Candidate>>): List<Candidate> {
        fun emission(c: Candidate) = -0.5 * (c.distM / sigmaM) * (c.distM / sigmaM)
        var score = cands[0].map { emission(it) }.toDoubleArray()
        val back = mutableListOf<IntArray>()
        for (t in 1 until cands.size) {
            val gc = Geo.distanceM(points[t - 1], points[t])
            val next = DoubleArray(cands[t].size)
            val ptr = IntArray(cands[t].size)
            for ((bi, b) in cands[t].withIndex()) {
                var bestScore = Double.NEGATIVE_INFINITY
                var bestIdx = 0
                for ((ai, a) in cands[t - 1].withIndex()) {
                    val route = routeDistance(a, b) ?: (gc * 1.5 + 60.0)
                    val s = score[ai] - abs(route - gc) / betaM
                    if (s > bestScore) { bestScore = s; bestIdx = ai }
                }
                next[bi] = bestScore + emission(b)
                ptr[bi] = bestIdx
            }
            back.add(ptr)
            score = next
        }
        var idx = score.indices.maxBy { score[it] }
        val path = ArrayList<Candidate>(cands.size)
        path.add(cands.last()[idx])
        for (t in cands.size - 1 downTo 1) {
            idx = back[t - 1][idx]
            path.add(cands[t - 1][idx])
        }
        path.reverse()
        return path
    }

    /** Converts consecutive matched fixes into covered stretches of road. Leg k = fixes k-1 → k. */
    fun coverage(matched: List<Candidate?>): List<EdgeInterval> {
        val out = mutableListOf<EdgeInterval>()
        for (k in 1 until matched.size) {
            val a = matched[k - 1] ?: continue
            val b = matched[k] ?: continue
            if (a.edge === b.edge) {
                out += EdgeInterval(a.edge.id, min(a.alongM, b.alongM), max(a.alongM, b.alongM), k)
                continue
            }
            val shared = sharedNodeDistance(a, b) ?: continue
            val (aEnd, bStart) = shared.second
            out += if (aEnd) EdgeInterval(a.edge.id, a.alongM, a.edge.lengthM, k) else EdgeInterval(a.edge.id, 0.0, a.alongM, k)
            out += if (bStart) EdgeInterval(b.edge.id, 0.0, b.alongM, k) else EdgeInterval(b.edge.id, b.alongM, b.edge.lengthM, k)
        }
        return out
    }
}
