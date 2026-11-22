package world.wander.app.geo

import org.json.JSONObject
import world.wander.app.data.EdgeEntity
import world.wander.app.data.LandmarkEntity
import world.wander.app.data.WaterAreaEntity
import world.wander.app.data.TerrainAreaEntity
import world.wander.engine.Landmark
import world.wander.app.data.PlaceEntity
import world.wander.engine.Cells
import world.wander.engine.Geo
import world.wander.engine.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches walkable roads and named places for one H3 resolution-7 tile from the OpenStreetMap
 * Overpass API, and splits ways into edges at intersections.
 */
object Overpass {
    /**
     * Only the official OpenStreetMap-backed server (FOSSGIS e.V.). Every request reveals the area you
     * walked in, so unofficial mirrors are deliberately not used. See docs/security.md.
     */
    private val ENDPOINTS = listOf("https://overpass-api.de/api/interpreter")
    private const val USER_AGENT = "WanderWorld-alpha/0.1 (personal fitness map)"

    private const val WALKABLE =
        "^(footway|path|pedestrian|steps|track|residential|living_street|service|unclassified|" +
            "tertiary|tertiary_link|secondary|secondary_link|primary|primary_link|cycleway|bridleway|road)$"
    private const val PLACES = "^(neighbourhood|quarter|suburb|hamlet|village|town|city)$"
    private val UNPAVED = setOf("unpaved", "dirt", "earth", "ground", "grass", "gravel", "fine_gravel", "mud", "sand", "woodchips", "compacted", "pebblestone")

    data class TileData(val edges: List<EdgeEntity>, val places: List<PlaceEntity>)

    fun fetchTile(tile: String): TileData {
        val boundary = Cells.boundary(tile)
        val s = boundary.minOf { it.lat }; val n = boundary.maxOf { it.lat }
        val w = boundary.minOf { it.lng }; val e = boundary.maxOf { it.lng }
        // Places within ~2 km beyond the tile, so cells near the edge still find their neighborhood.
        val pad = 0.02
        val query = """
            [out:json][timeout:90];
            (
              way["highway"~"$WALKABLE"]["access"!~"^(private|no)$"]($s,$w,$n,$e);
              node["place"~"$PLACES"]["name"](${s - pad},${w - pad},${n + pad},${e + pad});
            );
            out body geom;
        """.trimIndent()
        val json = postWithRetry(query)
        return parse(json, tile)
    }

    /** Tries each server once. Throws the last error if all fail; the caller backs off between attempts. */
    private fun postWithRetry(query: String): String {
        val errors = mutableListOf<String>()
        for (endpoint in ENDPOINTS) {
            try {
                return post(endpoint, query)
            } catch (e: Exception) {
                errors += "${URL(endpoint).host}: ${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}"
            }
        }
        throw IllegalStateException(errors.joinToString(" | "))
    }

    /** Named places worth discovering. `nwr` lines are fetched as ways, relations and points. */
    private const val AREA_FILTERS = """
        nwr["leisure"~"^(park|garden|playground|nature_reserve|dog_park|golf_course|common|recreation_ground|marina|stadium|sports_centre)$"]["name"](BBOX);
        nwr["landuse"~"^(recreation_ground|cemetery|forest|village_green|meadow)$"]["name"](BBOX);
        nwr["natural"~"^(beach|wood|wetland|heath|grassland|water|sand)$"]["name"](BBOX);
        nwr["tourism"~"^(zoo|theme_park|attraction|camp_site|museum)$"]["name"](BBOX);
        nwr["amenity"~"^(university|college|school|kindergarten|marketplace|hospital|clinic|townhall|courthouse|police|fire_station|post_office|library|place_of_worship|theatre|arts_centre|cinema|community_centre|bus_station|ferry_terminal)$"]["name"](BBOX);
        nwr["office"="government"]["name"](BBOX);
        nwr["railway"="station"]["name"](BBOX);
        nwr["man_made"="lighthouse"](BBOX);
        nwr["historic"~"^(monument|memorial|castle|fort|ruins|archaeological_site|battlefield|building)$"]["name"](BBOX);
        way["shop"]["name"]["building"](BBOX);
        wr["natural"="water"](BBOX);
        wr["natural"~"^(beach|sand)$"](BBOX);
        wr["landuse"~"^(farmland|orchard|vineyard|allotments)$"](BBOX);
        wr["leisure"="garden"]["garden:type"="community"](BBOX);
        wr["waterway"="riverbank"](BBOX);
        wr["landuse"~"^(reservoir|basin)$"](BBOX);
    """

