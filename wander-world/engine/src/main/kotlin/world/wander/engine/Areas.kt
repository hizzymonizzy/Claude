package world.wander.engine

/** A named place from OpenStreetMap (place=neighbourhood, suburb, village, …). */
data class Place(val id: Long, val name: String, val type: String, val lat: Double, val lng: Double)

/**
 * Assigns a cell to an area: the most specific named place within [radiusM], nearest first.
 * Falls back to the cell's resolution-7 parent, which gets named later after its most-walked road.
 */
object AreaAssigner {
    /** Most specific first. */
    val placePriority = listOf("neighbourhood", "quarter", "suburb", "hamlet", "village", "town", "city")

    fun assign(cell: String, places: List<Place>, radiusM: Double = 2000.0): AreaInfo {
        val c = Cells.center(cell)
        val nearby = places
            .filter { it.type in placePriority }
            .map { it to Geo.distanceM(c.lat, c.lng, it.lat, it.lng) }
            .filter { it.second <= radiusM }
        for (type in placePriority) {
            val best = nearby.filter { it.first.type == type }.minByOrNull { it.second } ?: continue
            return AreaInfo("p:${best.first.id}", best.first.name, best.first.type)
        }
        return AreaInfo("r7:${Cells.parent(cell, 7)}", null, null)
    }
}
