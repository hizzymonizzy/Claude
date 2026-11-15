package world.wander.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActivityIntensityRecord
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import world.wander.app.data.DailyIntensityEntity
import world.wander.app.data.SegmentEntity
import world.wander.app.data.TrackPointEntity
import world.wander.app.data.WanderDao
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId

/**
 * Reads intensity minutes (for the weekly goal) and GPS workouts recorded by the watch
 * (which build the world) from Health Connect.
 */
class HealthSync(private val context: Context, private val dao: WanderDao, private val log: (String) -> Unit) {

    /** Each permission with a plain-language label, in the order shown in Settings. */
    val permissionLabels: List<Pair<String, String>> = listOf(
        HealthPermission.getReadPermission(ActivityIntensityRecord::class) to "Activity intensity (weekly goal)",
        HealthPermission.getReadPermission(ExerciseSessionRecord::class) to "Exercise sessions (find watch workouts)",
        HealthPermission.PERMISSION_READ_EXERCISE_ROUTES to "Exercise routes (watch GPS) · needs \"Always allow\"",
        HealthPermission.getReadPermission(StepsRecord::class) to "Steps (backup for intensity)",
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND to "Read in background (morning dispatch)",
    )
    val permissions: Set<String> = permissionLabels.map { it.first }.toSet()

    fun available(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    /** Syncs the last [days] days. Safe to run repeatedly. Watch routes before [worldStartDay] are ignored. */
    suspend fun sync(worldStartDay: Int, days: Long = 28) {
        if (!available()) { log("Health Connect not available"); return }
        val granted = granted()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.minusDays(days - 1).atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()

        val intensityFeature = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_ACTIVITY_INTENSITY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val canIntensity = intensityFeature && HealthPermission.getReadPermission(ActivityIntensityRecord::class) in granted

        val byDay = HashMap<Int, DoubleArray>() // [moderate, vigorous]
        var source = "none"
        if (canIntensity) {
            val res = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(ActivityIntensityRecord.MODERATE_DURATION_TOTAL, ActivityIntensityRecord.VIGOROUS_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
            for (r in res) {
                val day = r.startTime.toLocalDate().toEpochDay().toInt()
                val mod = r.result[ActivityIntensityRecord.MODERATE_DURATION_TOTAL]?.toMinutesPart(true) ?: 0.0
                val vig = r.result[ActivityIntensityRecord.VIGOROUS_DURATION_TOTAL]?.toMinutesPart(true) ?: 0.0
                byDay[day] = doubleArrayOf(mod, vig)
            }
            source = "intensity"
            log("Health: intensity records for ${res.size} days, total %.0f min".format(byDay.values.sumOf { it[0] + 2 * it[1] }))
        }
        if (byDay.values.sumOf { it[0] + it[1] } < 1.0 && HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            // Fallback: estimate from steps per minute (≥100/min moderate, ≥130/min vigorous).
            byDay.clear()
            var cursor = start
            while (cursor < end) {
                val next = minOf(cursor.plusDays(2), end)
                val res = client.aggregateGroupByDuration(
                    AggregateGroupByDurationRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(cursor, next),
                        timeRangeSlicer = Duration.ofMinutes(1),
                    ),
                )
                for (r in res) {
                    val steps = r.result[StepsRecord.COUNT_TOTAL] ?: continue
                    val day = LocalDateTime.ofInstant(r.startTime, zone).toLocalDate().toEpochDay().toInt()
                    val acc = byDay.getOrPut(day) { DoubleArray(2) }
                    when {
                        steps >= 130 -> acc[1] += 1.0
                        steps >= 100 -> acc[0] += 1.0
                    }
                }
                cursor = next
            }
            source = "steps-cadence"
            log("Health: estimated from step cadence, total %.0f min".format(byDay.values.sumOf { it[0] + 2 * it[1] }))
        }
        if (byDay.isNotEmpty()) {
            dao.upsertIntensity(byDay.map { (d, v) -> DailyIntensityEntity(d, v[0], v[1], source) })
        }

        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) importWatchRoutes(maxOf(start, LocalDate.ofEpochDay(worldStartDay.toLong()).atStartOfDay()), end, zone)
    }

    /**
     * The newest watch workout whose route is waiting for consent. Asking for it shows Health Connect's
     * route prompt, which offers "Allow all routes" (that grants route access for good).
     */
    suspend fun routeConsentSessionId(): String? {
        if (!available()) return null
        val zone = ZoneId.systemDefault()
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay()
        val sessions = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(end.minusDays(29), end)),
        ).records
        return sessions.sortedByDescending { it.startTime }
            .firstOrNull { it.exerciseRouteResult is ExerciseRouteResult.ConsentRequired }
            ?.metadata?.id
    }

    /** Imports outdoor GPS workouts (e.g. from the Pixel Watch) that the phone didn't capture itself. */
    private suspend fun importWatchRoutes(start: LocalDateTime, end: LocalDateTime, zone: ZoneId) {
        val outdoor = mapOf(
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING to "FOOT",
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING to "FOOT",
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING to "FOOT",
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING to "CYCLE",
        )
        val sessions = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(start, end)),
        ).records
        var imported = 0
        var needConsent = 0
        for (s in sessions) {
            val mode = outdoor[s.exerciseType] ?: continue
            if (s.metadata.dataOrigin.packageName == context.packageName) continue
            val startMs = s.startTime.toEpochMilli(); val endMs = s.endTime.toEpochMilli()
            val overlapping = dao.segmentsOverlapping(startMs, endMs)
            if (overlapping.any { it.source == "watch" && it.startMs == startMs }) continue // already imported
            if (overlapping.any { it.source == "phone" && dao.pointCount(it.id) > 20 }) continue // phone captured it
            when (val route = s.exerciseRouteResult) {
                is ExerciseRouteResult.Data -> {
                    val pts = route.exerciseRoute.route
                    if (pts.size < 2) continue
                    val segId = dao.insertSegment(SegmentEntity(startMs = startMs, endMs = endMs, source = "watch", modeHint = mode))
                    dao.insertPoints(pts.map {
                        TrackPointEntity(
                            segmentId = segId, timeMs = it.time.toEpochMilli(), lat = it.latitude, lng = it.longitude,
                            accuracyM = it.horizontalAccuracy?.inMeters?.toFloat() ?: 10f,
                        )
                    })
                    imported++
                }
                is ExerciseRouteResult.ConsentRequired -> needConsent++
                else -> {}
            }
        }
        if (imported > 0) log("Imported $imported watch workout route(s)")
        if (needConsent > 0) log("$needConsent watch workout(s) need route access: set Exercise routes to \"Always allow\" in Health Connect")
    }

    private fun Duration.toMinutesPart(@Suppress("UNUSED_PARAMETER") exact: Boolean): Double = toMillis() / 60_000.0
}