    /**
     * Sea features only need to tell us the tile is coastal. Their geometry can be enormous (a whole bay or
     * the ocean's coastline), which made the server time out, so we ask for tags only.
     */
    private const val COAST_QUERY =
        "[out:json][timeout:30];way[\"natural\"=\"coastline\"](BBOX);out geom(BBOX);" +
            "(way[\"natural\"~\"^(bay|strait)$\"](BBOX);relation[\"natural\"~\"^(bay|strait)$\"](BBOX););out ids 1;"

    /** Named places, plus every water area (named or not) and whether the tile touches the coast. */
    data class TileExtras(
        val landmarks: List<LandmarkEntity>, val water: List<WaterAreaEntity>, val coastal: Boolean, val skippedLarge: Int = 0,
        val terrain: List<TerrainAreaEntity> = emptyList(),
    )

    /** Points of interest mapped as a single point get a small circle you can walk through. */
    private const val POINT_RADIUS_M = 35.0

    /** Relations whose bounding box is wider than this are skipped: a whole river or bay outline can time out the server. */
    private const val MAX_RELATION_SPAN_M = 6_000.0

    /**
     * Named places in a tile: parks, woods, beaches, lakes, civic buildings, big stores. Small shops are dropped.
     * Ways and points come with their outlines in one request; relations (multi-part areas) come first as
     * bounding boxes only, then a second request fetches outlines for the ones of a sensible size.
     */
    fun fetchLandmarks(tile: String): TileExtras {
        val boundary = Cells.boundary(tile)
        val bbox = "${boundary.minOf { it.lat }},${boundary.minOf { it.lng }},${boundary.maxOf { it.lat }},${boundary.maxOf { it.lng }}"
        val first = JSONObject(postWithRetry(landmarkQuery(bbox))).getJSONArray("elements")
        val (small, skipped) = relationsToFetch(first)
        val elements = org.json.JSONArray()
        for (i in 0 until first.length()) first.getJSONObject(i).let { if (it.getString("type") != "relation") elements.put(it) }
        if (small.isNotEmpty()) {
            Thread.sleep(1_500) // the public server answers 429 to back-to-back requests
            val rel = JSONObject(postWithRetry(relationQuery(small))).getJSONArray("elements")
            for (i in 0 until rel.length()) elements.put(rel.getJSONObject(i))
        }
        // Big parks, woods and reserves (0.12): fetched one at a time instead of skipped with the seas and rivers, so a
        // trail through a large park still finds its wood. If one still times out, its bounding box stands in.
        var bigKept = 0
        for (big in bigGreenRelations(first).take(MAX_BIG_GREEN)) {
            Thread.sleep(1_500)
            val got = try { JSONObject(postWithRetry(relationQuery(listOf(big.getLong("id"))))).getJSONArray("elements") } catch (_: Exception) { null }
            if (got != null && got.length() > 0) { for (i in 0 until got.length()) elements.put(got.getJSONObject(i)) }
            else boundsAsRelation(big)?.let { elements.put(it) }
            bigKept++
        }
        val json = JSONObject().put("elements", elements).toString()
        Thread.sleep(1_500)
        var coast = emptyList<TerrainAreaEntity>()
        val coastal = try {
            val c = postWithRetry(coastQuery(bbox))
            coast = parseCoast(c, tile)
            JSONObject(c).getJSONArray("elements").length() > 0
        } catch (_: Exception) { false }
        return TileExtras(parseLandmarks(json, tile), parseWater(json, tile), coastal, skippedLarge = skipped - bigKept, terrain = parseSand(json, tile) + parseFields(json, tile) + coast)
    }

