package world.wander.app.ui

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.prep.PreparedGeometry
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import world.wander.app.data.MapLayers
import world.wander.engine.Cells
import world.wander.engine.LatLng
import world.wander.engine.WorldState

/**
 * The heat map (ADR 0007): hexes at three sizes, each drawn with its ground's texture at one of five heat steps
 * (1, 3, 10, 30, 100 distinct days, the road stages). Hexes of the same ground and step have no outline, so they
 * merge; the texture change is the edge. Settlements are paved hexes that spread with tier. Roads stay as they are,
 * drawn on top. Below county zoom, regions become nodes.
 */
object HeatLayers {
    private val gf = GeometryFactory()
    /** Ground types with a texture, in the order they're checked (first match wins). */
    val MATERIALS = listOf("town", "sand", "forest", "grass")
    val TEXTURES = mapOf("town" to "road_cobble", "sand" to "ground_sand", "forest" to "ground_forest", "grass" to "ground_grass")

    /** Paved rings around a settlement by tier (ruins, camp, hamlet, village, town, city, metropolis), per hex size. */
    private val TOWN_K_BLOCK = intArrayOf(0, 0, 1, 2, 3, 4, 5)
    private val TOWN_K_HOOD = intArrayOf(-1, -1, 0, 0, 1, 2, 3)
    private val TOWN_K_COUNTY = intArrayOf(-1, -1, -1, 0, 1, 2, 2)

    /** Where wooded and sandy ground is, for picking each hex's texture. */
    class Grounds(val forest: PreparedGeometry?, val sand: PreparedGeometry?)

    fun grounds(layers: MapLayers, kinds: Map<String, String>): Grounds {
        fun union(rings: List<List<List<LatLng>>>): PreparedGeometry? {
            val gs = rings.mapNotNull { MapLayersBuilder.placePolygon(it) }
            if (gs.isEmpty()) return null
            return try { PreparedGeometryFactory.prepare(org.locationtech.jts.operation.union.UnaryUnionOp.union(gs)) } catch (_: Exception) { null }
        }
        val wooded = layers.landmarks.filter { kinds[it.first] in MapIcons.WOODED || kinds[it.first] in MapIcons.GREEN }.map { it.second }
        val sandy = layers.landmarks.filter { kinds[it.first] == "beach" }.map { it.second } + layers.sand
        return Grounds(union(wooded), union(sandy))
    }

    /** Hexes of one size as textured polygons: property "pattern" = heat_<ground>_<level 0–5> (see HeatSpread). */
    fun hexes(
        levels: Map<String, Int>, res: Int, grounds: Grounds, state: WorldState?, explored: Geometry?,
    ): FeatureCollection {
        val towns = townHexes(state, res, explored)
        val prepExplored = explored?.let { PreparedGeometryFactory.prepare(it) }
        val out = ArrayList<Feature>(levels.size + towns.size)
        for ((cell, level) in levels) {
            if (cell in towns) continue
            val c = Cells.center(cell)
            val pt = gf.createPoint(Coordinate(c.lng, c.lat))
            val ground = when {
                grounds.sand?.contains(pt) == true -> "sand"
                grounds.forest?.contains(pt) == true -> "forest"
                else -> "grass"
            }
            out += hex(cell, "heat_${ground}_${level.coerceIn(0, 5)}")
        }
        for ((cell, tierStep) in towns) {
            // Block-size town hexes only on explored ground; coarser ones always (they're the overview).
            if (res == Cells.HEAT_RES && prepExplored != null) {
                val c = Cells.center(cell)
                if (!prepExplored.contains(gf.createPoint(Coordinate(c.lng, c.lat)))) continue
            }
            out += hex(cell, "heat_town_$tierStep")
        }
        return FeatureCollection.fromFeatures(out)
    }

    /** Paved hexes around each settlement at this hex size, with the step their tier earns. */
    private fun townHexes(state: WorldState?, res: Int, explored: Geometry?): Map<String, Int> {
        val ks = when (res) { Cells.HEAT_RES -> TOWN_K_BLOCK; Cells.HOOD_RES -> TOWN_K_HOOD; else -> TOWN_K_COUNTY }
        val out = HashMap<String, Int>()
        for (s in state?.settlements.orEmpty()) {
            val k = ks[s.tier.ordinal]
            if (k < 0) continue
            val stepForTier = s.tier.ordinal.coerceIn(1, 5)
            for (c in Cells.disk(Cells.cellAt(s.position.lat, s.position.lng, res), k)) out.merge(c, stepForTier, ::maxOf)
        }
        return out
    }

    private fun hex(cell: String, pattern: String): Feature {
        val b = Cells.boundary(cell)
        val ring = (b + b.first()).map { Point.fromLngLat(it.lng, it.lat) }
        return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addStringProperty("pattern", pattern) }
    }

    /** Beyond county zoom: each region as a node at its best settlement, sized by that settlement's tier. */
    fun regionNodes(state: WorldState?): FeatureCollection = FeatureCollection.fromFeatures(
        state?.regions.orEmpty().map { r ->
            val best = state!!.settlements.filter { it.regionId == r.id }.maxByOrNull { it.tier.ordinal }
            val at = best?.position ?: r.center
            Feature.fromGeometry(Point.fromLngLat(at.lng, at.lat)).apply {
                val tier = best?.tier?.ordinal ?: 0
                addStringProperty("icon", "hex_tier_$tier" + if (best?.isCapital == true) "_cap" else "")
                addNumberProperty("tier", tier)
                addStringProperty("label", r.name + "\n" + (best?.tier?.label ?: "Waypoints"))
            }
        },
    )

    /** Links between region nodes (portals found by travelling between them). */
    fun regionLinks(state: WorldState?): FeatureCollection {
        val nodes = regionNodes(state).features().orEmpty()
        val pos = state?.regions.orEmpty().mapIndexed { i, r -> r.id to (nodes.getOrNull(i)?.geometry() as? Point) }.toMap()
        return FeatureCollection.fromFeatures(state?.portals.orEmpty().mapNotNull { p ->
            val a = pos[p.regionA] ?: return@mapNotNull null; val b = pos[p.regionB] ?: return@mapNotNull null
            Feature.fromGeometry(LineString.fromLngLats(listOf(a, b)))
        })
    }
}
