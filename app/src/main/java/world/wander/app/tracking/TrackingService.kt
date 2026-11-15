package world.wander.app.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import world.wander.app.R
import world.wander.app.WanderApp
import world.wander.app.data.Repository
import world.wander.app.data.SegmentEntity
import world.wander.app.data.TrackPointEntity
import world.wander.app.ui.MainActivity
import world.wander.engine.Geo
import world.wander.engine.LatLng

/**
 * Captures GPS while you move. Three ways in:
 *  - Manual: "Start a walk" in the app.
 *  - All-day: the service idles in the foreground all day (GPS off) and switches GPS on when
 *    activity recognition reports walking, running or cycling.
 *  - Auto: activity recognition tries to start the service from the background. Android may refuse;
 *    the diagnostics log records whether it works on this phone (Spike S1).
 */
class TrackingService : LifecycleService() {

    private val repo by lazy { Repository.get(this) }
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var allDay = false
    private var manual = false
    private var capturing = false
    private var segmentId: Long? = null
    private var modeHint: String? = null
    private val buffer = mutableListOf<TrackPointEntity>()
    private var distanceM = 0.0
    private var lastMoveMs = 0L
    private var lastMovePoint: LatLng? = null
    private var stillJob: Job? = null
    private var idleWatchJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) onFix(loc.time, loc.latitude, loc.longitude, loc.accuracy)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action
        if (!goForeground()) { stopSelf(); return START_NOT_STICKY }
        when (action) {
            ACTION_START_MANUAL -> { manual = true; startCapture(intent.getStringExtra(EXTRA_MODE)) }
            ACTION_STOP_MANUAL -> { manual = false; endCapture("manual stop"); maybeStop() }
            ACTION_START_ALLDAY -> { allDay = true; repo.log("track", "All-day mode on: idling, waiting for movement"); updateNotification() }
            ACTION_STOP_ALLDAY -> { allDay = false; if (!manual) endCapture("all-day off"); maybeStop() }
            ACTION_MOVING -> {
                stillJob?.cancel()
                if (!capturing) startCapture(intent.getStringExtra(EXTRA_MODE))
            }
            ACTION_STILL -> if (capturing && !manual) scheduleStillStop()
            null -> {
                // Restarted by the system after being killed: resume all-day idling if it was on.
                allDay = repo.prefs.autoTrackingEnabled && repo.prefs.trackingMode == "allday"
                repo.log("track", "Service restarted by system (allDay=$allDay)")
                if (!allDay) maybeStop()
            }
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean = try {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (e: Exception) {
        repo.log("track", "S1: startForeground refused: ${e.javaClass.simpleName}: ${e.message}")
        false
    }

    @SuppressLint("MissingPermission")
    private fun startCapture(mode: String?) {
        if (capturing) return
        modeHint = mode
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, if (mode == "CYCLE") 4_000L else 5_000L)
            .setMinUpdateDistanceMeters(4f)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            repo.log("track", "No location permission: ${e.message}")
            return
        }
        capturing = true
        distanceM = 0.0
        lastMoveMs = System.currentTimeMillis()
        lastMovePoint = null
        repo.liveTrack.value = emptyList()
        val start = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            segmentId = repo.dao.insertSegment(SegmentEntity(startMs = start, source = "phone", modeHint = mode))
        }
        repo.log("track", "Capture started (${if (manual) "manual" else "auto"}, mode=${mode ?: "?"})")
        repo.trackingStatus.value = "Charting your walk"
        startIdleWatch()
        updateNotification()
    }

    private fun onFix(timeMs: Long, lat: Double, lng: Double, acc: Float) {
        val id = segmentId ?: return
        val p = LatLng(lat, lng)
        val prev = repo.liveTrack.value.lastOrNull()
        if (prev != null && acc <= 30f) distanceM += Geo.distanceM(prev, p)
        repo.lastLocation.value = p
        if (acc <= 30f) repo.liveTrack.value = repo.liveTrack.value + p
        val anchor = lastMovePoint
        if (anchor == null || Geo.distanceM(anchor, p) > 25) { lastMovePoint = p; lastMoveMs = System.currentTimeMillis() }
        buffer += TrackPointEntity(segmentId = id, timeMs = timeMs, lat = lat, lng = lng, accuracyM = acc)
        if (buffer.size >= 10) flush()
        repo.trackingStatus.value = "Charting · %.2f km".format(distanceM / 1000)
        updateNotification()
    }

    private fun flush() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList(); buffer.clear()
        lifecycleScope.launch(Dispatchers.IO) { repo.dao.insertPoints(batch) }
    }

    /** When not moving for a while, end the capture (unless it's a manual session). */
    private fun startIdleWatch() {
        idleWatchJob?.cancel()
        idleWatchJob = lifecycleScope.launch {
            while (isActive && capturing) {
                delay(30_000)
                if (!manual && System.currentTimeMillis() - lastMoveMs > 4 * 60_000) {
                    endCapture("no movement for 4 min")
                    maybeStop()
                }
            }
        }
    }

    private fun scheduleStillStop() {
        stillJob?.cancel()
        stillJob = lifecycleScope.launch {
            delay(2 * 60_000)
            if (capturing && !manual) { endCapture("still for 2 min"); maybeStop() }
        }
    }

    private fun endCapture(reason: String) {
        if (!capturing) return
        capturing = false
        idleWatchJob?.cancel()
        fused.removeLocationUpdates(callback)
        flush()
        val id = segmentId
        segmentId = null
        val end = System.currentTimeMillis()
        repo.log("track", "Capture ended ($reason), %.2f km".format(distanceM / 1000))
        repo.trackingStatus.value = if (allDay) "Waiting for movement" else "Idle"
        lifecycleScope.launch(Dispatchers.IO) {
            if (id != null) repo.dao.endSegment(id, end)
            repo.processPending()
            repo.rebuild()
        }
        updateNotification()
    }

    private fun maybeStop() {
        if (!capturing && !allDay && !manual) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else updateNotification()
    }

    override fun onDestroy() {
        if (capturing) endCapture("service destroyed")
        running = false
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        repo.log("track", "Foreground service timed out by the system")
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val text = when {
            capturing -> "Charting your walk · %.2f km".format(distanceM / 1000)
            allDay -> "Waiting for you to start moving"
            else -> "Ready"
        }
        return NotificationCompat.Builder(this, WanderApp.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle("Wander World")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val NOTIFICATION_ID = 7
        const val ACTION_START_MANUAL = "start_manual"
        const val ACTION_STOP_MANUAL = "stop_manual"
        const val ACTION_START_ALLDAY = "start_allday"
        const val ACTION_STOP_ALLDAY = "stop_allday"
        const val ACTION_MOVING = "moving"
        const val ACTION_STILL = "still"
        const val EXTRA_MODE = "mode"

        @Volatile var running = false
            private set

        fun intent(context: Context, action: String, mode: String? = null) =
            Intent(context, TrackingService::class.java).setAction(action).apply { if (mode != null) putExtra(EXTRA_MODE, mode) }

        fun start(context: Context, action: String, mode: String? = null) {
            ContextCompat.startForegroundService(context, intent(context, action, mode))
        }
    }

    override fun onCreate() { super.onCreate(); running = true }
}
