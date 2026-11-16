package world.wander.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import world.wander.app.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import world.wander.app.data.Repository
import world.wander.app.geo.Overpass
import world.wander.engine.LatLng
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import world.wander.engine.SettlementState
import world.wander.engine.RegionState
import world.wander.engine.Tier
import world.wander.engine.WorldParams
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TIER_SPRITES = listOf(
    R.drawable.oad_medal_tier_0, R.drawable.oad_medal_tier_1, R.drawable.oad_medal_tier_2, R.drawable.oad_medal_tier_3,
    R.drawable.oad_medal_tier_4, R.drawable.oad_medal_tier_5, R.drawable.oad_medal_tier_6,
)

private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
fun dayLabel(day: Int): String = LocalDate.ofEpochDay(day.toLong()).format(dateFmt)

@Composable
fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))

// ---- Dispatch ----

@Composable
fun DispatchScreen(repo: Repository) {
    val world by repo.world.collectAsState()
    val archive by repo.dao.dispatches().collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        world?.let { w ->
            item {
                val d = repo.dispatchFor(w, w.today)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Today so far", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        d.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
        if (archive.isEmpty()) item {
            Text(
                "Your first dispatch arrives tomorrow morning.",
                style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
            )
        }
        items(archive, key = { it.day }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.title, style = MaterialTheme.typography.titleMedium)
                    Text(dayLabel(d.day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    d.body.lines().forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---- Atlas ----

/** Something in your world you can search for and fly to. */
private data class AtlasHit(val name: String, val kind: String, val where: LatLng)

@Composable
fun AtlasScreen(repo: Repository, onShowOnMap: () -> Unit = {}) {
    val world by repo.world.collectAsState()
    val layers by repo.layers.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val w = world
    fun go(p: LatLng) { repo.focus.value = p; onShowOnMap() }

    // Everything searchable: settlements, waypoints, places and charted streets.
    val index = remember(w, layers) {
        val hits = mutableListOf<AtlasHit>()
        w?.settlements?.forEach { hits += AtlasHit(it.name, it.tier.label, it.position) }
        w?.regions?.forEach { r -> hits += AtlasHit(r.name, if (r.isHome) "Home region" else "Region", r.center) }
        w?.exploredAreas?.filter { !it.settled }?.forEach { hits += AtlasHit(it.name, "Waypoint", it.center) }
        layers.landmarks.forEach { (name, rings, _) ->
            val r = rings.maxBy { it.size }
            hits += AtlasHit(name, "Place", LatLng(r.sumOf { it.lat } / r.size, r.sumOf { it.lng } / r.size))
        }
        layers.discovered.filter { !it.first.name.isNullOrBlank() }.groupBy { it.first.name!! }.forEach { (name, edges) ->
            val pts = Overpass.decode(edges.maxBy { it.first.lengthM }.first.geometry)
            hits += AtlasHit(name, "Street", pts[pts.size / 2])
        }
        hits
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Atlas")
            if (w != null) {
                val km = w.days.values.sumOf { it.footMeters } / 1000
                Text(
                    "${w.discoveredCount} roads charted · ${w.landmarks.size} places · ${w.exploredAreas.size} areas · %.1f km walked".format(km),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search towns, places, streets") }, modifier = Modifier.fillMaxWidth(),
            )
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val results = index.filter { q in it.name.lowercase() }.distinctBy { it.name to it.kind }.take(50)
            if (results.isEmpty()) item { Text("Nothing found yet. Maybe it's still in the fog.", fontStyle = FontStyle.Italic) }
            items(results) { r -> HitRow(r.name, r.kind) { go(r.where) } }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        val regionOrder = w?.regions.orEmpty().sortedWith(compareByDescending<RegionState> { it.isHome }.thenBy { it.dormant }.thenByDescending { it.lastPresenceDay })
        val list = w?.settlements.orEmpty().sortedWith(
            compareBy<SettlementState> { s -> regionOrder.indexOfFirst { it.id == s.regionId }.let { if (it < 0) 99 else it } }
                .thenByDescending { it.isCapital }.thenByDescending { it.tier.ordinal },
        )
        item { SectionTitle("Settlements") }
        if (list.isEmpty()) item {
            Text(
                "No settlements yet. Walk somewhere on three different days within a month and a camp will appear.",
                style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
            )
        }
        items(list, key = { it.areaId }) { s ->
            val first = list.first { it.regionId == s.regionId } === s
            if (first && (w?.regions?.size ?: 0) > 1) {
                val r = w!!.regions.first { it.id == s.regionId }
                Text(
                    (if (r.isHome) "Home · " else "") + r.name + if (r.dormant) " · dormant (resting while you're away)" else "",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            Card(onClick = { go(s.position) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            ImageBitmap.imageResource(TIER_SPRITES[s.tier.ordinal]), contentDescription = s.tier.label,
                            filterQuality = FilterQuality.Medium, modifier = Modifier.size(52.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text((if (s.isCapital) "★ " else "") + s.name, style = MaterialTheme.typography.titleMedium)
                    }
                    val peak = if (s.peakTier != s.tier) " (once a ${s.peakTier.label})" else ""
                    Text(s.tier.label + peak, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    val next = Tier.entries.getOrNull(s.tier.ordinal + 1)
                    if (next != null && !(next == Tier.METROPOLIS && !s.isCapital)) {
                        val need = WorldParams().tierThresholds[next.ordinal]
                        LinearProgressIndicator(progress = { (s.development / need).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("%.0f / %.0f toward %s".format(s.development, need, next.label), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("Founded ${dayLabel(s.foundedDay)} · ${s.areaDays} visit-days · ${s.exploredCells} blocks explored", style = MaterialTheme.typography.bodySmall)
                    Text("Liveliness %.0f%% · last visit ${dayLabel(s.lastAreaDay)}".format(s.liveliness * 100), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val waypoints = w?.exploredAreas.orEmpty().filter { !it.settled }.sortedByDescending { it.lastVisitDay }
        if (waypoints.isNotEmpty()) {
            item { SectionTitle("Waypoints") }
            items(waypoints, key = { "wp-" + it.id }) { a ->
                HitRow(a.name, "${a.cells} block${if (a.cells == 1) "" else "s"} · last ${dayLabel(a.lastVisitDay)}", R.drawable.oad_medal_waypoint) { go(a.center) }
            }
        }

        val stones = w?.waystones.orEmpty().sortedByDescending { it.foundDay }
        if (stones.isNotEmpty()) {
            item { SectionTitle("Waystones") }
            items(stones, key = { "ws-" + it.id }) { st ->
                val kind = if (st.great) "Great waystone" else "Waystone"
                HitRow(
                    "$kind · ${w!!.regionName(st.regionId)}",
                    "Found ${dayLabel(st.foundDay)} · ${st.trips} arrival${if (st.trips == 1) "" else "s"}", if (st.great) R.drawable.medal_waystone_great else R.drawable.medal_waystone,
                ) { go(st.center) }
            }
            val links = w!!.portals
            if (links.isNotEmpty()) item {
                links.forEach { l ->
                    Text("Portal: ${w.regionName(l.regionA)} ↔ ${w.regionName(l.regionB)} · ${l.trips} trip${if (l.trips == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        val places = w?.landmarks.orEmpty().sortedByDescending { it.visitDays }
        if (places.isNotEmpty()) {
            item { SectionTitle("Places discovered") }
            items(places, key = { "lm-" + it.id }) { l ->
                val hit = index.firstOrNull { it.kind == "Place" && it.name == l.name }
                HitRow(
                    l.name, "${l.kind.replace('_', ' ')} · ${l.visitDays} visit-day${if (l.visitDays == 1) "" else "s"} · since ${dayLabel(l.discoveredDay)}",
                    when (MapLayersBuilder.placeIcon(l.kind)) {
                        "medal_place_store" -> R.drawable.oad_medal_place_store
                        "medal_place_wood" -> R.drawable.oad_medal_place_wood
                        "medal_place_park" -> R.drawable.oad_medal_place_park
                        else -> R.drawable.oad_medal_place_generic
                    },
                ) { hit?.let { go(it.where) } }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HitRow(title: String, subtitle: String, sprite: Int? = null, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (sprite != null) {
                Image(ImageBitmap.imageResource(sprite), contentDescription = null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text("Show ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---- Goal ----

@Composable
fun GoalScreen(repo: Repository) {
    val world by repo.world.collectAsState()
    val w = world ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Building your world…") }
    val g = w.goal
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle("This week")
            Text("%.0f / %d min".format(g.trailing7Minutes, g.goal), style = MaterialTheme.typography.displaySmall)
            Text(g.protection.label + if (g.locked) " · goal locked" else "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (g.ratio / 1.5).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(10.dp))
            Text("Rolling 7 days · vigorous minutes count double · 100% stops decay, up to 150% restores", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(16.dp))
            SectionTitle("Last 14 days")
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                val days = (w.today - 13..w.today).map { w.days[it]?.intensityMinutes ?: 0.0 }
                val max = (days.maxOrNull() ?: 0.0).coerceAtLeast(30.0)
                days.forEach { v ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight((v / max).toFloat().coerceIn(0.02f, 1f))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Weeks")
        }
        items(g.weeks.reversed().take(12)) { wk ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Week of ${dayLabel(wk.weekStartDay)}")
                Text("%.0f / %d".format(wk.minutes, wk.goal))
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
