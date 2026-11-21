package world.wander.engine

/**
 * The economy: everything is earned by moving, never by trading or waiting.
 *
 *  Gold  — your weekly-goal effort: intensity minutes (vigorous ×2, tapering after 90 a day) + 2 per outdoor km
 *  Wood  — time in woods and parks
 *  Food  — paddling, beaches and lakes
 *  Stone — kilometres walked on mapped roads
 *  Metal — discovering and revisiting points of interest
 *
 * Settlements propose one project at a time; you approve or decline. Projects add prosperity, never tier.
 */
data class Cost(val gold: Int = 0, val wood: Int = 0, val food: Int = 0, val stone: Int = 0, val metal: Int = 0) {
    fun affordable(t: Treasury) = t.gold >= gold && t.wood >= wood && t.food >= food && t.stone >= stone && t.metal >= metal
    override fun toString(): String = listOfNotNull(
        gold.takeIf { it > 0 }?.let { "$it gold" }, wood.takeIf { it > 0 }?.let { "$it wood" },
        food.takeIf { it > 0 }?.let { "$it food" }, stone.takeIf { it > 0 }?.let { "$it stone" },
        metal.takeIf { it > 0 }?.let { "$it metal" },
    ).joinToString(", ")
}

data class Treasury(val gold: Double = 0.0, val wood: Double = 0.0, val food: Double = 0.0, val stone: Double = 0.0, val metal: Double = 0.0) {
    operator fun plus(o: Treasury) = Treasury(gold + o.gold, wood + o.wood, food + o.food, stone + o.stone, metal + o.metal)
    fun spend(c: Cost) = Treasury(gold - c.gold, wood - c.wood, food - c.food, stone - c.stone, metal - c.metal)
    val isEmpty get() = gold < 0.5 && wood < 0.5 && food < 0.5 && stone < 0.5 && metal < 0.5
    override fun toString(): String = listOfNotNull(
        gold.toInt().takeIf { it > 0 }?.let { "$it gold" }, wood.toInt().takeIf { it > 0 }?.let { "$it wood" },
        food.toInt().takeIf { it > 0 }?.let { "$it food" }, stone.toInt().takeIf { it > 0 }?.let { "$it stone" },
        metal.toInt().takeIf { it > 0 }?.let { "$it metal" },
    ).joinToString(", ")
}

data class ProjectDef(
    val id: String,
    val name: String,
    val minTier: Tier,
    val cost: Cost,
    val prosperity: Int,
    val description: String,
    /** Only offered to settlements with water nearby (a lake, beach, harbour or a route you've paddled). */
    val needsWater: Boolean = false,
    /** An upgrade: only offered once this project is built here, and drawn in its place. */
    val upgrades: String? = null,
)

object Projects {
    val ALL = listOf(
        ProjectDef("campfire", "Campfire circle", Tier.CAMP, Cost(gold = 20, wood = 5), 1, "Somewhere to gather at night."),
        ProjectDef("well", "Well", Tier.HAMLET, Cost(gold = 50, stone = 10), 1, "Fresh water for the square."),
        ProjectDef("market", "Market square", Tier.VILLAGE, Cost(gold = 150, wood = 10, stone = 20), 2, "Traders come from the roads you've walked."),
        ProjectDef("bridge", "Bridge", Tier.VILLAGE, Cost(gold = 120, wood = 30), 1, "Joins the banks.", needsWater = true),
        ProjectDef("inn", "Inn", Tier.TOWN, Cost(gold = 200, wood = 20, stone = 40), 2, "Travellers stop and tell stories."),
        ProjectDef("pier", "Pier", Tier.TOWN, Cost(gold = 200, wood = 40, food = 20), 2, "Boats tie up here.", needsWater = true),
        ProjectDef("lighthouse", "Lighthouse", Tier.TOWN, Cost(gold = 300, stone = 60, metal = 10), 3, "A light for the water.", needsWater = true),
        ProjectDef("walls", "Town walls", Tier.TOWN, Cost(gold = 300, stone = 80, metal = 20), 3, "Stone walls and a gate."),
        ProjectDef("great_hall", "Great hall", Tier.CITY, Cost(gold = 800, wood = 60, stone = 120, metal = 40), 5, "The pride of the city."),
        // Upgrade chains (0.11): each replaces the one before it on the map.
        ProjectDef("meeting_hall", "Meeting hall", Tier.HAMLET, Cost(gold = 80, wood = 15, stone = 10), 2, "The campfire gets a roof.", upgrades = "campfire"),
        ProjectDef("town_hall", "Town hall", Tier.TOWN, Cost(gold = 250, wood = 30, stone = 60, metal = 10), 3, "Where the town decides things.", upgrades = "meeting_hall"),
        ProjectDef("fountain", "Fountain", Tier.VILLAGE, Cost(gold = 120, stone = 30, metal = 5), 2, "The well becomes the square's centrepiece.", upgrades = "well"),
        ProjectDef("market_hall", "Market hall", Tier.TOWN, Cost(gold = 300, wood = 20, stone = 40, metal = 10), 2, "A covered market: better prices.", upgrades = "market"),
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id }

