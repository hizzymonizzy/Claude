package world.wander.engine.sim

import world.wander.engine.AreaState
import world.wander.engine.Cells
import world.wander.engine.EdgeState
import world.wander.engine.GoalState
import world.wander.engine.LandmarkState
import world.wander.engine.LatLng
import world.wander.engine.MonumentState
import world.wander.engine.Monuments
import world.wander.engine.PortalLink
import world.wander.engine.Projects
import world.wander.engine.Protection
import world.wander.engine.RegionState
import world.wander.engine.SettlementState
import world.wander.engine.WaystoneState
import world.wander.engine.WorldState
import kotlin.math.pow

/** The replayed history as an immutable [WorldState] for the UI and the dispatch. */
internal fun Simulation.snapshot(): WorldState {
    val areaNames = areaNames()
    val lastSummary = days[today]
    val goalState = GoalState(
        goal = goal,
        locked = input.goalLocked,
        trailing7Minutes = trailing7(today),
        ratio = trailing7(today) / goal,
        protection = lastSummary?.protection ?: Protection.GRACE,
        weeks = weeks,
        weekMinutes = (weekStartOf(today)..today).sumOf { effortByDay[it] ?: 0.0 },
        weekStartDay = weekStartOf(today),
        coolingApplies = weekStartOf(today) >= startDay && weekStartOf(today) + 6 - startDay >= p.graceDays && !isResting(today),
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
                regionId = areaRegion[s.areaId] ?: 0,
                projects = built[s.areaId].orEmpty(),
                prosperity = built[s.areaId].orEmpty().sumOf { Projects.byId(it)?.prosperity ?: 0 },
                dormant = areaRegion[s.areaId]?.let { regions[it].dormant } ?: false,
            )
        },
        goal = goalState,
        events = events,
        days = days,
        areaNames = areaNames,
        landmarks = landmarkFirst.map { (id, first) ->
            val info = input.landmarks.getValue(id)
            LandmarkState(id, info.name, info.kind, info.areaM2, first, landmarkDays[id] ?: 0, landmarkLast[id] ?: first)
        },
        troddenWear = trodden,
        wakeWear = wake,
        regions = regions.map { r ->
            RegionState(
                id = r.id,
                name = r.firstAreaId?.let { areaNames[it] } ?: "Unnamed lands",
                center = r.seed, isHome = r.id == (homeRegion ?: 0), dormant = r.dormant, lastPresenceDay = r.lastPresence,
            )
        },
        waystones = waystones.map { WaystoneState(it.id, it.center, it.great, it.regionId, it.foundDay, it.trips) },
        portals = portals.map { (k, v) -> PortalLink(k.first, k.second, v) },
        treasury = treasury,
        owned = owned.toSet(),
        boughtThisWeek = bought.filterKeys { it.first == world.wander.engine.Shop.week(today) }.mapKeys { it.key.second },
        // The trader waits by the village you visited most recently.
        traderAt = settlements.values.maxByOrNull { it.lastVisit }?.position?.let { LatLng(it.lat + 0.0004, it.lng + 0.0006) },
        proposal = pending,
        abundance = harvest.snapshot(today).keys.filter { it.startsWith("l:") || it.startsWith("m:") || it.startsWith("t:") }
            .map { it.substring(2) }.toSet()
            .associateWith { id -> minOf(harvest.abundance("l:$id", today), harvest.abundance("m:$id", today), harvest.abundance("t:$id", today)) },
        // Places by id; shore cells keep their "s:" prefix.
        gatheredToday = harvest.harvestedOn(today).mapNotNull { k -> when {
            k.startsWith("l:") || k.startsWith("m:") || k.startsWith("t:") -> k.substring(2)
            k.startsWith("s:") -> k
            else -> null
        } }.toSet(),
        shoreWalked = harvest.snapshot(today).filterKeys { it.startsWith("s:") },
        heat = HashMap(heat), heatHood = HashMap(heatHood), heatCounty = HashMap(heatCounty),
        monuments = monuments.map { (id, d) -> Monuments.byId(id).let { MonumentState(it.id, it.name, it.description, d) } },
        exploredAreas = areas.filter { (id, a) -> a.cells.isNotEmpty() || id in settlements }
            .map { (id, a) ->
                val known = a.cells.filter { (familiarity[it] ?: 0.0) > 0 }.ifEmpty { a.cells.toList() }
                var wSum = 0.0; var lat = 0.0; var lng = 0.0
                for (c in known) { val w = familiarity[c] ?: 1.0; val ctr = Cells.center(c); lat += ctr.lat * w; lng += ctr.lng * w; wSum += w }
                AreaState(
                    id = id, name = areaNames[id] ?: "Unnamed wilds",
                    center = settlements[id]?.position ?: LatLng(lat / wSum, lng / wSum),
                    cells = a.cells.size, visitDays = a.areaDays, lastVisitDay = maxOf(a.lastAreaDay, a.lastSeenDay),
                    settled = id in settlements,
                )
            },
    )
}

/** Names: real place names, else the area's most-worn named road. */
private fun Simulation.areaNames(): Map<String, String> {
    val roadsByArea = HashMap<String, MutableList<EdgeAcc>>()
    for (e in edges.values) {
        if (e.discoveredDay == null || e.info.name.isNullOrBlank()) continue
        val areaId = input.cellArea[Cells.cell(e.info.midpoint.lat, e.info.midpoint.lng)] ?: continue
        roadsByArea.getOrPut(areaId) { mutableListOf() }.add(e)
    }
    return (areas.keys + settlements.keys).associateWith { areaId ->
        input.areas[areaId]?.name ?: roadsByArea[areaId]?.maxByOrNull { it.wear }?.info?.name ?: "Unnamed wilds"
    }
}

private fun Simulation.roadLevel(wear: Double): Int {
    var level = 1
    p.wearLevels.forEachIndexed { i, need -> if (wear >= need) level = i + 1 }
    return level
}

/** The Monday of the week containing this day. */
internal fun weekStartOf(day: Int): Int = day - (java.time.LocalDate.ofEpochDay(day.toLong()).dayOfWeek.value - 1)
