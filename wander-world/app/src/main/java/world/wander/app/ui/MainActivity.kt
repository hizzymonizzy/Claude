package world.wander.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import world.wander.app.data.Repository
import world.wander.app.tracking.ActivityReceiver
import world.wander.app.tracking.TrackingService

enum class Tab(val label: String, val icon: String) {
    MAP("Map", "🗺"), DISPATCH("Dispatch", "📜"), ATLAS("Atlas", "🏰"), GOAL("Goal", "❤"), SETTINGS("Settings", "⚙")
}

private val Parchment = lightColorScheme(
    primary = Color(0xFF5B4636),
    onPrimary = Color(0xFFF4ECD8),
    secondary = Color(0xFF9C7A54),
    background = Color(0xFFF4ECD8),
    surface = Color(0xFFFAF5E9),
    surfaceVariant = Color(0xFFEDE3CC),
    primaryContainer = Color(0xFFE6D5B8),
)

class MainActivity : ComponentActivity() {
    private val repo by lazy { Repository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTab = if (intent.getStringExtra("tab") == "dispatch") Tab.DISPATCH else Tab.MAP
        setContent { MaterialTheme(colorScheme = Parchment) { WanderRoot(repo, startTab) } }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            // Show the world right away; walks waiting on map downloads get folded in afterwards.
            try { repo.rebuild() } catch (e: Exception) { repo.log("world", "Rebuild failed: $e") }
            if (repo.processPending() > 0) repo.rebuild()
        }
        resumeTracking()
    }

    /** Re-registers activity detection (it doesn't survive reboots) and restarts all-day mode. */
    private fun resumeTracking() {
        if (!repo.prefs.autoTrackingEnabled) return
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasActivity = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (!hasLocation || !hasActivity) return
        ActivityReceiver.register(this)
        if (repo.prefs.trackingMode == "allday" && !TrackingService.running) {
            TrackingService.start(this, TrackingService.ACTION_START_ALLDAY)
        }
    }
}

@Composable
fun WanderRoot(repo: Repository, startTab: Tab) {
    var tab by rememberSaveable { mutableStateOf(startTab) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            // Unfolded Fold / tablet: the map stays on the left, everything else on the right.
            var side by rememberSaveable { mutableStateOf(if (startTab == Tab.MAP) Tab.DISPATCH else startTab) }
            Row(Modifier.fillMaxSize()) {
                MapScreen(repo, onOpenDispatch = { side = Tab.DISPATCH }, modifier = Modifier.weight(1.3f))
                Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(top = 32.dp)) {
                        val tabs = Tab.entries.filter { it != Tab.MAP }
                        PrimaryTabRow(selectedTabIndex = tabs.indexOf(side)) {
                            tabs.forEach { t -> Tab(selected = side == t, onClick = { side = t }, text = { Text(t.label) }) }
                        }
                        TabContent(repo, side, onOpenDispatch = { side = Tab.DISPATCH })
                    }
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Text(t.icon) }, label = { Text(t.label) })
                        }
                    }
                },
            ) { pad ->
                Surface(Modifier.padding(pad).fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TabContent(repo, tab, onOpenDispatch = { tab = Tab.DISPATCH }, onShowOnMap = { tab = Tab.MAP })
                }
            }
        }
    }
}

@Composable
private fun TabContent(repo: Repository, tab: Tab, onOpenDispatch: () -> Unit, onShowOnMap: () -> Unit = {}) {
    when (tab) {
        Tab.MAP -> MapScreen(repo, onOpenDispatch)
        Tab.DISPATCH -> DispatchScreen(repo)
        Tab.ATLAS -> AtlasScreen(repo, onShowOnMap)
        Tab.GOAL -> GoalScreen(repo)
        Tab.SETTINGS -> SettingsScreen(repo)
    }
}

@Suppress("unused")
private val sidePanelWidth = Modifier.width(360.dp)
