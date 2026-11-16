package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MapMatcherTest {
    private val o = LatLng(40.7357, -74.1724)
    private val proj = LocalProjection(o)
    private fun at(x: Double, y: Double) = proj.toLatLng(x, y)

    /**
     * A small street grid: two east-west streets (y = 0 and y = 100) and two north-south (x = 0, x = 200),
     * each split at intersections. Node ids: (x,y) corners.
     */
    private val edges = listOf(
        GraphEdge("elm", "Elm Ave", 1, 2, listOf(at(0.0, 0.0), at(200.0, 0.0))),
        GraphEdge("maple", "Maple Ave", 3, 4, listOf(at(0.0, 100.0), at(200.0, 100.0))),
        GraphEdge("grove", "Grove St", 1, 3, listOf(at(0.0, 0.0), at(0.0, 100.0))),
        GraphEdge("ridge", "Ridge Rd", 2, 4, listOf(at(200.0, 0.0), at(200.0, 100.0))),
    )

    @Test
    fun walkAlongOneStreetCoversOnlyThatStreet() {
        // Noisy fixes along Elm, ±6 m off the centerline.
        val pts = (0..20).map { at(it * 10.0, if (it % 2 == 0) 6.0 else -6.0) }
        val m = MapMatcher(edges)
        val matched = m.match(pts)
        // Fixes right at the corners may snap to the cross street; everything in between is Elm.
        assertTrue(matched.subList(1, matched.size - 1).all { it?.edge?.id == "elm" })
        val cov = WorldEngine.mergedLength(m.coverage(matched).filter { it.edgeId == "elm" }.map { it.fromM to it.toM })
        assertTrue("covered $cov", cov > 190)
    }

    @Test
    fun turningTheCornerCreditsBothStreets() {
        val pts = (0..10).map { at(it * 20.0, 3.0) } + (1..5).map { at(197.0, it * 20.0) }
        val m = MapMatcher(edges)
        val ids = m.coverage(m.match(pts)).map { it.edgeId }.toSet()
        assertEquals(setOf("elm", "ridge"), ids)
    }

    @Test
    fun paddlingChartsAWaterRouteButNoRoadsOrSettlements() {
        val zone = ZoneId.of("America/New_York")
        val t0 = 1_767_600_000_000L
        // Paddle 400 m along y = 40 (parallel to Elm, well within matching distance of nothing else).
        val paddle = (0..40).map { TrackPoint(t0 + it * 8_000L, at(it * 10.0, 40.0).lat, at(it * 10.0, 40.0).lng, 6f) }
        val r = TrackProcessor(zone).process(paddle, Mode.WATER, edges)
        assertTrue(r.coverage.isEmpty())
        assertTrue(r.presence.isEmpty())
        assertTrue(r.trails.isEmpty())
        assertEquals(1, r.waterTrails.size)
        assertTrue(r.wake.size >= 5)
        assertEquals(400.0, r.movement.sumOf { it.waterMeters }, 20.0)
    }

    @Test
    fun paddlingIsDetectedFromBeingOnWater() {
        val zone = ZoneId.of("America/New_York")
        val t0 = 1_767_600_000_000L
        // A pond north of the grid; paddle across it with no hint at all.
        val pond = Landmark("pond", "Mill Pond", "water", listOf(listOf(at(0.0, 300.0), at(400.0, 300.0), at(400.0, 600.0), at(0.0, 600.0), at(0.0, 300.0))))
        val paddle = (0..30).map { TrackPoint(t0 + it * 8_000L, at(20.0 + it * 12.0, 450.0).lat, at(20.0 + it * 12.0, 450.0).lng, 6f) }
        val r = TrackProcessor(zone).process(paddle, null, edges, water = listOf(pond))
        assertEquals(1, r.waterTrails.size)
        assertTrue(r.presence.isEmpty())
        // The same track with no water mapped is just a walk.
        val asWalk = TrackProcessor(zone).process(paddle, null, edges)
        assertTrue(asWalk.waterTrails.isEmpty())
    }

    @Test
    fun potteringAroundTheHouseEarnsNothing() {
        val zone = ZoneId.of("America/New_York")
        val t0 = 1_767_600_000_000L
        val rnd = java.util.Random(4)
        // 10 minutes of fixes wandering within ~20 m (moving room to room, GPS drifting indoors).
        val indoor = (0 until 120).map {
            val p = at(50.0 + rnd.nextGaussian() * 12, 50.0 + rnd.nextGaussian() * 12)
            TrackPoint(t0 + it * 5_000L, p.lat, p.lng, 15f)
        }
        val r = TrackProcessor(zone).process(indoor, null, edges)
        assertTrue(r.presence.isEmpty())
        assertTrue(r.coverage.isEmpty())
        assertTrue(r.trails.isEmpty())
        assertTrue(r.stationaryPoints > 100)
    }

    @Test
    fun trackProcessorDropsVehicleSpeedAndProducesObservations() {
        val zone = ZoneId.of("America/New_York")
        val t0 = 1_767_600_000_000L
        // Walk east along Elm at ~1.4 m/s (10 m every 7 s)…
        val walk = (0..20).map { TrackPoint(t0 + it * 7_000L, at(it * 10.0, 2.0).lat, at(it * 10.0, 2.0).lng, 5f) }
        // …then a car ride: 200 m every 10 s.
        val drive = (1..5).map { TrackPoint(t0 + 140_000L + it * 10_000L, at(200.0, it * 200.0).lat, at(200.0, it * 200.0).lng, 5f) }
        val r = TrackProcessor(zone).process(walk + drive, null, edges)
        assertTrue(r.coverage.any { it.edgeId == "elm" && it.mode == Mode.FOOT })
        assertTrue(r.coverage.none { it.edgeId == "ridge" })
        val walkedM = r.movement.sumOf { it.footMeters }
        assertTrue("walked $walkedM", walkedM in 180.0..220.0)
        assertTrue(r.presence.sumOf { it.seconds } in 130.0..150.0)
        // Driving 1 km then walking again is an arrival.
        val walkAfter = (1..10).map { TrackPoint(t0 + 200_000L + it * 7_000L, at(200.0, 1000.0 + it * 10.0).lat, at(200.0, 1000.0 + it * 10.0).lng, 5f) }
        val r2 = TrackProcessor(zone).process(walk + drive + walkAfter, null, edges)
        assertEquals(1, r2.arrivals.size)
        assertTrue(Geo.distanceM(LatLng(r2.arrivals[0].lat, r2.arrivals[0].lng), at(200.0, 1010.0)) < 15)
        assertTrue(r.arrivals.isEmpty())
        // The walked path is kept (simplified: a straight walk needs only its ends), the drive is not.
        val trail = r.trails.single().points
        assertTrue("trail ${trail.size}", trail.size in 2..4)
        assertTrue(trail.all { Geo.distanceM(it, at(it.let { p -> proj.x(p) }, 2.0)) < 5 })
    }
}
