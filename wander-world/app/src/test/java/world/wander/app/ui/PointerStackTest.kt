package world.wander.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Edge pointers stack instead of overlapping (0.14). */
class PointerStackTest {
    @Test
    fun overlappingPointersStackAlongTheirEdge() {
        // Three pointers clamped to nearly the same spot on the right edge, one on the top edge.
        val pts = listOf(1000f to 500f, 1000f to 510f, 1000f to 495f, 400f to 100f)
        val out = stackPointers(pts, boxW = 56f, boxH = 54f, left = 30f, top = 100f, right = 1000f, bottom = 1800f)
        assertEquals(pts.first(), out.first())
        for (i in out.indices) for (j in i + 1 until out.size) {
            val (a, b) = out[i] to out[j]
            assertTrue("$i and $j overlap: $a $b", abs(a.first - b.first) >= 56f || abs(a.second - b.second) >= 54f)
        }
        assertTrue("right-edge pointers stay on the right edge", out.take(3).all { it.first == 1000f })
        assertEquals(400f to 100f, out[3])
    }
}
