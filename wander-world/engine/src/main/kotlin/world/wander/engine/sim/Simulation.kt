package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.DaySummary
import world.wander.engine.Harvest
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
internal class Simulation(val input: WorldInput, val p: WorldParams, val today: Int) {
    val startDay = min(input.startDay, today)

    // Observations, indexed by day.
    val coverageByDay = input.coverage.groupBy { it.day }
    val presenceByDay = input.presence.groupBy { it.day }
    val intensityByDay = input.intensity.associate { it.day to (it.moderateMin + p.vigorousWeight * it.vigorousMin) }
    val movementByDay = input.movement.groupBy { it.day }
    val visitsByDay = input.landmarkVisits.groupBy { it.day }
    val troddenByDay = input.trodden.groupBy { it.day }
    val wakeByDay = input.wake.groupBy { it.day }
    val shoreByDay = input.shore.groupBy { it.day }
    val arrivalsByDay = input.arrivals.groupBy { it.day }
    val decisionsByDay = input.decisions.groupBy { it.day }

    // Roads, places, your own paths.
    val edges = HashMap<String, EdgeAcc>()
    val landmarkFirst = HashMap<String, Int>()
    val landmarkDays = HashMap<String, Int>()
    val landmarkLast = HashMap<String, Int>()
    val trodden = HashMap<String, Double>()
    val wake = HashMap<String, Double>()

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
    var lastRaiseWeek = -1_000
    val weeks = mutableListOf<WeekRecord>()

    // Economy.
    /** Gold: one purse for the whole world (effort is effort, wherever you are). */
    var gold = 0.0
    /** Wood, food, stone and metal, banked in the area where they were gathered. */
    val banks = HashMap<String, Treasury>()
    val harvest = Harvest(p.harvest)
    val built = HashMap<String, MutableList<String>>()          // areaId -> project ids
    val declinedOn = HashMap<Pair<String, String>, Int>()       // (areaId, projectId) -> day
    var pending: Proposal? = null
    val monuments = LinkedHashMap<String, Int>()
    var totalKm = 0.0
    var paddledEver = false

    // Output.
    val events = mutableListOf<WorldEvent>()
    val days = HashMap<Int, DaySummary>()

    /** What one day produced so far, handed from step to step. */
    class Day(val day: Int, val resting: Boolean) {
        /** The area you spent most of today in; gains with no clear place go to its bank. */
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
            protection(day)        // 5. decay or restoration
            weeklyGoal(day)        // 6. goal adjustment on Sundays
            economy(day)           // 7. earnings, monuments, next proposal
            decisions(day)         // 8. your answers to proposals (after today's proposal)
            summarize(day)
        }
        return snapshot()
    }

    /** The area a point lies in, if you've been in its cell. */
    fun areaAt(p: LatLng?): String? = p?.let { input.cellArea[Cells.cell(it.lat, it.lng)] }
    /** The area of a fine (res 11) cell. */
    fun areaOfFine(cell: String): String? = try { input.cellArea[Cells.parent(cell, Cells.CELL_RES)] } catch (_: Exception) { null }

    fun trailing7(day: Int) = (day - 6..day).sumOf { intensityByDay[it] ?: 0.0 }
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
            ratio = d.ratio,
            protection = d.protection,
            goal = goal,
        )
    }
}
