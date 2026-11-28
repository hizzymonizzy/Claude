package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.Geo
import world.wander.engine.LatLng
import world.wander.engine.Protection
import world.wander.engine.Tier
import world.wander.engine.WorldEvent
import kotlin.math.max
import kotlin.math.min

/** 2. Cells and areas: familiarity, regions, and area development from time on foot. */
internal fun Simulation.areas(d: Simulation.Day) {
    val day = d.day
    val footByArea = HashMap<String, Double>()
    val cycleByArea = HashMap<String, Double>()
    presenceByDay[day]?.groupBy { it.cell }?.forEach { (cell, ps) ->
        val foot = ps.filter { it.mode == world.wander.engine.Mode.FOOT }.sumOf { it.seconds }
        val cycle = ps.filter { it.mode == world.wander.engine.Mode.CYCLE }.sumOf { it.seconds }
        when {
            foot >= p.cellPresenceSeconds -> familiarity.merge(cell, 1.0, Double::plus)
            cycle >= p.cellPresenceSeconds -> familiarity.merge(cell, p.cyclingWeight, Double::plus)
        }
        val rid = cellRegion.getOrPut(cell) {
            val from = lastRegion
            val (r, created) = regionFor(centerOf(cell), day)
            if (created) announceRegion(r, day, from)
            r
        }
        touchRegion(rid, day)
        val areaId = input.cellArea[cell] ?: return@forEach
        areaRegion.putIfAbsent(areaId, rid)
        if (regions[rid].firstAreaId == null) regions[rid].firstAreaId = areaId
        areas.getOrPut(areaId) { AreaAcc() }.also { it.cells += cell; it.lastSeenDay = day }
        footByArea.merge(areaId, foot / 60.0, Double::plus)
        cycleByArea.merge(areaId, cycle / 60.0, Double::plus)
    }
    d.mainArea = (footByArea.keys + cycleByArea.keys).maxByOrNull { (footByArea[it] ?: 0.0) + (cycleByArea[it] ?: 0.0) }
    for (areaId in footByArea.keys + cycleByArea.keys) {
        val a = areas.getOrPut(areaId) { AreaAcc() }
        val footMin = footByArea[areaId] ?: 0.0
        val cycleMin = cycleByArea[areaId] ?: 0.0
        val s = settlements[areaId]
        // Homecoming (0.12.1): any settlement below its best tier counts double until it's back, like roads and hexes;
        // a shrine makes it triple (0.13). A lean-to makes every visit-day count 25% extra.
        val list = built[areaId].orEmpty()
        val remember = (if (s != null && s.tier.ordinal < s.peakTier.ordinal) (if ("shrine" in list) 3.0 else p.ruinsRememberMultiplier) else 1.0) *
            (if ("lean_to" in list) 1.25 else 1.0)
        val gain = when {
            footMin >= p.areaDayMinutes -> {
                a.footDays += day
                1.0 + p.areaDayBonus * min(1.0, (footMin - p.areaDayMinutes) / p.areaDayBonusMinutes)
            }
            cycleMin >= p.areaDayMinutes -> p.cyclingWeight
            else -> 0.0
        }
        if (gain > 0) {
            a.d += gain * remember
            a.areaDays++
            a.lastAreaDay = day
            if (s != null) { s.d += gain * remember; s.lastVisit = day }
        }
        // Capital: founded the first day you move at home.
        if (s == null && areaId == input.homeAreaId && footMin * 60 >= p.cellPresenceSeconds) {
            a.lastAreaDay = day
            found(day, areaId, capital = true)
        }
    }
}

/**
 * 3. Camps by stays (0.15). A camp is pitched wherever you spend a night away from home. A night within an existing
 * camp's reach counts toward it; one or more nights in a row there make one stay, and camps grow by stays, never by
 * walking (Camp 1, Hamlet 3, Village 10, Town 30, City 100). Reach grows with the tier (2–6 neighbourhood hexes);
 * camps whose reaches meet merge, adding up their stays (the one with more keeps its name, place and buildings).
 * The capital is still founded the first day you move at home (see areas()).
 */
