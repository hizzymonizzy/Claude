package world.wander.engine

/**
 * How walked hexes enrich the ground around them (0.12.2, player's rule): a hex walked to step N reaches N rings out,
 * one step poorer per ring, so the outermost ring is pale explored ground (level 0) and the hex itself shows its step.
 * Overlaps take the richest. Busy paths fill in the map around them; a one-off walk leaves a thin pale band.
 */
object HeatSpread {
    private val levels = listOf(1.0, 3.0, 10.0, 30.0, 100.0)
    fun step(v: Double): Int = levels.count { v >= it }

    /** Block hexes (res 12) to their shown level 0 (pale) … 5 (richest). */
    fun coverage(heat: Map<String, Double>): Map<String, Int> {
        val out = HashMap<String, Int>(heat.size * 4)
        for ((cell, v) in heat) {
            val s = step(v)
            if (s <= 0) continue
            val rings = try { Cells.rings(cell, s) } catch (_: Exception) { continue }
            rings.forEachIndexed { d, ring -> val level = s - d; for (c in ring) if ((out[c] ?: -1) < level) out[c] = level }
        }
        return out
    }

    /**
     * Coarser hexes from finer coverage: shown only where at least [minShare] of the hex is covered, at the rounded
     * mean level of what's covered, so a big hex merely clipped by a walk doesn't show as a solid block.
     */
    fun aggregate(fine: Map<String, Int>, res: Int, childrenPerHex: Int, minShare: Double = 0.2): Map<String, Int> {
        val groups = HashMap<String, MutableList<Int>>()
        for ((c, l) in fine) groups.getOrPut(try { Cells.parent(c, res) } catch (_: Exception) { continue }) { mutableListOf() } += l
        return groups.filterValues { it.size >= minShare * childrenPerHex }.mapValues { (_, ls) -> Math.round(ls.average()).toInt() }
    }
}
