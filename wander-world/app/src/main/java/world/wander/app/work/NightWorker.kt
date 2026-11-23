package world.wander.app.work

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import world.wander.app.data.Repository
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * About 3 am: one low-power location check, so a night spent away founds a camp there (0.13, player's rule: camps
 * come from camping). No tracking, no GPS walk; the fix stays on the phone.
 */
class NightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        return try {
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await() ?: client.lastLocation.await()
            when {
                loc == null -> repo.log("camp", "Night check: no location")
                loc.accuracy > 500 -> repo.log("camp", "Night check: too vague (±${loc.accuracy.toInt()} m), skipped")
                else -> repo.recordNight(loc.latitude, loc.longitude, loc.accuracy)
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            repo.log("camp", "Night check failed: ${e.javaClass.simpleName}")
            Result.success()
        }
    }

    companion object {
        private const val NAME = "night-check"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(3, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<NightWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
