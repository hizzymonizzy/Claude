package world.wander.app.ui

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.operation.union.UnaryUnionOp
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import world.wander.app.data.MapLayers
import world.wander.app.geo.Overpass
import world.wander.engine.GATHER_M
import world.wander.engine.LatLng
import world.wander.engine.LocalProjection
import world.wander.engine.WorldState
import org.maplibre.geojson.Polygon as GeoPolygon

/** Turns world state into GeoJSON for the map: fog, worn roads, peeks, settlements. */
object MapLayersBuilder {
    private val gf = GeometryFactory()
    /**
     * Fog cleared either side of a charted road, by wear level (1 faint trail … 5 avenue). Roads you walk
     * often clear wider, so a loop you repeat gradually fills in. Index 0 is unused.
     */
    val REVEAL_BY_LEVEL_M = doubleArrayOf(40.0, 40.0, 55.0, 75.0, 100.0, 130.0)
    /** How far fog clears either side of the path you actually walked. */
    private const val TRAIL_RADIUS_M = 35.0
    /** Discovered places up to this size clear completely (a small park, a store); bigger ones just get named. */
    private const val CLEAR_PLACES_UNDER_M2 = 50_000.0
    /** Fogged pockets smaller than this, fully enclosed by explored ground, are cleared too (≈ 2 hectares). */
    private const val FILL_HOLES_UNDER_M2 = 20_000.0

    fun roads(layers: MapLayers): FeatureCollection = FeatureCollection.fromFeatures(
        layers.discovered.map { (e, s) ->
            Feature.fromGeometry(line(Overpass.decode(e.geometry))).apply {
                addNumberProperty("level", s.level)
                addStringProperty("name", e.name ?: "")
            }
        },
    )

    fun wild(layers: MapLayers): FeatureCollection = FeatureCollection.fromFeatures(
        layers.wild.map { (pts, level) -> Feature.fromGeometry(line(pts)).apply { addNumberProperty("level", level) } },
    )

    /** One signpost per area walked in that hasn't grown a settlement yet (settlements have their own sprite). */
    fun waypoints(state: WorldState?): FeatureCollection = FeatureCollection.fromFeatures(
        state?.exploredAreas.orEmpty().filter { !it.settled }.map { a ->
            Feature.fromGeometry(Point.fromLngLat(a.center.lng, a.center.lat)).apply { addStringProperty("name", a.name) }
        },
    )

    fun waystones(state: WorldState?): FeatureCollection = FeatureCollection.fromFeatures(
        state?.waystones.orEmpty().map { w ->
            Feature.fromGeometry(Point.fromLngLat(w.center.lng, w.center.lat)).apply {
                addStringProperty("label", if (w.great) "Great waystone" else "Waystone")
                addStringProperty("icon", if (w.great) "medal_waystone_great" else "medal_waystone")
            }
        },
    )

    private val WOODED = MapIcons.WOODED
    private val GREEN = MapIcons.GREEN

    /** Medallion for a kind of place. */
    fun placeIcon(kind: String?): String = MapIcons.placeIcon(kind)

    /** Beaches and sand: discovered beaches plus every mapped sand area nearby, drawn under the fog. */
    fun sandPlaces(layers: MapLayers, kinds: Map<String, String>): FeatureCollection = FeatureCollection.fromFeatures(
        (layers.landmarks.filter { kinds[it.first] == "beach" }.map { it.second } + layers.sand).mapNotNull { rings ->
            placePolygon(rings)?.let { g -> polygons(g).map { Feature.fromGeometry(toGeo(it)) } }
        }.flatten(),
    )

    /** How wide the wet-sand shore is along a coastline (the sea side is hidden under the water). */
    private const val SHORE_M = 18.0

    /** A wet-sand strip along every coastline nearby, drawn under the fog and under the water. */
    fun shore(layers: MapLayers): FeatureCollection = FeatureCollection.fromFeatures(
        layers.coast.mapNotNull { bufferedLatLng(it, SHORE_M) }.flatMap { g -> polygons(g).map { Feature.fromGeometry(toGeo(it)) } },
    )

    /** Water routes you've paddled, with their wear level. */
    fun water(layers: MapLayers): FeatureCollection = FeatureCollection.fromFeatures(
        layers.water.map { (pts, level) -> Feature.fromGeometry(line(pts)).apply { addNumberProperty("level", level) } },
    )

    /** Discovered parks and woods, drawn with a forest-floor texture (under the fog). */
    fun greenPlaces(layers: MapLayers, kinds: Map<String, String>): FeatureCollection = FeatureCollection.fromFeatures(
        layers.landmarks.filter { kinds[it.first] in WOODED || kinds[it.first] in GREEN }.mapNotNull { (_, rings, _) ->
            placePolygon(rings)?.let { g -> polygons(g).map { Feature.fromGeometry(toGeo(it)) } }
        }.flatten(),
    )

