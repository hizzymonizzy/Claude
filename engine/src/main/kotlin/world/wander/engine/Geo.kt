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

    fun cell(lat: Double, lng: Double): String = h3.latLngToCellAddress(lat, lng, CELL_RES)
    fun tile(lat: Double, lng: Double): String = h3.latLngToCellAddress(lat, lng, TILE_RES)
    fun parent(cell: String, res: Int): String = h3.cellToParentAddress(cell, res)

    fun center(cell: String): LatLng = h3.cellToLatLng(cell).let { LatLng(it.lat, it.lng) }

    fun boundary(cell: String): List<LatLng> = h3.cellToBoundary(cell).map { LatLng(it.lat, it.lng) }

    fun disk(cell: String, k: Int): List<String> = h3.gridDisk(cell, k)
}
