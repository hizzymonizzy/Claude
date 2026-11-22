package world.wander.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatSpreadTest {
    private val a = Cells.cellAt(40.30, -74.30, Cells.HEAT_RES)

    @Test
    fun aHexReachesAsManyRingsAsItsStepAndFadesOutward() {
        val once = HeatSpread.coverage(mapOf(a to 1.0))           // step 1: itself + 1 ring
        assertEquals(7, once.size)
        assertEquals(1, once[a]); assertEquals(6, once.values.count { it == 0 })
        val avenue = HeatSpread.coverage(mapOf(a to 120.0))       // step 5: 5 rings, 91 hexes
        assertEquals(91, avenue.size)
        assertEquals(5, avenue[a])
        for (d in 0..5) for (c in Cells.rings(a, 5)[d]) assertEquals(5 - d, avenue[c])
    }

    @Test
    fun overlapsTakeTheRichestAndBigHexesNeedRealCoverage() {
        val b = Cells.rings(a, 2)[2].first()
        val cov = HeatSpread.coverage(mapOf(a to 30.0, b to 1.0))  // b sits 2 rings out from a step-4 hex
        assertEquals(2, cov[b])                                    // a's spread (4 − 2) beats b's own step 1
        val hood = HeatSpread.aggregate(cov, Cells.HOOD_RES, 49)
        assertTrue(hood.isNotEmpty())
        // A single pale ring clipping a neighbourhood hex doesn't paint it.
        assertTrue(HeatSpread.aggregate(HeatSpread.coverage(mapOf(a to 1.0)), Cells.HOOD_RES, 49).values.all { it <= 1 })
    }
}
