package world.wander.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import world.wander.engine.Geo
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
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
        item { NoticeLine(repo) }
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
    var sortBy by rememberSaveable { mutableStateOf("Nearest") }
    var building by remember { mutableStateOf<Pair<String, String>?>(null) }
    val w = world
    building?.let { (area, id) -> if (w != null) BuildingDialog(w, area, id) { building = null } }
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
                placeholder = { Text("Search settlements, places, streets") }, modifier = Modifier.fillMaxWidth(),
            )
            if (w != null) {
                Spacer(Modifier.height(8.dp))
                TreasuryRow(w.treasury)
            }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            val results = index.filter { q in it.name.lowercase() }.distinctBy { it.name to it.kind }.take(50)
            if (results.isEmpty()) item { Text("Nothing found yet. Maybe it's still in the fog.", fontStyle = FontStyle.Italic) }
            items(results) { r -> HitRow(r.name, r.kind) { go(r.where) } }
            item { Spacer(Modifier.height(24.dp)) }
            return@LazyColumn
        }

        // 0.13: settlements only, for wayfinding: distance and direction from you, Go, and areas you could camp in.
        val me = repo.lastLocation.value ?: repo.prefs.homeLat?.let { la -> repo.prefs.homeLng?.let { LatLng(la, it) } }
        fun dist(p: LatLng) = me?.let { Geo.distanceM(it, p) } ?: 0.0
        fun where(p: LatLng): String = me?.let { m ->
            val d = Geo.distanceM(m, p)
            val bearing = (Math.toDegrees(Math.atan2(p.lng - m.lng, p.lat - m.lat)) + 360) % 360
            val arrow = listOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")[((bearing + 22.5) / 45).toInt() % 8]
            "$arrow " + if (d < 1000) "%.0f m".format(d) else "%.1f km".format(d / 1000)
        } ?: ""
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (o in listOf("Nearest", "Recent", "Needs a visit")) {
                    androidx.compose.material3.FilterChip(selected = sortBy == o, onClick = { sortBy = o }, label = { Text(o) })
                }
            }
        }
        // 0.14 (mockup_atlas.png): the capital on its own card with its buildings, then the current request.
        val capital = w?.settlements?.firstOrNull { it.isCapital }
        if (capital != null) item { CapitalCard(capital, where(capital.position), onBuilding = { building = capital.areaId to it }) { go(capital.position) } }
        item { NoticeLine(repo) }
        if (w?.proposal != null) item { ProposalCard(repo, w, onShowOnMap) }
        val list = w?.settlements.orEmpty().filter { !it.isCapital }.let { all ->
            when (sortBy) {
                "Recent" -> all.sortedByDescending { it.lastAreaDay }
                "Needs a visit" -> all.sortedBy { it.lastAreaDay }
                else -> all.sortedBy { dist(it.position) }
            }
        }
        if (list.isNotEmpty() || capital == null) item { SectionTitle("Settlements") }
        if (list.isEmpty() && capital == null) item {
            Text("No settlements yet. Spend a night somewhere new and a camp will appear.", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }
        items(list, key = { it.areaId }) { s ->
            Card(onClick = { go(s.position) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HexArt(s.tier.ordinal, false, 56)
                    Spacer(Modifier.width(10.dp))
                    // The text takes what's left; the distance column has a fixed width, so nothing overlaps (0.14).
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TierProgress(s)
                        // Buildings as small hexes; tap one to see what it does (0.14.1).
                        if (s.projects.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            for (id in Projects.shown(s.projects)) MapIcons.images["bldg_hex_$id"]?.let { res ->
                                Image(ImageBitmap.imageResource(res), Projects.byId(id)?.name, filterQuality = FilterQuality.Medium,
                                    modifier = Modifier.size(30.dp).clickable { building = s.areaId to id })
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.End) {
                        Text(where(s.position), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(dayLabel(s.lastAreaDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * What a building does (0.14.1): its picture, name, perk, the building it grew from and what it can become, and when
 * it was built. Opened by tapping a building in the Atlas or on the map.
 */
@Composable
fun BuildingDialog(w: WorldState, areaId: String, projectId: String, onClose: () -> Unit) {
    val def = Projects.byId(projectId) ?: return onClose()
    val builtDay = w.events.lastOrNull { it is world.wander.engine.WorldEvent.ProjectBuilt && it.areaId == areaId && it.projectId == projectId }?.day
    val from = def.upgrades?.let { Projects.byId(it) }
    val next = Projects.ALL.firstOrNull { it.upgrades == projectId }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Close") } },
        icon = { MapIcons.images["bldg_hex_$projectId"]?.let { Image(ImageBitmap.imageResource(it), null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(64.dp)) } },
        title = { Text(def.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(def.perk.ifBlank { "No special effect" }, style = MaterialTheme.typography.titleSmall, color = Color(0xFF3F7D3A))
                Text(def.description, style = MaterialTheme.typography.bodyMedium)
                from?.let { Text("Grew from the ${it.name.lowercase()}" + (it.perk.takeIf { p -> p.isNotBlank() }?.let { p -> " (was: ${p.replaceFirstChar { c -> c.lowercase() }})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                next?.let { Text("Can become a ${it.name.lowercase()} at ${it.minTier.label}: ${it.perk.replaceFirstChar { c -> c.lowercase() }}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                Text((w.areaNames[areaId] ?: "") + (builtDay?.let { " · built ${dayLabel(it)}" } ?: ""), style = MaterialTheme.typography.labelSmall)
            }
        },
    )
}

/** What just got built or declined, until tapped away (0.14.1). */
@Composable
private fun NoticeLine(repo: Repository) {
    val notice by repo.notice.collectAsState()
    val n = notice ?: return
    Card(onClick = { repo.notice.value = null }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(n, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3F7D3A), modifier = Modifier.weight(1f))
            Text("✕", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** A settlement's hex picture (the map's own, tools/gen_hex_icons.py). */
@Composable
private fun HexArt(tier: Int, capital: Boolean, sizeDp: Int) {
    val res = MapIcons.images["hex_tier_$tier" + if (capital) "_cap" else ""] ?: TIER_SPRITES[tier]
    Image(ImageBitmap.imageResource(res), contentDescription = null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(sizeDp.dp))
}

/** "Hamlet · 9 / 25 to Village" and a bar. */
@Composable
private fun TierProgress(s: SettlementState) {
    val next = Tier.entries.getOrNull(s.tier.ordinal + 1)?.takeIf { it != Tier.METROPOLIS || s.isCapital }
    Text((if (s.isCapital) "Capital · " else "") + s.tier.label + (next?.let { " · %.0f / %.0f to %s".format(s.development, WorldParams().tierThresholds[it.ordinal], it.label) } ?: ""),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (next != null) LinearProgressIndicator(progress = { (s.development / WorldParams().tierThresholds[next.ordinal]).toFloat().coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}

/** The capital's card (0.14, mockup_atlas.png): its hex, progress to the next tier, and its buildings as hex badges. */
@Composable
private fun CapitalCard(s: SettlementState, where: String, onBuilding: (String) -> Unit, onGo: () -> Unit) {
    Card(onClick = onGo, modifier = Modifier.fillMaxWidth(), border = BorderStroke(2.dp, Color(0xFF4A3622))) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HexArt(s.tier.ordinal, true, 72)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TierProgress(s)
                    Text(listOf(where, dayLabel(s.lastAreaDay)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            val built = Projects.shown(s.projects)
            if (built.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    for (id in built) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clickable { onBuilding(id) }) {
                        MapIcons.images["bldg_hex_$id"]?.let { Image(ImageBitmap.imageResource(it), null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(44.dp)) }
                        Text(Projects.byId(id)?.name ?: id, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
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
    var msg by remember(p) { mutableStateOf<String?>(null) }
    var busy by remember(p) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Proposal from ${w.areaNames[p.areaId] ?: "a settlement"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(def.name, style = MaterialTheme.typography.titleMedium)
            // 0.14: what it does for you (its perk), not an abstract prosperity number.
            Text(def.perk.ifBlank { def.description }, style = MaterialTheme.typography.bodySmall, color = Color(0xFF3F7D3A))
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
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = canAfford && !busy, onClick = { busy = true; scope.launch { msg = repo.decide(p, true).message; busy = false } }) { Text("Approve") }
                OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { msg = repo.decide(p, false).message; busy = false } }) { Text("Decline") }
            }
            // 0.14.1: never silent. If it couldn't be built, say why (a success closes the card; NoticeLine says so).
            msg?.let { Text(it + ".", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9A3B2A), modifier = Modifier.padding(top = 6.dp)) }
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
