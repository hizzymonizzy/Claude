package world.wander.app.geo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import world.wander.engine.Geo
import world.wander.engine.LatLng

/** Parsing of Overpass responses (made-up places), and the shape of the queries we send. */
class OverpassTest {
    private val tile = "872a1065cffffff"

    /** A closed square ring of about [sideM] metres around a point, as Overpass `geometry`. */
    private fun square(lat: Double, lng: Double, sideM: Double): String {
        val dLat = sideM / 2 / 110_540.0; val dLng = sideM / 2 / (111_320.0 * Math.cos(Math.toRadians(lat)))
        val pts = listOf(lat - dLat to lng - dLng, lat - dLat to lng + dLng, lat + dLat to lng + dLng, lat + dLat to lng - dLng, lat - dLat to lng - dLng)
        return pts.joinToString(",", "[", "]") { (a, b) -> """{"lat":$a,"lon":$b}""" }
    }

    private fun response(vararg elements: String) = """{"elements":[${elements.joinToString(",")}]}"""

    @Test
    fun placesAreCategorizedAndTooSmallOnesDropped() {
        val json = response(
            """{"type":"way","id":1,"tags":{"leisure":"park","name":"Oakdale Green"},"geometry":${square(40.0, -74.0, 150.0)}}""",
            """{"type":"way","id":2,"tags":{"natural":"wood","name":"Elm Woods"},"geometry":${square(40.01, -74.0, 300.0)}}""",
            """{"type":"node","id":3,"lat":40.02,"lon":-74.0,"tags":{"amenity":"library","name":"Oakdale Library"}}""",
            """{"type":"way","id":4,"tags":{"shop":"supermarket","name":"Corner Shop","building":"yes"},"geometry":${square(40.03, -74.0, 20.0)}}""",
            """{"type":"way","id":5,"tags":{"shop":"supermarket","name":"Big Market","building":"yes"},"geometry":${square(40.04, -74.0, 80.0)}}""",
            """{"type":"way","id":6,"tags":{"leisure":"park"},"geometry":${square(40.05, -74.0, 150.0)}}""",
            """{"type":"node","id":7,"lat":40.06,"lon":-74.0,"tags":{"man_made":"lighthouse"}}""",
        )
        val byName = Overpass.parseLandmarks(json, tile).associateBy { it.name }
        assertEquals("park", byName.getValue("Oakdale Green").kind)
        assertEquals("wood", byName.getValue("Elm Woods").kind)
        assertEquals("library", byName.getValue("Oakdale Library").kind)
        assertEquals("n3", byName.getValue("Oakdale Library").id)
        assertTrue("small shops are dropped", "Corner Shop" !in byName)
        assertEquals("store", byName.getValue("Big Market").kind)
        assertEquals("lighthouses keep a name even untagged", "lighthouse", byName.getValue("Lighthouse").kind)
        assertEquals("unnamed places are skipped", 5, byName.size)
        // A point of interest becomes a ~35 m circle you can walk through.
        val ring = Overpass.decodeRings(byName.getValue("Oakdale Library").geometry).single()
        assertTrue(ring.all { Geo.distanceM(it, LatLng(40.02, -74.0)) in 30.0..40.0 })
    }

    @Test
    fun categoriesFromTags() {
        fun cat(vararg kv: Pair<String, String>) = Overpass.categorize(JSONObject(kv.toMap()))
        assertEquals("hospital", cat("amenity" to "clinic"))
        assertEquals("government", cat("office" to "government"))
        assertEquals("emergency", cat("amenity" to "fire_station"))
        assertEquals("transport", cat("railway" to "station"))
        assertEquals("harbour", cat("leisure" to "marina"))
        assertEquals("beach", cat("natural" to "sand"))
        assertEquals("historic", cat("historic" to "memorial"))
        assertEquals("nature_reserve", cat("leisure" to "nature_reserve"))
    }

