package world.wander.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Polygon
import world.wander.app.data.EdgeEntity
import world.wander.app.data.MapLayers
import world.wander.app.geo.Overpass
import world.wander.engine.AreaState
import world.wander.engine.EdgeState
import world.wander.engine.GoalState
import world.wander.engine.Landmark
import world.wander.engine.LatLng
import world.wander.engine.LocalProjection
import world.wander.engine.MonumentState
import world.wander.engine.Protection
import world.wander.engine.SettlementState
import world.wander.engine.Tier
import world.wander.engine.WaystoneState
import world.wander.engine.WorldState

/** The GeoJSON the map draws: where fog clears, and which marker each thing gets. */
class MapLayersBuilderTest {
    private val origin = LatLng(40.0, -74.0)
    private val proj = LocalProjection(origin)
    private fun at(xM: Double, yM: Double) = proj.toLatLng(xM, yM)

    /** Is this point under fog? Even-odd over all rings of all fog polygons (the world with explored holes). */
    private fun fogged(fog: FeatureCollection, p: LatLng): Boolean = fog.features()!!.any { f ->
        val rings = (f.geometry() as Polygon).coordinates().map { r -> r.map { LatLng(it.latitude(), it.longitude()) } }
        Landmark("f", "", "", rings).contains(p.lat, p.lng)
    }

    private fun edge(id: String, from: LatLng, to: LatLng) = EdgeEntity(id, 1, "Elm Ave", "residential", 1, 2, 300.0, Overpass.encode(listOf(from, to)), "t")
    private fun edgeState(id: String, level: Int) = EdgeState(id, "Elm Ave", true, 0, level.toDouble(), level, 300.0, 300.0)

    private fun world(
        settlements: List<SettlementState> = emptyList(), waystones: List<WaystoneState> = emptyList(),
        areas: List<AreaState> = emptyList(), monuments: List<MonumentState> = emptyList(),
    ) = WorldState(
        today = 0, edges = emptyMap(), cellFamiliarity = emptyMap(), settlements = settlements,
        goal = GoalState(150, false, 0.0, 0.0, Protection.GRACE, emptyList()), events = emptyList(), days = emptyMap(),
        areaNames = emptyMap(), waystones = waystones, exploredAreas = areas, monuments = monuments,
    )

    private fun settlement(capital: Boolean, tier: Tier = Tier.HAMLET) = SettlementState(
        "a", "Oakdale", capital, tier, tier, 10.0, 10.0, 0, 0, 10, 3, 1.0, origin,
    )

    @Test
    fun fogClearsAlongWalkedRoadsAndWornRoadsClearWider() {
        val layers = MapLayers(
            discovered = listOf(
                edge("faint", at(0.0, 0.0), at(300.0, 0.0)) to edgeState("faint", 1),
                edge("avenue", at(0.0, 1000.0), at(300.0, 1000.0)) to edgeState("avenue", 5),
            ),
            peek = emptyList(),
        )
        val (fog, explored) = MapLayersBuilder.fogAndExplored(layers)
        assertTrue(explored.features()!!.isNotEmpty())
        assertFalse("on the road", fogged(fog, at(150.0, 0.0)))
        assertFalse("within 40 m of a faint trail", fogged(fog, at(150.0, 35.0)))
        assertTrue("beyond a faint trail's reach", fogged(fog, at(150.0, 70.0)))
        assertFalse("an avenue clears 130 m", fogged(fog, at(150.0, 1100.0)))
        assertTrue("far from everything", fogged(fog, at(5000.0, 5000.0)))
    }

    @Test
    fun aSmallPocketInsideAWalkedLoopIsFilledIn() {
        // A 200 m square loop: the 120 m × 120 m middle (≈1.4 ha) is enclosed and below 2 ha, so it clears.
        val c = listOf(at(0.0, 0.0), at(200.0, 0.0), at(200.0, 200.0), at(0.0, 200.0), at(0.0, 0.0))
        val layers = MapLayers(discovered = emptyList(), peek = emptyList(), trails = listOf(c))
        val (fog, _) = MapLayersBuilder.fogAndExplored(layers)
        assertFalse(fogged(fog, at(100.0, 100.0)))
    }

    @Test
    fun aWaystoneSitsInASmallClearing() {
        val ws = at(3000.0, 3000.0)
        val (fog, _) = MapLayersBuilder.fogAndExplored(MapLayers(emptyList(), emptyList()), clearings = listOf(ws))
        assertFalse(fogged(fog, ws))
        assertFalse(fogged(fog, at(3030.0, 3000.0)))
        assertTrue(fogged(fog, at(3100.0, 3000.0)))
    }

