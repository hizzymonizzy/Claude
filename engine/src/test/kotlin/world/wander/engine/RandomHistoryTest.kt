package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Random but busy histories (roads, places, paddling, travel, rest, decisions). Used once to prove the split
 * simulation matched the old engine exactly (alpha 0.9.1); now guards determinism and the world's invariants.
 */
class RandomHistoryTest {
    private val start = LocalDate.of(2026, 1, 5).toEpochDay().toInt()

    private fun randomInput(seed: Int): WorldInput {
        val rnd = Random(seed)
        val home = Cells.disk(Cells.cell(40.41, -74.15), 4)
        val far = Cells.disk(Cells.cell(40.70, -74.40), 3)
        val hnl = Cells.disk(Cells.cell(21.28, -157.83), 2)
        val all = home + far + hnl
        val days = 160
        val edges = (0 until 40).associate { i ->
            val a = Cells.center(home[i % home.size]); val b = Cells.center(home[(i + 1) % home.size])
            "e$i" to EdgeInfo("e$i", if (i % 3 == 0) null else "Road $i", Geo.distanceM(a, b), i.toLong(), i + 1L, LatLng((a.lat + b.lat) / 2, (a.lng + b.lng) / 2))
        }
        val lms = (0 until 12).associate { i ->
            val kinds = listOf("park", "wood", "beach", "water", "school", "library", "harbour", "store")
            val c = Cells.center(home[(i * 7) % home.size])
            "l$i" to LandmarkInfo("l$i", "Place $i", kinds[i % kinds.size], 10_000.0 * (i + 1), pointLike = i % 4 == 0, center = c)
        }
        val presence = mutableListOf<CellPresence>(); val coverage = mutableListOf<EdgeCoverage>()
        val movement = mutableListOf<DayMovement>(); val visits = mutableListOf<LandmarkVisit>()
        val trodden = mutableListOf<TroddenCell>(); val wake = mutableListOf<TroddenCell>()
        val arrivals = mutableListOf<Arrival>(); val decisions = mutableListOf<ProjectDecision>()
        for (d in 0 until days) {
            val day = start + d
            if (rnd.nextDouble() < 0.25) continue
            val pool = when { d in 60..70 -> hnl; rnd.nextDouble() < 0.15 -> far; else -> home }
            if (d == 60 || d == 71 || rnd.nextDouble() < 0.05) pool.random(rnd).let { val c = Cells.center(it); arrivals += Arrival(day, c.lat + 0.01, c.lng) }
            repeat(rnd.nextInt(1, 8)) {
                val mode = if (rnd.nextDouble() < 0.15) Mode.CYCLE else Mode.FOOT
                presence += CellPresence(pool.random(rnd), day, rnd.nextDouble(60.0, 1500.0), mode)
            }
            if (pool === home) repeat(rnd.nextInt(0, 6)) {
                val e = edges.values.random(rnd); val from = rnd.nextDouble(0.0, e.lengthM)
                coverage += EdgeCoverage(e.id, day, from, minOf(e.lengthM, from + rnd.nextDouble(20.0, 400.0)), if (rnd.nextBoolean()) Mode.FOOT else Mode.CYCLE)
            }
            if (rnd.nextDouble() < 0.5) visits += LandmarkVisit(lms.keys.random(rnd), day, rnd.nextDouble(5.0, 1200.0))
            if (d % 3 != 0) visits += LandmarkVisit("l1", day, 900.0)   // a favourite wood, to exercise thinning
            repeat(rnd.nextInt(0, 4)) { trodden += TroddenCell(Cells.cellAt(40.41 + rnd.nextDouble(-0.01, 0.01), -74.15, Cells.TRODDEN_RES), day) }
            val water = if (rnd.nextDouble() < 0.1) rnd.nextDouble(500.0, 4000.0) else 0.0
            if (water > 0) repeat(rnd.nextInt(0, 5)) { wake += TroddenCell(Cells.cellAt(40.42, -74.14 + rnd.nextDouble(0.0, 0.02), Cells.TRODDEN_RES), day) }
            movement += DayMovement(day, rnd.nextDouble(0.0, 9000.0), rnd.nextDouble(0.0, 3000.0), water)
            if (rnd.nextDouble() < 0.2) decisions += ProjectDecision(day, "a${rnd.nextInt(0, 4)}", Projects.ALL.random(rnd).id, rnd.nextBoolean())
        }
        // Also answer whatever the capital proposes, a few times.
        Projects.ALL.forEachIndexed { i, p -> decisions += ProjectDecision(start + 20 + i * 9, "a0", p.id, i % 3 != 0) }
        return WorldInput(
            startDay = start, homeAreaId = "a0", homePoint = Cells.center(home[0]), edges = edges, coverage = coverage,
            presence = presence, intensity = (0 until days).map { DayIntensity(start + it, rnd.nextDouble(0.0, 60.0), rnd.nextDouble(0.0, 20.0)) },
            movement = movement, cellArea = all.associateWith { c -> "a" + (all.indexOf(c) % 4).let { if (c in hnl) 9 else it } },
            areas = mapOf("a0" to AreaInfo("a0", "Oakdale", null), "a9" to AreaInfo("a9", "Island", null)),
            restPeriods = listOf(start + 100..start + 110), landmarks = lms, landmarkVisits = visits, trodden = trodden,
            arrivals = arrivals, wake = wake, decisions = decisions,
        )
    }

    @Test
    fun theWorldIsDeterministicAndKeepsItsPromises() {
        for (seed in 1..12) {
            val input = randomInput(seed)
            for (today in listOf(start + 30, start + 90, start + 159)) {
                val w = WorldEngine().build(input, today)
                assertEquals("seed $seed day $today", w, WorldEngine().build(input, today))
                for (s in w.settlements) {
                    // Nothing falls more than two tiers below its peak.
                    assertTrue(s.tier.ordinal >= s.peakTier.ordinal - 2)
                    if (s.isCapital) assertTrue(s.tier != Tier.RUINS)
                }
                assertTrue(w.treasury.gold >= 0 && w.treasury.wood >= 0 && w.treasury.stone >= 0)
                assertTrue(w.abundance.values.all { it in 0.3..1.0 })
                assertTrue(w.goal.goal in 150..300)
                // No waystone near home.
                assertTrue(w.waystones.none { Geo.distanceM(it.center, input.homePoint!!) < 2_000 })
            }
        }
        // The histories really exercise the rules.
        val events = (1..12).flatMap { WorldEngine().build(randomInput(it), start + 159).events }
        assertTrue(events.any { it is WorldEvent.ProjectBuilt })
        assertTrue(events.any { it is WorldEvent.SourceThinning })
        assertTrue(events.any { it is WorldEvent.WaystoneFound && it.great })
        assertTrue(events.any { it is WorldEvent.GoalChanged })
    }
}
