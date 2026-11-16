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
import world.wander.app.data.ResourceTarget
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
fun InventoryBar(t: Treasury, targets: List<ResourceTarget>, onPick: (ResourceTarget) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x55806040)),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("gold" to t.gold, "wood" to t.wood, "food" to t.food, "stone" to t.stone, "metal" to t.metal).forEach { (kind, v) ->
                val target = targets.firstOrNull { it.resource == kind }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (target != null) Modifier.clickable { onPick(target) } else Modifier,
                ) {
                    Image(ImageBitmap.imageResource(RESOURCE_ICONS.getValue(kind)), contentDescription = kind, filterQuality = FilterQuality.Medium, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(v.toInt().toString(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * Pointers to the nearest source of each resource. On screen, a small marker sits on the spot; off screen,
 * a chip waits at the edge of the map pointing the way, with the distance.
 *
 * [project] turns a map position into pixels on the map view (null if the map isn't ready);
 * [topInsetPx] keeps edge chips clear of the cards at the top.
 */
@Composable
fun ResourcePointers(
    targets: List<ResourceTarget>,
    project: (LatLng) -> Pair<Float, Float>?,
    topInsetPx: Float,
    onPick: (ResourceTarget) -> Unit,
    @Suppress("UNUSED_PARAMETER") cameraTick: Int,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
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
            val onScreen = x in left..right && y in top..bottom
            if (onScreen) {
                Box(
                    Modifier.offset { IntOffset((x - chip / 2).roundToInt(), (y - chip / 2).roundToInt()) }
                        .size(30.dp).clickable { onPick(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape, color = Color(0xCCF4ECD8), border = BorderStroke(2.dp, Color(0xFFC4A052))) {
                        Image(ImageBitmap.imageResource(icon), contentDescription = t.resource, filterQuality = FilterQuality.Medium, modifier = Modifier.padding(3.dp).size(22.dp))
                    }
                }
            } else {
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
}
