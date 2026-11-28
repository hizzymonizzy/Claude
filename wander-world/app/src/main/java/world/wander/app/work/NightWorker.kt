package world.wander.app.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import world.wander.app.data.Repository
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 3 am where you are: one low-power location check, so a night spent away counts toward a camp there (camps grow by
 * stays, 0.15). No tracking, no GPS walk; the fix stays on the phone.
 *
 * 0.15: scheduled one night at a time for the next 3 am in the phone's current time zone, and rescheduled when the
 * zone changes (checked on app start and by the background workers), so after a flight the check follows local time.
 * The night is credited to the local evening before.
 */
class NightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val zone = ZoneId.systemDefault()
        val at = ZonedDateTime.now(zone)
        val stamp = "%02d:%02d (%s, UTC%s)".format(at.hour, at.minute, zone.id, at.offset.id.replace("Z", "+0"))
        try {
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() ?: client.lastLocation.await()
            when {
                loc == null -> repo.log("camp", "Night check $stamp: no location")
                loc.accuracy > 500 -> repo.log("camp", "Night check $stamp: too vague (±${loc.accuracy.toInt()} m), skipped")
                else -> repo.recordNight(loc.latitude, loc.longitude, loc.accuracy, stamp)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            repo.log("camp", "Night check $stamp failed: ${e.javaClass.simpleName}")
        } finally {
            schedule(applicationContext, force = true)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "night-check-local"
        private const val OLD_PERIODIC = "night-check"

        /**
         * Makes sure the next check is at 3 am local time. Cheap to call often: it only replaces the scheduled check
         * when [force] is set or the phone's time zone has changed since it was scheduled.
         */
        fun schedule(context: Context, force: Boolean = false) {
            val repo = Repository.get(context)
            val zone = ZoneId.systemDefault()
            val changed = repo.prefs.nightZone != zone.id
            if (!force && !changed) return
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(OLD_PERIODIC)   // the 0.13 24-hour repeat stayed on home time
            val now = ZonedDateTime.now(zone)
            var next = now.toLocalDate().atTime(3, 0).atZone(zone)
            if (!next.isAfter(now.plusMinutes(5))) next = next.plusDays(1)
            val request = OneTimeWorkRequestBuilder<NightWorker>()
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
            if (changed && repo.prefs.nightZone != null) repo.log("camp", "Time zone now ${zone.id}: night check moved to 3 am local")
            repo.prefs.nightZone = zone.id
        }
    }
}
