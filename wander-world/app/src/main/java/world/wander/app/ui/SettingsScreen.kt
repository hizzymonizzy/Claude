package world.wander.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.PermissionController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import world.wander.app.data.Repository
import world.wander.app.data.SegmentEntity
import world.wander.app.tracking.ActivityReceiver
import world.wander.app.tracking.TrackingService
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun Context.has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
private val timeFmt = DateTimeFormatter.ofPattern("MMM d HH:mm:ss").withZone(ZoneId.systemDefault())

@Composable
fun SettingsScreen(repo: Repository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var healthGranted by remember { mutableStateOf<Set<String>>(emptySet()) }
    val logs by repo.dao.diagnostics().collectAsState(initial = emptyList())
    val prefs = repo.prefs

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        healthGranted = it; refresh++
    }
    LaunchedEffect(refresh) {
        healthGranted = try { if (repo.health.available()) repo.health.granted() else emptySet() } catch (_: Exception) { emptySet() }
    }

    fun setAuto(enabled: Boolean, mode: String = prefs.trackingMode) {
        prefs.autoTrackingEnabled = enabled
        prefs.trackingMode = mode
        if (enabled) {
            ActivityReceiver.register(ctx)
            if (mode == "allday") TrackingService.start(ctx, TrackingService.ACTION_START_ALLDAY)
            else if (TrackingService.running) TrackingService.start(ctx, TrackingService.ACTION_STOP_ALLDAY)
        } else {
            ActivityReceiver.unregister(ctx)
            if (TrackingService.running) TrackingService.start(ctx, TrackingService.ACTION_STOP_ALLDAY)
        }
        repo.log("settings", "Auto tracking ${if (enabled) "on ($mode)" else "off"}")
        refresh++
    }

    @Suppress("UNUSED_EXPRESSION") refresh
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

            SectionTitle("1 · Permissions")
            PermRow("Location (precise)", ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)) {
                permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            PermRow("Location all the time", ctx.has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            PermRow("Physical activity", ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) {
                permLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            }
            PermRow("Notifications", ctx.has(Manifest.permission.POST_NOTIFICATIONS)) {
                permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
            val healthAvailable = repo.health.available()
            SectionTitle("Health Connect")
            if (!healthAvailable) {
                Text("Health Connect isn't available on this phone.", style = MaterialTheme.typography.bodySmall)
            } else {
                repo.health.permissionLabels.forEach { (perm, label) ->
                    Text((if (perm in healthGranted) "✓ " else "○ ") + label, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!healthGranted.containsAll(repo.health.permissions)) {
                        OutlinedButton(onClick = { healthLauncher.launch(repo.health.permissions) }) { Text("Grant") }
                    }
                    OutlinedButton(onClick = { openHealthConnectSettings(ctx) }) { Text("Open Health Connect") }
                }
                if (HealthPermission.PERMISSION_READ_EXERCISE_ROUTES !in healthGranted) {
                    Text(
                        "Watch routes: tap Open Health Connect, then Exercise routes → Always allow.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider()

            SectionTitle("2 · Home")
            Text(
                if (prefs.homeAreaId != null) "Capital set (area ${prefs.homeAreaId})" else "Stand at home and set it here. Your capital is founded on your first walk there.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = {
                scope.launch {
                    message = "Finding you…"
                    try {
                        val loc = LocationServices.getFusedLocationProviderClient(ctx)
                            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                        if (loc == null) { message = "Couldn't get a location fix"; return@launch }
                        message = "Fetching your neighborhood…"
                        val named = repo.setHome(loc.latitude, loc.longitude)
                        message = if (named) "Home set." else "Home saved. The map server is busy; your neighborhood will be named on the next try."
                    } catch (e: SecurityException) { message = "Grant location first" } catch (e: Exception) { message = "Failed: ${e.message}" }
                    refresh++
                }
            }) { Text(if (prefs.homeAreaId == null) "Set home to my location" else "Move home here") }

            SectionTitle("3 · Tracking")
            ToggleRow("Track my walks automatically", prefs.autoTrackingEnabled) { setAuto(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = prefs.trackingMode == "allday", onClick = { setAuto(prefs.autoTrackingEnabled, "allday") }, label = { Text("All-day (reliable)") })
                FilterChip(selected = prefs.trackingMode == "auto", onClick = { setAuto(prefs.autoTrackingEnabled, "auto") }, label = { Text("Background start (S1 test)") })
            }
            Text(
                "All-day keeps a quiet notification and only turns GPS on when you're moving. " +
                    "Background start tries to launch tracking when you start walking; check the log below to see if Android allows it.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("4 · Your goal")
            ToggleRow("Resource pointers on the map", prefs.resourcePointers) { repo.setResourcePointers(it); refresh++ }
            ToggleRow("Lock my weekly goal", prefs.goalLocked) { prefs.goalLocked = it; scope.launch { repo.rebuild() }; refresh++ }
            ToggleRow("Rest mode (pause decay)", prefs.resting) { on ->
                val today = repo.today()
                prefs.restPeriods = if (on) prefs.restPeriods + (today to -1)
                else prefs.restPeriods.map { if (it.second == -1) it.first to today else it }
                repo.log("settings", "Rest mode ${if (on) "on" else "off"}")
                scope.launch { repo.rebuild() }; refresh++
            }

            SectionTitle("5 · Run now")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        message = "Syncing Health Connect…"
                        try { repo.health.sync(prefs.startDay); message = "Health synced" } catch (e: Exception) { message = "Health sync failed: ${e.message}" }
                        repo.processPending(); repo.rebuild()
                    }
                }) { Text("Sync health") }
                OutlinedButton(onClick = {
                    scope.launch { message = "Processing…"; val n = repo.processPending(); repo.rebuild(); message = "Processed $n walk(s), world rebuilt" }
                }) { Text("Rebuild world") }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val d = withContext(Dispatchers.IO) { repo.dailyRun() }
                    message = if (d != null) "Yesterday's dispatch written" else "Nothing new to dispatch"
                }
            }) { Text("Run daily dispatch now") }
            Button(onClick = {
                scope.launch {
                    val extra = listOf(
                        "Permissions: location=${ctx.has(Manifest.permission.ACCESS_FINE_LOCATION)} background=${ctx.has(Manifest.permission.ACCESS_BACKGROUND_LOCATION)} " +
                            "activity=${ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)} notifications=${ctx.has(Manifest.permission.POST_NOTIFICATIONS)}",
                        "Health Connect: " + repo.health.permissionLabels.joinToString("; ") { (p, l) -> (if (p in healthGranted) "✓ " else "✗ ") + l },
                    )
                    shareDiagnostics(ctx, repo.diagnosticsReport(extra))
                }
            }) { Text("Share diagnostics") }

            SectionTitle("Credits & licenses")
            Text(
                "Map art (terrain, settlement, place and resource images) is adapted from 0 A.D. by Wildfire Games, " +
                    "licensed CC BY-SA 3.0. The adapted images are shared under the same license.",
                style = MaterialTheme.typography.bodySmall,
            )
            LinkRow("0 A.D. · play0ad.com", "https://play0ad.com", ctx)
            LinkRow("CC BY-SA 3.0 license", "https://creativecommons.org/licenses/by-sa/3.0/", ctx)
            Text("Roads, paths and place names © OpenStreetMap contributors (ODbL).", style = MaterialTheme.typography.bodySmall)
            LinkRow("openstreetmap.org/copyright", "https://www.openstreetmap.org/copyright", ctx)
            Text("Base map tiles: OpenFreeMap. Map rendering: MapLibre (BSD). Hex grid: H3 by Uber (Apache 2.0). " +
                "Geometry: JTS (EPL/EDL). Wanderer and waystone sprites, parchment fog: original to Wander World.",
                style = MaterialTheme.typography.bodySmall)

            SectionTitle("Recent captures")
        }
        item {
            var segs by remember { mutableStateOf(emptyList<SegmentEntity>()) }
            LaunchedEffect(refresh, logs.size) { segs = withContext(Dispatchers.IO) { repo.dao.recentSegments(8) } }
            Column {
                segs.forEach { s ->
                    Text(
                        "${timeFmt.format(Instant.ofEpochMilli(s.startMs))} · ${s.source} · ${s.modeHint ?: "?"} · " +
                            (if (s.endMs == null) "recording" else s.note ?: if (s.processed) "processed" else "waiting"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (segs.isEmpty()) Text("None yet", style = MaterialTheme.typography.bodySmall)
            }
            SectionTitle("Diagnostics log")
        }
        items(logs, key = { it.id }) { l ->
            Text(
                "${timeFmt.format(Instant.ofEpochMilli(l.timeMs))} [${l.tag}] ${l.message}",
                style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            )
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

private fun openHealthConnectSettings(ctx: Context) {
    // The system page for this app's Health Connect permissions (Android 14+), else Health Connect itself.
    val attempts = listOf(
        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME, ctx.packageName),
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null)),
        HealthConnectClient.getHealthConnectManageDataIntent(ctx),
    )
    for (i in attempts) {
        try { ctx.startActivity(i); return } catch (_: Exception) {}
    }
}

/** Writes the report to a file and opens the share sheet (Drive, email, messages…). */
private suspend fun shareDiagnostics(ctx: Context, report: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "diagnostics").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        File(dir, "wander-diagnostics-$stamp.txt").apply { writeText(report) }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share diagnostics"))
}

@Composable
private fun LinkRow(label: String, url: String, ctx: Context) {
    // Opens the browser only when tapped; nothing is fetched by the app itself.
    Text(
        label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 2.dp).clickable {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
        },
    )
}

@Composable
private fun PermRow(label: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text((if (granted) "✓ " else "○ ") + label, style = MaterialTheme.typography.bodyMedium)
        if (!granted) OutlinedButton(onClick = onGrant) { Text("Grant") }
    }
    HorizontalDivider()
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        // The label takes what's left, so the switch is never pushed off screen.
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/** Shown by Health Connect when you ask why the app wants your data. */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Wander World and your health data", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Wander World reads your activity intensity minutes to protect your world when you meet your weekly goal, " +
                            "and reads GPS routes from your workouts (for example, from your watch) to chart the roads you walked. " +
                            "All data stays on this phone. Nothing is uploaded.",
                    )
                }
            }
        }
    }
}