    private const val MAX_BIG_GREEN = 4

    /** Relations too large for the batch request that are parks, woods or reserves (by their tags). */
    internal fun bigGreenRelations(elements: org.json.JSONArray): List<JSONObject> = (0 until elements.length()).map { elements.getJSONObject(it) }
        .filter { el ->
            val b = el.optJSONObject("bounds")
            el.getString("type") == "relation" && b != null && el.optJSONObject("tags")?.let { categorize(it) in world.wander.engine.Yields.WOOD_KINDS } == true &&
                Geo.distanceM(LatLng(b.getDouble("minlat"), b.getDouble("minlon")), LatLng(b.getDouble("maxlat"), b.getDouble("maxlon"))) > MAX_RELATION_SPAN_M
        }

    /** A relation's bounding box as a one-ring relation, so it parses like any other place (rough, but walkable). */
    internal fun boundsAsRelation(el: JSONObject): JSONObject? {
        val b = el.optJSONObject("bounds") ?: return null
        val s = b.getDouble("minlat"); val w = b.getDouble("minlon"); val n = b.getDouble("maxlat"); val e = b.getDouble("maxlon")
        val ring = org.json.JSONArray()
        for ((la, lo) in listOf(s to w, s to e, n to e, n to w, s to w)) ring.put(JSONObject().put("lat", la).put("lon", lo))
        return JSONObject().put("type", "relation").put("id", el.getLong("id")).put("tags", el.optJSONObject("tags"))
            .put("members", org.json.JSONArray().put(JSONObject().put("type", "way").put("geometry", ring)))
    }

