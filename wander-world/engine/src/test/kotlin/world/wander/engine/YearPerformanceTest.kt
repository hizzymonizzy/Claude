package world.wander.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/** A year of invented daily walks (~4 km, ~500 heat hexes a day, mostly repeated routes): how long a rebuild takes. */
class YearPerformanceTest {
    @Test
    fun aYearOfWalkingRebuildsQuickly() {
        val start = LocalDate.of(2026, 1, 5).toEpochDay().toInt()
        val rnd = Random(3)
        val base = Cells.cellAt(40.30, -74.30, Cells.HEAT_RES)
        val routes = (0 until 12).map { r -> Cells.disk(Cells.cellAt(40.30 + r * 0.004, -74.30 + (r % 4) * 0.005, Cells.HEAT_RES), 12).shuffled(rnd).take(500) }
        val homeCells = Cells.disk(Cells.cell(40.30, -74.30), 4)
        val days = (0 until 365).filter { rnd.nextDouble() < 0.8 }.map { start + it }
        val heat = days.flatMap { d -> routes[rnd.nextInt(routes.size)].map { TroddenCell(it, d) } }
        val presence = days.flatMap { d -> (0 until 8).map { CellPresence(homeCells[rnd.nextInt(homeCells.size)], d, 300.0, Mode.FOOT) } }
        val input = WorldInput(
            startDay = start, homeAreaId = "home", edges = emptyMap(), coverage = emptyList(), presence = presence,
            intensity = emptyList(), movement = emptyList(), cellArea = homeCells.associateWith { "home" },
            areas = mapOf("home" to AreaInfo("home", "Oakdale", null)), heat = heat,
        )
        WorldEngine().build(input, start + 364) // warm up
        val t0 = System.nanoTime()
        val w = WorldEngine().build(input, start + 364)
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("year: ${days.size} walking days, ${heat.size} hex-days, ${w.heat.size} block / ${w.heatHood.size} hood / ${w.heatCounty.size} county hexes; rebuild $ms ms (JVM)")
        assertTrue("rebuild under 3 s on the JVM", ms < 3_000)
        assertTrue(base.isNotEmpty())
    }
}
