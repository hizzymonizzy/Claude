package world.wander.engine

/** How you were moving. Only on-foot movement establishes settlements; cycling is for discovery. */
enum class Mode { FOOT, CYCLE }

enum class Tier(val label: String) {
    RUINS("Ruins"), CAMP("Camp"), HAMLET("Hamlet"), VILLAGE("Village"),
    TOWN("Town"), CITY("City"), METROPOLIS("Metropolis");
}

enum class Protection(val label: String) {
    DECAYING("Nature is stirring"),
    SLOWED("Decay slowed"),
    HOLDING("Holding strong"),
    RESTORING("Restoring"),
    RESTING("Resting"),
    GRACE("New world grace"),
}

/** A road segment between intersections, from OpenStreetMap. */
data class EdgeInfo(
    val id: String,
    val name: String?,
    val lengthM: Double,
    val startNode: Long,
    val endNode: Long,
    val midpoint: LatLng,
)

// ---- Observations: the source of truth. World state is always derived from these. ----

/** Part of an edge covered on a given day, in meters along the edge. */
data class EdgeCoverage(val edgeId: String, val day: Int, val fromM: Double, val toM: Double, val mode: Mode)

/** Seconds spent moving inside an H3 cell on a given day. */
data class CellPresence(val cell: String, val day: Int, val seconds: Double, val mode: Mode)

/** Intensity minutes from Health Connect for a day (epoch day). */
data class DayIntensity(val day: Int, val moderateMin: Double, val vigorousMin: Double)

/** Outdoor distance moved per day, for the dispatch. */
data class DayMovement(val day: Int, val footMeters: Double, val cycleMeters: Double)

data class AreaInfo(val id: String, val name: String?, val placeType: String?)

data class WorldInput(
    /** Epoch day the world began (install day). */
    val startDay: Int,
    val homeAreaId: String?,
    val edges: Map<String, EdgeInfo>,
    val coverage: List<EdgeCoverage>,
    val presence: List<CellPresence>,
    val intensity: List<DayIntensity>,
    val movement: List<DayMovement>,
    /** H3 res-9 cell -> area id. */
    val cellArea: Map<String, String>,
    val areas: Map<String, AreaInfo>,
    val restPeriods: List<IntRange> = emptyList(),
    val goalLocked: Boolean = false,
    /** Optional fixed goal when locked; null = whatever the goal had adapted to. */
    val lockedGoal: Int? = null,
)

/** All tuning numbers in one place. Changing them and replaying rebuilds the world. */
data class WorldParams(
    val discoveryFraction: Double = 0.6,
    val discoveryMaxM: Double = 150.0,
    val wearDayFraction: Double = 0.5,
    val wearDayMaxM: Double = 150.0,
    val cyclingWeight: Double = 0.25,
    val cellPresenceSeconds: Double = 60.0,
    val areaDayMinutes: Double = 5.0,
    val areaDayBonusMinutes: Double = 30.0,
    val areaDayBonus: Double = 0.5,
    val foundingDays: Int = 3,
    val foundingWindowDays: Int = 30,
    /** D needed for each tier, indexed by Tier.ordinal. */
    val tierThresholds: List<Double> = listOf(0.0, 3.0, 10.0, 25.0, 60.0, 150.0, 400.0),
    /** Distinct explored cells needed for each tier. */
    val tierBreadth: List<Int> = listOf(0, 1, 3, 6, 12, 25, 40),
    val baseDecayDaily: Double = 0.0073,
    /** Decay multiplier by tier; bigger places are sturdier. */
    val sturdiness: List<Double> = listOf(1.0, 1.0, 0.8, 0.6, 0.45, 0.3, 0.2),
    val restoreDaily: Double = 0.04,
    val ruinsRememberMultiplier: Double = 2.0,
    val wearDecayWeekly: Double = 0.02,
    val graceDays: Int = 14,
    val livelinessHalfLifeDays: Double = 30.0,
    val goalStart: Int = 150,
    val goalFloor: Int = 150,
    val goalCeiling: Int = 300,
    val goalUpRatio: Double = 1.2,
    val goalUpWeeksNeeded: Int = 3,
    val goalUpStep: Double = 1.10,
    /** Weeks to wait after a raise before the goal can rise again. */
    val goalUpCooldownWeeks: Int = 4,
    val goalDownRatio: Double = 0.7,
    val vigorousWeight: Double = 2.0,
    val capitalMinFloorTier: Int = Tier.VILLAGE.ordinal,
    /** Wear needed for each visual road level (trail, path, cobble, road, avenue). */
    val wearLevels: List<Double> = listOf(1.0, 3.0, 10.0, 30.0, 100.0),
)

// ---- Derived state ----

data class EdgeState(
    val id: String,
    val name: String?,
    val discovered: Boolean,
    val discoveredDay: Int?,
    val wear: Double,
    /** 0 = undiscovered, 1 = faint trail … 5 = avenue. */
    val level: Int,
    val coveredM: Double,
    val lengthM: Double,
)

data class SettlementState(
    val areaId: String,
    val name: String,
    val isCapital: Boolean,
    val tier: Tier,
    val peakTier: Tier,
    val development: Double,
    val peakDevelopment: Double,
    val foundedDay: Int,
    val lastAreaDay: Int,
    val areaDays: Int,
    val exploredCells: Int,
    val liveliness: Double,
    val position: LatLng,
)

data class WeekRecord(val weekStartDay: Int, val goal: Int, val minutes: Double)

data class GoalState(
    val goal: Int,
    val locked: Boolean,
    val trailing7Minutes: Double,
    val ratio: Double,
    val protection: Protection,
    val weeks: List<WeekRecord>,
)

sealed class WorldEvent {
    abstract val day: Int
    data class Founded(override val day: Int, val areaId: String, val capital: Boolean) : WorldEvent()
    data class TierChanged(override val day: Int, val areaId: String, val from: Tier, val to: Tier) : WorldEvent()
    data class RoadsCharted(override val day: Int, val edgeIds: List<String>) : WorldEvent()
    data class GoalChanged(override val day: Int, val from: Int, val to: Int) : WorldEvent()
}

data class DaySummary(
    val day: Int,
    val footMeters: Double,
    val cycleMeters: Double,
    val intensityMinutes: Double,
    val ratio: Double,
    val protection: Protection,
    val goal: Int,
)

data class WorldState(
    val today: Int,
    val edges: Map<String, EdgeState>,
    val cellFamiliarity: Map<String, Double>,
    val settlements: List<SettlementState>,
    val goal: GoalState,
    val events: List<WorldEvent>,
    val days: Map<Int, DaySummary>,
    val areaNames: Map<String, String>,
) {
    fun eventsOn(day: Int) = events.filter { it.day == day }
    val discoveredCount get() = edges.values.count { it.discovered }
}
