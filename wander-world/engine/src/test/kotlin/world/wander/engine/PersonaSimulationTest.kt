package world.wander.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Simulates whole years for a few kinds of people and prints how their worlds turn out.
 * Run with `./gradlew :engine:test --tests '*Persona*' -i` to see the timelines.
 */
class PersonaSimulationTest {
    private val start = LocalDate.of(2026, 1, 5).toEpochDay().toInt()
    private val home = Cells.disk(Cells.cell(40.7357, -74.1724), 4)
    private val park = Cells.disk(Cells.cell(40.7600, -74.2000), 4)
    private val trail = Cells.disk(Cells.cell(40.7900, -74.2400), 4)

    private fun input(presence: List<CellPresence>, intensity: List<DayIntensity>, rest: List<IntRange> = emptyList()) = WorldInput(
        startDay = start, homeAreaId = "home", edges = emptyMap(), coverage = emptyList(),
        presence = presence, intensity = intensity, movement = emptyList(),
        cellArea = home.associateWith { "home" } + park.associateWith { "park" } + trail.associateWith { "trail" },
        areas = mapOf(
            "home" to AreaInfo("home", "Ironbound", null),
            "park" to AreaInfo("park", "Ridgefield", null),
            "trail" to AreaInfo("trail", "Eagle Rock", null),
        ),
        restPeriods = rest,
    )

    private fun visit(cells: List<String>, day: Int, minutes: Double, rnd: Random, spread: Int = 6) =
        (0 until spread).map { CellPresence(cells[rnd.nextInt(cells.size)], day, minutes * 60 / spread, Mode.FOOT) }

    private fun report(name: String, state: WorldState, checkpoints: List<Int>, build: (Int) -> WorldState) {
        println("\n=== $name ===")
        for (d in checkpoints) {
            val s = build(d)
            val line = s.settlements.joinToString("  ") { "${it.name}: ${it.tier.label} (D=%.0f)".format(it.development) }
            println("day %3d | goal %d | %s".format(d - start, s.goal.goal, line))
        }
        println("events: " + state.events.count { it is WorldEvent.TierChanged } + " tier changes")
    }

    @Test
    fun commuterWhoWalksMostDays() {
        val rnd = Random(1)
        val presence = mutableListOf<CellPresence>()
        val intensity = mutableListOf<DayIntensity>()
        for (d in 0 until 365) {
            val day = start + d
            if (rnd.nextDouble() < 0.8) presence += visit(home, day, 25.0, rnd)
            if (d % 7 == 5) presence += visit(park, day, 50.0, rnd)
            intensity += DayIntensity(day, 20.0 + rnd.nextDouble() * 15, 0.0)
        }
        val engine = WorldEngine()
        val final = engine.build(input(presence, intensity), start + 364)
        report("Commuter", final, listOf(7, 30, 90, 180, 364).map { start + it }) { engine.build(input(presence, intensity), it) }
        val capital = final.settlements.first { it.isCapital }
        assertTrue(capital.tier.ordinal >= Tier.CITY.ordinal)
    }

    @Test
    fun injuredForTwoMonthsKeepsTheirWorld() {
        val rnd = Random(2)
        val presence = mutableListOf<CellPresence>()
        val intensity = mutableListOf<DayIntensity>()
        val injury = start + 150..start + 210
        for (d in 0 until 300) {
            val day = start + d
            if (day in injury) continue
            if (d % 2 == 0) presence += visit(home, day, 30.0, rnd)
            if (d % 7 == 6) presence += visit(trail, day, 60.0, rnd)
            intensity += DayIntensity(day, 30.0, 5.0)
        }
        val engine = WorldEngine()
        val withRest = engine.build(input(presence, intensity, listOf(injury)), start + 299)
        val noRest = engine.build(input(presence, intensity), start + 299)
        report("Injured (rest mode on)", withRest, listOf(149, 210, 299).map { start + it }) { engine.build(input(presence, intensity, listOf(injury)), it) }
        report("Injured (no rest mode)", noRest, listOf(149, 210, 299).map { start + it }) { engine.build(input(presence, intensity), it) }
        val before = engine.build(input(presence, intensity), start + 149)
        val after = engine.build(input(presence, intensity), start + 210)
        val capBefore = before.settlements.first { it.isCapital }
        val capAfter = after.settlements.first { it.isCapital }
        // Even without rest mode, two inactive months can't drop the capital more than one tier.
        assertTrue(capBefore.tier.ordinal - capAfter.tier.ordinal <= 1)
    }

    @Test
    fun overtrainerEarnsNoExtraProtection() {
        // No cooling at the goal, and recovery is capped at 150%: training far beyond it buys nothing more.
        assertTrue(WorldEngine.weeklyLoss(1.0) == 0.0 && WorldEngine.weeklyLoss(3.0) == 0.0)
        assertTrue(WorldEngine.weeklyRecovery(3.0) == WorldEngine.weeklyRecovery(1.5))
    }
}
