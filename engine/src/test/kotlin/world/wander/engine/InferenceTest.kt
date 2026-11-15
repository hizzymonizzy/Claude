package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Discovery beyond roads: named areas you walk through, and paths you make yourself. */
class InferenceTest {
    private val o = LatLng(40.4100, -74.1500)
    private val proj = LocalProjection(o)
    private fun at(x: Double, y: Double) = proj.toLatLng(x, y)
    private val zone = ZoneId.of("America/New_York")
    private val day0 = LocalDate.of(2026, 1, 5)
    private val start = day0.toEpochDay().toInt()
    private fun ms(day: Int, sec: Int) = day0.plusDays(day.toLong()).atTime(9, 0).atZone(zone).toInstant().toEpochMilli() + sec * 1000L

    /** A 300 × 200 m park with one mapped road along its south edge. */
    private val park = Landmark("w1", "Thompson Park", "park", listOf(listOf(at(0.0, 0.0), at(300.0, 0.0), at(300.0, 200.0), at(0.0, 200.0), at(0.0, 0.0))))
    private val road = GraphEdge("road", "Park Ave", 1, 2, listOf(at(-50.0, -10.0), at(350.0, -10.0)))

    /** Walk the road, then cut diagonally across the grass to the far corner. */
    private fun walk(day: Int): List<TrackPoint> =
        (0..10).map { TrackPoint(ms(day, it * 7), at(it * 10.0, -9.0).lat, at(it * 10.0, -9.0).lng, 5f) } +
            (1..20).map { k -> val p = at(100.0 + k * 9.0, k * 9.0); TrackPoint(ms(day, 70 + k * 7), p.lat, p.lng, 5f) }

    @Test
    fun parkAreaIsKnown() {
        assertEquals(60_000.0, park.areaM2, 600.0)
        assertTrue(park.contains(at(150.0, 100.0).lat, at(150.0, 100.0).lng))
        assertTrue(!park.contains(at(150.0, -30.0).lat, at(150.0, -30.0).lng))
    }

    @Test
    fun walkingThroughAParkFindsItAndYourOwnPath() {
        val r = TrackProcessor(zone).process(walk(0), Mode.FOOT, listOf(road), listOf(park))
        assertTrue(r.coverage.any { it.edgeId == "road" })
        assertEquals("park visit", 1, r.landmarkVisits.size)
        assertTrue(r.landmarkVisits.single().seconds >= 120)
        assertEquals(1, r.wildTrails.size)
        assertTrue(r.trodden.size >= 5)
    }

    @Test
    fun ownPathsWearInWithRepeatVisits() {
        val results = (0 until 4).map { TrackProcessor(zone).process(walk(it * 2), Mode.FOOT, listOf(road), listOf(park)) }
        val input = WorldInput(
            startDay = start, homeAreaId = null,
            edges = mapOf("road" to road.toInfo()),
            coverage = results.flatMap { it.coverage }, presence = results.flatMap { it.presence },
            intensity = emptyList(), movement = results.flatMap { it.movement },
            cellArea = emptyMap(), areas = emptyMap(),
            landmarks = mapOf("w1" to LandmarkInfo("w1", "Thompson Park", "park", park.areaM2)),
            landmarkVisits = results.flatMap { it.landmarkVisits },
            trodden = results.flatMap { it.trodden },
        )
        val state = WorldEngine().build(input, start + 7)
        val lm = state.landmarks.single()
        assertEquals("Thompson Park", lm.name)
        assertEquals(start, lm.discoveredDay)
        assertEquals(4, lm.visitDays)
        // Four separate days on the same cut-through → it has worn in to a path.
        assertEquals(4.0, state.troddenWear.values.max(), 1e-9)
        assertEquals(2, state.levelFor(state.troddenWear.values.max()))
        assertTrue(DispatchWriter.write(state, start).text.contains("Discovered Thompson Park"))
    }

    @Test
    fun stitchesSplitBoundaries() {
        val a = listOf(at(0.0, 0.0), at(100.0, 0.0), at(100.0, 100.0))
        val b = listOf(at(0.0, 0.0), at(0.0, 100.0), at(100.0, 100.0)) // reversed direction
        val rings = Landmark.stitch(listOf(a, b))
        assertEquals(1, rings.size)
        assertEquals(10_000.0, Landmark.ringArea(rings.single()), 200.0)
    }
}
