package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Weekly cooling (ADR 0008) end to end, on an invented neighbourhood: walk a street every day until it's an avenue
 * and the home settlement grows, then stop and watch the Tuesday scorings; then come back. Prints a table for the
 * release report.
 */
class CoolingSimulationTest {
    private val start = LocalDate.of(2026, 1, 5).also { check(it.dayOfWeek == DayOfWeek.MONDAY) }.toEpochDay().toInt()
    private val home = Cells.disk(Cells.cell(40.30, -74.30), 3)
    private val street = EdgeInfo("e1", "Elm Ave", 200.0, 1, 2, LatLng(40.30, -74.30))

    private fun input(walkDays: List<Int>, minutes: Double = 30.0, rest: List<IntRange> = emptyList(), streetDays: List<Int> = walkDays) = WorldInput(
        startDay = start, homeAreaId = "home", edges = mapOf("e1" to street),
        coverage = streetDays.map { EdgeCoverage("e1", it, 0.0, 200.0, Mode.FOOT) },
        presence = walkDays.flatMap { d -> (0 until 4).map { i -> CellPresence(home[(d + i) % home.size], d, minutes * 60 / 4, Mode.FOOT) } },
        intensity = emptyList(), movement = emptyList(),
        cellArea = home.associateWith { "home" }, areas = mapOf("home" to AreaInfo("home", "Oakdale", "neighbourhood")),
        restPeriods = rest, goalLocked = false,
    )

    private val built = (0 until 112).map { start + it }   // 16 weeks of daily 30-minute walks, slow (no intensity)

    private fun stepOf(w: WorldState) = w.levelFor(w.edges.getValue("e1").wear)

    @Test
    fun missedWeeksFadeAStepAtATimeAndStopTwoStepsDown() {
        val engine = WorldEngine()
        val before = engine.build(input(built), start + 111)
        assertEquals("walked daily: an avenue", 5, stepOf(before))
        val town = before.settlements.single()
        val rows = mutableListOf("missed weeks | street step | shown wear | home tier")
        val byWeeks = (1..13).associateWith { n ->
            // Last walk on day 111 (a Sunday); scorings fall on the following Tuesdays. Row n = n-1 missed weeks.
            engine.build(input(built), start + 111 + 2 + 7 * (n - 1))
        }
        for ((n, w) in byWeeks) rows += "${n - 1} | ${stepOf(w)} | %.1f | ${w.settlements.single().tier}".format(w.edges.getValue("e1").wear)
        println("Cooling with no walking (peak ${town.tier}, avenue wear %.0f):\n".format(before.edges.getValue("e1").wear) + rows.joinToString("\n"))
        fun missed(k: Int) = byWeeks.getValue(k + 1)
        // 0.12.1 partial decay: ¼ step a badly missed week, counted from the best, so a step goes after 4 weeks.
        for (k in 0..3) assertEquals("$k missed weeks: still an avenue", 5, stepOf(missed(k)))
        assertEquals("4 missed weeks: cobbled", 4, stepOf(missed(4)))
        assertEquals("8 missed weeks: lane, the floor", 3, stepOf(missed(8)))
        assertEquals("12 missed weeks: still the floor", 3, stepOf(missed(12)))
        assertEquals(town.tier, missed(3).settlements.single().tier)
        assertEquals("a missed month costs a tier", town.tier.ordinal - 1, missed(4).settlements.single().tier.ordinal)
        assertTrue("never more than two tiers down", missed(12).settlements.single().tier.ordinal >= town.peakTier.ordinal - 2)
        val byWeeks2 = byWeeks
        val scored = byWeeks2.getValue(3).events.filterIsInstance<WorldEvent.WeekScored>().last()
        assertTrue(scored.fadedRoads.isNotEmpty() || scored.loss > 0)
        println(DispatchWriter.write(missed(4), missed(4).today).text)
    }

    @Test
    fun slowWalksCountTowardTheGoal() {
        // 30 slow minutes a day with no watch intensity at all: 210 active minutes a week ≥ 150, nothing cools.
        val w = WorldEngine().build(input(built), start + 111)
        val scorings = w.events.filterIsInstance<WorldEvent.WeekScored>()
        assertTrue(scorings.isNotEmpty())
        assertTrue(scorings.all { it.loss == 0.0 })
    }

