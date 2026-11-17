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
    /** What kind of source: a place kind ("park", "library"…), "shore", "field" or "street". */
    val kind: String = "",
    /** How full it is today (1 = fresh; thinned sources grow back 5% a day). */
    val abundance: Double = 1.0,
) {
    /** Roughly what a visit (or walking 150 m of a line) would give right now. */
    val expectedYield: Double get() {
        val bonus = if (note == "new") 2.0 else 1.0
        return when {
            note == "gathered" -> 0.0
            resource == "stone" -> 0.15 * bonus
            kind == "shore" || kind == "field" -> 0.3 * bonus * abundance
            resource == "wood" -> 1.1 * bonus * abundance
            resource == "food" -> 1.05 * bonus * abundance
            resource == "metal" -> if (note == "new") 3.0 else 1.5 * abundance
            else -> 0.0
        }
    }
}

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

    /** Shores are split into stretches about this long; each is one food node. */
    private const val STRETCH_M = 150.0

    /** Cuts a line into stretches of about [STRETCH_M]. */
    fun stretches(raw: List<LatLng>): List<List<LatLng>> {
        if (raw.size < 2) return emptyList()
        // Long straight segments (a mapped coastline can have 2 km between vertices) are split every ~25 m first.
        val line = mutableListOf(raw.first())
        for (i in 1 until raw.size) {
            val a = raw[i - 1]; val b = raw[i]; val n = (Geo.distanceM(a, b) / 25.0).toInt().coerceAtLeast(1)
            for (k in 1..n) { val t = k.toDouble() / n; line += LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t) }
        }
        val out = mutableListOf<List<LatLng>>(); var cur = mutableListOf(line.first()); var len = 0.0
        for (i in 1 until line.size) {
            len += Geo.distanceM(line[i - 1], line[i]); cur += line[i]
            if (len >= STRETCH_M) { out += cur; cur = mutableListOf(line[i]); len = 0.0 }
        }
        if (cur.size >= 2) out += cur
        return out
    }

    /** The fine shore cells a stretch passes through (as harvest keys "s:<cell>"). */
    fun stretchCells(st: List<LatLng>): Set<String> =
        (1 until st.size).flatMap { i ->
            val a = st[i - 1]; val b = st[i]; val n = (Geo.distanceM(a, b) / 20).toInt() + 1
            (0..n).map { k -> val t = k.toDouble() / n; "s:" + Cells.cellAt(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t, Cells.TRODDEN_RES) }
        }.toSet()

    fun find(
        from: LatLng, places: List<Landmark>, water: List<Landmark>, streets: List<Street>, world: WorldState?,
        /** Coastlines; water outlines and beaches are added as shores automatically. */
        coast: List<List<LatLng>> = emptyList(),
        /** Edges of farmland, orchards and allotments: food lines inland. */
        fields: List<List<LatLng>> = emptyList(),
    ): List<ResourceTarget> {
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
                .map { (l, d) -> ResourceTarget(resource, l.name.ifBlank { "Water" }, l.anchor, d, note(l), l.id, best = l.id == best, kind = l.kind, abundance = abundance[l.id] ?: 1.0) }
        }
        val metal = places.filter { it.kind in Yields.METAL_KINDS }
        // Stone: unwalked streets. The node sits at the street's middle; walking any of it counts.
        val walked = world?.edges.orEmpty().filterValues { it.discovered }.keys
        val stone = streets.asSequence().filter { it.id !in walked && it.points.size >= 2 }.map { s ->
            val d = (1 until s.points.size).minOf { i -> Geo.distanceToSegmentM(from, s.points[i - 1], s.points[i]) }
            s to d
        }.sortedBy { it.second }.take(1).toList().map { (s, d) ->
            ResourceTarget("stone", s.name ?: "A new street", s.points[s.points.size / 2], d, "new", s.id, best = true, kind = "street")
        }
        // Food: shores are lines, like streets for stone. Each ~150 m stretch is a node anchored at its middle;
        // walking any of it gathers. Harbours are point nodes.
        val shoreLines = (coast + water.flatMap { it.rings } + places.filter { it.kind == "beach" }.flatMap { it.rings }).map { it to "shore" } +
            fields.map { it to "field" }
        val walkedShore = world?.shoreWalked.orEmpty()
        val shore = shoreLines.asSequence().flatMap { (l, k) -> stretches(l).asSequence().map { it to k } }.mapNotNull { (st, k) ->
            val d = (1 until st.size).minOf { i -> Geo.distanceToSegmentM(from, st[i - 1], st[i]) }
            if (d > MAX_POINT_M) null else Triple(st, (d - Yields.SHORE_M).coerceAtLeast(0.0), k)
        }.sortedBy { it.second }.take(40).map { (st, d, k) ->
            val cells = stretchCells(st)
            val note = when {
                cells.any { it in gathered } -> "gathered"
                cells.none { it in walkedShore } -> "new"
                cells.mapNotNull { walkedShore[it] }.average() < 0.5 -> "thinned"
                else -> ""
            }
            val mid = st[st.size / 2]
            val full = cells.mapNotNull { walkedShore[it] }.takeIf { it.isNotEmpty() }?.average() ?: 1.0
            ResourceTarget("food", if (k == "field") "Field edge" else "Shore", mid, d, note,
                "shore:%.5f,%.5f".format(java.util.Locale.US, mid.lat, mid.lng), kind = k, abundance = full)
        }.toList()
        val shoreBest = shore.filter { it.note != "gathered" }.minByOrNull { (it.distanceM + 300) / (if (it.note == "new") 2.0 else 1.0) }
        val harbours = nodes("food", places.filter { it.kind in Yields.FOOD_NODE_KINDS })
        val harbourBest = harbours.firstOrNull { it.best }
        val foodBest = listOfNotNull(shoreBest, harbourBest).minByOrNull { it.distanceM }
        val food = (shore.filter { it.distanceM <= NEARBY_M }.take(PER_RESOURCE) + listOfNotNull(shoreBest)).distinctBy { it.sourceId }
            .plus(harbours).map { it.copy(best = it.sourceId == foodBest?.sourceId) }
        return nodes("wood", places.filter { it.kind in Yields.WOOD_KINDS }) + food + stone + nodes("metal", metal)
    }
}

