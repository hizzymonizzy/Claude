package world.wander.engine

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Deterministic world simulation. Replays every observation day by day from the start of the world
 * to [today] and returns the derived state. Same inputs + same params = same world, always.
 */
class WorldEngine(private val params: WorldParams = WorldParams()) {

    private class EdgeAcc(val info: EdgeInfo) {
        val intervals = mutableListOf<Pair<Double, Double>>()
        var coveredM = 0.0
        var discoveredDay: Int? = null
        var wear = 0.0
    }

    private class SettlementAcc(val areaId: String, val capital: Boolean, val foundedDay: Int) {
        var d = 0.0
        var peakD = 0.0
        var tier = Tier.CAMP
        var peakTier = Tier.CAMP
        var position: LatLng? = null
    }

    private class AreaAcc {
        var d = 0.0
        var lastAreaDay = Int.MIN_VALUE
        var areaDays = 0
        val footDays = mutableListOf<Int>()
        val cells = mutableSetOf<String>()
    }

    fun build(input: WorldInput, today: Int): WorldState {
        val p = params
        val coverageByDay = input.coverage.groupBy { it.day }
        val presenceByDay = input.presence.groupBy { it.day }
        val intensityByDay = input.intensity.associate { it.day to (it.moderateMin + p.vigorousWeight * it.vigorousMin) }
        val movementByDay = input.movement.groupBy { it.day }

        val edges = HashMap<String, EdgeAcc>()
        val familiarity = HashMap<String, Double>()
        val areas = HashMap<String, AreaAcc>()
        val settlements = LinkedHashMap<String, SettlementAcc>()
        val events = mutableListOf<WorldEvent>()
        val days = HashMap<Int, DaySummary>()
        val weeks = mutableListOf<WeekRecord>()

        var goal = p.goalStart
        var lastRaiseWeek = -1_000
        val startDay = min(input.startDay, today)

        fun trailing7(day: Int) = (day - 6..day).sumOf { intensityByDay[it] ?: 0.0 }
        fun isResting(day: Int) = input.restPeriods.any { day in it }
        fun computeTier(s: SettlementAcc, a: AreaAcc): Tier {
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
        fun placeSettlement(s: SettlementAcc, a: AreaAcc) {
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
        fun updateTier(day: Int, s: SettlementAcc, a: AreaAcc) {
            val t = computeTier(s, a)
            if (t != s.tier) {
                events += WorldEvent.TierChanged(day, s.areaId, s.tier, t)
                if (t.ordinal > s.tier.ordinal) placeSettlement(s, a)
                s.tier = t
            }
            if (t.ordinal > s.peakTier.ordinal) s.peakTier = t
            s.peakD = max(s.peakD, s.d)
        }
        fun found(day: Int, areaId: String, capital: Boolean) {
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

        for (day in startDay..today) {
            val resting = isResting(day)

            // 1. Roads: discovery and wear.
            val newlyDiscovered = mutableListOf<String>()
            coverageByDay[day]?.groupBy { it.edgeId }?.forEach { (edgeId, covs) ->
                val info = input.edges[edgeId] ?: return@forEach
                val e = edges.getOrPut(edgeId) { EdgeAcc(info) }
                val len = info.lengthM.coerceAtLeast(1.0)
                val footLen = mergedLength(covs.filter { it.mode == Mode.FOOT }.map { it.fromM to it.toM })
                val cycleLen = mergedLength(covs.filter { it.mode == Mode.CYCLE }.map { it.fromM to it.toM })
                val wearNeed = min(p.wearDayFraction * len, p.wearDayMaxM)
                when {
                    footLen >= wearNeed -> e.wear += 1.0
                    cycleLen >= wearNeed -> e.wear += p.cyclingWeight
                }
                e.intervals += covs.map { it.fromM to it.toM }
                e.coveredM = mergedLength(e.intervals)
                compact(e)
                if (e.discoveredDay == null && e.coveredM >= min(p.discoveryFraction * len, p.discoveryMaxM)) {
                    e.discoveredDay = day
                    e.wear = max(e.wear, 1.0)
                    newlyDiscovered += edgeId
                }
            }
            if (newlyDiscovered.isNotEmpty()) events += WorldEvent.RoadsCharted(day, newlyDiscovered)

            // 2. Cells and areas: familiarity and development.
            val footByArea = HashMap<String, Double>()
            val cycleByArea = HashMap<String, Double>()
            presenceByDay[day]?.groupBy { it.cell }?.forEach { (cell, ps) ->
                val foot = ps.filter { it.mode == Mode.FOOT }.sumOf { it.seconds }
                val cycle = ps.filter { it.mode == Mode.CYCLE }.sumOf { it.seconds }
                when {
                    foot >= p.cellPresenceSeconds -> familiarity.merge(cell, 1.0, Double::plus)
                    cycle >= p.cellPresenceSeconds -> familiarity.merge(cell, p.cyclingWeight, Double::plus)
                }
                val areaId = input.cellArea[cell] ?: return@forEach
                areas.getOrPut(areaId) { AreaAcc() }.cells += cell
                footByArea.merge(areaId, foot / 60.0, Double::plus)
                cycleByArea.merge(areaId, cycle / 60.0, Double::plus)
            }
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
                    if (s != null) s.d += gain * remember
                }
                // Capital: founded the first day you move at home.
                if (s == null && areaId == input.homeAreaId && footMin * 60 >= p.cellPresenceSeconds) {
                    a.lastAreaDay = day
                    found(day, areaId, capital = true)
                }
            }

            // 3. Founding: enough on-foot days within the window.
            for ((areaId, a) in areas) {
                if (areaId in settlements || a.footDays.lastOrNull() != day) continue
                val recent = a.footDays.count { it > day - p.foundingWindowDays }
                if (recent >= p.foundingDays) found(day, areaId, capital = areaId == input.homeAreaId)
            }

            // 4. Growth tier changes.
            for (s in settlements.values) updateTier(day, s, areas.getValue(s.areaId))

            // 5. Protection: decay or restoration, at the end of the day.
            val minutes7 = trailing7(day)
            val ratio = minutes7 / goal
            val inGrace = day - startDay < p.graceDays
            val protection = when {
                resting -> Protection.RESTING
                inGrace -> Protection.GRACE
                ratio > 1.0 -> Protection.RESTORING
                ratio >= 1.0 -> Protection.HOLDING
                ratio >= 0.8 -> Protection.SLOWED
                else -> Protection.DECAYING
            }
            if (!resting && !inGrace) {
                val m = decayMultiplier(ratio)
                for (s in settlements.values) {
                    if (m > 0) {
                        val floorD = p.tierThresholds[floorTier(s).ordinal]
                        val loss = max(0.0, s.d - floorD) * p.baseDecayDaily * m * p.sturdiness[s.tier.ordinal]
                        s.d -= loss
                    }
                    if (ratio > 1.0) {
                        s.d += (s.peakD - s.d).coerceAtLeast(0.0) * p.restoreDaily * (min(ratio, 1.5) - 1.0) / 0.5
                    }
                    updateTier(day, s, areas.getValue(s.areaId))
                }
                if (ratio < 0.8) {
                    for (e in edges.values) if (e.discoveredDay != null) {
                        e.wear = max(1.0, e.wear - e.wear * p.wearDecayWeekly / 7.0 * m)
                    }
                }
            }

            // 6. Weekly goal adjustment, at the end of each Sunday.
            val date = LocalDate.ofEpochDay(day.toLong())
            if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                val weekStart = day - 6
                val weekMinutes = (weekStart..day).sumOf { intensityByDay[it] ?: 0.0 }
                weeks += WeekRecord(weekStart, goal, weekMinutes)
                val eligible = weeks.filter { it.weekStartDay >= startDay }
                val restingWeek = (weekStart..day).any { isResting(it) }
                if (!input.goalLocked && !restingWeek) {
                    val old = goal
                    val last4 = eligible.takeLast(4)
                    val last2 = eligible.takeLast(2)
                    val cooledDown = weeks.size - 1 - lastRaiseWeek >= p.goalUpCooldownWeeks
                    if (cooledDown && last4.size == 4 && last4.count { it.minutes >= p.goalUpRatio * goal } >= p.goalUpWeeksNeeded) {
                        goal = min(p.goalCeiling, round10(goal * p.goalUpStep))
                        if (goal != old) lastRaiseWeek = weeks.size - 1
                    } else if (last2.size == 2 && last2.all { it.minutes < p.goalDownRatio * goal }) {
                        val lowered = max(p.goalFloor, round10(last2.map { it.minutes }.average()))
                        if (lowered < goal) goal = lowered
                    }
                    if (goal != old) events += WorldEvent.GoalChanged(day, old, goal)
                }
            }
            if (input.goalLocked && input.lockedGoal != null) goal = input.lockedGoal

            val mv = movementByDay[day]
            days[day] = DaySummary(
                day = day,
                footMeters = mv?.sumOf { it.footMeters } ?: 0.0,
                cycleMeters = mv?.sumOf { it.cycleMeters } ?: 0.0,
                intensityMinutes = intensityByDay[day] ?: 0.0,
                ratio = ratio,
                protection = protection,
                goal = goal,
            )
        }

        // Names: real place names, else the area's most-worn named road.
        val areaNames = HashMap<String, String>()
        val roadsByArea = HashMap<String, MutableList<EdgeAcc>>()
        for (e in edges.values) {
            if (e.discoveredDay == null || e.info.name.isNullOrBlank()) continue
            val areaId = input.cellArea[Cells.cell(e.info.midpoint.lat, e.info.midpoint.lng)] ?: continue
            roadsByArea.getOrPut(areaId) { mutableListOf() }.add(e)
        }
        for (areaId in areas.keys + settlements.keys) {
            areaNames[areaId] = input.areas[areaId]?.name
                ?: roadsByArea[areaId]?.maxByOrNull { it.wear }?.info?.name
                ?: "Unnamed wilds"
        }

        val lastSummary = days[today]
        val goalState = GoalState(
            goal = goal,
            locked = input.goalLocked,
            trailing7Minutes = trailing7(today),
            ratio = trailing7(today) / goal,
            protection = lastSummary?.protection ?: Protection.GRACE,
            weeks = weeks,
        )

        return WorldState(
            today = today,
            edges = edges.mapValues { (_, e) ->
                EdgeState(
                    id = e.info.id,
                    name = e.info.name,
                    discovered = e.discoveredDay != null,
                    discoveredDay = e.discoveredDay,
                    wear = e.wear,
                    level = if (e.discoveredDay == null) 0 else roadLevel(e.wear),
                    coveredM = e.coveredM,
                    lengthM = e.info.lengthM,
                )
            },
            cellFamiliarity = familiarity,
            settlements = settlements.values.map { s ->
                val a = areas.getValue(s.areaId)
                SettlementState(
                    areaId = s.areaId,
                    name = areaNames[s.areaId] ?: "Unnamed wilds",
                    isCapital = s.capital,
                    tier = s.tier,
                    peakTier = s.peakTier,
                    development = s.d,
                    peakDevelopment = s.peakD,
                    foundedDay = s.foundedDay,
                    lastAreaDay = a.lastAreaDay,
                    areaDays = a.areaDays,
                    exploredCells = a.cells.count { (familiarity[it] ?: 0.0) >= 1.0 },
                    liveliness = 0.5.pow((today - a.lastAreaDay).coerceAtLeast(0) / p.livelinessHalfLifeDays),
                    position = s.position ?: LatLng(0.0, 0.0),
                )
            },
            goal = goalState,
            events = events,
            days = days,
            areaNames = areaNames,
        )
    }

