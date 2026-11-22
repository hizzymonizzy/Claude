package world.wander.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import world.wander.app.geo.Overpass
import world.wander.app.health.HealthSync
import world.wander.app.work.ProcessWorker
import world.wander.engine.AreaAssigner
import world.wander.engine.AreaInfo
import world.wander.engine.CellPresence
import world.wander.engine.Cells
import world.wander.engine.DayIntensity
import world.wander.engine.DayMovement
import world.wander.engine.Dispatch
import world.wander.engine.DispatchWriter
import world.wander.engine.EdgeCoverage
import world.wander.engine.EdgeState
import world.wander.engine.GraphEdge
import world.wander.engine.LatLng
import world.wander.engine.Landmark
import world.wander.engine.LandmarkInfo
import world.wander.engine.LandmarkVisit
import world.wander.engine.TroddenCell
import world.wander.engine.Arrival
import world.wander.engine.ProjectDecision
import world.wander.engine.Proposal
import world.wander.engine.Geo
import world.wander.engine.Mode
import world.wander.engine.Place
import world.wander.engine.TrackPoint
import world.wander.engine.TrackProcessor
import world.wander.engine.WorldEngine
import world.wander.engine.WorldInput
import world.wander.engine.WorldState
import world.wander.engine.Yields
import world.wander.engine.Purchase
import world.wander.engine.WorldEvent
import world.wander.engine.ResourceFinder
import world.wander.engine.ResourceTarget
import java.time.LocalDate
import java.time.ZoneId

// Split out of Repository.kt (review M2): the shareable diagnostics report.

/** A plain-text report of everything useful for debugging, for sharing. */
suspend fun Repository.diagnosticsReport(extra: List<String>): String = withContext(Dispatchers.IO) {
    val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone)
    val pkg = app.packageManager.getPackageInfo(app.packageName, 0)
    val w = world.value ?: try { rebuild() } catch (e: Exception) { log("world", "Rebuild failed: $e"); null }
    buildString {
        appendLine("Wander World diagnostics · ${fmt.format(java.time.Instant.now())}")
        appendLine("App ${pkg.versionName} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("World start: ${java.time.LocalDate.ofEpochDay(prefs.startDay.toLong())} · home area: ${prefs.homeAreaId} · tracking: ${if (prefs.autoTrackingEnabled) prefs.trackingMode else "off"} · resting: ${prefs.resting} · goal locked: ${prefs.goalLocked} · resource pointers: ${if (prefs.resourcePointers) "on" else "off"}")
        extra.forEach { appendLine(it) }
        appendLine()
        appendLine("== World ==")
        if (w != null) {
            appendLine("Roads charted: ${w.discoveredCount} · cells known: ${w.cellFamiliarity.size} · own-path cells: ${w.troddenWear.size}")
            appendLine("Regions: " + w.regions.joinToString { "${it.name}${if (it.isHome) " (home)" else ""}${if (it.dormant) " (dormant)" else ""}" })
            appendLine("Treasury: ${w.treasury.toString().ifEmpty { "empty" }} · proposal: ${w.proposal?.let { "${it.projectId} for ${it.areaId}" } ?: "none"} · monuments: ${w.monuments.joinToString { it.id }.ifEmpty { "none" }}")
            appendLine("Waystones: ${w.waystones.size} (${w.waystones.count { it.great }} great) · portals: ${w.portals.size}")
            appendLine("Places discovered: " + w.landmarks.joinToString { "${it.name} (${it.kind}, ${it.visitDays}d)" }.ifEmpty { "none" })
            appendLine("Goal: %.0f / %d active min, last 7 days (ratio %.2f, %s) · this week %.0f · cooling applies: ${w.goal.coolingApplies}".format(w.goal.trailing7Minutes, w.goal.goal, w.goal.ratio, w.goal.protection, w.goal.weekMinutes))
            for (st in w.settlements) appendLine("  ${st.name} [${st.areaId}] ${st.tier} (peak ${st.peakTier}) D=%.1f days=${st.areaDays} cells=${st.exploredCells} at %.4f,%.4f".format(st.development, st.position.lat, st.position.lng))
            appendLine("Waypoints: ${w.exploredAreas.count { !it.settled }} · map layers: see [map] lines in the log")
            appendLine("Last 14 days (active min · intensity min · walked km):")
            for (d in w.today - 13..w.today) {
                val s = w.days[d] ?: continue
                appendLine("  ${java.time.LocalDate.ofEpochDay(d.toLong())}  %5.1f · %5.1f min · %.2f km · %s".format(s.activeMinutes, s.intensityMinutes, s.footMeters / 1000, s.protection))
            }
        } else appendLine("(could not be built; see log)")
        val waiting = dao.pendingSegments()
        if (waiting.isNotEmpty()) {
            val proc = TrackProcessor(zone)
            val missing = waiting.flatMap { seg -> proc.usablePoints(pointsOf(seg.id).map { TrackPoint(it.timeMs, it.lat, it.lng, it.accuracyM) }).map { Cells.tile(it.lat, it.lng) } }.toSet().filter { dao.tile(it) == null }
            appendLine("Waiting walks: ${waiting.size} · missing map tiles: ${missing.joinToString().ifEmpty { "none" }}")
        }
        appendLine()
        appendLine("== Intensity from Health Connect ==")
        dao.allIntensity().sortedByDescending { it.day }.take(28).forEach {
            appendLine("  ${java.time.LocalDate.ofEpochDay(it.day.toLong())} moderate %.0f vigorous %.0f (${it.source})".format(it.moderateMin, it.vigorousMin))
        }
        appendLine()
        appendLine("== Captures ==")
        dao.recentSegments(40).forEach {
            appendLine("  ${fmt.format(java.time.Instant.ofEpochMilli(it.startMs))} → ${it.endMs?.let { e -> fmt.format(java.time.Instant.ofEpochMilli(e)) } ?: "recording"} · ${it.source} · ${it.modeHint ?: "?"} · ${dao.pointCount(it.id)} pts · ${it.note ?: if (it.processed) "processed" else "waiting"}")
        }
        appendLine()
        appendLine("== Log (newest first) ==")
        dao.recentLogs(1000).forEach { appendLine("${fmt.format(java.time.Instant.ofEpochMilli(it.timeMs))} [${it.tag}] ${it.message}") }
    }
}

