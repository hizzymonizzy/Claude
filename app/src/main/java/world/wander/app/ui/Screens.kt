package world.wander.app.ui

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
import world.wander.engine.SettlementState
import world.wander.engine.Tier
import world.wander.engine.WorldParams
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

@Composable
fun AtlasScreen(repo: Repository) {
    val world by repo.world.collectAsState()
    val w = world
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Atlas")
            if (w != null) {
                val km = w.days.values.sumOf { it.footMeters } / 1000
                Text(
                    "${w.discoveredCount} roads charted · %.1f km walked · ${w.settlements.size} settlements".format(km),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        val list = w?.settlements.orEmpty().sortedWith(compareByDescending<SettlementState> { it.isCapital }.thenByDescending { it.tier.ordinal })
        if (list.isEmpty()) item {
            Text(
                "No settlements yet. Walk somewhere on three different days within a month and a camp will appear.",
                style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
            )
        }
        items(list, key = { it.areaId }) { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text((if (s.isCapital) "★ " else "") + s.name, style = MaterialTheme.typography.titleMedium)
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
        item { Spacer(Modifier.height(24.dp)) }
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
