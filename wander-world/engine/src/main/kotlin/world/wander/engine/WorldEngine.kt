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
        /**
         * Steps a place loses at the weekly scoring (ADR 0008, revised 0.12.1: partial decay): a quarter of a step
         * below half the goal, sliding to an eighth at 80%, at most a fortieth for a near miss, nothing at the goal.
         * So 2 missed weeks dim a place by half a step and 4 cost a whole step.
         */
        fun weeklyLoss(ratio: Double): Double = when {
            ratio >= 1.0 -> 0.0
            ratio >= 0.8 -> 0.025 * (1.0 - ratio) / 0.2
            ratio >= 0.5 -> 0.25 - 0.125 * (ratio - 0.5) / 0.3
            else -> 0.25
        }

        /** Steps that come back at the weekly scoring after a week over the goal: a quarter, like a missed week's loss. */
        fun weeklyRecovery(ratio: Double): Double = if (ratio > 1.0) 0.25 else 0.0

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