    @Test
    fun waterAreasNamedOrNotAndRelationsStitched() {
        val a = """{"lat":40.0,"lon":-74.0}"""; val b = """{"lat":40.0,"lon":-73.99}"""
        val c = """{"lat":40.01,"lon":-73.99}"""; val d = """{"lat":40.01,"lon":-74.0}"""
        val json = response(
            """{"type":"way","id":10,"tags":{"natural":"water"},"geometry":${square(40.1, -74.1, 200.0)}}""",
            // A lake as a relation of two open outer ways that meet end to end, plus an island (inner) to ignore.
            """{"type":"relation","id":11,"tags":{"natural":"water","name":"Elm Lake"},"members":[
                {"type":"way","role":"outer","geometry":[$a,$b,$c]},
                {"type":"way","role":"outer","geometry":[$c,$d,$a]},
                {"type":"way","role":"inner","geometry":${square(40.005, -73.995, 50.0)}}]}""",
            """{"type":"way","id":12,"tags":{"leisure":"park","name":"Not Water"},"geometry":${square(40.2, -74.2, 200.0)}}""",
        )
        val water = Overpass.parseWater(json, tile).associateBy { it.id }
        assertEquals(setOf("w10", "r11"), water.keys)
        val lake = Overpass.decodeRings(water.getValue("r11").geometry)
        assertEquals("outer pieces stitched into one ring", 1, lake.size)
        assertEquals(lake[0].first(), lake[0].last())
    }

    @Test
    fun roadsSplitAtIntersections() {
        // Two ways crossing at node 2: way 100 (1-2-3) splits into two edges; way 101 (4-2) stays one.
        val json = response(
            """{"type":"way","id":100,"tags":{"highway":"residential","name":"Elm Ave"},"nodes":[1,2,3],
                "geometry":[{"lat":40.0,"lon":-74.0},{"lat":40.0,"lon":-73.999},{"lat":40.0,"lon":-73.998}]}""",
            """{"type":"way","id":101,"tags":{"highway":"footway"},"nodes":[4,2],
                "geometry":[{"lat":40.001,"lon":-73.999},{"lat":40.0,"lon":-73.999}]}""",
            """{"type":"node","id":200,"lat":40.0,"lon":-74.0,"tags":{"place":"suburb","name":"Oakdale"}}""",
        )
        val data = Overpass.parse(json, tile)
        assertEquals(setOf("w100:1-2", "w100:2-3", "w101:4-2"), data.edges.map { it.id }.toSet())
        assertEquals("Elm Ave", data.edges.first { it.id == "w100:1-2" }.name)
        assertEquals(85.0, data.edges.first { it.id == "w100:1-2" }.lengthM, 2.0)
        assertEquals("Oakdale", data.places.single().name)
    }

    @Test
    fun geometryRoundTrips() {
        val pts = listOf(LatLng(40.123456, -74.654321), LatLng(-33.9, 151.2))
        assertEquals(pts, Overpass.decode(Overpass.encode(pts)))
    }

    @Test
    fun seaFeaturesAreNeverFetchedWithGeometry() {
        // Alpha 0.9.1: a bay's full geometry timed out the server for a whole coastal tile.
        val bbox = "40.0,-74.1,40.1,-74.0"
        val places = Overpass.landmarkQuery(bbox)
        assertFalse(places.contains("coastline"))
        assertFalse(places.contains("bay"))
        assertFalse("no shorthand the server may not support", Regex("""[;(]\s*(nwr|wr)\[""").containsMatchIn(places))
        assertTrue(places.contains("way[\"natural\"=\"water\"](40.0,-74.1,40.1,-74.0)"))
        val coast = Overpass.coastQuery(bbox)
        assertTrue(coast.contains("coastline") && coast.contains("bay"))
        assertTrue("ids only", coast.endsWith("out ids 1;"))
        assertFalse(coast.contains("BBOX"))
    }

    @Test
    fun relationsComeAsBoundingBoxesFirstAndOnlySmallOnesAreFetchedWhole() {
        // Alpha 0.9.2: one river-sized water relation with full geometry kept a tile's places from loading at all.
        val q = Overpass.landmarkQuery("40.0,-74.1,40.1,-74.0")
        val (geomPart, bbPart) = q.split("out body geom;")
        assertFalse("no relation fetched with its outline up front", geomPart.contains("relation["))
        assertTrue(bbPart.contains("relation[\"natural\"=\"water\"]") && bbPart.trimEnd().endsWith("out tags bb;"))
        val els = JSONObject("""{"elements":[
            {"type":"relation","id":1,"bounds":{"minlat":40.0,"minlon":-74.0,"maxlat":40.01,"maxlon":-73.99}},
            {"type":"relation","id":2,"bounds":{"minlat":40.0,"minlon":-74.5,"maxlat":40.3,"maxlon":-74.0}},
            {"type":"way","id":3}]}""").getJSONArray("elements")
        assertEquals(listOf(1L) to 1, Overpass.relationsToFetch(els))
        assertEquals("[out:json][timeout:90];relation(id:1,5);out body geom;", Overpass.relationQuery(listOf(1, 5)))
    }
}
