package world.wander.engine

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * End-to-end walk through resource nodes, the way the app runs it: pointers recomputed on every GPS fix
 * (with the world as it was before the walk, since walks are credited when they end), then the track is
 * processed and the world rebuilt. [trace] prints the whole walk for a human to read.
 */
class GatheringSimulationTest {
    private val zone = ZoneId.of("America/New_York")
    private val o = LatLng(40.30, -74.30)            // invented area
    private val proj = LocalProjection(o)
    private fun at(x: Double, y: Double) = proj.toLatLng(x, y)
    private fun rect(id: String, name: String, kind: String, x0: Double, y0: Double, x1: Double, y1: Double) =
        Landmark(id, name, kind, listOf(listOf(at(x0, y0), at(x1, y0), at(x1, y1), at(x0, y1), at(x0, y0))))

    val park = rect("w1", "Oakdale Park", "park", 300.0, -100.0, 500.0, 150.0)
    val wood = rect("w2", "Elm Woods", "wood", 900.0, 300.0, 1300.0, 700.0)
    val pond = rect("w3", "", "water", 700.0, -250.0, 800.0, -150.0)
    val library = rect("n4", "Oakdale Library", "library", 1050.0, -40.0, 1120.0, 30.0)
    val streets = listOf(Street("s1", "Elm Ave", listOf(at(-200.0, 0.0), at(1400.0, 0.0))))
    val edges = listOf(GraphEdge("s1", "Elm Ave", 1, 2, listOf(at(-200.0, 0.0), at(1400.0, 0.0))))

    /** Walk the given waypoints at 1.4 m/s, a fix every 5 s. */
    fun walk(t0: Long, vararg wp: Pair<Double, Double>): List<TrackPoint> {
        val pts = mutableListOf<TrackPoint>(); var t = t0
        for (i in 1 until wp.size) {
            val (ax, ay) = wp[i - 1]; val (bx, by) = wp[i]
            val n = (Math.hypot(bx - ax, by - ay) / 7.0).toInt().coerceAtLeast(1)
            for (k in 0 until n) { val p = at(ax + (bx - ax) * k / n, ay + (by - ay) * k / n); pts += TrackPoint(t, p.lat, p.lng, 6f); t += 5_000 }
        }
        val e = at(wp.last().first, wp.last().second); pts += TrackPoint(t, e.lat, e.lng, 6f)
        return pts
    }

    fun world(results: List<TrackResult>, start: Int, today: Int): WorldState {
        val visits = results.flatMap { it.landmarkVisits }
        val all = listOf(park, wood, pond, library).associateBy { it.id }
        return WorldEngine().build(WorldInput(
            startDay = start, homeAreaId = "home", edges = edges.associate { it.id to it.toInfo() },
            coverage = results.flatMap { it.coverage }, presence = results.flatMap { it.presence }, intensity = emptyList(),
            movement = results.flatMap { it.movement },
            cellArea = results.flatMap { it.presence }.associate { it.cell to "home" },
            areas = mapOf("home" to AreaInfo("home", "Oakdale", null)),
            landmarks = visits.map { it.landmarkId }.distinct().mapNotNull { all[it] }.associate { it.id to LandmarkInfo(it.id, it.name, it.kind, it.areaM2, center = it.center) },
            landmarkVisits = visits, trodden = results.flatMap { it.trodden }, wake = results.flatMap { it.wake },
        ), today)
    }

    private val start = LocalDate.of(2026, 3, 2).toEpochDay().toInt()
    private val t0 = LocalDate.ofEpochDay(start.toLong()).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    /** Along Elm Ave, into the park and across it, back out, along the pond's shore, to the library. */
    private val track by lazy { walk(t0, 0.0 to 0.0, 300.0 to 0.0, 400.0 to 50.0, 500.0 to 0.0, 700.0 to -120.0, 800.0 to -120.0, 1085.0 to 0.0) }
    private fun nodes(p: TrackPoint, w: WorldState) =
        ResourceFinder.find(LatLng(p.lat, p.lng), listOf(park, wood, library), listOf(pond), streets, w)

