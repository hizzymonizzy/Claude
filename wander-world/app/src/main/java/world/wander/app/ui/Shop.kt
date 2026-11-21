package world.wander.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import world.wander.app.data.Repository
import world.wander.engine.Projects
import world.wander.engine.Shop
import world.wander.engine.WorldState

/**
 * The shop: the travelling trader (2× prices) or a village market (base prices, 10% off with a market hall).
 * Each unit of a resource bought in a week costs 10% more than the last; the count resets on Mondays.
 * Cosmetics (banners for the capital, outfits for your wanderer) are looks only.
 */
@Composable
fun ShopSheet(repo: Repository, w: WorldState, seller: String, looks: Pair<String?, String?>, onLooks: (Pair<String?, String?>) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val market = w.settlements.firstOrNull { it.areaId == seller }
    val multiplier = when {
        seller == Shop.TRADER -> Shop.TRADER_MULTIPLIER
        market != null && Projects.hasChain(market.projects, "market_hall") -> Shop.MARKET_HALL_MULTIPLIER
        market != null && Projects.hasChain(market.projects, "market") -> 1.0
        else -> return
    }
    val title = if (seller == Shop.TRADER) "Travelling trader" else "${market?.name} market"
    fun buy(item: String, qty: Int) = scope.launch {
        val ok = repo.buy(seller, item, qty)
        message = if (ok) "Bought ${if (qty > 1) "$qty " else ""}${item.substringAfter('_').replace('_', ' ')}." else "Not enough gold."
    }
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0x55806040)), shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp).heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (multiplier) {
                            Shop.TRADER_MULTIPLIER -> "Twice market prices, but always nearby."
                            Shop.MARKET_HALL_MULTIPLIER -> "Market hall: 10% off."
                            else -> "Market prices."
                        } + " Prices climb 10% for each one you buy this week; they reset on Monday.",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text("${w.treasury.gold.toInt()} gold", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            for (res in listOf("wood", "food", "stone", "metal")) {
                val n = w.boughtThisWeek[res] ?: 0
                val one = Shop.price(res, multiplier, n, 1)
                val five = Shop.price(res, multiplier, n, 5)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Image(ImageBitmap.imageResource(RESOURCE_ICONS.getValue(res)), contentDescription = res, filterQuality = FilterQuality.Medium, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(res.replaceFirstChar { it.uppercase() }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { buy(res, 1) }, enabled = w.treasury.gold >= one) { Text("1 · $one") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick = { buy(res, 5) }, enabled = w.treasury.gold >= five) { Text("5 · $five") }
                }
            }
            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))
            Text("Looks", style = MaterialTheme.typography.titleSmall)
            Text("Banners fly over your capital; outfits dress your wanderer. Looks only.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            val (outfit, banner) = looks
            for ((id, price) in Shop.COSMETICS) {
                val kind = id.substringBefore('_'); val name = id.substringAfter('_')
                val owned = id in w.owned
                val equipped = if (kind == "banner") banner == name else outfit == name
                val image = if (kind == "banner") MapIcons.images["banner_$name"] else MapIcons.images["wanderer_$name"]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    image?.let { Image(ImageBitmap.imageResource(it), contentDescription = null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(32.dp)) }
                    Text("${name.replaceFirstChar { it.uppercase() }} ${if (kind == "banner") "banner" else "outfit"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    when {
                        equipped -> TextButton(onClick = { onLooks(if (kind == "banner") outfit to null else null to banner) }) { Text("Take off") }
                        owned -> TextButton(onClick = { onLooks(if (kind == "banner") outfit to name else name to banner) }) { Text("Wear") }
                        else -> OutlinedButton(onClick = { buy(id, 1) }, enabled = w.treasury.gold >= price) { Text("$price gold") }
                    }
                }
            }
            message?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Row(Modifier.padding(top = 4.dp)) { Spacer(Modifier.weight(1f)); TextButton(onClick = onClose) { Text("Close") } }
        }
    }
}
