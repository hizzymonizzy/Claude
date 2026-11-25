package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Path normalization (0.14), simulated on invented streets: road snap (gaps up to 35 m beside the street between two
 * matched stretches count as that street) and own-path merge (parallel stretches within 20 m, beach-depth aware).
 */
class PathNormalizationTest {
    private val o = LatLng(40.7357, -74.1724)
    private val proj = LocalProjection(o)
    private fun at(x: Double, y: Double) = proj.toLatLng(x, y)
    private val zone = ZoneId.of("America/New_York")
    private val t0 = 1_767_600_000_000L

    private val edges = listOf(
        GraphEdge("elm", "Elm Ave", 1, 2, listOf(at(0.0, 0.0), at(400.0, 0.0))),
        GraphEdge("maple", "Maple Ave", 3, 4, listOf(at(0.0, 200.0), at(400.0, 200.0))),
        GraphEdge("grove", "Grove St", 1, 3, listOf(at(0.0, 0.0), at(0.0, 200.0))),
    )

    /** A walk through these local (x, y) points at about 1.4 m/s, one fix every 7 s. */
    private fun walk(vararg xy: Pair<Double, Double>, startMs: Long = t0): List<TrackPoint> {
        val pts = mutableListOf<LatLng>()
        for (i in 1 until xy.size) {
            val (ax, ay) = xy[i - 1]; val (bx, by) = xy[i]
            val n = (kotlin.math.hypot(bx - ax, by - ay) / 10).toInt().coerceAtLeast(1)
            for (k in 0 until n) pts += at(ax + (bx - ax) * k / n, ay + (by - ay) * k / n)
        }
        pts += at(xy.last().first, xy.last().second)
        return pts.mapIndexed { i, p -> TrackPoint(startMs + i * 7_000L, p.lat, p.lng, 5f) }
    }

    private fun covered(r: TrackResult, id: String) =
        WorldEngine.mergedLength(r.coverage.filter { it.edgeId == id }.map { it.fromM to it.toM })

    @Test
    fun driftBesideTheStreetIsCreditedToIt() {
        // Along Elm, drifting 30 m off it for 150 m in the middle (beyond the matcher's 25 m), then back on.
        val r = TrackProcessor(zone).process(walk(0.0 to 2.0, 120.0 to 2.0, 130.0 to 30.0, 270.0 to 30.0, 280.0 to 2.0, 400.0 to 2.0), Mode.FOOT, edges)
        println("snap: elm %.0f m, snapped %s, left %s, wild %d".format(covered(r, "elm"), r.snappedM, r.unmatched, r.wildTrails.size))
        assertTrue(covered(r, "elm") > 380)
        assertTrue(r.wildTrails.isEmpty())
        assertEquals(1, r.snappedM.size)
    }

    @Test
    fun aRealDetourStaysYourOwnPath() {
        // 50 m off the street: a path of your own, not drift.
        val r = TrackProcessor(zone).process(walk(0.0 to 2.0, 120.0 to 2.0, 130.0 to 50.0, 270.0 to 50.0, 280.0 to 2.0, 400.0 to 2.0), Mode.FOOT, edges)
        println("detour: elm %.0f m, left %s, wild %d".format(covered(r, "elm"), r.unmatched.map { "%.0f m, %.0f m out".format(it.first, it.second) }, r.wildTrails.size))
        assertTrue(r.snappedM.isEmpty())
        assertEquals(1, r.wildTrails.size)
        assertTrue(r.unmatched.single().second > 35)
    }

    @Test
    fun driftAlongTheStreetJoiningTwoMatchedOnesCreditsIt() {
        // Elm westward, then up beside Grove 30 m off it, then Maple eastward: the connector is Grove.
        val r = TrackProcessor(zone).process(walk(200.0 to 0.0, 40.0 to 0.0, -30.0 to 40.0, -30.0 to 160.0, 40.0 to 200.0, 200.0 to 200.0), Mode.FOOT, edges)
        println("connector: grove %.0f m, snapped %s, wild %d".format(covered(r, "grove"), r.snappedM, r.wildTrails.size))
        assertTrue(covered(r, "grove") > 100)
        assertTrue(r.wildTrails.isEmpty())
    }

    @Test
    fun aParallelWalkJoinsYourPath() {
        // Off-network (y = 600, far from any street): yesterday's path, then today 12 m beside it.
        val first = TrackProcessor(zone).process(walk(0.0 to 600.0, 300.0 to 600.0), Mode.FOOT, edges)
        val path = first.wildTrails.single().points
        val second = TrackProcessor(zone).process(walk(0.0 to 612.0, 300.0 to 612.0, startMs = t0 + 86_400_000L), Mode.FOOT, edges, ownPaths = listOf(path))
        val offset = second.wildTrails.single().points.maxOf { kotlin.math.abs(proj.y(it) - 600.0) }
        println("parallel 12 m: merged ${second.mergedStretches} stretch(es), %.0f m; furthest from yesterday's path %.1f m; shared cells %d of %d"
            .format(second.mergedM, offset, second.trodden.map { it.cell }.toSet().intersect(first.trodden.map { it.cell }.toSet()).size, second.trodden.size))
        assertEquals(1, second.mergedStretches)
        assertTrue(offset < 1.0)
        // 25 m apart: two paths.
        val apart = TrackProcessor(zone).process(walk(0.0 to 625.0, 300.0 to 625.0, startMs = t0 + 86_400_000L), Mode.FOOT, edges, ownPaths = listOf(path))
        assertEquals(0, apart.mergedStretches)
    }

    @Test
    fun aCrossingPathStaysItsOwn() {
        val path = listOf(at(0.0, 600.0), at(300.0, 600.0))
        val r = TrackProcessor(zone).process(walk(150.0 to 450.0, 150.0 to 750.0), Mode.FOOT, edges, ownPaths = listOf(path))
        assertEquals(0, r.mergedStretches)
    }

    private fun beach(depth: Double) = Landmark("w1", "Oakdale Beach", "beach",
        listOf(listOf(at(-50.0, 590.0), at(400.0, 590.0), at(400.0, 590.0 + depth), at(-50.0, 590.0 + depth), at(-50.0, 590.0))))

    @Test
    fun beachPathsMergeByDepth() {
        val path = listOf(at(0.0, 600.0), at(300.0, 600.0))
        fun merges(depth: Double, apart: Double): Boolean {
            val r = TrackProcessor(zone).process(walk(0.0 to 600.0 + apart, 300.0 to 600.0 + apart), Mode.FOOT, edges, landmarks = listOf(beach(depth)), ownPaths = listOf(path))
            return r.mergedStretches > 0
        }
        println("beach depth: narrow 35 m → limit %.0f; 90 m → %.0f; 200 m → %.0f".format(
            OwnPaths(emptyList(), listOf(beach(35.0))).limitAt(at(100.0, 610.0)),
            OwnPaths(emptyList(), listOf(beach(90.0))).limitAt(at(100.0, 610.0)),
            OwnPaths(emptyList(), listOf(beach(200.0))).limitAt(at(100.0, 610.0))))
        assertTrue("narrow beach: one path", merges(35.0, 24.0))
        assertTrue("deep beach: within a third of its depth", merges(90.0, 26.0))
        assertTrue("deep beach: further apart stays two", !merges(90.0, 34.0))
        assertTrue("never beyond 40 m", !merges(35.0, 45.0))
    }
}
