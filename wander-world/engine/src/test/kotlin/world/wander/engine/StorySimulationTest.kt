package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The development story (0.13) over an invented season: encounters, the trader's request, achievements, the ladder. */
class StorySimulationTest {
    private val start = LocalDate.of(2026, 1, 5).also { check(it.dayOfWeek == DayOfWeek.MONDAY) }.toEpochDay().toInt()
    private val home = Cells.disk(Cells.cell(40.30, -74.30), 3)

    /** 90 days, walking 5 of 7 days for 30 minutes and ~2.5 km, with 40 new heat hexes each walk (exploring). */
    private fun season(decisions: List<ProjectDecision> = emptyList(), purchases: List<Purchase> = emptyList(), intensity: Double = 0.0): WorldInput {
        val days = (0 until 90).filter { it % 7 < 5 }.map { start + it }
        var hexSeed = Cells.cellAt(40.30, -74.30, Cells.HEAT_RES)
        val heat = days.flatMap { d -> Cells.rings(hexSeed, 4)[4].take(40).map { TroddenCell(it, d) }.also { hexSeed = it.last().cell } }
        return WorldInput(
            startDay = start, homeAreaId = "home", edges = emptyMap(), coverage = emptyList(),
            presence = days.flatMap { d -> (0 until 4).map { i -> CellPresence(home[(d + i) % home.size], d, 450.0, Mode.FOOT) } },
            intensity = days.map { DayIntensity(it, intensity, 0.0) }, movement = days.map { DayMovement(it, 2500.0, 0.0) },
            cellArea = home.associateWith { "home" }, areas = mapOf("home" to AreaInfo("home", "Oakdale", null)),
            heat = heat, decisions = decisions, purchases = purchases,
        )
    }

    @Test
    fun encountersComeAboutWeeklyAndNeverTakeAnything() {
        val w = WorldEngine().build(season(), start + 89)
        val appeared = w.events.filterIsInstance<WorldEvent.EncounterAppeared>()
        val done = w.events.filterIsInstance<WorldEvent.EncounterDone>()
        val gone = w.events.filterIsInstance<WorldEvent.EncounterGone>()
        println("90 days: ${appeared.size} encounters (${appeared.groupingBy { it.encounter.kind }.eachCount()}), ${done.size} done, ${gone.size} left; " +
            "achievements ${w.events.filterIsInstance<WorldEvent.AchievementEarned>().map { "${it.name} ${it.tier}" }}; looks ${w.owned}")
        assertTrue("about one a week or a little more", appeared.size in 6..30)
        assertTrue(done.any { it.encounter.kind == "bard" })     // exploring walks finish the bard's ask
        assertTrue(w.request != null)
        assertTrue(w.achievements.single { it.id == "explorer" }.tier >= 1)
    }

    @Test
    fun aPeddlersTradeGivesARareLookAndTheTradersRequestPays() {
        val first = WorldEngine().build(season(), start + 89)
        val peddler = first.events.filterIsInstance<WorldEvent.EncounterAppeared>().firstOrNull { it.encounter.kind == "peddler" }
        val req = WorldEngine().build(season(), start + 7).request!!
        println("trader request week 2: ${req.qty} ${req.resource} → ${req.rewardQty} ${req.rewardResource}")
        if (peddler != null) {
            // Give the wood and stone it asks for by the trade day via the trader (2× price), then trade.
            val d = peddler.day + 1
            val buys = listOf(Purchase(d, Shop.TRADER, "wood", 10), Purchase(d, Shop.TRADER, "stone", 10), Purchase(d, "encounter", "enc:${peddler.encounter.id}"))
            // Not enough gold for both at the trader's price: refused, and nothing is taken.
            val poor = WorldEngine().build(season(purchases = buys), d)
            assertTrue(poor.events.any { it is WorldEvent.PurchaseRefused && it.day == d && it.item == "enc:${peddler.encounter.id}" })
            assertTrue("outfit_patchwork" !in poor.owned)
            // With intensity minutes (more gold) the same plan works and the cloak is yours.
            val rich = WorldEngine().build(season(purchases = buys, intensity = 60.0), d)
            println("peddler on day ${peddler.day - start}: poor ${poor.eventsOn(d).filterIsInstance<WorldEvent.PurchaseRefused>().map { it.item }}; rich owns ${rich.owned}")
            assertTrue("outfit_patchwork" in rich.owned)
        }
    }

    @Test
    fun aCampHasSeveralThingsToBuildInStoryOrder() {
        val w = WorldEngine().build(season(), start + 2)
        assertEquals("campfire", w.proposal?.projectId)
        val names = Projects.ALL.filter { it.minTier == Tier.CAMP }.map { it.id }
        assertEquals(listOf("campfire", "signpost", "forager_cache", "lean_to"), names)
        assertTrue(Projects.ALL.all { it.perk.isNotBlank() })
    }
}