    private fun floorTier(s: SettlementAcc): Tier {
        val peak = s.peakTier.ordinal
        val floor = if (s.capital) min(peak, max(params.capitalMinFloorTier, peak - 2)) else max(0, peak - 2)
        return Tier.entries[floor]
    }

    private fun roadLevel(wear: Double): Int {
        var level = 1
        params.wearLevels.forEachIndexed { i, need -> if (wear >= need) level = i + 1 }
        return level
    }

    private fun round10(v: Double): Int = ((v / 10.0).roundToInt() * 10)

    private fun compact(e: EdgeAcc) {
        if (e.intervals.size < 16) return
        val merged = mergeIntervals(e.intervals)
        e.intervals.clear(); e.intervals += merged
    }

    companion object {
        fun decayMultiplier(ratio: Double): Double = when {
            ratio >= 1.0 -> 0.0
            ratio >= 0.8 -> 0.5 * (1.0 - ratio)
            else -> 1.0 - ratio.coerceAtLeast(0.0)
        }

        fun mergeIntervals(list: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
            if (list.isEmpty()) return emptyList()
            val sorted = list.map { min(it.first, it.second) to max(it.first, it.second) }.sortedBy { it.first }
            val out = mutableListOf(sorted.first())
            for (iv in sorted.drop(1)) {
                val last = out.last()
                if (iv.first <= last.second + 1.0) out[out.size - 1] = last.first to max(last.second, iv.second)
                else out += iv
            }
            return out
        }

        fun mergedLength(list: List<Pair<Double, Double>>): Double =
            mergeIntervals(list).sumOf { it.second - it.first }
    }
}
