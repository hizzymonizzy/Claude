package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.DaySummary
import world.wander.engine.Harvest
import world.wander.engine.Projects
import world.wander.engine.LatLng
import world.wander.engine.Proposal
import world.wander.engine.Protection
import world.wander.engine.Treasury
import world.wander.engine.WeekRecord
import world.wander.engine.WorldEvent
import world.wander.engine.WorldInput
import world.wander.engine.WorldParams
import world.wander.engine.WorldState
import kotlin.math.min

/**
 * One replay of history: all the mutable state, and the day loop. Each step of a day lives in its own file
 * (Regions, RoadsAndPlaces, Settlements, Goal, Economy) as an extension on this class; [snapshot] turns the
 * result into an immutable [WorldState]. The order of steps in [run] is part of the rules.
 */
/** Regrowth speed of thinned sources in an area with a well or fountain. */
internal const val WELL_REGROWTH = 1.5

internal class Simulation(val input: WorldInput, val p: WorldParams, val today: Int) {
    val startDay = min(input.startDay, today)

    // Observations, indexed by day.
    val coverageByDay = input.coverage.groupBy { it.day }
    val presenceByDay = input.presence.groupBy { it.day }
    val intensityByDay = input.intensity.associate { it.day to (it.moderateMin + p.vigorousWeight * it.vigorousMin) }
    private val rawIntensityByDay = input.intensity.associate { it.day to (it.moderateMin + it.vigorousMin) }
    private val activeMinutesByDay = input.presence.groupBy { it.day }.mapValues { (_, ps) -> ps.sumOf { it.seconds } / 60.0 }
    /**
     * The weekly goal's measure (0.12): every active minute counts once (walking, cycling, paddling, from your
     * recordings, or the watch's intensity minutes if more), and intensity adds half a minute on top per intensity
     * minute (vigorous counts double), so moderate minutes count 1.5× and vigorous 2×.
     */
    val effortByDay: Map<Int, Double> = (activeMinutesByDay.keys + intensityByDay.keys).associateWith { day ->
        maxOf(activeMinutesByDay[day] ?: 0.0, rawIntensityByDay[day] ?: 0.0) + p.intensityBonus * (intensityByDay[day] ?: 0.0)
    }
    val movementByDay = input.movement.groupBy { it.day }
    val visitsByDay = input.landmarkVisits.groupBy { it.day }
    val troddenByDay = input.trodden.groupBy { it.day }
    val wakeByDay = input.wake.groupBy { it.day }
    val shoreByDay = input.shore.groupBy { it.day }
    val heatByDay = input.heat.groupBy { it.day }
    val arrivalsByDay = input.arrivals.groupBy { it.day }
    val nightsByDay = input.nights.groupBy { it.day }
    val decisionsByDay = input.decisions.groupBy { it.day }

    // Roads, places, your own paths.
    val edges = HashMap<String, EdgeAcc>()
    val landmarkFirst = HashMap<String, Int>()
    val landmarkDays = HashMap<String, Int>()
    val landmarkLast = HashMap<String, Int>()
    val trodden = HashMap<String, Double>()
    val wake = HashMap<String, Double>()
    val troddenBest = HashMap<String, Double>()
    val wakeBest = HashMap<String, Double>()
    // The heat map (ADR 0007): distinct days walked per hex at three sizes, cooling like roads.
    val heat = HashMap<String, Double>(); val heatBest = HashMap<String, Double>()
    val heatHood = HashMap<String, Double>(); val heatHoodBest = HashMap<String, Double>()
    val heatCounty = HashMap<String, Double>(); val heatCountyBest = HashMap<String, Double>()

    // Cells, areas, settlements.
    val familiarity = HashMap<String, Double>()
    val areas = HashMap<String, AreaAcc>()
    val settlements = LinkedHashMap<String, SettlementAcc>()

    // Regions, dormancy and waystones.
    val regions = mutableListOf<RegionAcc>()
    val cellRegion = HashMap<String, Int>()
    val areaRegion = HashMap<String, Int>()
    val waystones = mutableListOf<WaystoneAcc>()
    val portals = HashMap<Pair<Int, Int>, Int>()
    var lastRegion: Int? = null
    var homeRegion: Int? = null
    private val centerCache = HashMap<String, LatLng>()

    // Goal.
    var goal = p.goalStart
    /** The highest goal ever reached: its band stays as a title, never taken away. */
    var bestGoal = p.goalStart
    var lastRaiseWeek = -1_000
    val weeks = mutableListOf<WeekRecord>()

