package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegionsTest {
    private val start = LocalDate.of(2026, 1, 5).toEpochDay().toInt()
    private val homePt = LatLng(40.4100, -74.1500)        // New Jersey
    private val parkPt = LatLng(40.5500, -74.3500)        // ~22 km away: same region
    private val honoluluPt = LatLng(21.2850, -157.8350)   // ~8,000 km away
    private val home = Cells.disk(Cells.cell(homePt.lat, homePt.lng), 3)
    private val park = Cells.disk(Cells.cell(parkPt.lat, parkPt.lng), 3)
    private val hnl = Cells.disk(Cells.cell(honoluluPt.lat, honoluluPt.lng), 3)

    private fun walk(cells: List<String>, day: Int, minutes: Double = 30.0) =
        (0 until 4).map { CellPresence(cells[(day + it) % cells.size], day, minutes * 60 / 4, Mode.FOOT) }

    private fun input(presence: List<CellPresence>, arrivals: List<Arrival>, intensity: List<DayIntensity> = emptyList()) = WorldInput(
        startDay = start, homeAreaId = "home", edges = emptyMap(), coverage = emptyList(),
        presence = presence, intensity = intensity, movement = emptyList(),
        cellArea = home.associateWith { "home" } + park.associateWith { "park" } + hnl.associateWith { "hnl" },
        areas = mapOf(
            "home" to AreaInfo("home", "North Middletown", null),
            "park" to AreaInfo("park", "Holmdel", null),
            "hnl" to AreaInfo("hnl", "Waikīkī", null),
        ),
        arrivals = arrivals,
    )

    /** Home for a month, fly to Honolulu for a week, fly home, then never go back. */
    private fun trip(): WorldInput {
        val presence = (0 until 30).flatMap { walk(home, start + it) } +
            (31..37).flatMap { walk(hnl, start + it) } +
            (39 until 200).flatMap { walk(home, start + it) }
        val arrivals = listOf(
            Arrival(start + 31, honoluluPt.lat, honoluluPt.lng),   // landed in Honolulu
            Arrival(start + 39, homePt.lat, homePt.lng),           // back home: known ground
        )
        return input(presence, arrivals)
    }

    @Test
    fun aFlightFoundsANewRegionWithAGreatWaystoneAndPortal() {
        val s = WorldEngine().build(trip(), start + 40)
        assertEquals(2, s.regions.size)
        val homeRegion = s.regions.single { it.isHome }
        val hnlRegion = s.regions.single { !it.isHome }
        assertEquals("North Middletown", homeRegion.name)
        assertEquals("Waikīkī", hnlRegion.name)
        val ws = s.waystones.single()
        assertTrue(ws.great)
        assertEquals(hnlRegion.id, ws.regionId)
        assertEquals(PortalLink(homeRegion.id, hnlRegion.id, 1), s.portals.single())
        // Flying home onto explored ground finds no new waystone.
        assertTrue(s.events.none { it is WorldEvent.WaystoneFound && it.day == start + 39 })
        assertTrue(DispatchWriter.write(s, start + 31).text.contains("A new land: Waikīkī"))
    }

    @Test
    fun distantRegionGoesDormantAndBarelyDecays() {
        val s = WorldEngine().build(trip(), start + 199)
        val hnl = s.settlements.single { it.areaId == "hnl" }
        assertTrue(s.regions.single { !it.isHome }.dormant)
        assertTrue(hnl.dormant)
        // ~160 days with no exercise logged anywhere. Compare with the same trip if dormancy didn't exist.
        val awake = WorldEngine(WorldParams(dormantDecay = 1.0)).build(trip(), start + 199).settlements.single { it.areaId == "hnl" }
        assertTrue("dormant D=${hnl.development} vs awake D=${awake.development}", hnl.development > awake.development * 2.5)
        assertTrue(hnl.development > hnl.peakDevelopment * 0.6)
    }

    @Test
    fun returningWakesARegion() {
        val base = trip()
        val back = base.copy(
            presence = base.presence + (220..222).flatMap { walk(hnl, start + it) },
            arrivals = base.arrivals + Arrival(start + 220, honoluluPt.lat, honoluluPt.lng),
        )
        val s = WorldEngine().build(back, start + 222)
        assertTrue(!s.regions.single { !it.isHome }.dormant)
        assertTrue(s.events.any { it is WorldEvent.RegionWoke && it.day == start + 220 })
        assertEquals(2, s.waystones.single().trips)
        assertEquals(2, s.portals.single().trips)
        assertTrue(DispatchWriter.write(s, start + 220).text.contains("Waikīkī stirs"))
    }

    @Test
    fun aDriveWithinTheRegionFindsALesserWaystone() {
        val presence = (0 until 10).flatMap { walk(home, start + it) } + walk(park, start + 12)
        val s = WorldEngine().build(input(presence, listOf(Arrival(start + 12, parkPt.lat, parkPt.lng))), start + 12)
        assertEquals(1, s.regions.size)
        val ws = s.waystones.single()
        assertTrue(!ws.great)
        assertTrue(s.portals.isEmpty())
    }
}
