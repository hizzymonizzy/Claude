package world.wander.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
            PermRow(
                if (healthAvailable) "Health Connect (${healthGranted.size}/${repo.health.permissions.size})" else "Health Connect (not available)",
                healthGranted.containsAll(repo.health.permissions),
            ) { if (healthAvailable) healthLauncher.launch(repo.health.permissions) }

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
                        repo.setHome(loc.latitude, loc.longitude)
                        message = "Home set."
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
                        try { repo.health.sync(); message = "Health synced" } catch (e: Exception) { message = "Health sync failed: ${e.message}" }
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
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
