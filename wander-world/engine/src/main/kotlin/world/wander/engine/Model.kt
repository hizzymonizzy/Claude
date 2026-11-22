package world.wander.engine

/**
 * How you were moving. Only on-foot movement establishes settlements; cycling is for discovery;
 * WATER (paddleboard, canoe, kayak, rowing, open-water swimming) charts water routes and clears fog over water.
 */
enum class Mode { FOOT, CYCLE, WATER }

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
data class DayMovement(val day: Int, val footMeters: Double, val cycleMeters: Double, val waterMeters: Double = 0.0)

data class AreaInfo(val id: String, val name: String?, val placeType: String?)

data class WorldInput(
    /** Epoch day the world began (install day). */
    val startDay: Int,
    val homeAreaId: String?,
    /** Exact home location: the capital sits here rather than at its area's centre. */
    val homePoint: LatLng? = null,
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
    val landmarks: Map<String, LandmarkInfo> = emptyMap(),
    val landmarkVisits: List<LandmarkVisit> = emptyList(),
    val trodden: List<TroddenCell> = emptyList(),
    /** Where you started walking after a car ride or flight. */
    val arrivals: List<Arrival> = emptyList(),
    /** Fine cells paddled on a given day, for water-route wear. */
    val wake: List<TroddenCell> = emptyList(),
    /** Fine cells walked along shores, per day (food). */
    val shore: List<TroddenCell> = emptyList(),
    /** Things you bought, replayed like decisions. */
    val purchases: List<Purchase> = emptyList(),
    /** Your approvals and declines of project proposals. */
    val decisions: List<ProjectDecision> = emptyList(),
)

/** The first walking point after travelling by vehicle or plane. */
data class Arrival(val day: Int, val lat: Double, val lng: Double)

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
    /** Each active minute counts once toward the weekly goal; intensity adds this much per intensity minute on top. */
    val intensityBonus: Double = 0.5,
    /** Decay multiplier by tier; bigger places are sturdier. */
    val sturdiness: List<Double> = listOf(1.0, 1.0, 0.8, 0.6, 0.45, 0.3, 0.2),
    /** Recovery per week at 150% of the goal: this share of what cooled comes back (scaled from 100%). */
    val weeklyRecovery: Double = 0.5,
    val ruinsRememberMultiplier: Double = 2.0,
    /** A cooled place never drops more than this many steps below its best. */
    val coolingFloorSteps: Int = 2,
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
    /** Seconds inside a named area on one day to discover it. */
    val landmarkSeconds: Double = Yields.VISIT_SECONDS,
    /** Points of interest are small circles; walking past one for this long discovers it. */
    val pointLandmarkSeconds: Double = 15.0,
    /** Places this far apart belong to different regions. */
    val regionRadiusM: Double = 100_000.0,
    /** A region other than home goes dormant after this many days without a visit. */
    val dormantAfterDays: Int = 14,
    /** Decay multiplier in a dormant region. */
    val dormantDecay: Double = 0.1,
    /** An arrival this far from any explored ground finds a waystone. */
    val waystoneMinDistanceM: Double = 2_000.0,
    /** Arriving within this distance of an existing waystone counts as a return trip. */
    val waystoneReturnM: Double = 500.0,
    /** Discovery bonus, thinning and regrowth of resource sources. */
    val harvest: HarvestParams = HarvestParams(),
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
    val regionId: Int = 0,
    val dormant: Boolean = false,
    val projects: List<String> = emptyList(),
    val prosperity: Int = 0,
)

data class WeekRecord(val weekStartDay: Int, val goal: Int, val minutes: Double)

data class GoalState(
    val goal: Int,
    val locked: Boolean,
    /** Active minutes (intensity-weighted) in the last 7 days. */
    val trailing7Minutes: Double,
    val ratio: Double,
    val protection: Protection,
    val weeks: List<WeekRecord>,
    /** This Monday-to-Sunday week so far, which is scored next Tuesday. */
    val weekMinutes: Double = 0.0,
    val weekStartDay: Int = 0,
    /** Whether next Tuesday's scoring would cool anything (grace and rest mode never do). */
    val coolingApplies: Boolean = true,
)

