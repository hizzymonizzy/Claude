package world.wander.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import world.wander.app.data.Repository
import java.util.concurrent.TimeUnit

/** Retries walks that are waiting for map data, once the phone has a connection. */
class ProcessWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        NightWorker.schedule(applicationContext)   // follows a time-zone change (0.15)
        val done = repo.processPending()
        if (done > 0) repo.rebuild()
        val waiting = repo.dao.pendingSegments().size
        repo.log("process", "Background retry: processed $done, still waiting $waiting")
        return if (waiting > 0) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProcessWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("process-pending", ExistingWorkPolicy.KEEP, request)
        }
    }
}
