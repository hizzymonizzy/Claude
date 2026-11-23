package world.wander.engine.sim

import world.wander.engine.Cells
import world.wander.engine.WorldEngine
import world.wander.engine.WorldEvent
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max

/**
 * 5b. Weekly cooling (ADR 0008). Every Tuesday, last Monday-to-Sunday week is scored against the goal (a day late, so
 * late watch syncs count). Below the goal, every walked road, own path, water route and settlement cools by a share
 * of its heat; above it, places recover part of what they lost. Nothing falls more than two steps below its best,
 * nor back to fog. Grace, rest mode (any rest day that week or today) and dormant regions (×0.1) soften or skip it.
 */
internal fun Simulation.cooling(d: Simulation.Day) {
    val day = d.day
    if (LocalDate.ofEpochDay(day.toLong()).dayOfWeek != DayOfWeek.TUESDAY) return
    val weekStart = day - 8
    val weekEnd = day - 2
    if (weekStart < startDay || weekEnd - startDay < p.graceDays) return
    if ((weekStart..day).any { isResting(it) }) return
    val minutes = (weekStart..weekEnd).sumOf { effortByDay[it] ?: 0.0 }
    // Three weeks after a rest ends, return gently: only weeks under 75 minutes cool, and by an eighth.
    val lastRest = input.restPeriods.filter { it.first <= weekEnd }.maxOfOrNull { minOf(it.last, weekEnd) }
    val rampingBack = lastRest != null && weekStart - lastRest in 0..20
    val loss = WorldEngine.weeklyLoss(minutes, rampingBack)
    val recovery = WorldEngine.weeklyRecovery(minutes, goal)
    if (loss == 0.0 && recovery == 0.0) {
        events += WorldEvent.WeekScored(day, minutes, goal, 0.0, 0.0, emptyList(), 0, emptyList())
        return
    }

    fun dormancy(cell: String?): Double =
        cell?.let { cellRegion[it] }?.let { if (regions[it].dormant) p.dormantDecay else 1.0 } ?: 1.0
    /** Moves a value along its step scale: down by the loss (never below the floor), up by the recovery (never past best). */
    fun shift(v: Double, best: Double, scale: StepScale, floorStep: Double, dorm: Double): Double {
        var pos = scale.pos(v)
        val start = pos
        if (loss > 0) pos = max(minOf(floorStep, start), pos - loss * dorm)
        if (recovery > 0) pos = minOf(maxOf(scale.pos(best), start), pos + recovery)
        return if (pos == start) v else scale.value(pos)
    }
    fun cool(v: Double, best: Double, dorm: Double): Double =
        shift(v, best, WEAR_SCALE, (step(best) - p.coolingFloorSteps).coerceAtLeast(1).toDouble(), dorm)

    val faded = mutableListOf<String>()
    var recovered = 0
    for (e in edges.values) {
        if (e.discoveredDay == null) continue
        val before = step(shownWear(e.wear, e.best))
        e.wear = cool(e.wear, e.best, dormancy(e.info.midpoint?.let { Cells.cell(it.lat, it.lng) }))
        val after = step(shownWear(e.wear, e.best))
        e.info.name?.takeIf { after < before && it.isNotBlank() }?.let { faded.add(it) }
        if (after > before) recovered++
    }
    for ((cells, best) in listOf(trodden to troddenBest, wake to wakeBest, heat to heatBest, heatHood to heatHoodBest, heatCounty to heatCountyBest)) {
        for (c in cells.keys) {
            // The region a hex belongs to, by its res-9 cell (a county hex is coarser: use its centre's cell).
            val parent = try {
                if (Cells.resolution(c) >= Cells.CELL_RES) Cells.parent(c, Cells.CELL_RES) else Cells.center(c).let { Cells.cell(it.lat, it.lng) }
            } catch (_: Exception) { null }
            cells[c] = cool(cells.getValue(c), best[c] ?: cells.getValue(c), dormancy(parent))
        }
    }
    val dropped = mutableListOf<String>()
    val tierScale = StepScale(p.tierThresholds)
    for (s in settlements.values) {
        val before = s.tier
        val dorm = (areaRegion[s.areaId]?.let { if (regions[it].dormant) p.dormantDecay else 1.0 } ?: 1.0) *
            (if ("walls" in built[s.areaId].orEmpty()) 0.5 else 1.0)   // town walls: cools half as fast
        s.d = shift(s.d, s.peakD, tierScale, floorTier(s).ordinal.toDouble(), dorm)
        updateTier(day, s, areas.getValue(s.areaId))
        if (s.tier.ordinal < before.ordinal) dropped += s.areaId
    }
    events += WorldEvent.WeekScored(day, minutes, goal, loss, recovery, faded.distinct(), recovered, dropped)
}

/**
 * A value's position on a ladder of thresholds (0.12.1): position k at threshold k, geometric in between (the steps
 * are about 3× apart), linear below the first non-zero threshold, and the last ratio carried on above the top. This is
 * what lets decay take "a quarter of a step" whatever the step.
 */
internal class StepScale(thresholds: List<Double>) {
    private val t = if (thresholds.first() == 0.0) thresholds else listOf(0.0) + thresholds
    fun pos(v: Double): Double {
        if (v <= 0) return 0.0
        if (v < t[1]) return v / t[1]
        for (k in 1 until t.size - 1) if (v < t[k + 1]) return k + Math.log(v / t[k]) / Math.log(t[k + 1] / t[k])
        val n = t.size - 1
        return n + Math.log(v / t[n]) / Math.log(t[n] / t[n - 1])
    }
    fun value(pos: Double): Double {
        if (pos <= 0) return 0.0
        if (pos < 1) return pos * t[1]
        val n = t.size - 1
        val k = minOf(pos.toInt(), n)
        val ratio = if (k < n) t[k + 1] / t[k] else t[n] / t[n - 1]
        return t[k] * Math.pow(ratio, pos - k)
    }
}

/** Road, path and hex wear: steps 1–5 at 1, 3, 10, 30, 100 days. */
internal val WEAR_SCALE = StepScale(listOf(1.0, 3.0, 10.0, 30.0, 100.0))

/**
 * Steps a cooled place shows as lost, counted from its best (0.12.1): only whole steps of cooling since then. A place
 * that has just reached a step doesn't drop back after one missed week; it takes a full step's cooling (4 badly missed
 * weeks), wherever in the step it was.
 */
internal fun stepsLost(scale: StepScale, v: Double, best: Double): Int =
    if (best <= v) 0 else Math.floor(scale.pos(best) - scale.pos(v) + 1e-9).toInt()

/** The wear a place shows: its own, or the bottom of the step it still holds after cooling (see [stepsLost]). */
internal fun shownWear(v: Double, best: Double): Double {
    if (best <= v) return v
    val held = Math.floor(WEAR_SCALE.pos(best) + 1e-9).toInt() - stepsLost(WEAR_SCALE, v, best)
    return if (held > Math.floor(WEAR_SCALE.pos(v) + 1e-9)) WEAR_SCALE.value(held.toDouble()) else v
}
