package world.wander.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import world.wander.app.R

/** One row of the legend; a null image makes it a section header. Mirrors tools/gen_legend.py. */
private data class LegendItem(val image: Int?, val title: String, val description: String = "", val wide: Boolean = false)

private val LEGEND = listOf(
    LegendItem(null, "Ground"),
    LegendItem(R.drawable.legend_ground_fog, "Fog", "Unexplored. Walk to clear it.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_explored, "Explored ground", "Cleared by your walks; worn roads clear wider.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_woods, "Woods & parks", "Named green places you've discovered.", wide = true),
    LegendItem(null, "Roads"),
    LegendItem(R.drawable.oad_legend_road_1, "Trail", "Walked on 1 day.", wide = true),
    LegendItem(R.drawable.oad_legend_road_2, "Footpath", "3 days.", wide = true),
    LegendItem(R.drawable.oad_legend_road_3, "Lane", "10 days.", wide = true),
    LegendItem(R.drawable.oad_legend_road_4, "Cobbled road", "30 days.", wide = true),
    LegendItem(R.drawable.oad_legend_road_5, "Avenue", "100 days. Tree-lined.", wide = true),
    LegendItem(R.drawable.oad_legend_road_wild, "Your own path", "Off the map's roads; wears in the same way.", wide = true),
    LegendItem(R.drawable.legend_road_peek, "Unwalked street", "Glimpsed at the edge of the fog.", wide = true),
    LegendItem(null, "Settlements"),
    LegendItem(R.drawable.oad_medal_tier_1, "Camp", "3 visit-days in a month."),
    LegendItem(R.drawable.oad_medal_tier_2, "Hamlet", "10 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_3, "Village", "25 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_4, "Town", "60 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_5, "City", "150 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_6, "Metropolis", "Capital only, 400."),
    LegendItem(R.drawable.oad_medal_tier_0, "Ruins", "Faded from neglect; never lost."),
    LegendItem(R.drawable.badge_capital, "Capital", "Your home. Never falls below Village once it gets there."),
    LegendItem(null, "Markers"),
    LegendItem(R.drawable.oad_medal_waypoint, "Waypoint", "Any area you've walked in."),
    LegendItem(R.drawable.medal_waystone, "Waystone", "Where a drive set you down, away from known ground."),
    LegendItem(R.drawable.medal_waystone_great, "Great waystone", "Arrival in a new region; links regions."),
    LegendItem(R.drawable.oad_medal_place_park, "Park", "Discovered green space."),
    LegendItem(R.drawable.oad_medal_place_wood, "Woods", "Discovered woodland."),
    LegendItem(R.drawable.oad_medal_place_store, "Store", "A big store you've walked through."),
    LegendItem(R.drawable.oad_medal_place_generic, "Place", "Other named places."),
    LegendItem(R.drawable.sprite_wanderer, "You"),
)

@Composable
fun LegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Legend") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(LEGEND) { item ->
                    if (item.image == null) {
                        Text(
                            item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    } else {
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(96.dp), contentAlignment = Alignment.Center) {
                                Image(
                                    ImageBitmap.imageResource(item.image), contentDescription = null,
                                    filterQuality = FilterQuality.Medium,
                                    modifier = if (item.wide) Modifier.size(width = 90.dp, height = 24.dp) else Modifier.size(36.dp),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium)
                                if (item.description.isNotEmpty()) {
                                    Text(item.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
        },
    )
}
