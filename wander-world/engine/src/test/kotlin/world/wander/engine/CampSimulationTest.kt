package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Camps by stays (0.15), simulated on invented places: a camp is pitched where you sleep away from home; nights in a
 * row are one stay; stays grow it (1/3/10/30/100); its reach grows with tier and camps whose reaches meet merge.
 */
class CampSimulationTest {
    private val start = LocalDate.of(2026, 3, 2).toEpochDay().toInt()
    private val home = LatLng(40.4100, -74.1500)
    private val homeCells = Cells.disk(Cells.cell(home.lat, home.lng), 2)

    /** The centre of the neighbourhood hex [steps] hexes east-ish of [from]'s hex. */
    private fun hoodAway(from: LatLng, steps: Int): LatLng {
        val c = Cells.cellAt(from.lat, from.lng, Cells.HOOD_RES)
        val ring = Cells.rings(c, steps)[steps]
        return Cells.center(ring.maxBy { Cells.center(it).lng })
    }

    private fun world(nights: List<Arrival>, today: Int) = WorldEngine().build(
        WorldInput(
            startDay = start, homeAreaId = "home", homePoint = home, edges = emptyMap(), coverage = emptyList(),
            presence = homeCells.map { CellPresence(it, start, 600.0, Mode.FOOT) },
            intensity = (start..today).map { DayIntensity(it, 30.0, 0.0) }, movement = emptyList(),
            cellArea = homeCells.associateWith { "home" }, areas = mapOf("home" to AreaInfo("home", "Oakdale", null)),
            nights = nights,
        ),
        today,
    )

    private fun stays(at: LatLng, vararg days: Int) = days.map { Arrival(start + it, at.lat, at.lng) }

    @Test
    fun campsGrowByStaysAndMergeWhenTheirReachesMeet() {
        val x = LatLng(40.6000, -74.3000)          // a cabin well away from home
        val far = LatLng(40.7000, -74.5000)        // somewhere else entirely (~20 km)
        val near = hoodAway(x, 3)                  // 3 neighbourhood hexes from the cabin
        val apart = hoodAway(x, 6)                 // 6 hexes away: its own camp at first

        // A week at the cabin is one stay; two more weekends make three: a Hamlet.
        val week = stays(x, 1, 2, 3, 4, 5, 6, 7)
        val w1 = world(week, start + 10)
        val cabin = w1.settlements.single { !it.isCapital }
        println("a week in a row: ${cabin.name}, ${cabin.tier}, D=${cabin.development}")
        assertEquals(Tier.CAMP, cabin.tier)
        assertEquals(1.0, cabin.development, 1e-9)

        val hamlet = week + stays(x, 14, 15) + stays(x, 21)
        assertEquals(Tier.HAMLET, world(hamlet, start + 22).settlements.single { !it.isCapital }.tier)

        // A Hamlet reaches 3 hexes: a night 3 hexes away counts toward it, not a new camp.
        val withNear = hamlet + stays(near, 28)
        val w2 = world(withNear, start + 29)
        assertEquals(1, w2.settlements.count { !it.isCapital })
        // 6 hexes away is out of reach (3 + 2 < 6): a second camp. Far away: a third.
        val withApart = withNear + stays(apart, 35) + stays(far, 40)
        val w3 = world(withApart, start + 41)
        println("separate spots: " + w3.settlements.filter { !it.isCapital }.map { "${it.name} ${it.tier}" })
        assertEquals(3, w3.settlements.count { !it.isCapital })

        // The cabin grows to a Village (10 stays, reach 4): 4 + 2 ≥ 6, so the second camp joins it, adding its stay.
        val village = withApart + (0 until 6).flatMap { stays(x, 50 + it * 3) }
        val w4 = world(village, start + 70)
        val camps = w4.settlements.filter { !it.isCapital }
        println("after growing: " + camps.map { "${it.name} ${it.tier} D=${it.development}" } +
            " · merged: " + w4.events.filterIsInstance<WorldEvent.CampsMerged>().size)
        assertEquals(2, camps.size)
        assertTrue(w4.events.any { it is WorldEvent.CampsMerged })
        assertEquals(Tier.VILLAGE, camps.maxBy { it.development }.tier)
    }

    @Test
    fun nightsAtHomeNeverMakeACamp() {
        val w = world(stays(home, 1, 2, 3, 8, 9), start + 10)
        assertTrue(w.settlements.all { it.isCapital })
    }

    @Test
    fun aTripAbroadPitchesACampThere() {
        val abroad = LatLng(35.6800, 139.7600)     // a city on another continent
        val w = world(stays(abroad, 20, 21, 22, 23), start + 30)
        val c = w.settlements.single { !it.isCapital }
        assertEquals(Tier.CAMP, c.tier)
        assertTrue(w.regions.size >= 2)            // its own region, away from home
        println("abroad: ${c.name} in region ${c.regionId} of ${w.regions.size}")
    }

    @Test
    fun eachRegionHasACultureYouCanChangeAtTheTrader() {
        val abroad = LatLng(35.6800, 139.7600)
        val base = world(stays(abroad, 20, 21), start + 30)
        val home = base.regions.single { it.isHome }; val away = base.regions.single { !it.isHome }
        assertEquals("britons", home.culture)
        println("default cultures: home ${home.culture}, away ${away.culture}")
        val input = WorldInput(
            startDay = start, homeAreaId = "home", homePoint = this.home, edges = emptyMap(), coverage = emptyList(),
            presence = homeCells.map { CellPresence(it, start, 600.0, Mode.FOOT) }, intensity = emptyList(), movement = emptyList(),
            cellArea = homeCells.associateWith { "home" }, areas = mapOf("home" to AreaInfo("home", "Oakdale", null)),
            nights = stays(abroad, 20, 21),
            purchases = listOf(Purchase(start + 25, Shop.TRADER, Cultures.item(home.id, "romans"), 1, atMs = 1)),
        )
        val w = WorldEngine().build(input, start + 30)
        assertEquals("romans", w.regions.single { it.isHome }.culture)
        assertTrue(w.eventsOn(start + 25).filterIsInstance<WorldEvent.ActionResult>().single().ok)
        assertEquals(WorldEngine().build(input.copy(purchases = emptyList()), start + 30).treasury.gold, w.treasury.gold, 1e-9)   // free
    }
}