    @Test
    fun comingBackRecoversQuickly() {
        val engine = WorldEngine()
        val away = engine.build(input(built), start + 111 + 2 + 7 * 4)          // four missed weeks
        val awayStep = stepOf(away)
        // Back for two weeks: 45 minutes a day (315/week, over 150% of the goal), walking the street again.
        val back = (0 until 14).map { start + 111 + 2 + 7 * 4 + it }
        val returned = engine.build(input(built + back, streetDays = built + back).let { inp ->
            inp.copy(presence = inp.presence.filter { it.day < back.first() } + input(back, minutes = 45.0).presence)
        }, back.last() + 2)
        println("Away 4 weeks: step $awayStep (wear %.1f); back 2 weeks: step ${stepOf(returned)} (wear %.1f)".format(
            away.edges.getValue("e1").wear, returned.edges.getValue("e1").wear))
        assertTrue(stepOf(returned) > awayStep)
    }

    @Test
    fun restModePausesCooling() {
        val engine = WorldEngine()
        val before = engine.build(input(built), start + 111)
        val rested = engine.build(input(built, rest = listOf(start + 112..start + 200)), start + 200)
        assertEquals(before.edges.getValue("e1").wear, rested.edges.getValue("e1").wear, 1e-9)
        assertEquals(before.settlements.single().tier, rested.settlements.single().tier)
    }

    @Test
    fun hexHeatStepsUpByDaysAndCoolsToItsFloor() {
        // 0.12 heat map: 40 walked days on the same hexes → step 4 (30+ days) at every size; then 8 missed weeks.
        val hexes = Cells.disk(Cells.cellAt(40.30, -74.30, Cells.HEAT_RES), 2)
        val days = (0 until 40).map { start + it }
        val inp = input(days).copy(heat = days.flatMap { d -> hexes.map { TroddenCell(it, d) } })
        val w = WorldEngine().build(inp, start + 39)
        val c = hexes.first()
        assertEquals(4, w.levelFor(w.heat.getValue(c)))
        assertEquals(4, w.levelFor(w.heatHood.getValue(Cells.parent(c, Cells.HOOD_RES))))
        assertEquals(4, w.levelFor(w.heatCounty.getValue(Cells.parent(c, Cells.COUNTY_RES))))
        val later = WorldEngine().build(inp, start + 39 + 7 * 9)
        println("hex heat: %.0f after 40 days → %.1f after 8 missed weeks".format(w.heat.getValue(c), later.heat.getValue(c)))
        assertEquals("8 missed weeks: two steps below its best (its floor), never fog", 2, later.levelFor(later.heat.getValue(c)))
    }

    @Test
    fun aHighGoalNeverMakesAGuidelineWeekCool() {
        // ADR 0011: goal locked at 300 (Pathfinder); 200 active minutes a week meets the guideline, so nothing cools,
        // and nothing warms either (that needs the goal). 60 minutes a week does cool.
        val engine = WorldEngine()
        val streetDays = built
        val after = (112 until 112 + 56).map { start + it }
        fun weekOf(minPerDay: Double) = input(built, streetDays = streetDays).let { inp ->
            inp.copy(presence = inp.presence + input(after, minutes = minPerDay).presence, goalLocked = true, lockedGoal = 300)
        }
        val ok = engine.build(weekOf(200.0 / 7), start + 112 + 56)
        val low = engine.build(weekOf(60.0 / 7), start + 112 + 56)
        val okScored = ok.events.filterIsInstance<WorldEvent.WeekScored>().filter { it.day > start + 112 }
        println("goal 300: 200 min weeks → losses ${okScored.map { it.loss }}; 60 min weeks → losses " +
            low.events.filterIsInstance<WorldEvent.WeekScored>().filter { it.day > start + 112 }.map { it.loss })
        assertTrue(okScored.all { it.loss == 0.0 && it.recovery == 0.0 })
        assertTrue(low.events.filterIsInstance<WorldEvent.WeekScored>().filter { it.day > start + 119 }.all { it.loss == 0.25 })
        assertEquals("Pathfinder", ok.goal.band)
    }

    @Test
    fun afterARestTheFirstThreeWeeksAreGentle() {
        // Rest for two weeks, then 12 minutes a day (84 a week): normally an eighth of a step; ramping back, nothing.
        val rest = listOf(start + 112..start + 125)
        val back = (126 until 126 + 28).map { start + it }
        val inp = input(built, rest = rest).let { it.copy(presence = it.presence + input(back, minutes = 12.0).presence) }
        val w = WorldEngine().build(inp, start + 126 + 28)
        val scored = w.events.filterIsInstance<WorldEvent.WeekScored>().filter { it.day > start + 126 }
        println("ramping back after rest: " + scored.map { "%.0f min → %.3f".format(it.minutes, it.loss) })
        assertTrue(scored.take(3).all { it.loss == 0.0 })
    }
}
