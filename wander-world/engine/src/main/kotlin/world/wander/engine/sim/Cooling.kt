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
    val ratio = minutes / goal
    val loss = WorldEngine.weeklyLoss(ratio)
    val recovery = WorldEngine.weeklyRecovery(ratio, p.weeklyRecovery)
    if (loss == 0.0 && recovery == 0.0) {
        events += WorldEvent.WeekScored(day, minutes, goal, 0.0, 0.0, emptyList(), 0, emptyList())
        return
    }

    fun dormancy(cell: String?): Double =
        cell?.let { cellRegion[it] }?.let { if (regions[it].dormant) p.dormantDecay else 1.0 } ?: 1.0
    fun cool(v: Double, best: Double, dorm: Double): Double {
        val floor = p.wearLevels[(step(best) - p.coolingFloorSteps).coerceIn(1, p.wearLevels.size) - 1]
        var nv = v
        if (loss > 0) nv = max(floor, nv - nv * loss * dorm).coerceAtMost(v)
        if (recovery > 0) nv += (best - nv).coerceAtLeast(0.0) * recovery
        return nv
    }

    val faded = mutableListOf<String>()
    var recovered = 0
    for (e in edges.values) {
        if (e.discoveredDay == null) continue
        val before = step(e.wear)
        e.wear = cool(e.wear, e.best, dormancy(e.info.midpoint?.let { Cells.cell(it.lat, it.lng) }))
        val after = step(e.wear)
        e.info.name?.takeIf { after < before && it.isNotBlank() }?.let { faded.add(it) }
        if (after > before) recovered++
    }
    for ((cells, best) in listOf(trodden to troddenBest, wake to wakeBest)) {
        for (c in cells.keys) {
            val parent = try { Cells.parent(c, Cells.CELL_RES) } catch (_: Exception) { null }
            cells[c] = cool(cells.getValue(c), best[c] ?: cells.getValue(c), dormancy(parent))
        }
    }
    val dropped = mutableListOf<String>()
    for (s in settlements.values) {
        val before = s.tier
        val floorD = p.tierThresholds[floorTier(s).ordinal]
        val dorm = areaRegion[s.areaId]?.let { if (regions[it].dormant) p.dormantDecay else 1.0 } ?: 1.0
        if (loss > 0) s.d -= max(0.0, s.d - floorD) * loss * p.sturdiness[s.tier.ordinal] * dorm
        if (recovery > 0) s.d += (s.peakD - s.d).coerceAtLeast(0.0) * recovery
        updateTier(day, s, areas.getValue(s.areaId))
        if (s.tier.ordinal < before.ordinal) dropped += s.areaId
    }
    events += WorldEvent.WeekScored(day, minutes, goal, loss, recovery, faded.distinct(), recovered, dropped)
}