internal fun Simulation.founding(d: Simulation.Day) {
    val day = d.day
    for (n in nightsByDay[day].orEmpty()) {
        val pt = LatLng(n.lat, n.lng)
        if (input.cellArea[Cells.cell(n.lat, n.lng)]?.let { it == input.homeAreaId } == true) continue
        if (input.homePoint?.let { Geo.distanceM(it, pt) < p.campMinFromHomeM } == true) continue
        val hood = Cells.cellAt(n.lat, n.lng, Cells.HOOD_RES)
        val camp = settlements.values.filter { it.camp && it.hood != null }
            .map { it to hexDistance(it.hood!!, hood) }.filter { (s, dist) -> dist <= reach(s) }
            .minByOrNull { it.second }?.first
        if (camp == null) {
            foundCamp(day, hood, pt)
        } else {
            val newStay = camp.lastNight != day - 1
            camp.lastNight = day
            camp.lastVisit = day
            areas.getValue(camp.areaId).lastAreaDay = day
            if (newStay) {
                camp.stays++
                camp.d += if (camp.tier.ordinal < camp.peakTier.ordinal) (if ("shrine" in built[camp.areaId].orEmpty()) 3.0 else p.ruinsRememberMultiplier) else 1.0
                events += WorldEvent.CampedOvernight(day, camp.areaId)
            }
        }
    }
    mergeCamps(day)
}

private fun Simulation.reach(s: SettlementAcc) = p.campReach[s.tier.ordinal]

private fun hexDistance(a: String, b: String): Int = try { Cells.distance(a, b) } catch (_: Exception) { Int.MAX_VALUE }

private fun Simulation.foundCamp(day: Int, hood: String, pt: LatLng) {
    val id = "camp:$hood"
    if (id in settlements) return
    val a = areas.getOrPut(id) { AreaAcc() }
    a.lastAreaDay = day
    val s = SettlementAcc(id, false, day).apply {
        camp = true; this.hood = hood; position = pt; lastNight = day; stays = 1; d = 1.0; peakD = 1.0
        nearArea = input.cellArea[Cells.cell(pt.lat, pt.lng)]
        tier = Tier.CAMP; peakTier = Tier.CAMP
    }
    settlements[id] = s
    val (rid, created) = regionFor(pt, day)
    if (created) announceRegion(rid, day, lastRegion)
    areaRegion[id] = rid
    events += WorldEvent.Founded(day, id, false)
    events += WorldEvent.CampedOvernight(day, id)
}

/** Camps whose reaches meet become one: stays add up; the one with more stays keeps its name, place and buildings. */
private fun Simulation.mergeCamps(day: Int) {
    var merged = true
    while (merged) {
        merged = false
        val camps = settlements.values.filter { it.camp && it.hood != null }
        loop@ for (i in camps.indices) for (j in i + 1 until camps.size) {
            val a = camps[i]; val b = camps[j]
            if (hexDistance(a.hood!!, b.hood!!) > reach(a) + reach(b)) continue
            val (keep, gone) = if (a.stays >= b.stays) a to b else b to a
            keep.stays += gone.stays
            keep.d += gone.d
            keep.peakD = max(keep.peakD, keep.d)
            keep.lastNight = max(keep.lastNight, gone.lastNight)
            keep.lastVisit = max(keep.lastVisit, gone.lastVisit)
            built[gone.areaId]?.let { list -> val into = built.getOrPut(keep.areaId) { mutableListOf() }; list.filter { it !in into }.forEach { into += it } }
            built.remove(gone.areaId)
            if (pending?.areaId == gone.areaId) pending = null
            settlements.remove(gone.areaId)
            events += WorldEvent.CampsMerged(day, keep.areaId, gone.areaId)
            updateTier(day, keep, areas.getValue(keep.areaId))
            merged = true
            break@loop
        }
    }
}

/** 4. Growth tier changes. */
internal fun Simulation.growth(d: Simulation.Day) {
    for (s in settlements.values) updateTier(d.day, s, areas.getValue(s.areaId))
}

/** 5. Protection: decay or restoration at the end of the day, by the weekly-goal ratio. */
internal fun Simulation.protection(d: Simulation.Day) {
    val day = d.day
    val ratio = trailing7(day) / goal
    val inGrace = day - startDay < p.graceDays
    d.ratio = ratio
    d.protection = when {
        d.resting -> Protection.RESTING
        inGrace -> Protection.GRACE
        ratio > 1.0 -> Protection.RESTORING
        ratio >= 1.0 -> Protection.HOLDING
        ratio >= 0.8 -> Protection.SLOWED
        else -> Protection.DECAYING
    }
    // Decay and restoration happen once a week now, in cooling() (ADR 0008).
}

