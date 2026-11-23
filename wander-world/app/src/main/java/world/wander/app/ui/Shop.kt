package world.wander.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import world.wander.app.data.Repository
import world.wander.engine.EncounterState
import world.wander.engine.Projects
import world.wander.engine.Shop
import world.wander.engine.Story
import world.wander.engine.WorldState
import java.time.LocalDate
import java.time.LocalTime

private val FRAME = Color(0xFF4A3622)
private val FRAME_GOLD = Color(0xFFC49E56)
private val SOFT = Color(0xFF78624C)
private val RED = Color(0xFFAA3428)
private val GREEN = Color(0xFF3C6E3C)

/** One-line flavour for each item: MMORPG-tooltip tone (0.13). */
private val FLAVOUR = mapOf(
    "wood" to "Seasoned oak. Splinters sold separately.",
    "food" to "Trail rations. Tastes like adventure, mostly salt.",
    "stone" to "Quarried fresh. Great for walls, terrible for pillows.",
    "metal" to "Ingots of questionable origin. Don't ask.",
    "banner_crimson" to "Says 'we were here' in a very loud red.",
    "banner_royal" to "Blue blood not included.",
    "banner_forest" to "Blends in. Mostly.",
    "banner_gold" to "Subtle as a dragon's hoard.",
    "banner_violet" to "For rulers with a flair for drama.",
    "outfit_crimson" to "Swish +5.",
    "outfit_navy" to "For long marches and longer naps.",
    "outfit_ochre" to "The colour of a well-travelled road.",
    "outfit_hat" to "+1 to looking like you know the way.",
    "banner_ironbound" to "Heavy. Honest. Slightly rusty.",
    "outfit_patchwork" to "Every patch a story; most of them true.",
    "banner_spring" to "Smells faintly of blossom.", "banner_summer" to "Warm to the touch.",
    "banner_harvest" to "Stitched from good weeks.", "banner_winter" to "Frost that never melts.",
    "banner_tidewalker" to "Still damp. Always damp.", "banner_evergreen" to "The woods remember you.",
    "outfit_pathfinder" to "Mud on the hem, by design.", "outfit_wayfarer" to "Pockets for every waystone.",
    "outfit_laurel" to "Twelve weeks of steady feet.", "outfit_cartographer" to "Brim wide enough for a map.",
    "banner_wonder_1" to "From the first stone.", "banner_wonder_2" to "Walls that echo.",
    "banner_wonder_3" to "Seen from every road.", "banner_wonder_4" to "There is no higher banner.",
)

/** What the trader says when you walk up: time of day, the season, your week. Nothing from the internet. */
private fun greeting(w: WorldState, trader: Boolean): String {
    val hour = LocalTime.now().hour
    val month = LocalDate.now().monthValue
    val hello = when (hour) { in 5..11 -> "Fine morning for the road, traveller."; in 12..17 -> "Afternoon, traveller."; else -> "Late for walking, isn't it?" }
    val season = when (Story.season(month)) {
        "winter" -> "Dark early these days. Best walk before supper."
        "spring" -> "Everything's coming up green out there."
        "summer" -> "Long days. Bring water."
        else -> "Leaves underfoot and a nip in the air."
    }
    val yesterday = w.days[w.today - 1]?.footMeters ?: 0.0
    val you = if (yesterday >= 3000) "Long walk yesterday, eh? Your boots say so." else if (trader) "Fresh stock, honest-ish prices." else "Fair prices, honest scales."
    return "$hello $season $you"
}

/**
 * The shop, JRPG-style (0.13): ✕ top right, a greeting, the trader's weekly request, Wares and Looks tabs, a ▶ cursor on
 * the selected row with − / + (or type the number), the total and Buy. Prices and totals show the rules; no rules text.
 */