sealed class WorldEvent {
    abstract val day: Int
    data class Founded(override val day: Int, val areaId: String, val capital: Boolean) : WorldEvent()
    data class TierChanged(override val day: Int, val areaId: String, val from: Tier, val to: Tier) : WorldEvent()
    data class RoadsCharted(override val day: Int, val edgeIds: List<String>) : WorldEvent()
    data class RegionFounded(override val day: Int, val regionId: Int, val fromRegionId: Int?) : WorldEvent()
    data class RegionWoke(override val day: Int, val regionId: Int) : WorldEvent()
    data class WaystoneFound(override val day: Int, val waystoneId: Int, val great: Boolean, val regionId: Int) : WorldEvent()
    data class WaystoneReturned(override val day: Int, val waystoneId: Int) : WorldEvent()
    data class ProjectProposed(override val day: Int, val areaId: String, val projectId: String) : WorldEvent()
    data class ProjectBuilt(override val day: Int, val areaId: String, val projectId: String) : WorldEvent()
    data class MonumentRaised(override val day: Int, val monumentId: String) : WorldEvent()
    data class LandmarkDiscovered(override val day: Int, val landmarkId: String) : WorldEvent()
    /** A place you harvest often has thinned below half; it grows back if left to rest. */
    data class SourceThinning(override val day: Int, val landmarkId: String) : WorldEvent()
    data class Purchased(override val day: Int, val item: String, val qty: Int, val price: Int, val seller: String) : WorldEvent()
    data class PurchaseRefused(override val day: Int, val item: String) : WorldEvent()
    data class GoalChanged(override val day: Int, val from: Int, val to: Int) : WorldEvent()
    /** Tuesday: last Monday-to-Sunday week scored against the goal; places faded or recovered a step. */
    data class WeekScored(
        override val day: Int, val minutes: Double, val goal: Int, val loss: Double, val recovery: Double,
        val fadedRoads: List<String>, val recoveredRoads: Int, val settlementsDropped: List<String>,
    ) : WorldEvent()
}

data class RegionState(
    val id: Int,
    val name: String,
    val center: LatLng,
    val isHome: Boolean,
    val dormant: Boolean,
    val lastPresenceDay: Int,
)

data class WaystoneState(val id: Int, val center: LatLng, val great: Boolean, val regionId: Int, val foundDay: Int, val trips: Int)

data class PortalLink(val regionA: Int, val regionB: Int, val trips: Int)

/** Any area you've walked in, settled or not: a waypoint on the map. */
data class AreaState(
    val id: String,
    val name: String,
    val center: LatLng,
    val cells: Int,
    val visitDays: Int,
    val lastVisitDay: Int,
    val settled: Boolean,
)

data class LandmarkState(
    val id: String,
    val name: String,
    val kind: String,
    val areaM2: Double,
    val discoveredDay: Int,
    val visitDays: Int,
    val lastVisitDay: Int,
)

data class DaySummary(
    val day: Int,
    val footMeters: Double,
    val cycleMeters: Double,
    val intensityMinutes: Double,
    val waterMeters: Double = 0.0,
    val ratio: Double,
    /** What you earned that day. */
    val earned: Treasury = Treasury(),
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
    val landmarks: List<LandmarkState> = emptyList(),
    /** Wear on your own paths, per fine cell. */
    val troddenWear: Map<String, Double> = emptyMap(),
    /** Wear on water routes you paddle, per fine cell. */
    val wakeWear: Map<String, Double> = emptyMap(),
    val exploredAreas: List<AreaState> = emptyList(),
    val regions: List<RegionState> = emptyList(),
    val waystones: List<WaystoneState> = emptyList(),
    val portals: List<PortalLink> = emptyList(),
    val treasury: Treasury = Treasury(),
    val proposal: Proposal? = null,
    val monuments: List<MonumentState> = emptyList(),
    /** How full each place's resources are today (1 = untouched or fully grown back), by landmark id. */
    val abundance: Map<String, Double> = emptyMap(),
    /** Sources you gathered from today (places and unnamed water), so their nodes show as gathered. */
    val gatheredToday: Set<String> = emptySet(),
    /** Shore cells you've ever walked ("s:<cell>") and how full they are today. */
    val shoreWalked: Map<String, Double> = emptyMap(),
    /** Cosmetics you own. */
    val owned: Set<String> = emptySet(),
    /** Units of each resource bought this week (prices climb with each). */
    val boughtThisWeek: Map<String, Int> = emptyMap(),
    /** Where the travelling trader is waiting: by the village you visited most recently. */
    val traderAt: LatLng? = null,
) {
    fun regionName(id: Int) = regions.firstOrNull { it.id == id }?.name ?: "a distant land"

    /** Visual level (1–5) for a wear value, same scale as roads. */
    fun levelFor(wear: Double, levels: List<Double> = WorldParams().wearLevels): Int {
        var level = 1
        levels.forEachIndexed { i, need -> if (wear >= need) level = i + 1 }
        return level
    }

    fun eventsOn(day: Int) = events.filter { it.day == day }
    val discoveredCount get() = edges.values.count { it.discovered }
}