    @Test
    fun theCapitalGetsOneMedallionWithItsBadgesBakedIn() {
        fun icon(w: WorldState) = MapLayersBuilder.settlements(w).features()!!.single().getStringProperty("icon")
        assertEquals("medal_tier_2", icon(world(listOf(settlement(capital = false)))))
        assertEquals("medal_tier_2_cap", icon(world(listOf(settlement(capital = true)))))
        val withMonument = world(listOf(settlement(capital = true)), monuments = listOf(MonumentState("day_5k", "x", "y", 0)))
        assertEquals("medal_tier_2_capmon", icon(withMonument))
        // Every icon the map asks for exists (base images, plus the capital variants made at load time).
        for (t in 0..6) assertTrue(MapIcons.images.containsKey("medal_tier_$t"))
    }

    @Test
    fun waypointsOnlyForUnsettledAreasAndWaystonesLabelled() {
        val areas = listOf(
            AreaState("a", "Oakdale", origin, 3, 5, 0, settled = true),
            AreaState("b", "Elm Park", at(800.0, 0.0), 1, 1, 0, settled = false),
        )
        val wps = MapLayersBuilder.waypoints(world(areas = areas)).features()!!
        assertEquals(listOf("Elm Park"), wps.map { it.getStringProperty("name") })
        val stones = MapLayersBuilder.waystones(world(waystones = listOf(
            WaystoneState(1, at(5000.0, 0.0), great = false, regionId = 0, foundDay = 0, trips = 1),
            WaystoneState(2, at(9000.0, 0.0), great = true, regionId = 1, foundDay = 0, trips = 1),
        ))).features()!!
        assertEquals(listOf("medal_waystone", "medal_waystone_great"), stones.map { it.getStringProperty("icon") })
        stones.forEach { assertTrue(MapIcons.images.containsKey(it.getStringProperty("icon"))) }
    }

    @Test
    fun everyPlaceKindHasAMedallion() {
        val kinds = listOf("park", "wood", "beach", "water", "store", "hospital", "government", "emergency", "civic",
            "school", "library", "worship", "culture", "sports", "transport", "harbour", "lighthouse", "historic", "zoo")
        for (k in kinds) assertTrue(k, MapIcons.images.containsKey(MapIcons.placeIcon(k)))
    }

    @Test
    fun resourcePointersArePinnedMapPointsWithKnownIcons() {
        // Alpha 0.9.4: they were a screen overlay that didn't follow the map; now they're map features.
        val targets = listOf("wood", "food", "stone", "metal").map {
            world.wander.engine.ResourceTarget(it, "x", at(100.0, 0.0), 100.0)
        }
        val fc = MapLayersBuilder.resources(targets).features()!!
        assertEquals(4, fc.size)
        fc.forEach { assertTrue(MapIcons.images.containsKey(it.getStringProperty("icon"))) }
        val p = fc.first().geometry() as org.maplibre.geojson.Point
        assertEquals(at(100.0, 0.0).lng, p.longitude(), 1e-9)
    }

    @Test
    fun villagesShowTheirBuildingsAMarketToTapAndAPlazaOnExploredGroundOnly() {
        // Alpha 0.11: buildings drawn at the settlement; upgrades replace what they upgrade; plaza clipped to explored.
        val town = settlement(capital = true, tier = Tier.VILLAGE).copy(projects = listOf("campfire", "well", "market", "meeting_hall"))
        val w = world(listOf(town))
        val bldgs = MapLayersBuilder.villageBuildings(w).features()!!
        val icons = bldgs.map { it.getStringProperty("icon") }.toSet()
        assertEquals(setOf("bldg_meeting_hall", "bldg_well", "bldg_market"), icons)
        icons.forEach { assertTrue(it, MapIcons.images.containsKey(it)) }
        assertEquals("market:a", bldgs.single { it.getStringProperty("icon") == "bldg_market" }.getStringProperty("id"))
        assertEquals("medal_tier_3_cap_bcrimson", MapLayersBuilder.settlements(w, "crimson").features()!!.single().getStringProperty("icon"))
        // Explored only on one side (x < 0): the plaza is cut at the edge of the green.
        val half = org.locationtech.jts.geom.GeometryFactory().createPolygon(listOf(at(-500.0, -500.0), at(0.0, -500.0), at(0.0, 500.0), at(-500.0, 500.0), at(-500.0, -500.0))
            .map { org.locationtech.jts.geom.Coordinate(it.lng, it.lat) }.toTypedArray())
        val (plaza, gardens, _) = MapLayersBuilder.village(w, half)
        assertTrue(plaza.features()!!.isNotEmpty())
        val maxLng = plaza.features()!!.flatMap { (it.geometry() as org.maplibre.geojson.Polygon).coordinates().flatten() }.maxOf { it.longitude() }
        assertTrue("plaza stays on explored ground", maxLng <= origin.lng + 1e-7)
        assertTrue(gardens.features()!!.isNotEmpty())
        assertTrue(MapLayersBuilder.village(w, null).first.features()!!.isEmpty())
    }
}
