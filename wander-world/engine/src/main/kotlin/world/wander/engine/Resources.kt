package world.wander.engine

/**
 * A resource node on the map: a fixed spot on a place (or street) where you can gather one resource.
 * Nodes stay where they are; after you gather from one it remains on the map marked as gathered.
 */
data class ResourceTarget(
    val resource: String,
    val name: String,
    /** The node's fixed spot (a place's [Landmark.anchor], or the middle of a street). Never follows you. */
    val point: LatLng,
    /** How far you are from being able to gather here (0 when you're in its gathering zone). */
    val distanceM: Double,
    /** "new" (discovery bonus), "thinned", "gathered" (already today), or "". */
    val note: String = "",
    val sourceId: String = "",
    /** The node the edge arrow points to for this resource: best yield for the distance, not yet gathered today. */
    val best: Boolean = false,
)

/** A mapped street, for stone. */
data class Street(val id: String, val name: String?, val points: List<LatLng>)

/**
 * The resource nodes around you. Pure (no Android), so the whole loop of walking, pointing, gathering and
 * crediting can be simulated in tests (see GatheringSimulationTest).
 */
object ResourceFinder {
    /** Nodes are listed within this distance; the best one per resource is pointed to up to [MAX_POINT_M]. */
    const val NEARBY_M = 2_000.0
    const val MAX_POINT_M = 8_000.0
    private const val PER_RESOURCE = 4

    /** Distance from [p] to a place's gathering zone: 0 inside or within its margin, else to the edge minus the margin. */
    fun zoneDistanceM(l: Landmark, p: LatLng): Double {
        val margin = Yields.gatherMarginM(l.kind)
        if (l.near(p.lat, p.lng, margin)) return 0.0
        val edge = l.rings.minOf { r -> (1 until r.size).minOf { i -> Geo.distanceToSegmentM(p, r[i - 1], r[i]) } }
        return (edge - margin).coerceAtLeast(0.0)
    }

    fun find(from: LatLng, places: List<Landmark>, water: List<Landmark>, streets: List<Street>, world: WorldState?): List<ResourceTarget> {
        val discovered = world?.landmarks.orEmpty().map { it.id }.toSet()
        val abundance = world?.abundance.orEmpty()
        val gathered = world?.gatheredToday.orEmpty()
        fun note(l: Landmark) = when {
            l.id in gathered -> "gathered"
            l.name.isNotBlank() && l.id !in discovered && l.id !in abundance -> "new"
            (abundance[l.id] ?: 1.0) < 0.5 -> "thinned"
            else -> ""
        }
        // What a source would yield now: never-harvested named places pay the discovery bonus, thinned ones less.
        fun worth(l: Landmark) = abundance[l.id] ?: if (l.id in discovered || l.name.isBlank()) 1.0 else 2.0
        fun nodes(resource: String, candidates: List<Landmark>): List<ResourceTarget> {
            val near = candidates.map { it to zoneDistanceM(it, from) }.filter { it.second <= MAX_POINT_M }
            // The arrow goes to the best value for the walk that you haven't gathered from today.
            val best = near.filter { it.first.id !in gathered }.minByOrNull { (l, d) -> (d + 300) / worth(l) }?.first?.id
            return near.sortedBy { it.second }
                .filter { (l, d) -> d <= NEARBY_M || l.id == best }
                .take(PER_RESOURCE).let { list -> if (best != null && list.none { it.first.id == best }) list + near.first { it.first.id == best } else list }
                .map { (l, d) -> ResourceTarget(resource, l.name.ifBlank { "Water" }, l.anchor, d, note(l), l.id, best = l.id == best) }
        }
        val metal = places.filter { it.kind in Yields.METAL_KINDS }
        // Stone: unwalked streets. The node sits at the street's middle; walking any of it counts.
        val walked = world?.edges.orEmpty().filterValues { it.discovered }.keys
        val stone = streets.asSequence().filter { it.id !in walked && it.points.size >= 2 }.map { s ->
            val d = (1 until s.points.size).minOf { i -> Geo.distanceToSegmentM(from, s.points[i - 1], s.points[i]) }
            s to d
        }.sortedBy { it.second }.take(1).toList().map { (s, d) ->
            ResourceTarget("stone", s.name ?: "A new street", s.points[s.points.size / 2], d, "new", s.id, best = true)
        }
        return nodes("wood", places.filter { it.kind in Yields.WOOD_KINDS }) +
            nodes("food", places.filter { it.kind in Yields.FOOD_KINDS } + water) +
            stone + nodes("metal", metal)
    }
}

/** Within this distance of a node's gathering zone the ring turns green. */
const val GATHER_M = 0.5
