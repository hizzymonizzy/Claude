package world.wander.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PointArchiveTest {
    @Test
    fun packedWalksComeBackExactlyAtStoredPrecision() {
        // An invented hour-long walk at 1 Hz near an invented origin.
        val rnd = Random(7)
        var lat = 40.30; var lng = -74.30; var t = 1_700_000_000_000L
        val pts = (0 until 3600).map {
            lat += rnd.nextDouble(-2e-5, 2e-5); lng += rnd.nextDouble(-2e-5, 2e-5); t += 1000 + rnd.nextLong(-50, 50)
            TrackPointEntity(segmentId = 9, timeMs = t, lat = lat, lng = lng, accuracyM = rnd.nextInt(30, 400) / 10f)
        }
        val data = PointArchive.encode(pts)
        val back = PointArchive.decode(9, data)
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.timeMs, b.timeMs)
            assertEquals(a.lat, b.lat, 1e-7)
            assertEquals(a.lng, b.lng, 1e-7)
            assertEquals(a.accuracyM, b.accuracyM, 0.051f)
            assertEquals(9L, b.segmentId)
        }
        println("3600 points → ${data.size / 1024} KB")
        assert(data.size < 60_000) { "archive should be compact, was ${data.size} bytes" }
    }

    @Test
    fun emptyWalk() = assertEquals(0, PointArchive.decode(1, PointArchive.encode(emptyList())).size)
}