    /** Ids of relations small enough to fetch whole, and how many were too large. */
    internal fun relationsToFetch(elements: org.json.JSONArray): Pair<List<Long>, Int> {
        val ids = mutableListOf<Long>(); var skipped = 0
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.getString("type") != "relation") continue
            val b = el.optJSONObject("bounds") ?: continue
            val span = Geo.distanceM(LatLng(b.getDouble("minlat"), b.getDouble("minlon")), LatLng(b.getDouble("maxlat"), b.getDouble("maxlon")))
            if (span <= MAX_RELATION_SPAN_M) ids += el.getLong("id") else skipped++
        }
        return ids to skipped
    }

    /**
     * The places-and-water query for a tile: points and ways with their outlines, then relations as bounding
     * boxes only (`out tags bb`). `nwr`/`wr` lines are spelled out per element type.
     */
    internal fun landmarkQuery(bbox: String): String {
        val lines = AREA_FILTERS.replace("BBOX", bbox).lines().map { it.trim() }.filter { it.isNotEmpty() }
        val parts = lines.flatMap { l ->
            when {
                l.startsWith("nwr[") -> listOf(l.replaceFirst("nwr[", "way["), l.replaceFirst("nwr[", "relation["), l.replaceFirst("nwr[", "node["))
                l.startsWith("wr[") -> listOf(l.replaceFirst("wr[", "way["), l.replaceFirst("wr[", "relation["))
                else -> listOf(l)
            }
        }
        val (relations, rest) = parts.partition { it.startsWith("relation[") }
        return "[out:json][timeout:90];(" + rest.joinToString("") + ");out body geom;(" + relations.joinToString("") + ");out tags bb;"
    }

    /** Full outlines for the given relations. */
    internal fun relationQuery(ids: List<Long>): String = "[out:json][timeout:90];relation(id:${ids.joinToString(",")});out body geom;"

    /** Sea features only tell us the tile is coastal: ids only, never their (huge) geometry. */
    internal fun coastQuery(bbox: String): String = COAST_QUERY.replace("BBOX", bbox)

    /** Farmland, orchards, vineyards, allotments and community gardens: their edges are food lines inland. */
    internal fun parseFields(json: String, tile: String): List<TerrainAreaEntity> {
        val elements = JSONObject(json).getJSONArray("elements")
        val out = mutableListOf<TerrainAreaEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val t = el.optJSONObject("tags") ?: continue
            val field = t.optString("landuse") in setOf("farmland", "orchard", "vineyard", "allotments") ||
                (t.optString("leisure") == "garden" && t.optString("garden:type") == "community")
            if (!field) continue
            val rings = elementRings(el) ?: continue
            out += TerrainAreaEntity("f${el.getString("type").first()}${el.getLong("id")}", tile, "farm", rings.joinToString("|") { encode(it) })
        }
        return out
    }

    /** Beaches and sand, named or not, as areas to paint (not places to discover). */
    internal fun parseSand(json: String, tile: String): List<TerrainAreaEntity> {
        val elements = JSONObject(json).getJSONArray("elements")
        val out = mutableListOf<TerrainAreaEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val natural = el.optJSONObject("tags")?.optString("natural") ?: continue
            if (natural != "beach" && natural != "sand") continue
            val rings = elementRings(el) ?: continue
            out += TerrainAreaEntity("${el.getString("type").first()}${el.getLong("id")}", tile, "sand", rings.joinToString("|") { encode(it) })
        }
        return out
    }

    /**
     * Coastline ways clipped to the tile (`out geom(bbox)`). Clipping can leave gaps (null points), so each way
     * is split into its unbroken runs. Returned as lines of kind "coast".
     */
    internal fun parseCoast(json: String, tile: String): List<TerrainAreaEntity> {
        val elements = JSONObject(json).getJSONArray("elements")
        val out = mutableListOf<TerrainAreaEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            if (el.getString("type") != "way" || el.optJSONObject("tags")?.optString("natural") != "coastline") continue
            val g = el.optJSONArray("geometry") ?: continue
            val runs = mutableListOf<MutableList<LatLng>>(mutableListOf())
            for (k in 0 until g.length()) {
                val o = g.optJSONObject(k)
                if (o == null) { if (runs.last().isNotEmpty()) runs += mutableListOf<LatLng>(); continue }
                runs.last() += LatLng(o.getDouble("lat"), o.getDouble("lon"))
            }
            val lines = runs.filter { it.size >= 2 }
            if (lines.isEmpty()) continue
            out += TerrainAreaEntity("c${el.getLong("id")}", tile, "coast", lines.joinToString("|") { encode(it) })
        }
        return out
    }

    private fun isWater(t: JSONObject) = t.optString("natural") == "water" ||
        t.optString("waterway") == "riverbank" || t.optString("landuse") in setOf("reservoir", "basin")

    /** Every water area (lakes, ponds, rivers, bays), named or not: where you're paddling. */
    internal fun parseWater(json: String, tile: String): List<WaterAreaEntity> {
        val elements = JSONObject(json).getJSONArray("elements")
        val out = mutableListOf<WaterAreaEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            if (!isWater(tags)) continue
            val rings = elementRings(el) ?: continue
            out += WaterAreaEntity("${el.getString("type").first()}${el.getLong("id")}", tile, rings.joinToString("|") { encode(it) })
        }
        return out
    }

    private fun elementRings(el: JSONObject): List<List<LatLng>>? = when (el.getString("type")) {
        "way" -> {
            val g = el.optJSONArray("geometry")
            val pts = g?.let { (0 until it.length()).map { k -> val o = it.getJSONObject(k); LatLng(o.getDouble("lat"), o.getDouble("lon")) } }
            if (pts != null && pts.size >= 4 && pts.first() == pts.last()) listOf(pts) else null
        }
        "relation" -> {
            val members = el.optJSONArray("members")
            val pieces = members?.let { (0 until it.length()).mapNotNull { m ->
                val mem = it.getJSONObject(m)
                if (mem.optString("type") != "way" || mem.optString("role") == "inner") return@mapNotNull null
                val g = mem.optJSONArray("geometry") ?: return@mapNotNull null
                (0 until g.length()).map { k -> val o = g.getJSONObject(k); LatLng(o.getDouble("lat"), o.getDouble("lon")) }
            } }
            pieces?.let { Landmark.stitch(it) }?.ifEmpty { null }
        }
        else -> null
    }

    /** Our category for a place, from its OpenStreetMap tags. Drives its medallion and legend entry. */
    internal fun categorize(t: JSONObject): String {
        fun tag(k: String) = t.optString(k)
        val amenity = tag("amenity"); val leisure = tag("leisure"); val natural = tag("natural")
        return when {
            amenity in setOf("hospital", "clinic") -> "hospital"
            amenity in setOf("townhall", "courthouse") || tag("office") == "government" -> "government"
            amenity in setOf("police", "fire_station") -> "emergency"
            amenity == "marketplace" -> "market"
            tag("leisure") == "garden" && tag("garden:type") == "community" -> "community_garden"
            amenity in setOf("post_office", "community_centre") -> "civic"
            amenity in setOf("school", "college", "university", "kindergarten") -> "school"
            amenity == "library" -> "library"
            amenity == "place_of_worship" -> "worship"
            amenity in setOf("theatre", "arts_centre", "cinema") || tag("tourism") == "museum" -> "culture"
            leisure in setOf("stadium", "sports_centre") -> "sports"
            amenity in setOf("bus_station", "ferry_terminal") || tag("railway") == "station" -> "transport"
            leisure == "marina" -> "harbour"
            tag("man_made") == "lighthouse" -> "lighthouse"
            tag("historic").isNotBlank() -> "historic"
            natural in setOf("beach", "sand") -> "beach"
            natural == "water" -> "water"
            tag("shop").isNotBlank() -> "store"
            else -> listOf("leisure", "landuse", "natural", "tourism").firstNotNullOfOrNull { k -> tag(k).takeIf { it.isNotBlank() } } ?: "place"
        }
    }

    private fun circle(lat: Double, lng: Double, radiusM: Double): List<LatLng> {
        val dLat = radiusM / 110_540.0
        val dLng = radiusM / (111_320.0 * Math.cos(Math.toRadians(lat)))
        val ring = (0 until 16).map { i -> val a = 2 * Math.PI * i / 16; LatLng(lat + dLat * Math.sin(a), lng + dLng * Math.cos(a)) }
        return ring + ring.first()
    }

    internal fun parseLandmarks(json: String, tile: String): List<LandmarkEntity> {
        val elements = JSONObject(json).getJSONArray("elements")
        val out = mutableListOf<LandmarkEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val kind = categorize(tags)
            val name = tags.optString("name").takeIf { it.isNotBlank() } ?: if (kind == "lighthouse") "Lighthouse" else continue
            val rings: List<List<LatLng>> = when (el.getString("type")) {
                "node" -> listOf(circle(el.getDouble("lat"), el.getDouble("lon"), POINT_RADIUS_M))
                "way" -> {
                    val g = el.optJSONArray("geometry") ?: continue
                    val pts = (0 until g.length()).map { val o = g.getJSONObject(it); LatLng(o.getDouble("lat"), o.getDouble("lon")) }
                    if (pts.size >= 4 && pts.first() == pts.last()) listOf(pts) else continue
                }
                "relation" -> {
                    val members = el.optJSONArray("members") ?: continue
                    val pieces = (0 until members.length()).mapNotNull { m ->
                        val mem = members.getJSONObject(m)
                        if (mem.optString("type") != "way") return@mapNotNull null
                        val g = mem.optJSONArray("geometry") ?: return@mapNotNull null
                        (0 until g.length()).map { val o = g.getJSONObject(it); LatLng(o.getDouble("lat"), o.getDouble("lon")) }
                    }
                    Landmark.stitch(pieces).ifEmpty { continue }
                }
                else -> continue
            }
            val lm = try { Landmark("x", name, kind, rings) } catch (_: Exception) { continue }
            if (kind == "store" && lm.areaM2 < 2_000) continue // big-box stores only
            if (lm.areaM2 < 200) continue
            out += LandmarkEntity(
                id = "${el.getString("type").first()}${el.getLong("id")}",
                name = name, kind = kind,
                geometry = rings.joinToString("|") { encode(it) },
                areaM2 = lm.areaM2, tile = tile,
            )
        }
        return out
    }

    fun decodeRings(s: String): List<List<LatLng>> = s.split('|').filter { it.isNotBlank() }.map { decode(it) }

    private fun post(endpoint: String, query: String): String {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 10_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
        val code = conn.responseCode
        if (code != 200) {
            val host = URL(endpoint).host
            throw IllegalStateException("$host HTTP $code ${conn.responseMessage ?: ""}".trim())
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private class Way(val id: Long, val nodes: List<Long>, val geom: List<LatLng>, val name: String?, val highway: String?)

    internal fun parse(json: String, tile: String): TileData {
        val elements = JSONObject(json).getJSONArray("elements")
        val ways = mutableListOf<Way>()
        val places = mutableListOf<PlaceEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags")
            when (el.getString("type")) {
                "way" -> {
                    val nodesArr = el.optJSONArray("nodes") ?: continue
                    val geomArr = el.optJSONArray("geometry") ?: continue
                    if (nodesArr.length() != geomArr.length() || nodesArr.length() < 2) continue
                    val nodes = (0 until nodesArr.length()).map { nodesArr.getLong(it) }
                    val geom = (0 until geomArr.length()).map {
                        val g = geomArr.getJSONObject(it); LatLng(g.getDouble("lat"), g.getDouble("lon"))
                    }
                    // Unpaved footways count as trails for wood (0.12); the surface rides along in the highway value.
                    val hw = tags?.optString("highway")?.takeIf { it.isNotBlank() }
                    val unpaved = tags?.optString("surface") in UNPAVED
                    ways += Way(el.getLong("id"), nodes, geom, tags?.optString("name")?.takeIf { it.isNotBlank() },
                        if (hw != null && unpaved && hw !in setOf("path", "track", "bridleway")) "$hw|unpaved" else hw)
                }
                "node" -> {
                    val name = tags?.optString("name")?.takeIf { it.isNotBlank() } ?: continue
                    places += PlaceEntity(el.getLong("id"), name, tags.optString("place"), el.getDouble("lat"), el.getDouble("lon"))
                }
            }
        }
        // A node used by more than one way (or twice by one) is an intersection.
        val uses = HashMap<Long, Int>()
        for (w in ways) for (n in w.nodes) uses.merge(n, 1, Int::plus)
        val edges = mutableListOf<EdgeEntity>()
        for (w in ways) {
            var startIdx = 0
            for (i in 1 until w.nodes.size) {
                val isEnd = i == w.nodes.size - 1
                if (isEnd || (uses[w.nodes[i]] ?: 0) > 1) {
                    val pts = w.geom.subList(startIdx, i + 1)
                    val a = w.nodes[startIdx]; val b = w.nodes[i]
                    edges += EdgeEntity(
                        id = "w${w.id}:$a-$b",
                        wayId = w.id,
                        name = w.name,
                        highway = w.highway,
                        startNode = a,
                        endNode = b,
                        lengthM = Geo.lengthM(pts),
                        geometry = encode(pts),
                        tile = tile,
                    )
                    startIdx = i
                }
            }
        }
        return TileData(edges, places)
    }

    fun encode(points: List<LatLng>): String =
        points.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.US, it.lat, it.lng) }

    fun decode(s: String): List<LatLng> =
        s.split(';').map { val (a, b) = it.split(','); LatLng(a.toDouble(), b.toDouble()) }
}
