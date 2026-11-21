package world.wander.app.data

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.roundToLong

/**
 * Compact storage for a finished walk's GPS points (review H1, alpha 0.11.1). Points used to be deleted after
 * 90 days, which meant older walks could never be re-read under new rules; now they're packed into one row per walk.
 *
 * Format: deflated text, one point per line as deltas from the previous point:
 * `dtMs,dLat,dLng,accDm` with lat/lng in 1e-7 degrees (~1 cm) and accuracy in decimetres. Lossless at those units.
 */
object PointArchive {
    private const val E7 = 1e7

    fun encode(points: List<TrackPointEntity>): ByteArray {
        val sb = StringBuilder(points.size * 24)
        var t = 0L; var la = 0L; var ln = 0L
        for (p in points) {
            val pt = p.timeMs; val pla = (p.lat * E7).roundToLong(); val pln = (p.lng * E7).roundToLong()
            sb.append(pt - t).append(',').append(pla - la).append(',').append(pln - ln).append(',')
                .append((p.accuracyM * 10).roundToLong()).append('\n')
            t = pt; la = pla; ln = pln
        }
        val input = sb.toString().toByteArray(Charsets.US_ASCII)
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(input); finish() }
        val out = ByteArrayOutputStream(input.size / 3 + 16)
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    fun decode(segmentId: Long, data: ByteArray): List<TrackPointEntity> {
        val inf = Inflater().apply { setInput(data) }
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        var t = 0L; var la = 0L; var ln = 0L
        return out.toString(Charsets.US_ASCII.name()).lineSequence().filter { it.isNotEmpty() }.map { line ->
            val f = line.split(',')
            t += f[0].toLong(); la += f[1].toLong(); ln += f[2].toLong()
            TrackPointEntity(segmentId = segmentId, timeMs = t, lat = la / E7, lng = ln / E7, accuracyM = f[3].toLong() / 10f)
        }.toList()
    }
}
