package world.wander.engine.sim

import world.wander.engine.Mode
import world.wander.engine.WorldEngine.Companion.mergeIntervals
import world.wander.engine.WorldEngine.Companion.mergedLength
import world.wander.engine.WorldEvent
import world.wander.engine.Yields
import kotlin.math.max
import kotlin.math.min

/** 1. Roads: discovery and wear, and stone from walking them. */
internal fun Simulation.roads(d: Simulation.Day) {
    val day = d.day
    val newlyDiscovered = mutableListOf<String>()
    coverageByDay[day]?.groupBy { it.edgeId }?.forEach { (edgeId, covs) ->
        val info = input.edges[edgeId] ?: return@forEach
        val e = edges.getOrPut(edgeId) { EdgeAcc(info) }
        val len = info.lengthM.coerceAtLeast(1.0)
        val footLen = mergedLength(covs.filter { it.mode == Mode.FOOT }.map { it.fromM to it.toM })
        val cycleLen = mergedLength(covs.filter { it.mode == Mode.CYCLE }.map { it.fromM to it.toM })
        d.gain(areaAt(info.midpoint), stone = harvest.take("e:$edgeId", day, footLen / 1000.0))
        val wearNeed = min(p.wearDayFraction * len, p.wearDayMaxM)
        val boost = homecoming(e.wear, e.best)
        when {
            footLen >= wearNeed -> e.wear += 1.0 * boost
            cycleLen >= wearNeed -> e.wear += p.cyclingWeight * boost
        }
        e.intervals += covs.map { it.fromM to it.toM }
        e.coveredM = mergedLength(e.intervals)
        compact(e)
        if (e.discoveredDay == null && e.coveredM >= min(p.discoveryFraction * len, p.discoveryMaxM)) {
            e.discoveredDay = day
            e.wear = max(e.wear, 1.0)
            newlyDiscovered += edgeId
        }
        e.best = max(e.best, e.wear)
    }
    if (newlyDiscovered.isNotEmpty()) events += WorldEvent.RoadsCharted(day, newlyDiscovered)
}

/** 1b. Named places walked through (wood, food, metal; discovery), your own paths and paddled water. */
internal fun Simulation.places(d: Simulation.Day) {
    val day = d.day
    visitsByDay[day]?.groupBy { it.landmarkId }?.forEach { (id, vs) ->
        val info = input.landmarks[id] ?: return@forEach
        val secs = vs.sumOf { it.seconds }
        val need = if (info.pointLike) p.pointLandmarkSeconds else p.landmarkSeconds
        if (secs < need) return@forEach // passing by isn't a visit
        val area = areaAt(info.center)
        if (info.kind in Yields.WOOD_KINDS) d.gain(area, wood = harvestPlace(id, day, Yields.visitYield("wood", secs)))
        if (info.kind in Yields.FORAGE_KINDS) d.gain(area, food = harvest.take("f:$id", day, Yields.FORAGE_FOOD))
        if (info.kind in Yields.FOOD_NODE_KINDS) d.gain(area, food = harvestPlace(id, day, Yields.visitYield("food", secs)))
        if (info.name.isBlank()) return@forEach // unnamed water: a food source, not a place to discover
        landmarkDays.merge(id, 1, Int::plus)
        landmarkLast[id] = day
        if (info.kind in Yields.METAL_KINDS) d.gain(area, metal = harvest.take("m:$id", day, 1.5))
        if (id !in landmarkFirst) {
            landmarkFirst[id] = day
            events += WorldEvent.LandmarkDiscovered(day, id)
        }
    }
    troddenByDay[day]?.map { it.cell }?.distinct()?.forEach { c -> wearCell(trodden, troddenBest, c) }
    wakeByDay[day]?.map { it.cell }?.distinct()?.forEach { c -> wearCell(wake, wakeBest, c) }
    // Shores: food for every stretch you walked along, like stone from roads; new shore pays double, worn shore less.
    shoreByDay[day]?.map { it.cell }?.distinct()?.forEach { d.gain(areaOfFine(it), food = harvest.take("s:$it", day, Yields.SHORE_FOOD_PER_CELL)) }
}

/** Harvest from a named place; notes when a favourite spot starts to thin. */
private fun Simulation.harvestPlace(id: String, day: Int, base: Double): Double {
    val before = harvest.abundance("l:$id", day)
    val got = harvest.take("l:$id", day, base)
    if (before >= 0.5 && harvest.abundance("l:$id", day) < 0.5) events += WorldEvent.SourceThinning(day, id)
    return got
}

private fun compact(e: EdgeAcc) {
    if (e.intervals.size < 16) return
    val merged = mergeIntervals(e.intervals)
    e.intervals.clear(); e.intervals += merged
}

private fun Simulation.wearCell(wear: HashMap<String, Double>, best: HashMap<String, Double>, cell: String) {
    val v = (wear[cell] ?: 0.0)
    val nv = v + homecoming(v, best[cell] ?: 0.0)
    wear[cell] = nv
    best[cell] = max(best[cell] ?: 0.0, nv)
}