/** Within this distance of a node's gathering zone the ring turns green. */
const val GATHER_M = 0.5

/**
 * Shorelines as segments, for "am I walking along a shore?" (within [Yields.SHORE_M] of an edge, or on a beach).
 * Segments are bucketed by a coarse grid so long coastlines stay cheap to test.
 */
class Shores(lines: List<List<LatLng>>, private val beaches: List<Landmark> = emptyList()) {
    private val buckets = HashMap<Long, MutableList<Pair<LatLng, LatLng>>>()
    private fun key(lat: Double, lng: Double) = (Math.floor(lat / 0.002).toLong() shl 32) xor (Math.floor(lng / 0.002).toLong() and 0xffffffffL)
    init {
        for (l in lines) for (i in 1 until l.size) {
            val a = l[i - 1]; val b = l[i]
            // Register the segment in every grid square it touches (squares ~200 m; segments are short).
            val steps = (Geo.distanceM(a, b) / 100).toInt() + 1
            val keys = (0..steps).map { k -> val t = k.toDouble() / steps; key(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t) }.toSet()
            for (k in keys) buckets.getOrPut(k) { mutableListOf() } += a to b
        }
    }
    val isEmpty get() = buckets.isEmpty() && beaches.isEmpty()
    fun near(lat: Double, lng: Double): Boolean = shorePoint(lat, lng) != null

    /**
     * The point on the shore you're walking along (the nearest point of the nearest edge within [Yields.SHORE_M]),
     * or where you stand if you're on a beach; null if you're not at a shore. Shore cells are recorded here, on the
     * shoreline itself, so they line up with the food nodes cut along the same line.
     */
    fun shorePoint(lat: Double, lng: Double): LatLng? {
        val p = LatLng(lat, lng)
        var best: Pair<LatLng, Double>? = null
        for (dl in -1..1) for (dg in -1..1) {
            val segs = buckets[key(lat + dl * 0.002, lng + dg * 0.002)] ?: continue
            for ((a, b) in segs) {
                val d = Geo.distanceToSegmentM(p, a, b)
                if (d <= Yields.SHORE_M && (best == null || d < best.second)) best = Geo.closestOnSegment(p, a, b) to d
            }
        }
        return best?.first ?: if (beaches.any { it.contains(lat, lng) }) p else null
    }
}
