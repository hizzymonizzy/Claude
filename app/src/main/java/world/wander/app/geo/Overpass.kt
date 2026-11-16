package world.wander.app.geo

import org.json.JSONObject
import world.wander.app.data.EdgeEntity
import world.wander.app.data.LandmarkEntity
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

    private const val AREA_FILTERS = """
        nwr["leisure"~"^(park|garden|playground|nature_reserve|dog_park|golf_course|common|recreation_ground|marina)$"]["name"](BBOX);
        nwr["landuse"~"^(recreation_ground|cemetery|forest|village_green|meadow)$"]["name"](BBOX);
        nwr["natural"~"^(beach|wood|wetland|heath|grassland)$"]["name"](BBOX);
        nwr["tourism"~"^(zoo|theme_park|attraction|camp_site)$"]["name"](BBOX);
        nwr["amenity"~"^(university|college|marketplace)$"]["name"](BBOX);
        way["shop"]["name"]["building"](BBOX);
    """

    /** Named areas in a tile: parks, woods, beaches, campuses, big stores. Small shops are dropped. */
    fun fetchLandmarks(tile: String): List<LandmarkEntity> {
        val boundary = Cells.boundary(tile)
        val bbox = "${boundary.minOf { it.lat }},${boundary.minOf { it.lng }},${boundary.maxOf { it.lat }},${boundary.maxOf { it.lng }}"
        val query = "[out:json][timeout:90];(" + AREA_FILTERS.replace("BBOX", bbox).replace("nwr[", "way[") +
            AREA_FILTERS.replace("BBOX", bbox).lines().filter { it.trim().startsWith("nwr[") }.joinToString("\n") { it.replace("nwr[", "relation[") } +
            ");out body geom;"
        return parseLandmarks(postWithRetry(query), tile)
    }

    internal fun parseLandmarks(json: String, tile: String): List<LandmarkEntity> {
        val elements = JSONObject(json).getJSONArray("elements")
        val out = mutableListOf<LandmarkEntity>()
        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            val name = tags.optString("name").takeIf { it.isNotBlank() } ?: continue
            val kind = listOf("leisure", "landuse", "natural", "tourism", "amenity", "shop").firstNotNullOfOrNull { k ->
                tags.optString(k).takeIf { it.isNotBlank() }?.let { if (k == "shop") "store" else it }
            } ?: "place"
            val rings: List<List<LatLng>> = when (el.getString("type")) {
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
                    ways += Way(el.getLong("id"), nodes, geom, tags?.optString("name")?.takeIf { it.isNotBlank() }, tags?.optString("highway"))
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