@Composable
fun ShopSheet(repo: Repository, w: WorldState, seller: String, looks: Pair<String?, String?>, onLooks: (Pair<String?, String?>) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("Wares") }
    var selected by remember { mutableStateOf("wood") }
    var qty by remember { mutableIntStateOf(1) }
    val market = w.settlements.firstOrNull { it.areaId == seller }
    val trader = seller == Shop.TRADER
    val multiplier = when {
        trader -> Shop.TRADER_MULTIPLIER
        market != null && Projects.hasChain(market.projects, "market_hall") && "guildhall" in market.projects -> Shop.MARKET_HALL_MULTIPLIER - 0.1
        market != null && Projects.hasChain(market.projects, "market_hall") -> Shop.MARKET_HALL_MULTIPLIER
        market != null && Projects.hasChain(market.projects, "market") -> 1.0
        else -> return
    }
    fun buy(item: String, n: Int, sellerId: String = seller) = scope.launch {
        val ok = repo.buy(sellerId, item, n)
        message = if (ok) "Purchased ${if (n > 1) "$n " else ""}${Story.LOOK_NAMES[item] ?: item.substringAfter('_')}." else "Not quite enough for that."
    }
    Window(if (trader) "Travelling trader" else "${market?.name} market", w.treasury.gold.toInt(), onClose) {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Framed {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageBitmap.imageResource(world.wander.app.R.drawable.sprite_trader), null, filterQuality = FilterQuality.None, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(greeting(w, trader), style = MaterialTheme.typography.bodyMedium)
                }
            }
            w.request?.takeIf { trader && !it.done }?.let { r ->
                Spacer(Modifier.height(8.dp))
                Framed {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageBitmap.imageResource(RESOURCE_ICONS.getValue(r.resource)), null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Wanted: ${r.qty} ${r.resource}", style = MaterialTheme.typography.titleSmall)
                            Text("“I'll give you ${r.rewardQty} ${r.rewardResource} for them.”", style = MaterialTheme.typography.bodySmall, color = SOFT)
                        }
                        GoldButton("Trade", enabled = resourceOf(w, r.resource) >= r.qty) {
                            scope.launch { message = if (repo.buy("request", "request", 1)) "A fair trade." else "Not quite enough for that." }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf("Wares", "Looks")) FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) })
            }
            if (tab == "Wares") {
                for (res in listOf("wood", "food", "stone", "metal")) {
                    val sel = res == selected
                    val unit = Shop.price(res, multiplier, w.boughtThisWeek[res] ?: 0, 1)
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .background(if (sel) Color(0xFFF0E4C6) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { selected = res; qty = 1 }.padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (sel) "▶" else " ", color = FRAME, modifier = Modifier.width(16.dp))
                            Image(ImageBitmap.imageResource(RESOURCE_ICONS.getValue(res)), null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(res.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                                Text(FLAVOUR[res] ?: "", style = MaterialTheme.typography.bodySmall, color = SOFT)
                            }
                            if (!sel) Text("$unit g", style = MaterialTheme.typography.labelLarge)
                        }
                        if (sel) {
                            val total = Shop.price(res, multiplier, w.boughtThisWeek[res] ?: 0, qty)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 56.dp, top = 6.dp)) {
                                StepButton("−") { if (qty > 1) qty-- }
                                OutlinedTextField(
                                    value = qty.toString(), onValueChange = { t -> t.filter { it.isDigit() }.take(3).toIntOrNull()?.let { qty = it.coerceAtLeast(1) } },
                                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(72.dp).padding(horizontal = 4.dp),
                                )
                                StepButton("+") { qty++ }
                                Spacer(Modifier.width(8.dp))
                                Text("$total g", style = MaterialTheme.typography.titleMedium, color = if (total > w.treasury.gold) RED else FRAME, modifier = Modifier.weight(1f))
                                GoldButton("Buy", enabled = total <= w.treasury.gold) { buy(res, qty) }
                            }
                        }
                    }
                }
            } else {
                val (outfit, banner) = looks
                val all = Shop.COSMETICS.keys.toList() + Story.RARE_BANNERS.map { "banner_$it" } + Story.RARE_OUTFITS.map { "outfit_$it" }
                for (id in all) {
                    val kind = id.substringBefore('_'); val name = id.substringAfter('_')
                    val owned = id in w.owned
                    val price = Shop.COSMETICS[id]
                    if (price == null && !owned && Story.LOOK_HOW[id] == null) continue
                    val worn = if (kind == "banner") banner == name else outfit == name
                    val image = if (kind == "banner") MapIcons.images["banner_$name"] else MapIcons.images["wanderer_$name"]
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        image?.let { Image(ImageBitmap.imageResource(it), null, filterQuality = FilterQuality.None, modifier = Modifier.size(40.dp)) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (worn) "★ " else "") + (Story.LOOK_NAMES[id] ?: "${name.replaceFirstChar { it.uppercase() }} ${if (kind == "banner") "banner" else "outfit"}"),
                                style = MaterialTheme.typography.titleSmall)
                            Text(FLAVOUR[id] ?: "", style = MaterialTheme.typography.bodySmall, color = SOFT)
                        }
                        when {
                            worn -> TextButton(onClick = { onLooks(if (kind == "banner") outfit to null else null to banner) }) { Text("Take off") }
                            owned -> GoldButton("Wear") { onLooks(if (kind == "banner") outfit to name else name to banner) }
                            price != null -> OutlinedButton(onClick = { buy(id, 1) }, enabled = w.treasury.gold >= price) { Text("$price g") }
                            else -> Text(Story.LOOK_HOW[id] ?: "", style = MaterialTheme.typography.labelSmall, color = SOFT, modifier = Modifier.width(110.dp))
                        }
                    }
                }
            }
            message?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.labelMedium, color = GREEN) }
        }
    }
}

