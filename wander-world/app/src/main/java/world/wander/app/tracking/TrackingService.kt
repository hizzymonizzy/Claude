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
    private var fixCount = 0
    private var lastFixMs = 0L
    private var lastFix: LatLng? = null
    private var vehicleSinceMs: Long? = null
    private var stillJob: Job? = null
    private var idleWatchJob: Job? = null
    private var confirmJob: Job? = null
    private var liveJob: Job? = null
    /** What the walk has gathered so far (0.14), e.g. "+3 wood · +2 food · 2 places". */
    private var liveLine: String? = null

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
                val now = System.currentTimeMillis()
                when {
                    capturing && modeHint == "PROBE" && now - lastVehicleMs >= VEHICLE_COOLDOWN_MS -> {
                        // Walking after the probe: this is a real walk now, so record it as one.
                        endCapture("walking after a vehicle probe")
                        startCapture(intent.getStringExtra(EXTRA_MODE))
                    }
                    capturing -> {}
                    now - lastVehicleMs < VEHICLE_COOLDOWN_MS -> {
                        repo.log("track", "Ignoring movement right after a vehicle was detected")
                        maybeStop()
                    }
                    confirmJob?.isActive == true -> {}
                    recentShortCaptures(now) >= SHORT_RUNS_BEFORE_CONFIRM -> confirmThenStart(intent.getStringExtra(EXTRA_MODE))
                    else -> startCapture(intent.getStringExtra(EXTRA_MODE))
                }
            }
            ACTION_STILL -> {
                if (confirmJob?.isActive == true) { confirmJob?.cancel(); repo.log("track", "Stopped again before 2 min: not a walk, GPS stayed off"); maybeStop() }
                if (capturing && !manual) scheduleStillStop()
            }
            ACTION_PROBE -> if (!capturing) {
                // "In vehicle" can mean a car or a paddleboard. Record briefly: car speeds end it within
                // 45 s (vehicle guard); paddling speeds keep going and are recognised as water later.
                repo.log("track", "Vehicle detected: probing GPS in case this is a paddle")
                startCapture("PROBE")
            } else maybeStop()
            null -> {
                // Restarted by the system after being killed: resume all-day idling if it was on.
                allDay = repo.prefs.autoTrackingEnabled && repo.prefs.trackingMode == "allday"
                repo.log("track", "Service restarted by system (allDay=$allDay)")
                if (!allDay) maybeStop()
            }
        }
        return START_STICKY
    }

    /**
     * After several short "no walk" recordings in a row (moving around indoors), wait for 2 minutes of continuous
     * walking before turning GPS on, so pottering doesn't keep starting recordings (alpha 0.12). Real walks still
     * start; they just begin 2 minutes in.
     */
    private fun confirmThenStart(mode: String?) {
        repo.log("track", "Several short stops in a row: waiting for 2 min of walking before recording")
        repo.trackingStatus.value = "Checking for a walk"
        confirmJob = lifecycleScope.launch {
            delay(CONFIRM_MS)
            if (!capturing) startCapture(mode)
        }
    }

    private fun recentShortCaptures(now: Long): Int = synchronized(shortCaptureEnds) {
        shortCaptureEnds.removeAll { now - it > 60 * 60_000L }
        shortCaptureEnds.size
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
        lastFix = null
        vehicleSinceMs = null
        lastMoveMs = System.currentTimeMillis()
        lastMovePoint = null
        repo.liveTrack.value = emptyList()
        val start = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            segmentId = repo.dao.insertSegment(SegmentEntity(startMs = start, source = "phone", modeHint = mode))
        }
        repo.log("track", "Capture started (${if (manual) "manual" else "auto"}, mode=${mode ?: "?"})")
        repo.captureMode.value = mode?.takeIf { it != "PROBE" } ?: "FOOT"
        repo.trackingStatus.value = if (mode == "WATER") "Paddling" else "Charting your walk"
        startIdleWatch()
        startLiveGathering()
        updateNotification()
    }

    /** Live gathering (0.14): every 5 minutes, the walk so far is credited in memory and shown on the notification. */
    private fun startLiveGathering() {
        liveJob?.cancel()
        liveLine = null
        liveJob = lifecycleScope.launch {
            while (isActive && capturing) {
                delay(LIVE_EVERY_MS)
                val id = segmentId ?: continue
                if (!capturing || modeHint == "PROBE") continue
                val batch = buffer.toList(); buffer.clear()
                try {
                    kotlinx.coroutines.withContext(Dispatchers.IO) { if (batch.isNotEmpty()) repo.dao.insertPoints(batch) }
                    val g = repo.gatherLive(id) ?: continue
                    if (!capturing || segmentId != id) continue
                    liveLine = liveText(g)
                    updateNotification()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    repo.log("live", "Live check failed: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    private fun liveText(g: Repository.LiveGather): String {
        val t = g.gathered
        val parts = listOf("wood" to t.wood, "food" to t.food, "stone" to t.stone, "metal" to t.metal, "gold" to t.gold)
            .filter { it.second >= 1 }.map { "+${it.second.toInt()} ${it.first}" }
        val places = if (g.places > 0) listOf("${g.places} place" + if (g.places > 1) "s" else "") else emptyList()
        return (parts + places).joinToString(" · ").ifEmpty { "nothing gathered yet" }
    }

    private fun onFix(timeMs: Long, lat: Double, lng: Double, acc: Float) {
        val id = segmentId ?: return
        val p = LatLng(lat, lng)
        // Vehicle guard: sustained speed above any walk, run or ride ends the capture.
        val prevFix = lastFix
        if (prevFix != null && acc <= 30f && timeMs > lastFixMs) {
            val legM = Geo.distanceM(prevFix, p)
            val speed = legM / ((timeMs - lastFixMs) / 1000.0)
            if (speed > VEHICLE_SPEED_MPS) {
                if (vehicleSinceMs == null) vehicleSinceMs = timeMs
                if (!manual && timeMs - vehicleSinceMs!! > 45_000) {
                    lastVehicleMs = System.currentTimeMillis()
                    lastVehicleEndMs = lastVehicleMs
                    endCapture("vehicle speed for 45 s")
                    maybeStop()
                    return
                }
            } else {
                vehicleSinceMs = null
                distanceM += legM
            }
        }
        if (acc <= 30f) { lastFix = p; lastFixMs = timeMs }
        repo.lastLocation.value = p
        if (acc <= 30f) repo.liveTrack.value = repo.liveTrack.value + p
        val anchor = lastMovePoint
        if (anchor == null || Geo.distanceM(anchor, p) > 25) { lastMovePoint = p; lastMoveMs = System.currentTimeMillis() }
        buffer += TrackPointEntity(segmentId = id, timeMs = timeMs, lat = lat, lng = lng, accuracyM = acc)
        if (++fixCount % 6 == 0 && acc <= 30f) checkWater(p)
        if (buffer.size >= 10) flush()
        repo.trackingStatus.value = (if (modeHint == "WATER") "Paddling" else "Charting") + " · %.2f km".format(distanceM / 1000)
        updateNotification()
    }

    private fun flush() {
        if (buffer.isEmpty()) return
        val batch = buffer.toList(); buffer.clear()
        lifecycleScope.launch(Dispatchers.IO) { repo.dao.insertPoints(batch) }
    }

    /** Show the paddling wanderer while you're over mapped water (the walk is classified properly when processed). */
    private fun checkWater(p: LatLng) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tile = world.wander.engine.Cells.tile(p.lat, p.lng)
            val onWater = repo.dao.waterAreasInTiles(listOf(tile)).any { w ->
                try { world.wander.engine.Landmark(w.id, "", "water", world.wander.app.geo.Overpass.decodeRings(w.geometry)).contains(p.lat, p.lng) } catch (_: Exception) { false }
            }
            repo.captureMode.value = if (onWater) "WATER" else (modeHint?.takeIf { it != "PROBE" } ?: "FOOT")
            repo.trackingStatus.value = (if (onWater) "Paddling" else "Charting") + " · %.2f km".format(distanceM / 1000)
        }
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
            // Phones often say "still" on the water; only stop if GPS agrees nothing has moved.
            if (capturing && !manual && System.currentTimeMillis() - lastMoveMs > 2 * 60_000) { endCapture("still for 2 min"); maybeStop() }
        }
    }

    private fun endCapture(reason: String) {
        if (!capturing) return
        capturing = false
        idleWatchJob?.cancel()
        liveJob?.cancel()
        liveLine = null
        fused.removeLocationUpdates(callback)
        flush()
        val id = segmentId
        segmentId = null
        val end = System.currentTimeMillis()
        repo.log("track", "Capture ended ($reason), %.2f km".format(distanceM / 1000))
        if (!manual && distanceM < SHORT_CAPTURE_M) synchronized(shortCaptureEnds) { shortCaptureEnds += end }
        repo.captureMode.value = null
        repo.trackingStatus.value = if (allDay) "Waiting for movement" else "Idle"
        lifecycleScope.launch(Dispatchers.IO) {
            if (id != null) repo.dao.endSegment(id, end)
            repo.processPending()
            repo.rebuild()
        }
        updateNotification()
    }

    private fun maybeStop() {
        if (!capturing && !allDay && !manual && confirmJob?.isActive != true) {
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
            capturing -> (if (modeHint == "WATER") "Paddling" else "Charting your walk") + " · %.2f km".format(distanceM / 1000) +
                (liveLine?.let { " · $it" } ?: "")
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
        const val ACTION_PROBE = "probe"
        const val EXTRA_MODE = "mode"

        @Volatile var running = false
            private set

        /** Last time a vehicle was detected (by activity recognition or speed). */
        @Volatile var lastVehicleMs = 0L
        /** When the vehicle guard last ended a recording; no new probe for 20 minutes after. */
        @Volatile var lastVehicleEndMs = 0L
        private const val VEHICLE_COOLDOWN_MS = 90_000L
        /** Recordings shorter than this count as "short stops"; this many in an hour turns on the 2-minute check. */
        private const val SHORT_CAPTURE_M = 100.0
        private const val SHORT_RUNS_BEFORE_CONFIRM = 3
        private const val CONFIRM_MS = 2 * 60_000L
        private val shortCaptureEnds = mutableListOf<Long>()
        private const val VEHICLE_SPEED_MPS = 7.0
        private const val LIVE_EVERY_MS = 5 * 60_000L

        fun intent(context: Context, action: String, mode: String? = null) =
            Intent(context, TrackingService::class.java).setAction(action).apply { if (mode != null) putExtra(EXTRA_MODE, mode) }

        fun start(context: Context, action: String, mode: String? = null) {
            ContextCompat.startForegroundService(context, intent(context, action, mode))
        }
    }

    override fun onCreate() { super.onCreate(); running = true }
}