private fun Simulation.computeTier(s: SettlementAcc, a: AreaAcc): Tier {
    if (s.camp) {
        var t = Tier.CAMP
        for (tier in Tier.entries) if (tier.ordinal <= Tier.CITY.ordinal && s.d >= p.campStayThresholds[tier.ordinal] && tier.ordinal >= Tier.CAMP.ordinal) t = tier
        val floor = floorTier(s)
        return if (t.ordinal < floor.ordinal) floor else t
    }
    val breadth = a.cells.count { (familiarity[it] ?: 0.0) >= 1.0 }
    val maxTier = if (s.capital) Tier.METROPOLIS else Tier.CITY
    var t = Tier.RUINS
    for (tier in Tier.entries) {
        if (tier.ordinal > maxTier.ordinal) break
        if (s.d >= p.tierThresholds[tier.ordinal] && breadth >= p.tierBreadth[tier.ordinal]) t = tier
    }
    val floor = floorTier(s)
    if (t.ordinal < floor.ordinal) t = floor
    if (s.capital && t.ordinal < Tier.CAMP.ordinal) t = Tier.CAMP
    return t
}

/** A settlement sits on its most-walked cell near the centre of what you know; the capital sits at home. */
private fun Simulation.placeSettlement(s: SettlementAcc, a: AreaAcc) {
    if (s.capital && input.homePoint != null) { s.position = input.homePoint; return }
    if (s.camp) return   // a camp stays where you first slept
    val cells = a.cells.filter { (familiarity[it] ?: 0.0) > 0 }.ifEmpty { a.cells.toList() }
    if (cells.isEmpty()) return
    var wSum = 0.0; var lat = 0.0; var lng = 0.0
    val centers = cells.associateWith { Cells.center(it) }
    for (c in cells) {
        val w = familiarity[c] ?: 1.0
        lat += centers.getValue(c).lat * w; lng += centers.getValue(c).lng * w; wSum += w
    }
    val centroid = LatLng(lat / wSum, lng / wSum)
    s.position = centers.values.minBy { Geo.distanceM(it, centroid) }
}

internal fun Simulation.updateTier(day: Int, s: SettlementAcc, a: AreaAcc) {
    var t = computeTier(s, a)
    // Cooling never costs a tier until a whole tier's worth is lost since the peak (0.12.1, partial decay).
    if (t.ordinal < s.tier.ordinal && s.d < s.peakD) {
        val held = Tier.entries[(s.peakTier.ordinal - stepsLost(StepScale(if (s.camp) p.campStayThresholds else p.tierThresholds), s.d, s.peakD)).coerceIn(0, s.tier.ordinal)]
        if (held.ordinal > t.ordinal) t = held
    }
    if (t != s.tier) {
        events += WorldEvent.TierChanged(day, s.areaId, s.tier, t)
        if (t.ordinal > s.tier.ordinal) placeSettlement(s, a)
        s.tier = t
    }
    if (t.ordinal > s.peakTier.ordinal) s.peakTier = t
    s.peakD = max(s.peakD, s.d)
}

private fun Simulation.found(day: Int, areaId: String, capital: Boolean) {
    val a = areas.getOrPut(areaId) { AreaAcc() }
    val s = SettlementAcc(areaId, capital, day)
    s.d = a.d
    s.peakD = a.d
    s.tier = computeTier(s, a).let { if (it.ordinal < Tier.CAMP.ordinal) Tier.CAMP else it }
    s.peakTier = s.tier
    settlements[areaId] = s
    placeSettlement(s, a)
    events += WorldEvent.Founded(day, areaId, capital)
}

/** Nothing falls more than two tiers below its peak; the capital keeps at least its floor tier once reached. */
internal fun Simulation.floorTier(s: SettlementAcc): Tier {
    val peak = s.peakTier.ordinal
    val floor = if (s.capital) min(peak, max(p.capitalMinFloorTier, peak - 2)) else max(0, peak - 2)
    return Tier.entries[floor]
}
