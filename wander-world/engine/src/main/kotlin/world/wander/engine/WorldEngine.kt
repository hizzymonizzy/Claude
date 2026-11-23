package world.wander.engine

import world.wander.engine.sim.Simulation
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic world simulation. Replays every observation day by day from the start of the world
 * to [today] and returns the derived state. Same inputs + same params = same world, always.
 *
 * The replay itself lives in `sim/`: [Simulation] holds the state and the order of a day's steps; each step
 * (regions, roads and places, settlements, goal, economy) has its own file.
 */
class WorldEngine(private val params: WorldParams = WorldParams()) {

    fun build(input: WorldInput, today: Int): WorldState = Simulation(input, params, today).run()

    companion object {
        /** The health guideline's weekly minimum (WHO 2020 / US 2018): it alone decides cooling (ADR 0011). */
        const val GUIDELINE_MINUTES = 150.0

        /**
         * Steps a place cools at the weekly scoring, judged against the fixed guideline, never the adaptive goal
         * (ADR 0011): under 75 minutes a quarter step, 75–119 an eighth, 120–149 a fortieth, 150 or more nothing.
         * During the three weeks after a rest, only weeks under 75 cool, and by an eighth.
         */
        fun weeklyLoss(minutes: Double, rampingBack: Boolean = false): Double = when {
            rampingBack -> if (minutes < 75) 0.125 else 0.0
            minutes >= GUIDELINE_MINUTES -> 0.0
            minutes >= 120 -> 0.025
            minutes >= 75 -> 0.125
            else -> 0.25
        }

        /** Steps that warm back at the weekly scoring: a quarter for a week at or over your (adaptive) goal. */
        fun weeklyRecovery(minutes: Double, goal: Int): Double = if (minutes >= goal) 0.25 else 0.0

        /** Goal bands: names for ranges of the adaptive goal (ADR 0011). */
        fun band(goal: Int): String = when {
            goal >= 300 -> "Pathfinder"
            goal >= 250 -> "Trekker"
            goal >= 200 -> "Rambler"
            else -> "Stroller"
        }

        fun mergeIntervals(list: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
            if (list.isEmpty()) return emptyList()
            val sorted = list.map { min(it.first, it.second) to max(it.first, it.second) }.sortedBy { it.first }
            val out = mutableListOf(sorted.first())
            for (iv in sorted.drop(1)) {
                val last = out.last()
                if (iv.first <= last.second + 1.0) out[out.size - 1] = last.first to max(last.second, iv.second)
                else out += iv
            }
            return out
        }

        fun mergedLength(list: List<Pair<Double, Double>>): Double =
            mergeIntervals(list).sumOf { it.second - it.first }
    }
}
