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
        // 0.13: declining the campfire moves on to the Camp's next project; the campfire waits two weeks.
        val declined = WorldEngine().build(input(5, 30.0, 3.0, listOf(ProjectDecision(start + 3, "home", "campfire", false))), start + 4)
        assertEquals("signpost", declined.proposal?.projectId)
        val laterInputs = listOf(ProjectDecision(start + 3, "home", "campfire", false), ProjectDecision(start + 5, "home", "signpost", false),
            ProjectDecision(start + 6, "home", "forager_cache", false), ProjectDecision(start + 7, "home", "lean_to", false))
        val later = WorldEngine().build(input(20, 30.0, 3.0, laterInputs), start + 15)
        assertTrue(later.proposal?.projectId != "campfire")   // still within its two weeks
        val after = WorldEngine().build(input(20, 30.0, 3.0, laterInputs), start + 18)
        assertTrue(after.proposal != null)
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

    @Test
    fun shopPricesClimbWeeklyAndResetOnMonday() {
        assertEquals(10, Shop.price("wood", 1.0, 0))
        assertEquals(20, Shop.price("wood", Shop.TRADER_MULTIPLIER, 0))
        assertEquals(Math.ceil(10 + 11.0 + 12.1).toInt(), Shop.price("wood", 1.0, 0, 3))
        assertEquals(11, Shop.price("wood", 1.0, 1))                   // second unit this week
        // 2026-11-23 is a Monday: a new week starts there.
        val sunday = java.time.LocalDate.of(2026, 11, 22).toEpochDay().toInt()
        assertEquals(Shop.week(sunday) + 1, Shop.week(sunday + 1))
        assertEquals(Shop.week(sunday + 1), Shop.week(sunday + 7))
    }

    @Test
    fun upgradesNeedTheirBaseAndTheMapShowsTheLatest() {
        assertEquals(listOf("meeting_hall", "well"), Projects.shown(listOf("campfire", "meeting_hall", "well")))
        assertTrue(Projects.hasChain(listOf("campfire", "meeting_hall"), "campfire"))
        assertTrue(!Projects.hasChain(listOf("well"), "campfire"))
        assertEquals("campfire", Projects.byId("meeting_hall")?.upgrades)
    }

    @Test
    fun aWellMakesThinnedSourcesRegrowFaster() {
        val plain = Harvest(); val boosted = Harvest(regrowth = { 1.5 })
        for (h in listOf(plain, boosted)) { h.take("l:p", 0, 1.0); for (d in 1..10) h.take("l:p", d, 1.0) }
        assertTrue(boosted.abundance("l:p", 15) > plain.abundance("l:p", 15))
    }

    @Test
    fun todaysTapsRunInTheOrderYouMadeThemAndEachSeesTheOneBefore() {
        // 0.14.1 (field bug 2026-11-24): an approval tapped after buying what it needs must build, on the same day.
        val base = input(6, 60.0, 4.0)
        val today = start + 5
        val w0 = WorldEngine().build(base, today)
        val p = w0.proposal!!
        assertEquals("campfire", p.projectId)
        assertTrue(w0.treasury.wood < 5 && w0.treasury.gold > 150)
        val buyThenApprove = base.copy(
            purchases = listOf(Purchase(today, Shop.TRADER, "wood", 5, atMs = 1)),
            decisions = listOf(ProjectDecision(today, p.areaId, p.projectId, true, atMs = 2)),
        )
        val w1 = WorldEngine().build(buyThenApprove, today)
        val results = w1.eventsOn(today).filterIsInstance<WorldEvent.ActionResult>()
        println("buy then approve: " + results.map { "${it.atMs}: ${it.ok} ${it.message}" })
        assertEquals(listOf(true, true), results.map { it.ok })
        assertTrue(w1.settlements.single().projects.contains("campfire"))
        // Tapped the other way round, the approval comes first and says what's missing.
        val approveThenBuy = buyThenApprove.copy(decisions = listOf(ProjectDecision(today, p.areaId, p.projectId, true, atMs = 0)))
        val w2 = WorldEngine().build(approveThenBuy, today)
        val first = w2.eventsOn(today).filterIsInstance<WorldEvent.ActionResult>().first()
        println("approve first: ${first.message}")
        assertTrue(!first.ok && first.message.contains("wood 0 of 5"))
        assertTrue(w2.settlements.single().projects.isEmpty())
    }

    @Test
    fun theTradersRequestCountsAsDoneAndItsRewardIsThereForTheNextTap() {
        val park = LandmarkInfo("park", "Oakdale Park", "park", 50_000.0)
        val base = input(8, 30.0, 3.0).copy(landmarks = mapOf("park" to park), landmarkVisits = (0 until 8).map { LandmarkVisit("park", start + it, 600.0) })
        val today = start + 7
        val r = WorldEngine().build(base, today).request!!
        val w = WorldEngine().build(base.copy(purchases = listOf(Purchase(today, "request", "request", 1, atMs = 5))), today)
        val res = w.eventsOn(today).filterIsInstance<WorldEvent.ActionResult>().single()
        println("request ${r.qty} ${r.resource} -> ${r.rewardQty} ${r.rewardResource}: ${res.ok} ${res.message}")
        assertEquals(res.ok, w.request!!.done)
    }
}
