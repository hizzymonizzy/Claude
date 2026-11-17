package world.wander.app.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import world.wander.app.R
import world.wander.app.WanderApp
import world.wander.app.data.Repository
import world.wander.app.ui.MainActivity
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a day: sync health, process walks, rebuild the world, and deliver yesterday's dispatch. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        return try {
            val dispatch = repo.dailyRun()
            repo.log("daily", "Daily run done; dispatch ${if (dispatch != null) "delivered" else "skipped"}")
            if (dispatch != null) notify(dispatch.title, dispatch.body)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Android stopped the job part-way (battery, Doze, app update…). Say why; WorkManager runs it again,
            // and the run is safe to repeat (yesterday's dispatch is only written once).
            val why = if (android.os.Build.VERSION.SDK_INT >= 31) " (stop reason ${stopReason})" else ""
            repo.log("daily", "Daily run stopped by Android$why; it will run again")
            throw e
        } catch (e: Exception) {
            repo.log("daily", "Daily run failed: ${e.message}")
            Result.retry()
        }
    }

    private fun notify(title: String, body: String) {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).putExtra("tab", "dispatch"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, WanderApp.CHANNEL_DISPATCH)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull() ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(100, n)
    }

    companion object {
        private const val NAME = "daily-dispatch"

        /** Schedules the daily run at the dispatch time (default 07:30). */
        fun schedule(context: Context, hour: Int, minute: Int, replace: Boolean = false) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(hour, minute)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)
            val request = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
