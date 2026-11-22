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
         * The share of heat a place loses at the weekly scoring (ADR 0008), by last week's ratio to the goal:
         * 30% below half the goal, sliding to 15% at 80%, then at most 3% for a near miss, nothing at 100%.
         */
        fun weeklyLoss(ratio: Double): Double = when {
            ratio >= 1.0 -> 0.0
            ratio >= 0.8 -> 0.03 * (1.0 - ratio) / 0.2
            ratio >= 0.5 -> 0.30 - 0.15 * (ratio - 0.5) / 0.3
            else -> 0.30
        }

        /** The share of what cooled that comes back at the weekly scoring, for a week above the goal (up to 150%). */
        fun weeklyRecovery(ratio: Double, full: Double = 0.5): Double =
            if (ratio <= 1.0) 0.0 else full * (minOf(ratio, 1.5) - 1.0) / 0.5

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
