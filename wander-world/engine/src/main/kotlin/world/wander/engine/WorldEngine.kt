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
        fun decayMultiplier(ratio: Double): Double = when {
            ratio >= 1.0 -> 0.0
            ratio >= 0.8 -> 0.5 * (1.0 - ratio)
            else -> 1.0 - ratio.coerceAtLeast(0.0)
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
