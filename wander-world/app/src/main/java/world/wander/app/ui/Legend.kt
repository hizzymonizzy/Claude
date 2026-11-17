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

/** One row of the legend; a null image makes it a section header. Generated from tools/gen_legend.py's LEGEND list. */
private data class LegendItem(val image: Int?, val title: String, val description: String = "", val wide: Boolean = false)

private val LEGEND = listOf(
    LegendItem(null, "Ground"),
    LegendItem(R.drawable.legend_ground_fog, "Fog", "Unexplored. Walk to clear it.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_explored, "Explored ground", "Cleared by your walks; worn roads clear wider.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_woods, "Woods & parks", "Named green places you've discovered.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_sand, "Sand & beaches", "Beaches and dunes you've discovered.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_water, "Water", "Clear and blue where you've been; faded under the fog.", wide = true),
    LegendItem(R.drawable.oad_legend_ground_shore, "Shore", "Wet sand along the coast. Walk it for food, like roads for stone.", wide = true),
    LegendItem(null, "Roads"),
    LegendItem(R.drawable.oad_legend_road_1, "Trail", "Walked on 1 day.", wide = true),
    LegendItem(R.drawable.oad_legend_road_2, "Footpath", "3 days.", wide = true),
    LegendItem(R.drawable.oad_legend_road_3, "Lane", "10 days.", wide = true),
    LegendItem(R.drawable.oad_legend_road_4, "Cobbled road", "30 days.", wide = true),
    LegendItem(R.drawable.oad_legend_road_5, "Avenue", "100 days. Tree-lined.", wide = true),
    LegendItem(R.drawable.legend_road_peek, "Unwalked street", "Inside explored ground every street shows its status; dashed = not walked yet.", wide = true),
    LegendItem(null, "Your own paths"),
    LegendItem(R.drawable.legend_wild_1, "Faint track", "Off the map's roads, 1 day.", wide = true),
    LegendItem(R.drawable.legend_wild_2, "Trodden path", "3 days.", wide = true),
    LegendItem(R.drawable.oad_legend_wild_3, "Dirt track", "10 days.", wide = true),
    LegendItem(R.drawable.oad_legend_wild_4, "Gravel trail", "30 days.", wide = true),
    LegendItem(R.drawable.oad_legend_wild_5, "Ranger trail", "100 days. Cairns mark the way.", wide = true),
    LegendItem(null, "Water routes"),
    LegendItem(R.drawable.legend_water_1, "Wake", "Paddled on 1 day.", wide = true),
    LegendItem(R.drawable.legend_water_2, "Paddle line", "3 days.", wide = true),
    LegendItem(R.drawable.legend_water_3, "Paddle route", "10 days.", wide = true),
    LegendItem(R.drawable.legend_water_4, "Marked route", "30 days. Buoys.", wide = true),
    LegendItem(R.drawable.legend_water_5, "Waterway", "100 days.", wide = true),
    LegendItem(null, "Settlements"),
    LegendItem(R.drawable.oad_medal_tier_1, "Camp", "3 visit-days in a month."),
    LegendItem(R.drawable.oad_medal_tier_2, "Hamlet", "10 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_3, "Village", "25 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_4, "Town", "60 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_5, "City", "150 visit-days."),
    LegendItem(R.drawable.oad_medal_tier_6, "Metropolis", "Capital only, 400."),
    LegendItem(R.drawable.oad_medal_tier_0, "Ruins", "Faded from neglect; never lost."),
    LegendItem(R.drawable.oad_legend_capital, "Capital", "Your home: star badge; an obelisk once you raise a monument."),
    LegendItem(null, "Markers"),
    LegendItem(R.drawable.oad_medal_waypoint, "Waypoint", "Any area you've walked in."),
    LegendItem(R.drawable.medal_waystone, "Waystone", "Where you set off on foot after a drive, away from home and known ground."),
    LegendItem(R.drawable.medal_waystone_great, "Great waystone", "Arrival in a new region; links regions."),
    LegendItem(null, "Places"),
    LegendItem(R.drawable.oad_medal_place_park, "Park", "Green space."),
    LegendItem(R.drawable.oad_medal_place_wood, "Woods", "Woodland, reserves, wetland."),
    LegendItem(R.drawable.oad_medal_place_beach, "Beach", "Sand by the water."),
    LegendItem(R.drawable.oad_medal_place_water, "Lake or river", "Named water."),
    LegendItem(R.drawable.medal_poi_hospital, "Hospital", "Hospitals and clinics."),
    LegendItem(R.drawable.oad_medal_poi_government, "Government", "Town hall, courthouse, offices."),
    LegendItem(R.drawable.oad_medal_poi_emergency, "Police & fire", "Stations."),
    LegendItem(R.drawable.oad_medal_poi_civic, "Civic", "Post office, community centre, market."),
    LegendItem(R.drawable.oad_medal_poi_school, "School", "Schools, colleges, universities."),
    LegendItem(R.drawable.oad_medal_poi_library, "Library", ""),
    LegendItem(R.drawable.oad_medal_poi_worship, "Place of worship", ""),
    LegendItem(R.drawable.oad_medal_poi_culture, "Culture", "Museums, theatres, cinemas."),
    LegendItem(R.drawable.oad_medal_poi_sports, "Sports", "Stadiums and sports centres."),
    LegendItem(R.drawable.oad_medal_poi_transport, "Station", "Train, bus and ferry."),
    LegendItem(R.drawable.oad_medal_poi_harbour, "Harbour", "Marinas."),
    LegendItem(R.drawable.oad_medal_poi_lighthouse, "Lighthouse", ""),
    LegendItem(R.drawable.oad_medal_poi_historic, "Historic", "Monuments, memorials, ruins."),
    LegendItem(R.drawable.oad_medal_place_store, "Store", "A big store you've walked through."),
    LegendItem(R.drawable.oad_medal_place_generic, "Place", "Other named places."),
    LegendItem(null, "Economy"),
    LegendItem(R.drawable.oad_medal_gold, "Gold", "Your weekly-goal effort and distance. One purse, usable everywhere."),
    LegendItem(R.drawable.oad_res_wood, "Wood", "30 s in or along (15 m) a wood or park. New ones pay double; favourites thin, rest restores."),
    LegendItem(R.drawable.oad_res_food, "Food", "Walk shores and field or garden edges (2 per km), forage in woods, paddle, visit markets and harbours."),
    LegendItem(R.drawable.oad_res_stone, "Stone", "Kilometres walked on roads; points to streets you haven't walked."),
    LegendItem(R.drawable.oad_res_metal, "Metal", "Discovering and revisiting places; points to undiscovered ones."),
    LegendItem(R.drawable.oad_medal_monument, "Monument", "A milestone, raised at your capital."),
    LegendItem(R.drawable.oad_legend_pointer, "Best source", "The arrow points to the best node you haven't gathered today: fresh beats thinned."),
    LegendItem(R.drawable.legend_gathering, "Gathering", "Nodes stay put; tap one for details. Green ring: you're in its zone. 30 s gathers; a tick = gathered today. Resources bank in the area you gather them; roads you've walked join banks."),
    LegendItem(null, "You"),
    LegendItem(R.drawable.sprite_wanderer, "Walking", ""),
    LegendItem(R.drawable.sprite_wanderer_paddle, "Paddling", ""),
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