    /** The projects drawn at a settlement: each chain shows only its latest building. */
    fun shown(built: List<String>): List<String> = built.filter { id -> ALL.none { it.upgrades == id && it.id in built } }

    /** Whether a chain (named by its first project) has been built here at any stage. */
    fun hasChain(built: List<String>, first: String): Boolean {
        var id: String? = first
        while (id != null) { if (id in built) return true; id = ALL.firstOrNull { it.upgrades == id }?.id }
        return false
    }
}

/** A choice you made about a proposal. Player decisions are inputs, replayed like walks. */
data class ProjectDecision(val day: Int, val areaId: String, val projectId: String, val approve: Boolean)

data class Proposal(val day: Int, val areaId: String, val projectId: String)

data class MonumentState(val id: String, val name: String, val description: String, val day: Int)

object Monuments {
    data class Def(val id: String, val name: String, val description: String)
    val ALL = listOf(
        Def("day_5k", "Milestone of Five", "Your first 5 km day."),
        Def("day_10k", "Milestone of Ten", "Your first 10 km day."),
        Def("day_21k", "The Long Road", "A half-marathon distance in one day."),
        Def("total_100k", "Hundred-League Stone", "100 km travelled in all."),
        Def("total_500k", "Pilgrim's Pillar", "500 km travelled in all."),
        Def("first_paddle", "Paddler's Post", "Your first route on the water."),
        Def("first_waystone", "Wayfinder's Cairn", "Your first waystone."),
        Def("first_region", "Far Shores Obelisk", "Your first new land."),
        Def("weeks_4", "Steady Hearth", "Weekly goal met in 4 weeks."),
        Def("weeks_12", "Season's Banner", "Weekly goal met in 12 weeks."),
        Def("weeks_52", "Year's Crown", "Weekly goal met in 52 weeks."),
    )
    fun byId(id: String) = ALL.first { it.id == id }
}

/** Place categories that yield each resource. */
object Yields {
    val WOOD_KINDS = setOf("wood", "forest", "nature_reserve", "wetland", "heath", "park", "garden", "common",
        "recreation_ground", "village_green", "meadow", "grassland", "dog_park", "golf_course", "cemetery")
    val FOOD_KINDS = setOf("beach", "water", "harbour", "market", "community_garden")
    val METAL_KINDS = setOf("hospital", "government", "emergency", "civic", "school", "library", "worship", "culture",
        "sports", "transport", "harbour", "lighthouse", "historic", "store")

    /**
     * How close to a place's edge counts as being at it: along a shore or beach (food), at the door of a
     * library or station (metal); for woods and parks you go inside.
     */
    fun gatherMarginM(kind: String): Double = when (kind) {
        in FOOD_KINDS -> 40.0
        in METAL_KINDS -> 25.0
        in WOOD_KINDS -> 15.0      // walking along a park's edge counts, like a shore for food
        else -> 0.0
    }

    /** Woods where you also forage a little food. */
    val FORAGE_KINDS = setOf("wood", "forest", "nature_reserve")

    /** Food from foraging on a day's visit to a wood (before thinning and the new-place bonus). */
    const val FORAGE_FOOD = 0.5

    /** Within this distance of the water's edge (or on a beach) you're walking along a shore. */
    const val SHORE_M = 40.0

    /** Food per fine cell of shore walked: about 2 per km, like stone from roads (doubled on shore never walked). */
    const val SHORE_FOOD_PER_CELL = 0.08

    /** Places that are food *nodes* (you go to them). Shores are food *lines* (you walk along them). */
    val FOOD_NODE_KINDS = setOf("harbour", "market", "community_garden")

    /** Time at a source that counts as a visit (and gathers). A walk through a small park is about this long. */
    const val VISIT_SECONDS = 30.0

    /**
     * What one day's visit to a source yields before thinning and the discovery bonus: one unit just for coming
     * by (so walking through a node always gathers something), plus more the longer you stay.
     */
    fun visitYield(resource: String, seconds: Double): Double = when (resource) {
        "wood" -> 1.0 + seconds / 300.0
        "food" -> 1.0 + seconds / 600.0
        else -> 0.0
    }

    /** One line on how to gather from a node, for its details card. */
    fun howToGather(resource: String, kind: String): String = when {
        kind == "shore" -> "Walk along this shore, within 40 m of the water. About 2 food per km."
        kind == "field" -> "Walk along the edge of these fields or gardens. About 2 food per km."
        resource == "wood" -> "Spend 30 s inside or along the edge (15 m). 1 wood, plus 1 per 5 min you stay." +
            (if (kind in FORAGE_KINDS) " You'll forage a little food too." else "")
        resource == "food" -> "Spend 30 s here. 1 food, plus 1 per 10 min you stay."
        resource == "metal" -> "Spend 30 s inside or at the door (25 m). 3 the first time, then 1.5 a day."
        else -> ""
    }

