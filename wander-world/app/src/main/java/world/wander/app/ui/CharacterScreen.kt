package world.wander.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import world.wander.app.R
import world.wander.app.data.Repository
import world.wander.engine.AchievementState
import world.wander.engine.WorldEngine
import world.wander.engine.WorldState

private val GOLD = Color(0xFF7E6028)
private val GREEN = Color(0xFF46783C)
private val TIER_COLOURS = listOf(Color(0xFFE2D6BE), Color(0xFFB07840), Color(0xFFC4C4CC), Color(0xFFE8BA4A))

/**
 * The Goal tab as a character sheet (0.13): your wanderer, title and level, this week's quest, the goal bands, stats,
 * week history and achievements. 150 active minutes holds your world; your goal warms it (ADR 0011).
 */
@Composable
fun CharacterScreen(repo: Repository) {
    val world by repo.world.collectAsState()
    val w = world ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Building your world…") }
    val scope = rememberCoroutineScope()
    val g = w.goal
    val km = w.achievements.firstOrNull { it.id == "long_road" }?.value ?: 0.0
    val level = levelFor(km)
    val outfit = repo.prefs.outfit?.takeIf { "outfit_$it" in w.owned }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sprite = outfit?.let { MapIcons.images["wanderer_$it"] } ?: R.drawable.sprite_wanderer
                Image(ImageBitmap.imageResource(sprite), null, filterQuality = FilterQuality.None, modifier = Modifier.size(96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("The Wanderer", style = MaterialTheme.typography.headlineSmall)
                    Text("${g.bestBand} · Level ${level.first}", style = MaterialTheme.typography.titleSmall, color = GOLD)
                    LinearProgressIndicator(progress = { level.second.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp))
                    Text("Levels come from distance walked · %.0f km".format(km), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { WeekCard(w) }
        item {
            Text("Goal band", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = !g.locked, onClick = {
                    repo.prefs.goalLocked = false; repo.prefs.lockedGoal = null; scope.launch { repo.rebuild() }
                }, label = { Text("Auto") })
                for ((name, mins) in listOf("Stroller" to 150, "Rambler" to 200, "Trekker" to 250, "Pathfinder" to 300)) {
                    FilterChip(selected = g.locked && g.goal == mins, onClick = {
                        repo.prefs.goalLocked = true; repo.prefs.lockedGoal = mins; scope.launch { repo.rebuild() }
                    }, label = { Text(name) })
                }
            }
            Text(
                if (g.locked) "Your goal is set to ${g.goal} (${g.band}). Auto lets it follow you."
                else "Now ${g.goal} (${g.band}). It grows after 3 strong weeks out of 4, never past what you've been doing, and eases after 2 light weeks.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item { Stats(w) }
        item { Weeks(w) }
        item { Text("Achievements", style = MaterialTheme.typography.titleMedium) }
        items(w.achievements) { a -> AchievementRow(a) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Level n needs 5·n² km in all (level 1 at 0, 2 at 20, 3 at 45 …): quick early levels, steady later. */
private fun levelFor(km: Double): Pair<Int, Double> {
    var n = 1
    while (5.0 * (n + 1) * (n + 1) - 5 <= km) n++
    val from = 5.0 * n * n - 5; val to = 5.0 * (n + 1) * (n + 1) - 5
    return n to ((km - from) / (to - from)).coerceIn(0.0, 1.0)
}

@Composable
private fun WeekCard(w: WorldState) {
    val g = w.goal
    Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, Color(0xFF4A3622)), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            Text("This week", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.0f".format(g.weekMinutes), style = MaterialTheme.typography.displaySmall)
                Text(" / ${g.goal} active minutes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            LinearProgressIndicator(progress = { (g.weekMinutes / g.goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(12.dp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                "MTWTFSS".forEachIndexed { i, c ->
                    val day = g.weekStartDay + i
                    val walked = (w.days[day]?.activeMinutes ?: 0.0) >= 1
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(18.dp).background(if (walked) GOLD else Color(0xFFE2D6BE), CircleShape))
                        Text(c.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(weekLine(w).substringAfter(" · "), style = MaterialTheme.typography.bodyMedium, color = GREEN)
        }
    }
}

@Composable
private fun Stats(w: WorldState) {
    fun v(id: String) = w.achievements.firstOrNull { it.id == id }?.value ?: 0.0
    val endurance = w.goal.weeks.takeLast(4).map { it.minutes }.ifEmpty { listOf(w.goal.trailing7Minutes) }.average()
    val stats = listOf(
        Triple(R.drawable.oad_res_food, "Endurance", "%.0f min a week".format(endurance)),
        Triple(R.drawable.oad_res_stone, "Cartographer", "%.0f roads".format(v("cartographer"))),
        Triple(R.drawable.oad_res_wood, "Explorer", "%,.0f hexes".format(v("explorer"))),
        Triple(R.drawable.oad_res_metal, "Discoverer", "%.0f places".format(v("discoverer"))),
        Triple(R.drawable.oad_medal_tier_1, "Settler", "%.0f settlements".format(v("founder"))),
        Triple(R.drawable.badge_capital, "Steadfast", "%.0f weeks in a row".format(v("steadfast"))),
    )
    Text("Stats", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.chunked(2).forEach { row ->
            Row {
                row.forEach { (icon, name, value) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageBitmap.imageResource(icon), null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Weeks(w: WorldState) {
    val weeks = w.goal.weeks.takeLast(8)
    Text("Weeks", style = MaterialTheme.typography.titleMedium)
    if (weeks.isEmpty()) {
        Text("Your first week is scored next Tuesday.", style = MaterialTheme.typography.bodySmall)
        return
    }
    val max = (weeks.maxOf { maxOf(it.minutes, it.goal.toDouble()) } * 1.1).coerceAtLeast(160.0)
    Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        weeks.forEach { wk ->
            val colour = when {
                wk.minutes >= wk.goal -> GREEN
                wk.minutes >= WorldEngine.GUIDELINE_MINUTES -> GOLD
                else -> Color(0xFFB8A27E)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().fillMaxHeight((wk.minutes / max).toFloat().coerceIn(0.03f, 0.85f)).background(colour))
                Text(dayLabel(wk.weekStartDay).substringBefore(','), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text("Green: warmed your world · gold: held it · pale: a quiet week", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun AchievementRow(a: AchievementState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (k in 1..3) Box(Modifier.size(18.dp).background(if (a.tier >= k) TIER_COLOURS[k] else TIER_COLOURS[0], CircleShape))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall)
            val next = a.targets.getOrNull(a.tier)
            Text(
                if (next != null) "%,.0f / %,.0f %s".format(a.value, next, a.unit) else "%,.0f %s · complete".format(a.value, a.unit),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
            )
            if (next != null) LinearProgressIndicator(progress = { (a.value / next).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp))
            a.reward?.let { r ->
                Text("Reward at ${listOf("", "bronze", "silver", "gold")[a.rewardAt]}: ${world.wander.engine.Story.LOOK_NAMES[r] ?: r}",
                    style = MaterialTheme.typography.labelSmall, color = GOLD)
            }
        }
    }
}
