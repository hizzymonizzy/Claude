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
import world.wander.engine.LatLng
import world.wander.engine.LocalProjection
import world.wander.engine.WorldState
import org.maplibre.geojson.Polygon as GeoPolygon

/** Turns world state into GeoJSON for the map: fog, worn roads, peeks, settlements. */
object MapLayersBuilder {
    private val gf = GeometryFactory()
    private const val REVEAL_RADIUS_M = 40.0
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
                addNumberProperty("size", if (w.great) 0.8 else 0.55)
            }
        },
    )

    /** Sprite for a kind of place. */
    fun placeIcon(kind: String?): String = when (kind) {
        "park", "garden", "nature_reserve", "wood", "forest", "common", "recreation_ground", "village_green",
        "meadow", "heath", "grassland", "dog_park", "golf_course", "playground", "wetland" -> "place_park"
        "store" -> "place_store"
        else -> "place_generic"
    }

    fun landmarkLabels(layers: MapLayers, kinds: Map<String, String> = emptyMap()): FeatureCollection = FeatureCollection.fromFeatures(
        layers.landmarks.map { (name, rings, _) ->
            val r = rings.maxBy { it.size }
            Feature.fromGeometry(Point.fromLngLat(r.sumOf { it.lng } / r.size, r.sumOf { it.lat } / r.size)).apply {
                addStringProperty("name", name)
                addStringProperty("icon", placeIcon(kinds[name]))
            }
        },
    )

    fun peeks(layers: MapLayers): FeatureCollection = FeatureCollection.fromFeatures(
        layers.peek.map { Feature.fromGeometry(line(Overpass.decode(it.geometry))) },
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
                addStringProperty("label", (if (s.isCapital) "★ " else "") + s.name + "\n" + s.tier.label)
                addNumberProperty("tier", s.tier.ordinal)
                addStringProperty("icon", "tier_${s.tier.ordinal}")
                addNumberProperty("liveliness", s.liveliness)
            }
        },
    )

    /** The whole world in fog, with holes around every road you've walked. */
    fun fog(layers: MapLayers): FeatureCollection {
        val world = gf.createPolygon(
            arrayOf(Coordinate(-180.0, -85.0), Coordinate(180.0, -85.0), Coordinate(180.0, 85.0), Coordinate(-180.0, 85.0), Coordinate(-180.0, -85.0)),
        )
        val revealed = layers.discovered.mapNotNull { (e, _) -> bufferedLatLng(Overpass.decode(e.geometry), REVEAL_RADIUS_M) } +
            layers.trails.mapNotNull { bufferedLatLng(it, TRAIL_RADIUS_M) } +
            layers.landmarks.filter { it.third <= CLEAR_PLACES_UNDER_M2 }.mapNotNull { (_, rings, _) -> placePolygon(rings) }
        val fog: Geometry = if (revealed.isEmpty()) world else try {
            world.difference(fillSmallHoles(UnaryUnionOp.union(revealed)))
        } catch (_: Exception) { world }
        val polys = when (fog) {
            is Polygon -> listOf(fog)
            is MultiPolygon -> (0 until fog.numGeometries).map { fog.getGeometryN(it) as Polygon }
            else -> emptyList()
        }
        return FeatureCollection.fromFeatures(polys.map { Feature.fromGeometry(toGeo(it)) })
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