/** Someone met on the map (0.13): what they ask, what they give; trades have a button, walks just need walking. */
@Composable
fun EncounterSheet(repo: Repository, w: WorldState, e: EncounterState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val title = when (e.kind) { "bard" -> "Wandering bard"; "courier" -> "Courier"; "lost_pet" -> "Worried neighbour"; "peddler" -> "Lost peddler"; "collector" -> "Collector"; else -> "Festival" }
    val line = when (e.kind) {
        "bard" -> "“Sing me somewhere new, friend. I've heard every song about the roads you know.”"
        "courier" -> "“My legs are done. Would you carry this the rest of the way?”"
        "lost_pet" -> "“Have you seen a scruffy little dog? He loves the park.”"
        "peddler" -> "“Lost my way and my cart. Help me restock and this cloak is yours.”"
        "collector" -> "“Metal, good metal. I'll part with this banner for it.”"
        else -> ""
    }
    Window(title, w.treasury.gold.toInt(), onClose) {
        Text(line, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Framed {
            Column {
                Text("Asks: ${e.ask}", style = MaterialTheme.typography.titleSmall)
                Text("Gives: ${e.reward}", style = MaterialTheme.typography.bodyMedium, color = GREEN)
                Text("Leaves in ${(e.expires - w.today).coerceAtLeast(0) + 1} day(s). No harm if you pass.", style = MaterialTheme.typography.labelSmall, color = SOFT)
            }
        }
        e.cost?.let { cost ->
            Spacer(Modifier.height(8.dp))
            GoldButton("Trade", enabled = cost.affordable(w.treasury)) {
                scope.launch { message = if (repo.buy("encounter", "enc:${e.id}", 1)) "A fair trade." else "Not quite enough for that." }
            }
        }
        message?.let { Text(it, color = GREEN, style = MaterialTheme.typography.labelMedium) }
    }
}

private fun resourceOf(w: WorldState, r: String) = when (r) { "wood" -> w.treasury.wood; "food" -> w.treasury.food; "stone" -> w.treasury.stone; else -> w.treasury.metal }

@Composable
private fun Window(title: String, gold: Int, onClose: () -> Unit, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFAF4E6), border = BorderStroke(3.dp, FRAME), shadowElevation = 8.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = FRAME, border = BorderStroke(1.dp, FRAME_GOLD)) {
                    Text(title, color = Color(0xFFF6E6BE), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("$gold g", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onClose, colors = IconButtonDefaults.filledIconButtonColors(containerColor = FRAME), modifier = Modifier.size(36.dp)) {
                    Text("✕", color = Color(0xFFF6E6BE))
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun Framed(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFFBF0), border = BorderStroke(2.dp, FRAME), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(10.dp)) { content() }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, colors = IconButtonDefaults.filledIconButtonColors(containerColor = FRAME), modifier = Modifier.size(40.dp)) {
        Text(label, color = Color(0xFFF6E6BE), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun GoldButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E6028))) { Text(label) }
}
