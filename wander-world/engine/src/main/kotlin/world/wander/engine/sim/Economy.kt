package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.Geo
import world.wander.engine.ProjectDef
import world.wander.engine.Projects
import world.wander.engine.Proposal
import world.wander.engine.Tier
import world.wander.engine.Treasury
import world.wander.engine.WorldEvent
import world.wander.engine.Yields

/** 7. Economy: the day's earnings, milestone monuments, and the next proposal if none is waiting. */
internal fun Simulation.economy(d: Simulation.Day) {
    val day = d.day
    val mv = movementByDay[day]
    val footKm = (mv?.sumOf { it.footMeters } ?: 0.0) / 1000
    val waterKm = (mv?.sumOf { it.waterMeters } ?: 0.0) / 1000
    val outdoorKm = footKm + (mv?.sumOf { it.cycleMeters } ?: 0.0) / 1000 + waterKm
    // Paddling: 2 food per km, spread over the water you crossed; new water pays the discovery bonus.
    val wakeCells = wakeByDay[day]?.map { it.cell }?.distinct().orEmpty()
    if (waterKm > 0) {
        if (wakeCells.isEmpty()) d.gain(null, food = waterKm * 2)
        else wakeCells.forEach { d.gain(areaOfFine(it), food = harvest.take("w:$it", day, waterKm * 2 / wakeCells.size)) }
    }
    // A campfire (or its meeting hall / town hall) feeds you on each day you walk in its area.
    for ((areaId, list) in built) {
        if (Projects.hasChain(list, "campfire") && areas[areaId]?.footDays?.lastOrNull() == day) d.gain(areaId, food = 1.0)
    }
    // Everything goes into one shared stock.
    val goldToday = Yields.gold(intensityByDay[day] ?: 0.0, outdoorKm)
    val materials = d.gains.values.fold(Treasury()) { acc, t -> acc + t }
    d.earned = materials.copy(gold = goldToday)
    treasury += d.earned
    totalKm += outdoorKm
    if (footKm >= 5) raise("day_5k", day)
    if (footKm >= 10) raise("day_10k", day)
    if (footKm >= 21.1) raise("day_21k", day)
    if (totalKm >= 100) raise("total_100k", day)
    if (totalKm >= 500) raise("total_500k", day)
    if (waterKm > 0.1 && !paddledEver) { paddledEver = true; raise("first_paddle", day) }
    if (waystones.isNotEmpty()) raise("first_waystone", day)
    if (regions.size > 1) raise("first_region", day)
    val goalWeeks = weeks.count { it.minutes >= it.goal }
    if (goalWeeks >= 4) raise("weeks_4", day)
    if (goalWeeks >= 12) raise("weeks_12", day)
    if (goalWeeks >= 52) raise("weeks_52", day)
    if (pending == null) pending = propose(day)?.also { events += WorldEvent.ProjectProposed(day, it.areaId, it.projectId) }
}

/** 8. Your decisions on project proposals (after today's proposal, so you can answer it the same day). */
internal fun Simulation.decisions(d: Simulation.Day) {
    val day = d.day
    decisionsByDay[day]?.forEach { dcs ->
        val pr = pending ?: return@forEach
        if (pr.areaId != dcs.areaId || pr.projectId != dcs.projectId) return@forEach
        val def = Projects.byId(dcs.projectId) ?: return@forEach
        if (dcs.approve) {
            if (def.cost.affordable(treasury)) {
                treasury = treasury.spend(def.cost)
                built.getOrPut(dcs.areaId) { mutableListOf() } += def.id
                events += WorldEvent.ProjectBuilt(day, dcs.areaId, def.id)
                pending = null
            }
        } else {
            declinedOn[dcs.areaId to def.id] = day
            pending = null
        }
    }
}

/**
 * One proposal at a time: the most recently visited settlement with something it can build, cheapest first.
 * Declined projects come back after 14 days; nothing ever expires.
 */
private fun Simulation.propose(day: Int): Proposal? {
    val waterCenters = input.landmarks.values.filter { it.kind in Yields.FOOD_KINDS }.mapNotNull { it.center }
    fun nearWater(s: SettlementAcc): Boolean {
        val c = s.position ?: return false
        return waterCenters.any { Geo.distanceM(it, c) < 3_000 } ||
            input.wake.any { Geo.distanceM(Cells.center(it.cell), c) < 3_000 }
    }
    val options = settlements.values.filter { it.tier != Tier.RUINS }.flatMap { s ->
        Projects.ALL.filter { def ->
            s.tier.ordinal >= def.minTier.ordinal &&
                def.id !in built[s.areaId].orEmpty() &&
                (def.upgrades == null || def.upgrades in built[s.areaId].orEmpty()) &&
                (declinedOn[s.areaId to def.id]?.let { day - it >= 14 } ?: true) &&
                (!def.needsWater || nearWater(s))
        }.map { s to it }
    }
    val pick = options.sortedWith(
        compareByDescending<Pair<SettlementAcc, ProjectDef>> { it.first.lastVisit }.thenBy { it.second.cost.gold },
    ).firstOrNull() ?: return null
    return Proposal(day, pick.first.areaId, pick.second.id)
}

/**
 * 7b. Purchases: from the travelling trader (always, double price) or a settlement's market (once built; a market hall
 * is 10% cheaper). Resource prices climb 10% per unit bought in a week. A purchase you can't afford is refused.
 */
internal fun Simulation.purchases(d: Simulation.Day) {
    val day = d.day
    val week = world.wander.engine.Shop.week(day)
    for (pu in purchasesByDay[day].orEmpty()) {
        val shop = world.wander.engine.Shop
        if (pu.item in shop.COSMETICS) {
            val price = shop.COSMETICS.getValue(pu.item)
            if (pu.item in owned || treasury.gold < price) { events += WorldEvent.PurchaseRefused(day, pu.item); continue }
            treasury = treasury.copy(gold = treasury.gold - price)
            owned += pu.item
            events += WorldEvent.Purchased(day, pu.item, 1, price, pu.seller)
            continue
        }
        val multiplier = when {
            pu.seller == shop.TRADER -> shop.TRADER_MULTIPLIER
            Projects.hasChain(built[pu.seller].orEmpty(), "market_hall") -> shop.MARKET_HALL_MULTIPLIER
            Projects.hasChain(built[pu.seller].orEmpty(), "market") -> 1.0
            else -> { events += WorldEvent.PurchaseRefused(day, pu.item); continue }   // no market there (yet)
        }
        val n = bought[week to pu.item] ?: 0
        val price = shop.price(pu.item, multiplier, n, pu.qty)
        if (pu.qty <= 0 || treasury.gold < price) { events += WorldEvent.PurchaseRefused(day, pu.item); continue }
        treasury = treasury.copy(gold = treasury.gold - price) + when (pu.item) {
            "wood" -> Treasury(wood = pu.qty.toDouble()); "food" -> Treasury(food = pu.qty.toDouble())
            "stone" -> Treasury(stone = pu.qty.toDouble()); else -> Treasury(metal = pu.qty.toDouble())
        }
        bought[week to pu.item] = n + pu.qty
        events += WorldEvent.Purchased(day, pu.item, pu.qty, price, pu.seller)
    }
}
