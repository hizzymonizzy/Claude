package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.Cost
import world.wander.engine.EncounterState
import world.wander.engine.LatLng
import world.wander.engine.Shop
import world.wander.engine.Story
import world.wander.engine.Tier
import world.wander.engine.TraderRequest
import world.wander.engine.Treasury
import world.wander.engine.WorldEvent
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * 9. The development story (0.13): chance encounters after walks, the trader's weekly request, rare looks from feats,
 * and achievements. Everything is deterministic for a day (seeded by the day), so replays always agree. Nothing here
 * ever takes anything away: encounters that expire just leave.
 */
internal fun Simulation.story(d: Simulation.Day) {
    val day = d.day
    val date = LocalDate.ofEpochDay(day.toLong())
    val todayHexes = heatByDay[day]?.map { it.cell }?.distinct().orEmpty()
    val newHexes = todayHexes.count { seenHex.add(it) }
    val footKm = (movementByDay[day]?.sumOf { it.footMeters } ?: 0.0) / 1000

    // 1. Encounters you've walked to completion, or that have left.
    for (e in activeEncounters.toList()) {
        val done = when (e.kind) {
            "bard" -> day > e.day && (encounterProgress.merge(e.id, newHexes, Int::plus) ?: 0) >= BARD_HEXES
            "courier" -> day > e.day && areas[e.targetArea]?.footDays?.lastOrNull() == day
            "lost_pet" -> day > e.day && visitsByDay[day].orEmpty().any { v ->
                input.landmarks[v.landmarkId]?.kind in PARK_KINDS && v.seconds >= 30
            }
            else -> false
        }
        when {
            done -> completeEncounter(e, day)
            day > e.expires -> { activeEncounters.remove(e); events += WorldEvent.EncounterGone(day, e) }
        }
    }

    // 2. Trades you chose (replayed like purchases): an encounter's ask, or the trader's request.
    for (pu in purchasesByDay[day].orEmpty()) {
        when (pu.seller) {
            "encounter" -> {
                val e = activeEncounters.firstOrNull { "enc:${it.id}" == pu.item } ?: continue
                val cost = e.cost ?: continue
                if (!cost.affordable(treasury)) { events += WorldEvent.PurchaseRefused(day, pu.item); continue }
                treasury = treasury.spend(cost)
                completeEncounter(e, day)
            }
            "request" -> {
                val r = request ?: continue
                if (r.done || r.week != Shop.week(day) || resourceOf(treasury, r.resource) < r.qty) { events += WorldEvent.PurchaseRefused(day, "request"); continue }
                treasury = treasury.spend(costOf(r.resource, r.qty)) + gainOf(r.rewardResource, r.rewardQty)
                request = r.copy(done = true)
                events += WorldEvent.RequestFulfilled(day, request!!)
            }
        }
    }

    // 3. The trader's request for this week (made on the first day of each week you play).
    val week = Shop.week(day)
    if (request?.week != week) request = makeRequest(week)

    // 4. A new encounter after a walk: likelier when you walked new ground, twice as likely by an inn.
    if (footKm >= 1.0 && activeEncounters.isEmpty()) {
        val rnd = Random(day * 7919L + 17)
        val inn = d.mainArea?.let { "inn" in built[it].orEmpty() } == true
        val chance = ((0.10 + 0.01 * newHexes).coerceAtMost(0.35) * (if (inn) 2.0 else 1.0)).coerceAtMost(0.6)
        if (rnd.nextDouble() < chance) spawnEncounter(day, rnd, d.mainArea)
    }

    // 5. Festivals: the Tuesday after a week at your goal, if you have a village or bigger (one banner per season).
    if (date.dayOfWeek == DayOfWeek.TUESDAY && weeks.lastOrNull()?.let { it.minutes >= it.goal } == true &&
        settlements.values.any { it.tier.ordinal >= Tier.VILLAGE.ordinal } && Random(day * 31L + 3).nextDouble() < 0.25
    ) {
        val look = "banner_" + Story.season(date.monthValue)
        if (look !in owned) {
            val e = EncounterState(++encounterSeq, "festival", day, day, settlements.values.maxByOrNull { it.tier.ordinal }?.position ?: LatLng(0.0, 0.0),
                "A harvest of good weeks", "10 food and the ${Story.LOOK_NAMES[look]}", look = look)
            events += WorldEvent.EncounterAppeared(day, e)
            completeEncounter(e, day)
        }
    }

    // 6. Feats and achievements (a look at some tiers); the Wonder's stages each give a banner.
    for (s in Story.LADDERS) {
        val v = ladderValue(s.id)
        val tier = s.targets.count { v >= it }
        val before = achievementTiers[s.id] ?: 0
        for (t in before + 1..tier) events += WorldEvent.AchievementEarned(day, s.id, s.name, t)
        if (tier > before) achievementTiers[s.id] = tier
        if (s.reward != null && tier >= s.rewardAt && s.reward !in owned) earnLook(day, s.reward, s.name)
    }
    for ((areaId, list) in built) if (settlements[areaId]?.capital == true) {
        for (k in 1..4) if ("wonder_$k" in list && "banner_wonder_$k" !in owned) earnLook(day, "banner_wonder_$k", "the Wonder")
    }
}

