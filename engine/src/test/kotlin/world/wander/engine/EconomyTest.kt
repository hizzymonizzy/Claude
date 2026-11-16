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
        cellArea = home.associateWith { "home" }, areas = mapOf("home" to AreaInfo("home", "North Middletown", null)),
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
}
