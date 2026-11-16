package world.wander.engine

import kotlin.math.abs

/**
 * A named area from OpenStreetMap: a park, wood, beach, campus, big store… Walking inside one
 * discovers it, even where there are no mapped paths.
 */
class Landmark(
    val id: String,
    val name: String,
    val kind: String,
    /** Outer and inner rings; a point is inside by the even–odd rule. */
    val rings: List<List<LatLng>>,
) {
    private val minLat = rings.minOf { r -> r.minOf { it.lat } }
    private val maxLat = rings.maxOf { r -> r.maxOf { it.lat } }
    private val minLng = rings.minOf { r -> r.minOf { it.lng } }
    private val maxLng = rings.maxOf { r -> r.maxOf { it.lng } }

    /** Area in m²: rings inside the largest ring are holes; rings outside it are extra outer parts. */
    val areaM2: Double = run {
        val outer = rings.maxBy { ringArea(it) }
        rings.sumOf { r ->
            val area = ringArea(r)
            when {
                r === outer -> area
                pointInRing(r.first(), outer) -> -area
                else -> area
            }
        }.coerceAtLeast(0.0)
    }

    val center: LatLng = rings.maxBy { it.size }.let { r -> LatLng(r.sumOf { it.lat } / r.size, r.sumOf { it.lng } / r.size) }

    /** Inside, or within [marginM] of the outline (walking a lake's shore or a beach's edge counts as being there). */
    fun near(lat: Double, lng: Double, marginM: Double): Boolean {
        if (contains(lat, lng)) return true
        if (marginM <= 0) return false
        val dLat = marginM / 110_540.0; val dLng = marginM / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
        if (lat < minLat - dLat || lat > maxLat + dLat || lng < minLng - dLng || lng > maxLng + dLng) return false
        val p = LatLng(lat, lng)
        return rings.any { r -> (1 until r.size).any { i -> Geo.distanceToSegmentM(p, r[i - 1], r[i]) <= marginM } }
    }

    fun contains(lat: Double, lng: Double): Boolean {
        if (lat < minLat || lat > maxLat || lng < minLng || lng > maxLng) return false
        var inside = false
        for (ring in rings) {
            var j = ring.size - 1
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[j]
                if ((a.lat > lat) != (b.lat > lat) && lng < (b.lng - a.lng) * (lat - a.lat) / (b.lat - a.lat) + a.lng) inside = !inside
                j = i
            }
        }
        return inside
    }

    companion object {
        fun pointInRing(p: LatLng, ring: List<LatLng>): Boolean {
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[j]
                if ((a.lat > p.lat) != (b.lat > p.lat) && p.lng < (b.lng - a.lng) * (p.lat - a.lat) / (b.lat - a.lat) + a.lng) inside = !inside
                j = i
            }
            return inside
        }

        fun ringArea(ring: List<LatLng>): Double {
            if (ring.size < 3) return 0.0
            val proj = LocalProjection(ring.first())
            var sum = 0.0
            for (i in ring.indices) {
                val a = ring[i]; val b = ring[(i + 1) % ring.size]
                sum += proj.x(a) * proj.y(b) - proj.x(b) * proj.y(a)
            }
            return abs(sum) / 2
        }

        /** Joins open way pieces (e.g. a park's outer boundary split into several ways) into closed rings. */
        fun stitch(pieces: List<List<LatLng>>): List<List<LatLng>> {
            val open = pieces.filter { it.size >= 2 }.map { it.toMutableList() }.toMutableList()
            val rings = mutableListOf<List<LatLng>>()
            while (open.isNotEmpty()) {
                val ring = open.removeAt(0)
                var grew = true
                while (ring.first() != ring.last() && grew) {
                    grew = false
                    val it = open.iterator()
                    while (it.hasNext()) {
                        val p = it.next()
                        when {
                            p.first() == ring.last() -> { ring.addAll(p.drop(1)); it.remove(); grew = true }
                            p.last() == ring.last() -> { ring.addAll(p.reversed().drop(1)); it.remove(); grew = true }
                            p.last() == ring.first() -> { ring.addAll(0, p.dropLast(1)); it.remove(); grew = true }
                            p.first() == ring.first() -> { ring.addAll(0, p.reversed().dropLast(1)); it.remove(); grew = true }
                        }
                        if (grew) break
                    }
                }
                if (ring.size >= 4 && ring.first() == ring.last()) rings += ring
            }
            return rings
        }
    }
}

/** [pointLike]: mapped as a single point (we give it a small circle), so walking past it should count. */
data class LandmarkInfo(
    val id: String, val name: String, val kind: String, val areaM2: Double,
    val pointLike: Boolean = false, val center: LatLng? = null,
)
data class LandmarkVisit(val landmarkId: String, val day: Int, val seconds: Double)

/** A resolution-11 cell (~25 m) walked off the mapped network on a given day. */
data class TroddenCell(val cell: String, val day: Int)