private const val BARD_HEXES = 10
private val PARK_KINDS = setOf("park", "garden", "common", "recreation_ground", "village_green", "nature_reserve")

private fun Simulation.earnLook(day: Int, look: String, how: String) {
    owned += look
    events += WorldEvent.LookEarned(day, look, how)
}

private fun Simulation.completeEncounter(e: EncounterState, day: Int) {
    activeEncounters.remove(e)
    treasury += when (e.kind) {
        "bard" -> Treasury(gold = 30.0)
        "courier" -> Treasury(gold = 40.0, food = 5.0)
        "lost_pet" -> Treasury(gold = 20.0)
        "festival" -> Treasury(food = 10.0)
        else -> Treasury()
    }
    e.look?.let { if (it !in owned) earnLook(day, it, e.kind) }
    events += WorldEvent.EncounterDone(day, e)
}

private fun Simulation.spawnEncounter(day: Int, rnd: Random, mainArea: String?) {
    val at = presenceByDay[day]?.lastOrNull()?.cell?.let { centerOf(it) } ?: return
    val others = settlements.values.filter { it.areaId != mainArea }
    val parks = input.landmarks.values.any { it.kind in PARK_KINDS }
    val kinds = buildList {
        add("bard")
        if (others.isNotEmpty()) add("courier")
        if (parks) add("lost_pet")
        if ("outfit_patchwork" !in owned) add("peddler")
        if ("banner_ironbound" !in owned) add("collector")
    }
    val kind = kinds[rnd.nextInt(kinds.size)]
    val id = ++encounterSeq
    val e = when (kind) {
        "bard" -> EncounterState(id, kind, day, day + 3, at, "Walk somewhere you've never been ($BARD_HEXES new hexes) within 3 days", "30 gold and a verse")
        "courier" -> others[rnd.nextInt(others.size)].let { t ->
            EncounterState(id, kind, day, day + 7, at, "Carry a letter to ${areaNames()[t.areaId] ?: "a settlement"} within a week", "40 gold and 5 food", targetArea = t.areaId)
        }
        "lost_pet" -> EncounterState(id, kind, day, day + 2, at, "A lost dog: walk through a park within 2 days", "20 gold")
        "peddler" -> EncounterState(id, kind, day, day + 3, at, "10 wood and 10 stone", "a patchwork cloak", cost = Cost(wood = 10, stone = 10), look = "outfit_patchwork")
        else -> EncounterState(id, kind, day, day + 3, at, "20 metal", "an ironbound banner", cost = Cost(metal = 20), look = "banner_ironbound")
    }
    activeEncounters += e
    events += WorldEvent.EncounterAppeared(day, e)
}

private fun Simulation.makeRequest(week: Int): TraderRequest {
    val rnd = Random(week * 31L + 5)
    val res = listOf("wood", "food", "stone", "metal")
    // Something you have or can gather soon: your biggest stock, else any.
    val want = res.filter { resourceOf(treasury, it) >= 3 }.maxByOrNull { resourceOf(treasury, it) } ?: res[rnd.nextInt(4)]
    val qty = 3 + rnd.nextInt(3)
    val others = res - want
    val give = others[rnd.nextInt(others.size)]
    val value = qty * Shop.BASE.getValue(want) * 1.2
    return TraderRequest(week, want, qty, give, maxOf(1, Math.ceil(value / Shop.BASE.getValue(give)).toInt()))
}

internal fun Simulation.ladderValue(id: String): Double = when (id) {
    "long_road" -> totalKm
    "cartographer" -> edges.values.count { it.discoveredDay != null }.toDouble()
    "discoverer" -> landmarkFirst.size.toDouble()
    "explorer" -> seenHex.size.toDouble()
    "pathfinder" -> edges.values.count { it.discoveredDay != null && it.info.trail }.toDouble()
    "founder" -> settlements.size.toDouble()
    "wayfarer" -> waystones.size.toDouble()
    "tidewalker" -> paddledKm
    "woodsman" -> landmarkFirst.keys.count { input.landmarks[it]?.kind in world.wander.engine.Yields.WOOD_KINDS }.toDouble()
    "steadfast" -> weeks.reversed().takeWhile { it.minutes >= it.goal }.size.toDouble()
    else -> 0.0
}

private fun resourceOf(t: Treasury, r: String) = when (r) { "wood" -> t.wood; "food" -> t.food; "stone" -> t.stone; else -> t.metal }
private fun costOf(r: String, n: Int) = when (r) { "wood" -> Cost(wood = n); "food" -> Cost(food = n); "stone" -> Cost(stone = n); else -> Cost(metal = n) }
private fun gainOf(r: String, n: Int) = when (r) {
    "wood" -> Treasury(wood = n.toDouble()); "food" -> Treasury(food = n.toDouble()); "stone" -> Treasury(stone = n.toDouble()); else -> Treasury(metal = n.toDouble())
}

@Suppress("unused") private fun cellCenter(c: String) = Cells.center(c)
