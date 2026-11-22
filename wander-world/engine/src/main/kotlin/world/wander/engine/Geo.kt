package world.wander.engine

import com.uber.h3core.H3Core
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(a: LatLng, b: LatLng): Double = distanceM(a.lat, a.lng, b.lat, b.lng)

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Length of a polyline in meters. */
    /** The point of segment [a]–[b] closest to [p]. */
    fun closestOnSegment(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val proj = LocalProjection(p)
        val ax = proj.x(a); val ay = proj.y(a); val bx = proj.x(b); val by = proj.y(b)
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((-ax) * dx + (-ay) * dy) / len2).coerceIn(0.0, 1.0)
        return proj.toLatLng(ax + t * dx, ay + t * dy)
    }

    /** Distance in metres from [p] to the segment [a]–[b] (local flat projection; fine at street scale). */
    fun distanceToSegmentM(p: LatLng, a: LatLng, b: LatLng): Double {
        val proj = LocalProjection(p)
        val ax = proj.x(a); val ay = proj.y(a); val bx = proj.x(b); val by = proj.y(b)
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
        val c = t.coerceIn(0.0, 1.0)
        return kotlin.math.hypot(ax + c * dx, ay + c * dy)
    }

    fun lengthM(points: List<LatLng>): Double {
        var total = 0.0
        for (i in 1 until points.size) total += distanceM(points[i - 1], points[i])
        return total
    }
}

/**
 * A flat local projection (equirectangular) around a reference point. Accurate to well under
 * 1% across a city, which is all map matching needs.
 */
class LocalProjection(ref: LatLng) {
    private val lat0 = ref.lat
    private val lng0 = ref.lng
    private val mPerDegLat = 110_540.0
    private val mPerDegLng = 111_320.0 * cos(lat0 * PI / 180.0)

    fun x(p: LatLng) = (p.lng - lng0) * mPerDegLng
    fun y(p: LatLng) = (p.lat - lat0) * mPerDegLat
    fun toLatLng(x: Double, y: Double) = LatLng(lat0 + y / mPerDegLat, lng0 + x / mPerDegLng)
}

/** H3 hex cells. Resolution 9 is the familiarity cell; resolution 7 is the map-data tile. */
object Cells {
    const val CELL_RES = 9
    const val TILE_RES = 7

    /**
     * On Android the native library is packaged as a normal jniLib, so load it the system way.
     * On a desktop JVM (tests) the library extracts its own bundled native code.
     */
    private val h3: H3Core by lazy {
        val android = System.getProperty("java.vm.vendor").orEmpty().contains("Android", ignoreCase = true)
        if (android) H3Core.newSystemInstance() else H3Core.newInstance()
    }

    /** Fine cell (~25 m) for wear on paths you make yourself. */
    const val TRODDEN_RES = 11
    /** Heat map hexes (ADR 0007): block (~20 m), neighbourhood (~130 m) and county (~1 km). */
    const val HEAT_RES = 12
    const val HOOD_RES = 10
    const val COUNTY_RES = 8

    fun cell(lat: Double, lng: Double): String = h3.latLngToCellAddress(lat, lng, CELL_RES)
    fun cellAt(lat: Double, lng: Double, res: Int): String = h3.latLngToCellAddress(lat, lng, res)
    fun tile(lat: Double, lng: Double): String = h3.latLngToCellAddress(lat, lng, TILE_RES)
    fun parent(cell: String, res: Int): String = h3.cellToParentAddress(cell, res)
    fun resolution(cell: String): Int = h3.getResolution(cell)

    fun center(cell: String): LatLng = h3.cellToLatLng(cell).let { LatLng(it.lat, it.lng) }

    fun boundary(cell: String): List<LatLng> = h3.cellToBoundary(cell).map { LatLng(it.lat, it.lng) }

    fun disk(cell: String, k: Int): List<String> = h3.gridDisk(cell, k)
}
