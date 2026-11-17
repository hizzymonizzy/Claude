package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.Geo
import world.wander.engine.ProjectDef
import world.wander.engine.Projects
import world.wander.engine.Proposal
import world.wander.engine.Tier
import world.wander.engine.Treasury
import world.wander.engine.WorldEvent
import world.wander.engine.Yields

/** 7. Economy: the day's earnings, milestone monuments, and the next proposal if none is waiting. */
internal fun Simulation.economy(d: Simulation.Day) {
    val day = d.day
    val mv = movementByDay[day]
    val footKm = (mv?.sumOf { it.footMeters } ?: 0.0) / 1000
    val waterKm = (mv?.sumOf { it.waterMeters } ?: 0.0) / 1000
    val outdoorKm = footKm + (mv?.sumOf { it.cycleMeters } ?: 0.0) / 1000 + waterKm
    // Paddling: 2 food per km, spread over the water you crossed; new water pays the discovery bonus.
    val wakeCells = wakeByDay[day]?.map { it.cell }?.distinct().orEmpty()
    if (waterKm > 0) {
        if (wakeCells.isEmpty()) d.gain(null, food = waterKm * 2)
        else wakeCells.forEach { d.gain(areaOfFine(it), food = harvest.take("w:$it", day, waterKm * 2 / wakeCells.size)) }
    }
    // Gold goes to the one purse; everything else to the bank of the area where it was gathered.
    val goldToday = Yields.gold(intensityByDay[day] ?: 0.0, outdoorKm)
    gold += goldToday
    var materials = Treasury()
    for ((area, t) in d.gains) {
        val key = area ?: d.mainArea ?: input.homeAreaId ?: NO_AREA
        banks[key] = (banks[key] ?: Treasury()) + t
        materials += t
    }
    d.earned = materials.copy(gold = goldToday)
    totalKm += outdoorKm
    if (footKm >= 5) raise("day_5k", day)
    if (footKm >= 10) raise("day_10k", day)
    if (footKm >= 21.1) raise("day_21k", day)
    if (totalKm >= 100) raise("total_100k", day)
    if (totalKm >= 500) raise("total_500k", day)
    if (waterKm > 0.1 && !paddledEver) { paddledEver = true; raise("first_paddle", day) }
    if (waystones.isNotEmpty()) raise("first_waystone", day)
    if (regions.size > 1) raise("first_region", day)
    val goalWeeks = weeks.count { it.minutes >= it.goal }
    if (goalWeeks >= 4) raise("weeks_4", day)
    if (goalWeeks >= 12) raise("weeks_12", day)
    if (goalWeeks >= 52) raise("weeks_52", day)
    if (pending == null) pending = propose(day)?.also { events += WorldEvent.ProjectProposed(day, it.areaId, it.projectId) }
}

/** 8. Your decisions on project proposals (after today's proposal, so you can answer it the same day). */
internal fun Simulation.decisions(d: Simulation.Day) {
    val day = d.day
    decisionsByDay[day]?.forEach { dcs ->
        val pr = pending ?: return@forEach
        if (pr.areaId != dcs.areaId || pr.projectId != dcs.projectId) return@forEach
        val def = Projects.byId(dcs.projectId) ?: return@forEach
        if (dcs.approve) {
            if (def.cost.affordable(stock(dcs.areaId))) {
                spend(dcs.areaId, def.cost)
                built.getOrPut(dcs.areaId) { mutableListOf() } += def.id
                events += WorldEvent.ProjectBuilt(day, dcs.areaId, def.id)
                pending = null
            }
        } else {
            declinedOn[dcs.areaId to def.id] = day
            pending = null
        }
    }
}

/**
 * One proposal at a time: the most recently visited settlement with something it can build, cheapest first.
 * Declined projects come back after 14 days; nothing ever expires.
 */
private fun Simulation.propose(day: Int): Proposal? {
    val waterCenters = input.landmarks.values.filter { it.kind in Yields.FOOD_KINDS }.mapNotNull { it.center }
    fun nearWater(s: SettlementAcc): Boolean {
        val c = s.position ?: return false
        return waterCenters.any { Geo.distanceM(it, c) < 3_000 } ||
            input.wake.any { Geo.distanceM(Cells.center(it.cell), c) < 3_000 }
    }
    val options = settlements.values.filter { it.tier != Tier.RUINS }.flatMap { s ->
        Projects.ALL.filter { def ->
            s.tier.ordinal >= def.minTier.ordinal &&
                def.id !in built[s.areaId].orEmpty() &&
                (declinedOn[s.areaId to def.id]?.let { day - it >= 14 } ?: true) &&
                (!def.needsWater || nearWater(s))
        }.map { s to it }
    }
    val pick = options.sortedWith(
        compareByDescending<Pair<SettlementAcc, ProjectDef>> { it.first.lastVisit }.thenBy { it.second.cost.gold },
    ).firstOrNull() ?: return null
    return Proposal(day, pick.first.areaId, pick.second.id)
}

/** Bank for gains made where no area is known (rare: before any place data). */
internal const val NO_AREA = "_"

/**
 * Which network each area belongs to: areas joined by a chain of roads you've walked share one network
 * (and so one stock). Built from walked road segments meeting at shared junctions.
 */
internal fun Simulation.networks(): Map<String, String> {
    val parent = HashMap<String, String>()
    fun find(x: String): String { var r = x; while (parent[r] != null && parent[r] != r) r = parent.getValue(r); parent[x] = r; return r }
    fun union(a: String, b: String) { val ra = find(a); val rb = find(b); if (ra != rb) parent[ra] = rb }
    for (e in edges.values) {
        if (e.discoveredDay == null) continue
        val a = "n:${e.info.startNode}"; val b = "n:${e.info.endNode}"
        parent.putIfAbsent(a, a); parent.putIfAbsent(b, b)
        union(a, b)
        areaAt(e.info.midpoint)?.let { area -> val k = "a:$area"; parent.putIfAbsent(k, k); union(k, a) }
    }
    val areaIds = banks.keys + settlements.keys + areas.keys
    return areaIds.associateWith { id -> if (parent.containsKey("a:$id")) find("a:$id") else "solo:$id" }
}

/** What a settlement can spend: the one purse of gold, plus the banks of every area in its network. */
internal fun Simulation.stock(areaId: String): Treasury {
    val net = networks()
    val mine = net[areaId] ?: "solo:$areaId"
    return banks.filterKeys { (net[it] ?: "solo:$it") == mine }.values.fold(Treasury(gold = gold)) { acc, t -> acc + t }
}

/** Pays a cost from the purse and the network's banks, the settlement's own bank first. */
internal fun Simulation.spend(areaId: String, cost: world.wander.engine.Cost) {
    gold -= cost.gold
    val net = networks()
    val mine = net[areaId] ?: "solo:$areaId"
    val order = listOf(areaId) + banks.keys.filter { it != areaId && (net[it] ?: "solo:$it") == mine }.sorted()
    var w = cost.wood.toDouble(); var f = cost.food.toDouble(); var s = cost.stone.toDouble(); var m = cost.metal.toDouble()
    for (id in order) {
        val b = banks[id] ?: continue
        val tw = minOf(w, b.wood); val tf = minOf(f, b.food); val ts = minOf(s, b.stone); val tm = minOf(m, b.metal)
        banks[id] = Treasury(wood = b.wood - tw, food = b.food - tf, stone = b.stone - ts, metal = b.metal - tm)
        w -= tw; f -= tf; s -= ts; m -= tm
    }
}
