package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorldEngineTest {
    // A Monday, so calendar weeks line up with day offsets.
    private val start = LocalDate.of(2026, 1, 5).also { check(it.dayOfWeek == DayOfWeek.MONDAY) }.toEpochDay().toInt()

    /** A small neighborhood of real H3 cells around a point, all in one area. */
    private fun neighborhood(lat: Double, lng: Double, areaId: String, k: Int = 3): List<String> =
        Cells.disk(Cells.cell(lat, lng), k)

    private val homeCells = neighborhood(40.7357, -74.1724, "home")
    private val parkCells = neighborhood(40.7600, -74.2000, "park")

    private fun baseInput(
        presence: List<CellPresence>,
        intensity: List<DayIntensity> = emptyList(),
        rest: List<IntRange> = emptyList(),
        locked: Boolean = false,
    ) = WorldInput(
        startDay = start,
        homeAreaId = "home",
        edges = emptyMap(),
        coverage = emptyList(),
        presence = presence,
        intensity = intensity,
        movement = emptyList(),
        cellArea = homeCells.associateWith { "home" } + parkCells.associateWith { "park" },
        areas = mapOf("home" to AreaInfo("home", "Ironbound", "neighbourhood"), "park" to AreaInfo("park", "Ridgefield", "suburb")),
        restPeriods = rest,
        goalLocked = locked,
    )

    /** Walks [minutes] minutes spread over [cellCount] cells of an area on a day. */
    private fun walk(cells: List<String>, day: Int, minutes: Double, cellCount: Int = 4, mode: Mode = Mode.FOOT) =
        (0 until cellCount).map { i -> CellPresence(cells[(day + i) % cells.size], day, minutes * 60 / cellCount, mode) }

    private fun steadyIntensity(days: IntRange, perDay: Double) = days.map { DayIntensity(it, perDay, 0.0) }

    @Test
    fun capitalIsFoundedOnFirstWalkAtHome() {
        val state = WorldEngine().build(baseInput(walk(homeCells, start, 20.0)), start)
        val capital = state.settlements.single()
        assertTrue(capital.isCapital)
        assertEquals(Tier.CAMP, capital.tier)
        assertEquals("Ironbound", capital.name)
    }

    @Test
    fun capitalSitsExactlyAtHome() {
        val home = LatLng(40.7361, -74.1730)
        val state = WorldEngine().build(baseInput(walk(homeCells, start, 20.0)).copy(homePoint = home), start)
        assertEquals(home, state.settlements.single().position)
    }

    @Test
    fun campNeedsThreeFootDaysWithinAMonth() {
        val sparse = (0..2).flatMap { walk(parkCells, start + it * 20, 20.0) } // 20 days apart: too sparse
        assertTrue(WorldEngine().build(baseInput(sparse), start + 50).settlements.isEmpty())

        val three = (0..2).flatMap { walk(parkCells, start + it * 5, 20.0) }
        val s = WorldEngine().build(baseInput(three), start + 20).settlements.single()
        assertEquals("Ridgefield", s.name)
        assertEquals(Tier.CAMP, s.tier)
        assertEquals(start + 10, s.foundedDay)
    }

    @Test
    fun everyAreaWalkedInIsAWaypointEvenWithoutASettlement() {
        val brief = listOf(CellPresence(parkCells[0], start + 2, 90.0, Mode.FOOT)) // 90 s: no area-day
        val state = WorldEngine().build(baseInput(walk(homeCells, start, 20.0) + brief), start + 3)
        val park = state.exploredAreas.single { it.id == "park" }
        assertEquals("Ridgefield", park.name)
        assertTrue(!park.settled)
        assertEquals(start + 2, park.lastVisitDay)
        assertTrue(state.exploredAreas.single { it.id == "home" }.settled)
    }

    @Test
    fun cyclingAloneNeverFoundsASettlement() {
        val rides = (0..10).flatMap { walk(parkCells, start + it, 40.0, mode = Mode.CYCLE) }
        assertTrue(WorldEngine().build(baseInput(rides), start + 15).settlements.isEmpty())
    }

    @Test
    fun regularWalkingGrowsATown() {
        // 4 walks a week in the park area for 20 weeks, covering more cells over time.
        val days = (0 until 140).filter { it % 7 in listOf(0, 2, 4, 5) }.map { start + it }
        val presence = days.flatMap { walk(parkCells, it, 35.0, cellCount = 6) }
        val intensity = steadyIntensity(start..start + 140, 30.0)
        val s = WorldEngine().build(baseInput(presence, intensity), start + 140).settlements.single()
        assertEquals(Tier.TOWN, s.tier)
    }

    @Test
    fun inactivityDecaysButNeverBelowTwoTiersUnderPeak() {
        val days = (0 until 140).filter { it % 7 in listOf(0, 2, 4, 5) }.map { start + it }
        val presence = days.flatMap { walk(parkCells, it, 35.0, cellCount = 6) }
        val intensity = steadyIntensity(start..start + 140, 30.0)
        // Then two years of nothing at all.
        val state = WorldEngine().build(baseInput(presence, intensity), start + 140 + 730)
        val s = state.settlements.single()
        assertEquals(Tier.TOWN, s.peakTier)
        assertEquals(Tier.HAMLET, s.tier) // Town − 2
        assertTrue(s.liveliness < 0.01)
    }

    @Test
    fun meetingTheGoalStopsDecayCompletely() {
        val days = (0 until 60).filter { it % 2 == 0 }.map { start + it }
        val presence = days.flatMap { walk(parkCells, it, 30.0, cellCount = 6) }
        // 25 minutes every day = 175/week > 150 goal, but no more visits to the park after day 60.
        val intensity = steadyIntensity(start..start + 200, 25.0)
        val engine = WorldEngine()
        val atDay60 = engine.build(baseInput(presence, intensity), start + 60).settlements.single()
        val atDay200 = engine.build(baseInput(presence, intensity), start + 200).settlements.single()
        assertEquals(atDay60.tier, atDay200.tier)
        assertTrue(atDay200.development >= atDay60.development - 1e-9)
    }

    @Test
    fun restModePausesDecay() {
        val days = (0 until 60).filter { it % 2 == 0 }.map { start + it }
        val presence = days.flatMap { walk(parkCells, it, 30.0, cellCount = 6) }
        val engine = WorldEngine()
        val before = engine.build(baseInput(presence), start + 60).settlements.single()
        val rested = engine.build(baseInput(presence, rest = listOf(start + 61..start + 120)), start + 120).settlements.single()
        assertEquals(before.development, rested.development, 1e-9)
    }

    @Test
    fun goalRisesSlowlyAndFallsGently() {
        // 6 weeks at 200 min/week (≥ 1.2 × 150), then 3 weeks at 60.
        val high = steadyIntensity(start until start + 42, 200.0 / 7)
        val low = steadyIntensity(start + 42 until start + 63, 60.0 / 7)
        val state = WorldEngine().build(baseInput(emptyList(), high + low), start + 62)
        val changes = state.events.filterIsInstance<WorldEvent.GoalChanged>()
        assertEquals(170, changes.first().to) // +10% = 165, rounded to the nearest 10
        assertEquals(150, state.goal.goal)    // eased back to the floor
    }

    @Test
    fun goalNeverExceedsCeiling() {
        val huge = steadyIntensity(start until start + 7 * 60, 100.0)
        val state = WorldEngine().build(baseInput(emptyList(), huge), start + 7 * 60 - 1)
        assertEquals(300, state.goal.goal)
    }

    @Test
    fun lockedGoalNeverChanges() {
        val high = steadyIntensity(start until start + 70, 40.0)
        val state = WorldEngine().build(baseInput(emptyList(), high, locked = true), start + 69)
        assertEquals(150, state.goal.goal)
        assertTrue(state.events.none { it is WorldEvent.GoalChanged })
    }

    @Test
    fun weeklyLossAndRecoveryBands() {
        // ADR 0008: −30% below half the goal, sliding to −15% at 80%, at most −3% for a near miss, none at the goal.
        assertEquals(0.30, WorldEngine.weeklyLoss(0.0), 1e-9)
        assertEquals(0.30, WorldEngine.weeklyLoss(0.5), 1e-9)
        assertEquals(0.15, WorldEngine.weeklyLoss(0.7999999), 1e-6)
        assertEquals(0.03, WorldEngine.weeklyLoss(0.8), 1e-9)
        assertEquals(0.0, WorldEngine.weeklyLoss(1.0), 1e-9)
        assertEquals(0.0, WorldEngine.weeklyRecovery(1.0), 1e-9)
        assertEquals(0.25, WorldEngine.weeklyRecovery(1.25), 1e-9)
        assertEquals(0.5, WorldEngine.weeklyRecovery(2.0), 1e-9)
    }

    @Test
    fun roadsAreDiscoveredAtSixtyPercentAndWearByDistinctDays() {
        val edge = EdgeInfo("e1", "Grove St", 200.0, 1, 2, LatLng(40.7357, -74.1724))
        val half = EdgeCoverage("e1", start, 0.0, 100.0, Mode.FOOT)
        val more = EdgeCoverage("e1", start + 1, 90.0, 130.0, Mode.FOOT)
        val input = baseInput(emptyList()).copy(edges = mapOf("e1" to edge), coverage = listOf(half))
        assertTrue(WorldEngine().build(input, start).edges.getValue("e1").discovered.not())
        val both = WorldEngine().build(input.copy(coverage = listOf(half, more)), start + 1).edges.getValue("e1")
        assertTrue(both.discovered)
        assertEquals(start + 1, both.discoveredDay)

        // Same edge fully walked on 3 separate days → path level.
        val walks = (0..2).map { EdgeCoverage("e1", start + it, 0.0, 200.0, Mode.FOOT) } +
            (0..5).map { EdgeCoverage("e1", start, 0.0, 200.0, Mode.FOOT) } // laps the same day don't count
        val e = WorldEngine().build(input.copy(coverage = walks), start + 2).edges.getValue("e1")
        assertEquals(3.0, e.wear, 1e-9)
        assertEquals(2, e.level)
    }

    @Test
    fun undiscoveredEdgeHasNoDay() {
        val edge = EdgeInfo("e1", null, 500.0, 1, 2, LatLng(40.7357, -74.1724))
        val input = baseInput(emptyList()).copy(edges = mapOf("e1" to edge), coverage = listOf(EdgeCoverage("e1", start, 0.0, 50.0, Mode.FOOT)))
        assertNull(WorldEngine().build(input, start).edges.getValue("e1").discoveredDay)
    }

    @Test
    fun dispatchMentionsFoundingAndGoal() {
        val state = WorldEngine().build(baseInput(walk(homeCells, start, 20.0)), start)
        val text = DispatchWriter.write(state, start).text
        assertTrue(text, text.contains("Ironbound"))
        assertTrue(text, text.contains("Weekly goal"))
    }
}