    @Test
    fun trace() {
        val before = world(emptyList(), start, start)
        println("time   pos(x,y)     best wood                  best food                  best metal")
        track.forEachIndexed { i, p ->
            if (i % 6 != 0) return@forEachIndexed
            val ts = nodes(p, before)
            fun f(r: String) = ts.firstOrNull { it.resource == r && it.best }?.let { "%s(%.0f,%.0f) %.0fm".format(it.sourceId, proj.x(it.point), proj.y(it.point), it.distanceM) } ?: "-"
            println("%4ds  (%5.0f,%5.0f)  %-26s %-26s %s".format((p.timeMs - t0) / 1000, proj.x(LatLng(p.lat, p.lng)), proj.y(LatLng(p.lat, p.lng)), f("wood"), f("food"), f("metal")))
        }
        val r = TrackProcessor(zone).process(track, null, edges, landmarks = listOf(park, wood, library), water = listOf(pond))
        println("visits: " + r.landmarkVisits.joinToString { "${it.landmarkId}=${it.seconds.toInt()}s" })
        val after = world(listOf(r), start, start)
        println("treasury: ${after.treasury} (raw wood=%.2f food=%.2f metal=%.2f)".format(after.treasury.wood, after.treasury.food, after.treasury.metal))
        println("discovered: ${after.landmarks.map { it.name }} · gathered today: ${after.gatheredToday}")
        println("nodes after the walk: " + nodes(track.last(), after).joinToString { "${it.resource}:${it.sourceId}${if (it.best) "*" else ""} ${it.note}" })
    }

    @Test
    fun nodesStayPutYouGatherWalkingThroughAndGatheredNodesRemain() {
        val before = world(emptyList(), start, start)
        // 1. A node never moves: the park's wood node is the same spot at every GPS fix, and never on top of you.
        val spots = track.map { p -> nodes(p, before).first { it.sourceId == "w1" }.point }.toSet()
        org.junit.Assert.assertEquals(setOf(park.anchor), spots)
        // 2. Inside the park you're in its gathering zone; along the pond's shore too.
        val inPark = track.filter { park.contains(it.lat, it.lng) }
        org.junit.Assert.assertTrue(inPark.isNotEmpty() && inPark.all { p -> nodes(p, before).first { it.sourceId == "w1" }.distanceM == 0.0 })
        org.junit.Assert.assertTrue(track.any { p -> nodes(p, before).first { it.sourceId == "w3" }.distanceM == 0.0 })
        // 3. Walking through each node gathers: wood (new park, double), food from the unnamed pond's shore, metal.
        val r = TrackProcessor(zone).process(track, null, edges, landmarks = listOf(park, wood, library), water = listOf(pond))
        val after = world(listOf(r), start, start)
        org.junit.Assert.assertTrue("wood ${after.treasury.wood}", after.treasury.wood >= 2.0)
        org.junit.Assert.assertTrue("food ${after.treasury.food}", after.treasury.food >= 1.0)
        org.junit.Assert.assertTrue("metal ${after.treasury.metal}", after.treasury.metal >= 3.0)
        org.junit.Assert.assertEquals(setOf("Oakdale Park", "Oakdale Library"), after.landmarks.map { it.name }.toSet())
        // 4. Gathered nodes stay on the map, marked gathered, and the arrow moves on to somewhere you haven't been today.
        val now = nodes(track.last(), after)
        org.junit.Assert.assertEquals("gathered", now.first { it.sourceId == "w1" }.note)
        org.junit.Assert.assertEquals(park.anchor, now.first { it.sourceId == "w1" }.point)
        org.junit.Assert.assertEquals("w2", now.first { it.resource == "wood" && it.best }.sourceId)
        org.junit.Assert.assertTrue(now.none { it.best && it.note == "gathered" })
    }

    @Test
    fun aQuickCrossingOfASmallParkStillCounts() {
        // ~35 s across a 50 m pocket park: one visit, one wood (two the first time).
        val small = rect("w5", "Elm Green", "park", 100.0, -25.0, 150.0, 25.0)
        val quick = walk(t0, 0.0 to 0.0, 250.0 to 0.0)
        val r = TrackProcessor(zone).process(quick, null, edges, landmarks = listOf(small))
        val visits = r.landmarkVisits
        val w = WorldEngine().build(WorldInput(
            startDay = start, homeAreaId = "home", edges = edges.associate { it.id to it.toInfo() }, coverage = r.coverage,
            presence = r.presence, intensity = emptyList(), movement = r.movement, cellArea = r.presence.associate { it.cell to "home" },
            areas = mapOf("home" to AreaInfo("home", "Oakdale", null)),
            landmarks = mapOf("w5" to LandmarkInfo("w5", "Elm Green", "park", small.areaM2)), landmarkVisits = visits,
        ), start)
        org.junit.Assert.assertTrue("visit ${visits.sumOf { it.seconds }} s", visits.sumOf { it.seconds } >= 30)
        org.junit.Assert.assertTrue(w.treasury.wood >= 2.0)
        org.junit.Assert.assertEquals(listOf("Elm Green"), w.landmarks.map { it.name })
    }
}