    fun landmarkLabels(layers: MapLayers, kinds: Map<String, String> = emptyMap()): FeatureCollection = FeatureCollection.fromFeatures(
        layers.landmarks.map { (name, rings, _) ->
            val r = rings.maxBy { it.size }
            Feature.fromGeometry(Point.fromLngLat(r.sumOf { it.lng } / r.size, r.sumOf { it.lat } / r.size)).apply {
                addStringProperty("name", name)
                addStringProperty("icon", placeIcon(kinds[name]))
            }
        },
    )

    /**
     * Streets you haven't walked that lie in explored ground (or touch a street you have): drawn dashed, so
     * inside explored land every street shows its status. Base-map streets are hidden there.
     */
    fun peeks(layers: MapLayers, explored: Geometry? = null): FeatureCollection {
        val prepared = explored?.let { org.locationtech.jts.geom.prep.PreparedGeometryFactory.prepare(it) }
        return FeatureCollection.fromFeatures(
            layers.peek.mapNotNull { e ->
                val pts = Overpass.decode(e.geometry)
                if (pts.size < 2) return@mapNotNull null
                if (prepared != null && !prepared.intersects(gf.createLineString(pts.map { Coordinate(it.lng, it.lat) }.toTypedArray()))) return@mapNotNull null
                Feature.fromGeometry(line(pts))
            },
        )
    }

    /** Resource pointers as map points, so they stay pinned to the ground while you pan and zoom. */
    fun resources(targets: List<world.wander.engine.ResourceTarget>): FeatureCollection = FeatureCollection.fromFeatures(
        targets.map { t ->
            Feature.fromGeometry(Point.fromLngLat(t.point.lng, t.point.lat)).apply {
                addStringProperty("icon", "res_${t.resource}")
                addStringProperty("name", t.name)
                addBooleanProperty("near", t.distanceM <= GATHER_M)
                addStringProperty("state", t.note)
                addBooleanProperty("best", t.best)
            }
        },
    )

