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
        val remember = if (s != null && s.tier.ordinal <= s.peakTier.ordinal - 2) p.ruinsRememberMultiplier else 1.0
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

/** 3. Founding: enough on-foot days within the window. */
internal fun Simulation.founding(d: Simulation.Day) {
    for ((areaId, a) in areas) {
        if (areaId in settlements || a.footDays.lastOrNull() != d.day) continue
        val recent = a.footDays.count { it > d.day - p.foundingWindowDays }
        if (recent >= p.foundingDays) found(d.day, areaId, capital = areaId == input.homeAreaId)
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
    val t = computeTier(s, a)
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
