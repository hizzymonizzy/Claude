package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EconomyTest {
    private val start = LocalDate.of(2026, 1, 5).toEpochDay().toInt()
    private val home = Cells.disk(Cells.cell(40.4100, -74.1500), 3)

    private fun input(days: Int, minutesPerDay: Double, kmPerDay: Double, decisions: List<ProjectDecision> = emptyList()) = WorldInput(
        startDay = start, homeAreaId = "home", edges = emptyMap(), coverage = emptyList(),
        presence = (0 until days).flatMap { d -> (0 until 4).map { CellPresence(home[(d + it) % home.size], start + d, 30 * 60 / 4.0, Mode.FOOT) } },
        intensity = (0 until days).map { DayIntensity(start + it, minutesPerDay, 0.0) },
        movement = (0 until days).map { DayMovement(start + it, kmPerDay * 1000, 0.0) },
        cellArea = home.associateWith { "home" }, areas = mapOf("home" to AreaInfo("home", "Oakdale", null)),
        decisions = decisions,
    )

    @Test
    fun goldComesFromEffortAndDistanceAndTapersAfter90Minutes() {
        assertEquals(30.0 + 2 * 3.0, Yields.gold(30.0, 3.0), 1e-9)
        assertEquals(90.0 + 0.25 * 60, Yields.gold(150.0, 0.0), 1e-9)   // 150 min only earns 105
    }

    @Test
    fun aCapitalProposesACampfireAndApprovingBuildsIt() {
        val first = WorldEngine().build(input(3, 30.0, 3.0), start + 2)
        val proposal = first.proposal
        assertNotNull(proposal)
        assertEquals("campfire", proposal!!.projectId)
        assertTrue(first.treasury.gold >= 20)
        assertTrue(DispatchWriter.write(first, proposal.day).text.contains("asks for a campfire circle"))

        // Not enough wood yet (no woods visited): approval waits.
        val waiting = WorldEngine().build(input(4, 30.0, 3.0, listOf(ProjectDecision(start + 3, "home", "campfire", true))), start + 3)
        assertNotNull(waiting.proposal)
        assertTrue(waiting.settlements.single().projects.isEmpty())
    }

    @Test
    fun decliningRemovesTheProposalForTwoWeeks() {
        val declined = WorldEngine().build(input(5, 30.0, 3.0, listOf(ProjectDecision(start + 3, "home", "campfire", false))), start + 4)
        assertNull(declined.proposal)   // nothing else a Camp can build yet
        // Meanwhile the capital grows into a Hamlet and proposes a well instead; the campfire waits its turn.
        val later = WorldEngine().build(input(20, 30.0, 3.0, listOf(ProjectDecision(start + 3, "home", "campfire", false))), start + 19)
        assertEquals("well", later.proposal?.projectId)
    }

    @Test
    fun aProposalCanBeAnsweredTheDayItAppears() {
        // Woods for wood: a park visit each day gives timber so the campfire becomes affordable.
        val park = LandmarkInfo("park", "Thompson Park", "park", 50_000.0)
        val base = input(4, 30.0, 3.0)
        val withWood = base.copy(
            landmarks = mapOf("park" to park),
            landmarkVisits = (0 until 4).map { LandmarkVisit("park", start + it, 600.0) },
        )
        val first = WorldEngine().build(withWood, start + 3)
        val p = first.proposal!!
        val after = WorldEngine().build(withWood.copy(decisions = listOf(ProjectDecision(start + 3, p.areaId, p.projectId, true))), start + 3)
        assertTrue(after.settlements.single().projects.contains("campfire"))
        assertTrue(after.treasury.gold < first.treasury.gold)
    }

    @Test
    fun milestonesRaiseMonuments() {
        val s = WorldEngine().build(input(12, 30.0, 10.0), start + 11)
        val ids = s.monuments.map { it.id }
        assertTrue(ids.containsAll(listOf("day_5k", "day_10k", "total_100k")))
        assertTrue("day_21k" !in ids)
    }

    @Test
    fun newSourcesPayABonusFavouritesThinAndRestedOnesGrowBack() {
        val h = Harvest()
        assertEquals(2.0, h.take("park", 0, 1.0), 1e-9)          // discovery bonus
        assertEquals(0.9, h.take("park", 1, 1.0), 1e-9)          // thinned once, a day's regrowth
        var last = 0.0
        for (d in 2..20) last = h.take("park", d, 1.0)            // every day: down to the floor
        assertEquals(0.35, last, 1e-9)                            // floor plus a day's regrowth
        assertEquals(0.3 + 0.05 * 8, h.abundance("park", 28), 1e-9)    // eight days' rest
        assertEquals(1.0, h.abundance("park", 60), 1e-9)               // fully grown back
        assertEquals(2.0, h.take("new wood", 20, 1.0), 1e-9)      // somewhere new still pays double
    }

    @Test
    fun aDailyParkYieldsLessThanTheSameTimeSpreadOverNewWoods() {
        val parks = (0 until 10).associate { "p$it" to LandmarkInfo("p$it", "Wood $it", "wood", 50_000.0) }
        val base = input(10, 30.0, 3.0)
        val same = WorldEngine().build(base.copy(landmarks = parks, landmarkVisits = (0 until 10).map { LandmarkVisit("p0", start + it, 600.0) }), start + 9)
        val varied = WorldEngine().build(base.copy(landmarks = parks, landmarkVisits = (0 until 10).map { LandmarkVisit("p$it", start + it, 600.0) }), start + 9)
        assertTrue(varied.treasury.wood > same.treasury.wood * 1.5)
        assertTrue((same.abundance["p0"] ?: 1.0) < 0.5)
        assertTrue(DispatchWriter.write(same, same.events.filterIsInstance<WorldEvent.SourceThinning>().first().day).text.contains("Wood 0 is thinning"))
    }

    @Test
    fun foodComesFromWalkingShoresNotFromStandingAtWater() {
        // Alpha 0.9.7: shores are food lines (like roads for stone). Time at a pond alone gives none.
        val pond = LandmarkInfo("w9", "", "water", 0.0)
        val atPond = WorldEngine().build(input(2, 30.0, 3.0).copy(landmarks = mapOf("w9" to pond), landmarkVisits = listOf(LandmarkVisit("w9", start + 1, 600.0))), start + 1)
        assertTrue(atPond.treasury.food < 0.01)
        assertTrue(atPond.landmarks.isEmpty())
        val cells = (0 until 10).map { TroddenCell("s$it", start + 1) }
        val alongShore = WorldEngine().build(input(2, 30.0, 3.0).copy(shore = cells), start + 1)
        assertEquals(10 * Yields.SHORE_FOOD_PER_CELL * 2, alongShore.treasury.food, 1e-9)   // new shore pays double
    }
}
