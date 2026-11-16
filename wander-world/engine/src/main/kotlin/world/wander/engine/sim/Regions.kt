package world.wander.engine.sim

import world.wander.engine.Geo
import world.wander.engine.LatLng
import world.wander.engine.WorldEvent

/** 0. Arrivals by car or plane: waystones and new regions. */
internal fun Simulation.arrivals(d: Simulation.Day) {
    val day = d.day
    arrivalsByDay[day]?.forEach { arr ->
        val pt = LatLng(arr.lat, arr.lng)
        val from = lastRegion
        val (rid, created) = regionFor(pt, day)
        if (created) announceRegion(rid, day, from)
        val existing = waystones.firstOrNull { Geo.distanceM(it.center, pt) <= p.waystoneReturnM }
        val nearHome = input.homePoint?.let { Geo.distanceM(it, pt) < p.waystoneMinDistanceM } == true
        if (existing != null) {
            existing.add(pt)
            events += WorldEvent.WaystoneReturned(day, existing.id)
            if (existing.great && from != null && from != rid) portals.merge(portalKey(from, rid), 1, Int::plus)
        } else if (!nearHome && familiarity.keys.none { Geo.distanceM(centerOf(it), pt) < p.waystoneMinDistanceM }) {
            // Great only when arriving in a different region than the one you came from.
            val great = from != null && from != rid
            val ws = WaystoneAcc(waystones.size + 1, pt, great, rid, day)
            waystones += ws
            events += WorldEvent.WaystoneFound(day, ws.id, great, rid)
            if (great && from != null && from != rid) portals.merge(portalKey(from, rid), 1, Int::plus)
        }
        touchRegion(rid, day)
    }
}

/** 4b. The home region is fixed once known; other regions go dormant after a while without a visit. */
internal fun Simulation.quietRegions(d: Simulation.Day) {
    if (homeRegion == null) homeRegion = input.homeAreaId?.let { areaRegion[it] }
    val home = homeRegion ?: 0
    for (r in regions) {
        if (r.id != home && !r.dormant && d.day - r.lastPresence >= p.dormantAfterDays) r.dormant = true
    }
}

/** The region a point belongs to, creating one if it's far from all others. */
internal fun Simulation.regionFor(pt: LatLng, day: Int): Pair<Int, Boolean> {
    val near = regions.minByOrNull { Geo.distanceM(it.seed, pt) }
    if (near != null && Geo.distanceM(near.seed, pt) <= p.regionRadiusM) return near.id to false
    val r = RegionAcc(regions.size, pt, day)
    regions += r
    return r.id to true
}

internal fun Simulation.touchRegion(rid: Int, day: Int) {
    val r = regions[rid]
    if (r.dormant) { r.dormant = false; events += WorldEvent.RegionWoke(day, rid) }
    r.lastPresence = day
    lastRegion = rid
}

internal fun Simulation.announceRegion(rid: Int, day: Int, from: Int?) {
    if (rid > 0 && from != null) events += WorldEvent.RegionFounded(day, rid, from)
}

private fun portalKey(a: Int, b: Int) = if (a < b) a to b else b to a