    fun track(points: List<LatLng>): FeatureCollection =
        if (points.size < 2) FeatureCollection.fromFeatures(emptyList())
        else FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line(points))))

    fun point(p: LatLng?): FeatureCollection =
        if (p == null) FeatureCollection.fromFeatures(emptyList())
        else FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat))))

    fun settlements(state: WorldState?): FeatureCollection = FeatureCollection.fromFeatures(
        state?.settlements.orEmpty().map { s ->
            Feature.fromGeometry(Point.fromLngLat(s.position.lng, s.position.lat)).apply {
                addStringProperty("label", s.name + "\n" + (if (s.isCapital) "Capital · " else "") + s.tier.label)
                addNumberProperty("tier", s.tier.ordinal)
                val badge = when {
                    !s.isCapital -> ""
                    state?.monuments?.isNotEmpty() == true -> "_capmon"
                    else -> "_cap"
                }
                addStringProperty("icon", "medal_tier_${s.tier.ordinal}$badge")
                addNumberProperty("liveliness", s.liveliness)
            }
        },
    )

    /** The whole world in fog, and the explored ground (holes in the fog) as its own layer. */
    /** Each waystone sits in a small clearing, so it never floats in the fog. */
    private const val WAYSTONE_CLEARING_M = 45.0

    fun fogAndExplored(layers: MapLayers, clearings: List<LatLng> = emptyList()): Pair<FeatureCollection, FeatureCollection> =
        fogExploredGeometry(layers, clearings).let { it.first to it.second }

    /** Fog, explored ground, and the explored geometry itself (for clipping street status to it). */
    fun fogExploredGeometry(layers: MapLayers, clearings: List<LatLng> = emptyList()): Triple<FeatureCollection, FeatureCollection, Geometry?> {
        val world = gf.createPolygon(
            arrayOf(Coordinate(-180.0, -85.0), Coordinate(180.0, -85.0), Coordinate(180.0, 85.0), Coordinate(-180.0, 85.0), Coordinate(-180.0, -85.0)),
        )
        val revealed = layers.discovered.mapNotNull { (e, st) -> bufferedLatLng(Overpass.decode(e.geometry), REVEAL_BY_LEVEL_M[st.level.coerceIn(1, 5)]) } +
            layers.trails.mapNotNull { bufferedLatLng(it, TRAIL_RADIUS_M) } +
            layers.wild.mapNotNull { (pts, level) -> bufferedLatLng(pts, maxOf(TRAIL_RADIUS_M, REVEAL_BY_LEVEL_M[level.coerceIn(1, 5)] - 5)) } +
            layers.water.mapNotNull { (pts, level) -> bufferedLatLng(pts, REVEAL_BY_LEVEL_M[level.coerceIn(1, 5)] + 20) } +
            layers.landmarks.filter { it.third <= CLEAR_PLACES_UNDER_M2 }.mapNotNull { (_, rings, _) -> placePolygon(rings) } +
            clearings.mapNotNull { circle(it, WAYSTONE_CLEARING_M) }
        val explored: Geometry? = if (revealed.isEmpty()) null else try { fillSmallHoles(UnaryUnionOp.union(revealed)) } catch (_: Exception) { null }
        val fog: Geometry = explored?.let { try { world.difference(it) } catch (_: Exception) { world } } ?: world
        fun fc(g: Geometry?) = FeatureCollection.fromFeatures(polygons(g).map { Feature.fromGeometry(toGeo(it)) })
        return Triple(fc(fog), fc(explored), explored)
    }

    private fun polygons(g: Geometry?): List<Polygon> = when (g) {
        is Polygon -> listOf(g)
        is MultiPolygon -> (0 until g.numGeometries).map { g.getGeometryN(it) as Polygon }
        else -> emptyList()
    }

    private fun placePolygon(rings: List<List<LatLng>>): Geometry? = try {
        val polys = rings.filter { it.size >= 4 }.map { r -> gf.createPolygon(r.map { Coordinate(it.lng, it.lat) }.toTypedArray()) }
        if (polys.isEmpty()) null else UnaryUnionOp.union(polys)
    } catch (_: Exception) { null }

    /** Removes small fogged pockets enclosed by explored ground (e.g. the block inside a loop you walked). */
    private fun fillSmallHoles(g: Geometry): Geometry {
        fun fill(p: Polygon): Polygon {
            val keep = (0 until p.numInteriorRing).map { p.getInteriorRingN(it) }.filter { ringAreaM2(it.coordinates) >= FILL_HOLES_UNDER_M2 }
            return gf.createPolygon(p.exteriorRing as org.locationtech.jts.geom.LinearRing, keep.map { it as org.locationtech.jts.geom.LinearRing }.toTypedArray())
        }
        return when (g) {
            is Polygon -> fill(g)
            is MultiPolygon -> gf.createMultiPolygon((0 until g.numGeometries).map { fill(g.getGeometryN(it) as Polygon) }.toTypedArray())
            else -> g
        }
    }

    /** Area of a lng/lat ring in square meters (shoelace in a local projection). */
    private fun ringAreaM2(coords: Array<Coordinate>): Double {
        if (coords.size < 4) return 0.0
        val proj = LocalProjection(LatLng(coords[0].y, coords[0].x))
        var sum = 0.0
        for (i in 0 until coords.size - 1) {
            val a = LatLng(coords[i].y, coords[i].x); val b = LatLng(coords[i + 1].y, coords[i + 1].x)
            sum += proj.x(a) * proj.y(b) - proj.x(b) * proj.y(a)
        }
        return kotlin.math.abs(sum) / 2
    }

    private fun circle(c: LatLng, radiusM: Double): Geometry? {
        val proj = LocalProjection(c)
        val pts = (0..24).map { i -> val a = 2 * Math.PI * (i % 24) / 24; proj.toLatLng(radiusM * Math.cos(a), radiusM * Math.sin(a)) }
        return try { gf.createPolygon(pts.map { Coordinate(it.lng, it.lat) }.toTypedArray()) } catch (_: Exception) { null }
    }

    /** Buffers a line in local meters, then returns the outline in lat/lng degrees. */
    private fun bufferedLatLng(points: List<LatLng>, radiusM: Double): Geometry? {
        if (points.size < 2) return null
        val proj = LocalProjection(points.first())
        val ls = gf.createLineString(points.map { Coordinate(proj.x(it), proj.y(it)) }.toTypedArray())
        val buf = ls.buffer(radiusM, 4)
        val coords = buf.coordinates
        if (buf !is Polygon) return null
        fun ring(r: org.locationtech.jts.geom.LinearRing) = gf.createLinearRing(
            r.coordinates.map { c -> proj.toLatLng(c.x, c.y).let { Coordinate(it.lng, it.lat) } }.toTypedArray(),
        )
        if (coords.isEmpty()) return null
        return gf.createPolygon(ring(buf.exteriorRing), (0 until buf.numInteriorRing).map { ring(buf.getInteriorRingN(it)) }.toTypedArray())
    }

    private fun toGeo(p: Polygon): GeoPolygon {
        val rings = mutableListOf(p.exteriorRing.coordinates.map { Point.fromLngLat(it.x, it.y) })
        for (i in 0 until p.numInteriorRing) rings += p.getInteriorRingN(i).coordinates.map { Point.fromLngLat(it.x, it.y) }
        return GeoPolygon.fromLngLats(rings)
    }

    private fun line(points: List<LatLng>): LineString = LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })
}