    /** Gold for a day: intensity minutes (full rate to 90, then a quarter) plus 2 per outdoor km. */
    fun gold(intensityMinutes: Double, outdoorKm: Double): Double =
        minOf(intensityMinutes, 90.0) + 0.25 * maxOf(0.0, intensityMinutes - 90.0) + 2.0 * outdoorKm
}

/**
 * Every source of wood, food, metal or stone (a wood, a beach, a place, a street) has an abundance from
 * 0 to 1. Harvesting it on a day thins it; leaving it alone lets it grow back. The first harvest from a
 * source you've never touched pays a discovery bonus. So new ground pays best, a favourite spot pays less
 * the more you lean on it, and an old haunt left to rest is worth going back to.
 */
data class HarvestParams(
    /** Abundance lost each day a source is harvested. */
    val depletion: Double = 0.15,
    /** Abundance regained per day a source rests. */
    val regenPerDay: Double = 0.05,
    /** A source never yields less than this share. */
    val floor: Double = 0.3,
    /** The first harvest from a new source pays this multiple. */
    val discoveryBonus: Double = 2.0,
)

class Harvest(
    private val p: HarvestParams = HarvestParams(),
    /** Regrowth multiplier for a source (e.g. 1.5 near a well). */
    private val regrowth: (String) -> Double = { 1.0 },
) {
    private class Source(var abundance: Double, var lastDay: Int)
    private val sources = HashMap<String, Source>()

    /** Abundance of a source on [day], counting regrowth since it was last harvested (1.0 if never). */
    fun abundance(id: String, day: Int): Double =
        sources[id]?.let { minOf(1.0, it.abundance + p.regenPerDay * regrowth(id) * (day - it.lastDay)) } ?: 1.0

    fun isNew(id: String) = id !in sources

    /**
     * Harvests [base] from source [id] on [day] and returns what it actually yields. Harvesting the same
     * source twice in one day thins it only once.
     */
    fun take(id: String, day: Int, base: Double): Double {
        if (base <= 0) return 0.0
        val s = sources[id]
        if (s == null) {
            sources[id] = Source(1.0 - p.depletion, day)
            return base * p.discoveryBonus
        }
        if (s.lastDay == day) return base * (s.abundance + p.depletion).coerceAtMost(1.0)
        val a = abundance(id, day)
        s.abundance = maxOf(p.floor, a - p.depletion)
        s.lastDay = day
        return base * a
    }

    fun snapshot(day: Int): Map<String, Double> = sources.keys.associateWith { abundance(it, day) }

    /** Sources harvested on [day] (ids as given to [take], e.g. "l:w123"). */
    fun harvestedOn(day: Int): Set<String> = sources.filterValues { it.lastDay == day }.keys
}

/**
 * The shop: the travelling trader (near your most recent village, double price) and markets (base price, market hall
 * 10% off). Each unit of a resource bought this week costs 10% more than the last; the count resets every Monday.
 * Cosmetics have fixed prices and change looks only.
 */
object Shop {
    val BASE = mapOf("stone" to 6.0, "food" to 8.0, "wood" to 10.0, "metal" to 25.0)
    const val TRADER_MULTIPLIER = 2.0
    const val MARKET_HALL_MULTIPLIER = 0.9
    const val WEEKLY_CLIMB = 1.10
    val COSMETICS = mapOf(
        "banner_crimson" to 60, "banner_royal" to 60, "banner_forest" to 60, "banner_gold" to 60, "banner_violet" to 60,
        "outfit_crimson" to 120, "outfit_navy" to 120, "outfit_ochre" to 120, "outfit_hat" to 120,
    )
    const val TRADER = "trader"

    /** Price of the next [qty] units of [resource] from a seller with [multiplier], after [boughtThisWeek] this week. */
    fun price(resource: String, multiplier: Double, boughtThisWeek: Int, qty: Int = 1): Int {
        val base = BASE[resource] ?: return Int.MAX_VALUE
        return (0 until qty).sumOf { i -> base * multiplier * Math.pow(WEEKLY_CLIMB, (boughtThisWeek + i).toDouble()) }.let { Math.ceil(it).toInt() }
    }

    /** Monday-based week number for a day, so purchase counts reset on Mondays. */
    fun week(day: Int): Int = Math.floorDiv(day + 3, 7)   // epoch day 0 was a Thursday
}

/** Something you bought: a resource (item = "wood", qty units) or a cosmetic (item = its id, qty 1). */
data class Purchase(val day: Int, val seller: String, val item: String, val qty: Int = 1)
