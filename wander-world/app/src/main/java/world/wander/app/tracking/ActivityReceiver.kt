package world.wander.app.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import world.wander.app.data.Repository

/** Receives walking / running / cycling / still transitions and drives the tracking service. */
class ActivityReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val repo = Repository.get(context)
        world.wander.app.work.NightWorker.schedule(context)   // walking after a flight: move the night check to local 3 am
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        if (!repo.prefs.autoTrackingEnabled) return
        for (event in result.transitionEvents) {
            val name = activityName(event.activityType)
            val enter = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            repo.log("activity", "${if (enter) "Enter" else "Exit"} $name (service running=${TrackingService.running})")
            if (!enter) continue
            if (event.activityType == DetectedActivity.IN_VEHICLE) TrackingService.lastVehicleMs = System.currentTimeMillis()
            when (event.activityType) {
                DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT ->
                    send(context, repo, TrackingService.ACTION_MOVING, "FOOT")
                DetectedActivity.ON_BICYCLE -> send(context, repo, TrackingService.ACTION_MOVING, "CYCLE")
                DetectedActivity.STILL ->
                    if (TrackingService.running) send(context, repo, TrackingService.ACTION_STILL, null)
                DetectedActivity.IN_VEHICLE -> when {
                    TrackingService.running -> send(context, repo, TrackingService.ACTION_STILL, null)
                    System.currentTimeMillis() - TrackingService.lastVehicleEndMs > 20 * 60_000 ->
                        send(context, repo, TrackingService.ACTION_PROBE, null)
                }
            }
        }
    }

    private fun send(context: Context, repo: Repository, action: String, mode: String?) {
        try {
            TrackingService.start(context, action, mode)
            if (!TrackingService.running) repo.log("activity", "S1: started tracking from the background ✓")
        } catch (e: Exception) {
            // Android 12+ can refuse to start a foreground service from the background.
            repo.log("activity", "S1: background start refused: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        fun activityName(type: Int) = when (type) {
            DetectedActivity.WALKING -> "walking"
            DetectedActivity.RUNNING -> "running"
            DetectedActivity.ON_FOOT -> "on foot"
            DetectedActivity.ON_BICYCLE -> "cycling"
            DetectedActivity.IN_VEHICLE -> "in vehicle"
            DetectedActivity.STILL -> "still"
            else -> "activity $type"
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 42, Intent(context, ActivityReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        /** Registers for activity transitions. Lasts until reboot or app update, so call on every app start. */
        @SuppressLint("MissingPermission")
        fun register(context: Context) {
            val repo = Repository.get(context)
            val types = listOf(
                DetectedActivity.WALKING, DetectedActivity.RUNNING, DetectedActivity.ON_FOOT,
                DetectedActivity.ON_BICYCLE, DetectedActivity.STILL, DetectedActivity.IN_VEHICLE,
            )
            val transitions = types.flatMap { t ->
                listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                    ActivityTransition.Builder().setActivityType(t).setActivityTransition(it).build()
                }
            }
            try {
                ActivityRecognition.getClient(context)
                    .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
                    .addOnSuccessListener { repo.log("activity", "Activity detection registered") }
                    .addOnFailureListener { repo.log("activity", "Activity detection failed: ${it.message}") }
            } catch (e: SecurityException) {
                repo.log("activity", "Activity recognition permission missing")
            }
        }

        @SuppressLint("MissingPermission")
        fun unregister(context: Context) {
            try { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent(context)) } catch (_: Exception) {}
        }
    }
}
