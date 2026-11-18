package world.wander.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import world.wander.app.R
import world.wander.engine.ResourceTarget
import world.wander.engine.Yields
import world.wander.engine.GATHER_M
import world.wander.engine.LatLng
import world.wander.engine.Treasury
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

private val RESOURCE_ICONS = mapOf(
    "gold" to R.drawable.oad_medal_gold, "wood" to R.drawable.oad_res_wood, "food" to R.drawable.oad_res_food,
    "stone" to R.drawable.oad_res_stone, "metal" to R.drawable.oad_res_metal,
)

fun formatDistance(m: Double): String = if (m < 950) "${(m / 10).roundToInt() * 10} m" else "%.1f km".format(m / 1000)

/** Your stores on the map. Tapping a resource flies to its nearest source, when there is one. */
@Composable
fun InventoryBar(t: Treasury, targets: List<ResourceTarget>, today: Treasury? = null, onPick: (ResourceTarget) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x55806040)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val gained = mapOf("gold" to (today?.gold ?: 0.0), "wood" to (today?.wood ?: 0.0), "food" to (today?.food ?: 0.0),
                "stone" to (today?.stone ?: 0.0), "metal" to (today?.metal ?: 0.0))
            listOf("gold" to t.gold, "wood" to t.wood, "food" to t.food, "stone" to t.stone, "metal" to t.metal).forEach { (kind, v) ->
                val target = targets.firstOrNull { it.resource == kind }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (target != null) Modifier.clickable { onPick(target) } else Modifier,
                ) {
                    Image(ImageBitmap.imageResource(RESOURCE_ICONS.getValue(kind)), contentDescription = kind, filterQuality = FilterQuality.Medium, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(v.toInt().toString(), style = MaterialTheme.typography.labelLarge)
                    // What today's walks added (counted when a walk ends).
                    gained.getValue(kind).toInt().takeIf { it > 0 }?.let {
                        Text("+$it", fontSize = 10.sp, color = Color(0xFF3F7D3A), modifier = Modifier.padding(start = 1.dp))
                    }
                }
            }
        }
    }
}

/**
 * Arrows at the edge of the map toward resources that are off screen, with the distance. (On-screen resources
 * are drawn inside the map itself, in MapStyle, so they stay pinned to the ground.)
 *
 * [project] turns a map position into pixels on the map view (null if the map isn't ready); [cameraTick]
 * changes whenever the map moves and is read below so the arrows follow the camera; [topInsetPx] keeps them
 * clear of the cards at the top.
 */
@Composable
fun ResourcePointers(
    targets: List<ResourceTarget>,
    project: (LatLng) -> Pair<Float, Float>?,
    topInsetPx: Float,
    onPick: (ResourceTarget) -> Unit,
    cameraTick: Int,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Reading the tick here makes this block redraw on every camera move.
        if (cameraTick < 0) return@BoxWithConstraints
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val chip = with(density) { 30.dp.toPx() }
        val left = chip; val right = w - chip
        val top = topInsetPx + chip; val bottom = h - with(density) { 150.dp.toPx() }
        if (right <= left || bottom <= top) return@BoxWithConstraints
        val cx = w / 2; val cy = (top + bottom) / 2
        for (t in targets) {
            val (x, y) = project(t.point) ?: continue
            val icon = RESOURCE_ICONS[t.resource] ?: continue
            if (x in 0f..w && y in 0f..h) continue // on screen: the map draws it
            // Clamp the line from the middle of the map to the target onto the safe rectangle.
            val dx = x - cx; val dy = y - cy
            val sx = if (dx == 0f) Float.MAX_VALUE else ((if (dx > 0) right else left) - cx) / dx
            val sy = if (dy == 0f) Float.MAX_VALUE else ((if (dy > 0) bottom else top) - cy) / dy
            val k = minOf(abs(sx), abs(sy))
            val ex = cx + dx * k; val ey = cy + dy * k
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val half = with(density) { 26.dp.toPx() }
            Column(
                Modifier.offset { IntOffset((ex - half).roundToInt(), (ey - half).roundToInt()) }.clickable { onPick(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(52.dp, 34.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = Color(0xE6F4ECD8), border = BorderStroke(2.dp, Color(0xFF8A6A3A))) {
                        Image(ImageBitmap.imageResource(icon), contentDescription = t.resource, filterQuality = FilterQuality.Medium, modifier = Modifier.padding(3.dp).size(22.dp))
                    }
                    Text(
                        "➤", color = Color(0xFF5B4636), fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center).rotate(angle).offset(x = 24.dp),
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xE6F4ECD8)) {
                    Text(formatDistance(t.distanceM) + if (t.note == "new") " · new" else "", fontSize = 10.sp, color = Color(0xFF5B4636), modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** What a resource node is and how to gather from it; opened by tapping the node on the map. */
@Composable
fun NodeDetailsCard(n: ResourceTarget, onGo: () -> Unit, onClose: () -> Unit) {
    val icon = RESOURCE_ICONS[n.resource] ?: return
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, Color(0x55806040)), shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageBitmap.imageResource(icon), contentDescription = null, filterQuality = FilterQuality.Medium, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${n.resource.replaceFirstChar { it.uppercase() }} · ${n.kind.replace('_', ' ')}",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(if (n.distanceM <= GATHER_M) "you're here" else formatDistance(n.distanceM), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            val state = when (n.note) {
                "new" -> "New: the first gathering here pays double."
                "gathered" -> "Gathered today. Come back tomorrow, or try somewhere you haven't been."
                "thinned" -> "Thinned by frequent gathering (${(n.abundance * 100).toInt()}% full). It grows back about 5% a day."
                else -> if (n.abundance < 0.99) "${(n.abundance * 100).toInt()}% full, growing back about 5% a day." else "Fresh."
            }
            Text(state, style = MaterialTheme.typography.bodyMedium)
            Text(Yields.howToGather(n.resource, n.kind), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (n.expectedYield > 0) {
                Text("Right now: about %.1f ${n.resource}%s".format(n.expectedYield, if (n.kind in setOf("shore", "field", "street")) " for this stretch" else " a visit"),
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF3F7D3A), modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = onGo) { Text("Show on map") }
                androidx.compose.material3.OutlinedButton(onClick = onClose) { Text("Close") }
            }
        }
    }
}
