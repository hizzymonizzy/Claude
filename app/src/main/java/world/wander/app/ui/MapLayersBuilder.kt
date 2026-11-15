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

    fun roads(layers: MapLayers): FeatureCollection = FeatureCollection.fromFeatures(
        layers.discovered.map { (e, s) ->
            Feature.fromGeometry(line(Overpass.decode(e.geometry))).apply {
                addNumberProperty("level", s.level)
                addStringProperty("name", e.name ?: "")
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
                addNumberProperty("liveliness", s.liveliness)
            }
        },
    )

    /** The whole world in fog, with holes around every road you've walked. */
    fun fog(layers: MapLayers): FeatureCollection {
        val world = gf.createPolygon(
            arrayOf(Coordinate(-180.0, -85.0), Coordinate(180.0, -85.0), Coordinate(180.0, 85.0), Coordinate(-180.0, 85.0), Coordinate(-180.0, -85.0)),
        )
        val revealed = layers.discovered.mapNotNull { (e, _) -> bufferedLatLng(Overpass.decode(e.geometry)) }
        val fog: Geometry = if (revealed.isEmpty()) world else try {
            world.difference(UnaryUnionOp.union(revealed))
        } catch (_: Exception) { world }
        val polys = when (fog) {
            is Polygon -> listOf(fog)
            is MultiPolygon -> (0 until fog.numGeometries).map { fog.getGeometryN(it) as Polygon }
            else -> emptyList()
        }
        return FeatureCollection.fromFeatures(polys.map { Feature.fromGeometry(toGeo(it)) })
    }

    /** Buffers a road in local meters, then returns the outline in lat/lng degrees. */
    private fun bufferedLatLng(points: List<LatLng>): Geometry? {
        if (points.size < 2) return null
        val proj = LocalProjection(points.first())
        val ls = gf.createLineString(points.map { Coordinate(proj.x(it), proj.y(it)) }.toTypedArray())
        val buf = ls.buffer(REVEAL_RADIUS_M, 4)
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
