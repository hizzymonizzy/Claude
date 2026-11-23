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
import world.wander.engine.Cost
import world.wander.engine.Projects
import world.wander.engine.Treasury
import world.wander.engine.WorldState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
fun DispatchScreen(repo: Repository, onShowOnMap: () -> Unit = {}) {
    val world by repo.world.collectAsState()
    val archive by repo.dao.dispatches().collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(12.dp)) }
        world?.let { w -> if (w.proposal != null) item { ProposalCard(repo, w, onShowOnMap) } }
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
            if (w != null) {
                Spacer(Modifier.height(8.dp))
                TreasuryRow(w.treasury)
                Spacer(Modifier.height(8.dp))
                ProposalCard(repo, w, onShowOnMap)
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
                    if (s.projects.isNotEmpty()) {
                        Text(
                            "Built: " + s.projects.mapNotNull { Projects.byId(it)?.name }.joinToString() + " · Prosperity ${s.prosperity}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        val monuments = w?.monuments.orEmpty()
        if (monuments.isNotEmpty()) {
            item { SectionTitle("Monuments at your capital") }
            items(monuments, key = { "mo-" + it.id }) { m ->
                val capital = w?.settlements?.firstOrNull { it.isCapital }
                HitRow(m.name, "${m.description} · ${dayLabel(m.day)}", R.drawable.oad_medal_monument) { capital?.let { go(it.position) } }
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
                    l.name, "${l.kind.replace('_', ' ')} · ${l.visitDays} visit-day${if (l.visitDays == 1) "" else "s"} · since ${dayLabel(l.discoveredDay)}" +
                        (w?.abundance?.get(l.id)?.let { " · " + abundanceLabel(it) } ?: ""),
                    MapIcons.drawable(MapIcons.placeIcon(l.kind)),
                ) { hit?.let { go(it.where) } }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Gold, wood, food, stone and metal, with their 0 A.D. icons. */
@Composable
fun TreasuryRow(t: Treasury) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            R.drawable.oad_medal_gold to t.gold, R.drawable.oad_res_wood to t.wood, R.drawable.oad_res_food to t.food,
            R.drawable.oad_res_stone to t.stone, R.drawable.oad_res_metal to t.metal,
        ).filter { (icon, v) -> !(icon == R.drawable.oad_medal_gold && v == 0.0) }.forEach { (icon, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageBitmap.imageResource(icon), contentDescription = null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(3.dp))
                Text(v.toInt().toString(), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The current project proposal, with approve / decline. */
@Composable
fun ProposalCard(repo: Repository, w: WorldState, onShowOnMap: () -> Unit = {}) {
    val p = w.proposal ?: return
    val def = Projects.byId(p.projectId) ?: return
    val scope = rememberCoroutineScope()
    val stock = w.treasury
    val canAfford = def.cost.affordable(stock)
    val nodes by repo.resourceNodes.collectAsState()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Proposal from ${w.areaNames[p.areaId] ?: "a settlement"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(def.name, style = MaterialTheme.typography.titleMedium)
            Text(def.description + " · +${def.prosperity} prosperity", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("Costs ${def.cost}", style = MaterialTheme.typography.bodyMedium)
            if (!canAfford) {
                val c = def.cost
                val missing = listOf(
                    "gold" to c.gold - stock.gold, "wood" to c.wood - stock.wood, "food" to c.food - stock.food,
                    "stone" to c.stone - stock.stone, "metal" to c.metal - stock.metal,
                ).filter { it.second > 0.0 }
                Spacer(Modifier.height(4.dp))
                for ((res, short) in missing) {
                    val node = nodes.filter { it.resource == res && it.note != "gathered" }.minByOrNull { it.distanceM }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Need %d more %s".format(kotlin.math.ceil(short).toInt(), res) +
                                when {
                                    res == "gold" -> ": from your weekly-goal effort and distance."
                                    node != null -> ": nearest ${node.name}, ${formatDistance(node.distanceM)}."
                                    else -> "."
                                },
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f),
                        )
                        if (node != null && res != "gold") {
                            androidx.compose.material3.TextButton(onClick = { repo.focus.value = node.point; onShowOnMap() }) { Text("Go") }
                        }
                    }
                }
                Text("Or buy what's missing from the travelling trader on the map.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = canAfford, onClick = { scope.launch { repo.decide(p, true) } }) { Text("Approve") }
                OutlinedButton(onClick = { scope.launch { repo.decide(p, false) } }) { Text("Decline") }
            }
        }
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

/** How a harvested place is doing: thinned by frequent harvests, or grown back by rest. */
fun abundanceLabel(a: Double): String = when {
    a >= 0.95 -> "flourishing"
    a >= 0.7 -> "recovering"
    a >= 0.5 -> "worn"
    else -> "thinned · let it rest"
}