    // Economy.
    /** One shared stock of everything (0.11: per-area banks were simpler to drop than to manage). */
    var treasury = Treasury()
    /** Cosmetics you own. */
    val owned = LinkedHashSet<String>()
    /** Units of each resource bought per (week, resource), for the weekly price climb. */
    val bought = HashMap<Pair<Int, String>, Int>()
    val purchasesByDay = input.purchases.groupBy { it.day }
    // A well or fountain in an area makes thinned sources there regrow faster.
    val harvest = Harvest(p.harvest) { key ->
        val list = areaOfSource(key)?.let { built[it] }.orEmpty()
        when { "fountain" in list -> 2.0; "well" in list -> WELL_REGROWTH; else -> 1.0 }
    }
    val built = HashMap<String, MutableList<String>>()          // areaId -> project ids
    val declinedOn = HashMap<Pair<String, String>, Int>()       // (areaId, projectId) -> day
    var pending: Proposal? = null
    val monuments = LinkedHashMap<String, Int>()
    var totalKm = 0.0
    var paddledKm = 0.0
    // The development story (0.13).
    val activeEncounters = mutableListOf<world.wander.engine.EncounterState>()
    var encounterSeq = 0
    val encounterProgress = HashMap<Int, Int>()
    val seenHex = HashSet<String>()
    var request: world.wander.engine.TraderRequest? = null
    val achievementTiers = HashMap<String, Int>()
    var paddledEver = false

    // Output.
    val events = mutableListOf<WorldEvent>()
    val days = HashMap<Int, DaySummary>()

    /** What one day produced so far, handed from step to step. */
    class Day(val day: Int, val resting: Boolean) {
        /** The area you spent most of today in; gains with no clear place count toward its perks. */
        var mainArea: String? = null
        /** Today's gains, by the area they were gathered in (null = today's main area). */
        val gains = HashMap<String?, Treasury>()
        fun gain(area: String?, wood: Double = 0.0, food: Double = 0.0, stone: Double = 0.0, metal: Double = 0.0) {
            gains[area] = (gains[area] ?: Treasury()) + Treasury(wood = wood, food = food, stone = stone, metal = metal)
        }
        var ratio = 0.0
        var protection = Protection.GRACE
        var earned = Treasury()
    }

    fun run(): WorldState {
        for (d in startDay..today) {
            val day = Day(d, isResting(d))
            arrivals(day)          // 0. car or plane arrivals: waystones and new regions
            roads(day)             // 1. road discovery and wear
            places(day)            // 1b. named places, your own paths, water
            areas(day)             // 2. cell familiarity and area development
            founding(day)          // 3. new settlements
            growth(day)            // 4. tier changes
            quietRegions(day)      // 4b. home region; regions going dormant
            protection(day)        // 5. how this week is going (label only)
            cooling(day)           // 5b. Tuesday: last week scored; places fade or recover (ADR 0008)
            weeklyGoal(day)        // 6. Tuesday: goal adjustment from last week
            economy(day)           // 7. earnings, monuments, next proposal
            story(day)             // 8. encounters, the trader's request, looks, achievements (0.13)
            actions(day)           // 9. what you did today, in the order you did it: purchases, trades, answers (0.14.1)
            summarize(day)
        }
        return snapshot()
    }

    private val sourceArea = HashMap<String, String?>()
    /** The area a harvest source ("l:id", "s:cell", "e:edge"…) lies in. */
    fun areaOfSource(key: String): String? = sourceArea.getOrPut(key) {
        val id = key.substringAfter(':')
        when (key.substringBefore(':')) {
            "l", "m", "f" -> areaAt(input.landmarks[id]?.center)
            "s", "w" -> areaOfFine(id)
            "e", "t" -> areaAt(input.edges[id]?.midpoint)
            else -> null
        }
    }

    /** The area a point lies in, if you've been in its cell. */
    fun areaAt(p: LatLng?): String? = p?.let { input.cellArea[Cells.cell(it.lat, it.lng)] }
    /** The area of a fine (res 11) cell. */
    fun areaOfFine(cell: String): String? = try { input.cellArea[Cells.parent(cell, Cells.CELL_RES)] } catch (_: Exception) { null }

    fun trailing7(day: Int) = (day - 6..day).sumOf { effortByDay[it] ?: 0.0 }
    /** Wear step 0–5 (fog, trail … avenue) for a wear value. */
    fun step(v: Double): Int = p.wearLevels.count { v >= it }
    /** Walking a place that has cooled below its best step counts double until it's back (homecoming). */
    fun homecoming(v: Double, best: Double): Double = if (step(v) < step(best)) 2.0 else 1.0
    fun isResting(day: Int) = input.restPeriods.any { day in it }
    fun centerOf(cell: String) = centerCache.getOrPut(cell) { Cells.center(cell) }
    fun raise(id: String, day: Int) { if (id !in monuments) { monuments[id] = day; events += WorldEvent.MonumentRaised(day, id) } }

    private fun summarize(d: Day) {
        val mv = movementByDay[d.day]
        days[d.day] = DaySummary(
            day = d.day,
            footMeters = mv?.sumOf { it.footMeters } ?: 0.0,
            cycleMeters = mv?.sumOf { it.cycleMeters } ?: 0.0,
            waterMeters = mv?.sumOf { it.waterMeters } ?: 0.0,
            earned = d.earned,
            intensityMinutes = intensityByDay[d.day] ?: 0.0,
            activeMinutes = effortByDay[d.day] ?: 0.0,
            ratio = d.ratio,
            protection = d.protection,
            goal = goal,
        )
    }
}
