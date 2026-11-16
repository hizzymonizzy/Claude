package world.wander.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.height
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.expressions.Expression.gte
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.layers.PropertyFactory.lineOffset
import org.maplibre.android.style.expressions.Expression.lt
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillPattern
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.symbolSortKey
import org.maplibre.android.style.layers.PropertyFactory.textPadding
import org.maplibre.android.style.layers.PropertyFactory.iconPadding
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.linePattern
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.symbolPlacement
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection

/*
 * The map's look: our sources and layers (fog, ground, roads, paths, water, markers), the sprites they use,
 * and the base map restyled to parchment. MapScreen only feeds data into the sources.
 */
internal const val FOG = "#E9DFC7"
internal const val INK = "#5B4636"

internal fun Style.source(id: String): GeoJsonSource? = getSourceAs(id)

internal val OUR_LAYERS = listOf(
    "explored", "green-places", "shore", "fog", "fog-edge", "peek", "roads-edge", "roads-trail", "roads-dirt", "roads-cobble",
    "roads-trees-l", "roads-trees-r", "wild-1", "wild-2", "wild-3", "wild-gravel", "wild-cairns-l", "wild-cairns-r",
    "sand-places", "water-route", "water-light",
    "road-names", "track", "waypoints", "landmark-icons", "waystones", "settlements", "settlement-labels",
    "resource-rings", "resource-icons", "me",
)

internal fun addWorldLayers(s: Style, problems: MutableList<String>) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    for (id in listOf("explored", "green-places", "sand-places", "shore", "water", "fog", "peek", "wild", "roads", "landmarks", "track", "waypoints", "waystones", "settlements", "resources", "me")) s.addSource(GeoJsonSource(id, empty))

    // Explored ground: real terrain texture (0 A.D. grass / forest floor), slipped under the base map's
    // water, roads and buildings so those still show where you've been.
    val ground = FillLayer("explored", "explored").withProperties(fillPattern("ground_grass"), fillOpacity(0.92f))
    val woods = FillLayer("green-places", "green-places").withProperties(fillPattern("ground_forest"), fillOpacity(0.85f))
    val waterId = s.layers.firstOrNull { it is FillLayer && "water" in it.id.lowercase() }?.id
    val sand = FillLayer("sand-places", "sand-places").withProperties(fillPattern("ground_sand"), fillOpacity(0.9f))
    // Wet sand along coastlines, above beaches, still under the base map's water (so only the land side shows).
    val shore = FillLayer("shore", "shore").withProperties(fillPattern("ground_sand_wet"), fillOpacity(0.9f))
    if (waterId != null) { s.addLayerBelow(ground, waterId); s.addLayerAbove(woods, "explored"); s.addLayerAbove(sand, "green-places"); s.addLayerAbove(shore, "sand-places") }
    else { s.addLayer(ground); s.addLayer(woods); s.addLayer(sand); s.addLayer(shore) }

    // Fog: parchment you can see the land through, thicker when zoomed out so explored areas pop.
    s.addLayer(
        FillLayer("fog", "fog").withProperties(
            fillPattern("fog_parchment"),
            fillOpacity(interpolate(linear(), zoom(), stop(8, 0.85f), stop(13, 0.75f), stop(17, 0.62f))),
        ),
    )
    // Soft edge: a wide blurred line along the fog boundary.
    s.addLayer(LineLayer("fog-edge", "fog").withProperties(lineColor(FOG), lineWidth(12f), lineBlur(10f), lineOpacity(0.6f)))
    s.addLayer(
        LineLayer("peek", "peek").withProperties(
            lineColor(INK), lineOpacity(0.35f), lineWidth(1.5f), lineDasharray(arrayOf(1.5f, 2.5f)),
        ),
    )
    // Charted roads, five stages by distinct days walked (see the Legend):
    // 1 Trail (dashed sand) · 2 Footpath (dirt) · 3 Lane (dirt + edge) · 4 Cobbled road · 5 Avenue (cobbles, tree-lined).
    fun level(n: Int) = eq(get("level"), literal(n))
    guard(problems, "roads-edge") {
        s.addLayer(
            LineLayer("roads-edge", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineColor(match(get("level"), literal("#5E5A52"), stop(3, "#4A3A28"))), lineOpacity(0.75f),
                lineWidth(match(get("level"), literal(11f), stop(3, 7.5f), stop(4, 9.5f))),
            ).also { it.setFilter(gte(get("level"), literal(3))) },
        )
    }
    guard(problems, "roads-trail") {
        s.addLayer(
            LineLayer("roads-trail", "roads").withProperties(
                lineCap(Property.LINE_CAP_BUTT), lineColor("#E2CDA0"), lineWidth(2.5f), lineDasharray(arrayOf(1.6f, 1.2f)),
            ).also { it.setFilter(level(1)) },
        )
    }
    guard(problems, "roads-dirt") {
        s.addLayer(
            LineLayer("roads-dirt", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineColor("#D2B88A"), linePattern("road_dirt"), lineWidth(match(get("level"), literal(5f), stop(2, 3.5f))),
            ).also { it.setFilter(all(gte(get("level"), literal(2)), lt(get("level"), literal(4)))) },
        )
    }
    guard(problems, "roads-cobble") {
        s.addLayer(
            LineLayer("roads-cobble", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
                lineColor("#8E8A80"), linePattern("road_cobble"), lineWidth(match(get("level"), literal(8.5f), stop(4, 6.5f))),
            ).also { it.setFilter(gte(get("level"), literal(4))) },
        )
    }
    for ((id, offset) in listOf("roads-trees-l" to -9f, "roads-trees-r" to 9f)) guard(problems, id) {
        s.addLayer(
            LineLayer(id, "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineColor("#3A6832"), lineWidth(6f), lineOffset(offset),
                lineDasharray(arrayOf(0.01f, 3.2f)),
            ).also { it.setFilter(level(5)) },
        )
    }
    // Your own paths, five stages: 1 faint track · 2 trodden path · 3 dirt track · 4 gravel trail · 5 ranger trail.
    fun wildLevel(n: Int) = eq(get("level"), literal(n))
    guard(problems, "wild-edge") {
        s.addLayer(LineLayer("wild-edge", "wild").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#6E5638"), lineOpacity(0.6f), lineWidth(match(get("level"), literal(6f), stop(4, 5.5f))),
        ).also { it.setFilter(gte(get("level"), literal(4))) })
    }
    guard(problems, "wild-1") {
        s.addLayer(LineLayer("wild-1", "wild").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#F2E8CC"), lineWidth(1.6f), lineDasharray(arrayOf(0.4f, 1.8f)),
        ).also { it.setFilter(wildLevel(1)) })
    }
    guard(problems, "wild-2") {
        s.addLayer(LineLayer("wild-2", "wild").withProperties(
            lineCap(Property.LINE_CAP_BUTT), lineColor("#E6D3A8"), lineWidth(2.4f), lineDasharray(arrayOf(1.4f, 1.0f)),
        ).also { it.setFilter(wildLevel(2)) })
    }
    guard(problems, "wild-3") {
        s.addLayer(LineLayer("wild-3", "wild").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#C8AC7E"), linePattern("road_dirt"), lineWidth(3f),
        ).also { it.setFilter(wildLevel(3)) })
    }
    guard(problems, "wild-gravel") {
        s.addLayer(LineLayer("wild-gravel", "wild").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#D8C9A6"), linePattern("ground_sand"), lineWidth(match(get("level"), literal(4f), stop(4, 3.5f))),
        ).also { it.setFilter(gte(get("level"), literal(4))) })
    }
    for ((id, offset) in listOf("wild-cairns-l" to -6f, "wild-cairns-r" to 6f)) guard(problems, id) {
        s.addLayer(LineLayer(id, "wild").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#8D8A84"), lineWidth(4f), lineOffset(offset), lineDasharray(arrayOf(0.01f, 5f)),
        ).also { it.setFilter(wildLevel(5)) })
    }

    // Water routes you paddle, five stages: 1 wake · 2 paddle line · 3 paddle route · 4 marked route (buoys) · 5 waterway.
    guard(problems, "water-edge") {
        s.addLayer(LineLayer("water-edge", "water").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#3E7FA3"), lineOpacity(0.7f), lineWidth(match(get("level"), literal(7f), stop(3, 4.5f), stop(4, 5.5f))),
        ).also { it.setFilter(gte(get("level"), literal(3))) })
    }
    guard(problems, "water-route") {
        s.addLayer(LineLayer("water-route", "water").withProperties(
            lineCap(Property.LINE_CAP_ROUND),
            lineColor(match(get("level"), literal("#A6D3E8"), stop(1, "#E4F3F9"), stop(2, "#CBE6F2"))),
            lineWidth(match(get("level"), literal(4.5f), stop(1, 1.6f), stop(2, 2f), stop(3, 2.5f), stop(4, 3.2f))),
        ).also { it.setFilter(gte(get("level"), literal(3))) })
    }
    guard(problems, "water-light") {
        s.addLayer(LineLayer("water-light", "water").withProperties(
            lineCap(Property.LINE_CAP_BUTT),
            lineColor(match(get("level"), literal("#CBE6F2"), stop(1, "#E4F3F9"))),
            lineWidth(match(get("level"), literal(2f), stop(1, 1.6f))),
            lineDasharray(arrayOf(1f, 1.6f)),
        ).also { it.setFilter(lt(get("level"), literal(3))) })
    }
    guard(problems, "water-buoys-r") {
        s.addLayer(LineLayer("water-buoys-r", "water").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#E8833A"), lineWidth(5f), lineOffset(8f), lineDasharray(arrayOf(0.01f, 6f)),
        ).also { it.setFilter(gte(get("level"), literal(4))) })
    }
    guard(problems, "water-buoys-l") {
        s.addLayer(LineLayer("water-buoys-l", "water").withProperties(
            lineCap(Property.LINE_CAP_ROUND), lineColor("#E8833A"), lineWidth(5f), lineOffset(-8f), lineDasharray(arrayOf(0.01f, 6f)),
        ).also { it.setFilter(wildLevel(5)) })
    }
    s.addLayer(LineLayer("track", "track").withProperties(lineColor("#C0392B"), lineWidth(3f), lineOpacity(0.8f)))
    guard(problems, "road-names") { s.addLayer(
        SymbolLayer("road-names", "roads").withProperties(
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), textField(get("name")), textFont(FONT), textSize(11f),
            textColor(INK), textHaloColor("#FFFFFF"), textHaloWidth(1.2f),
        ).also { it.minZoom = 14f },
    ) }
    // Markers, least important first. MapLibre places the top layer first, so settlements claim their
    // space, then waystones, then places, then waypoints; anything that would overlap a more important
    // marker hides until you zoom in far enough for both. Each marker's name rides in the same symbol
    // and drops (text-optional) before the icon does.
    fun marker(id: String, source: String, icon: Expression, text: Expression, size: Expression, color: String, minZoom: Float, sort: Float) =
        SymbolLayer(id, source).withProperties(
            iconImage(icon), iconSize(size), iconAnchor(Property.ICON_ANCHOR_CENTER), iconPadding(4f),
            iconAllowOverlap(false), iconIgnorePlacement(false),
            textField(text), textFont(FONT), textSize(interpolate(linear(), zoom(), stop(10, 10f), stop(16, 13f))),
            textColor(color), textHaloColor("#F4ECD8"), textHaloWidth(2f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 1.3f)), textOptional(true), textPadding(2f),
            symbolSortKey(sort),
        ).also { it.minZoom = minZoom }
    guard(problems, "waypoints") { s.addLayer(marker(
        "waypoints", "waypoints", literal("medal_waypoint"), get("name"),
        interpolate(linear(), zoom(), stop(8, 0.3f), stop(13, 0.36f), stop(17, 0.5f)), INK, 0f, 3f,
    )) }
    guard(problems, "landmark-icons") { s.addLayer(marker(
        "landmark-icons", "landmarks", get("icon"), get("name"),
        interpolate(linear(), zoom(), stop(10, 0.3f), stop(14, 0.36f), stop(17, 0.5f)), "#2E5E3A", 11f, 2f,
    )) }
    guard(problems, "waystones") { s.addLayer(marker(
        "waystones", "waystones", get("icon"), get("label"),
        interpolate(linear(), zoom(), stop(9, 0.36f), stop(14, 0.45f), stop(17, 0.6f)), "#2E4A6B", 9f, 1f,
    )) }
    // Settlements always show. Their medallion has the capital star and monument baked in, so the
    // badges scale with it instead of drifting apart as you zoom.
    s.addLayer(
        SymbolLayer("settlements", "settlements").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(false), iconPadding(6f),
            iconSize(interpolate(linear(), zoom(), stop(8, 0.34f), stop(12, 0.42f), stop(15, 0.5f), stop(17, 0.62f))),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
        ),
    )
    guard(problems, "settlement-labels") { s.addLayer(
        SymbolLayer("settlement-labels", "settlements").withProperties(
            textField(get("label")), textFont(FONT), textSize(interpolate(linear(), zoom(), stop(10, 11f), stop(16, 15f))),
            textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f), textAllowOverlap(true), textIgnorePlacement(false),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 2.4f)),
        ),
    ) }
    // Nearest resources: drawn in the map so they stay pinned to the ground (gold ring + resource icon).
    guard(problems, "resource-rings") { s.addLayer(
        CircleLayer("resource-rings", "resources").withProperties(
            // Gold ring; green when you're close enough to gather.
            circleColor("#F4ECD8"), circleOpacity(0.85f),
            circleStrokeColor(switchCase(eq(get("near"), literal(true)), literal("#3F9D4A"), literal("#C4A052"))),
            circleStrokeWidth(switchCase(eq(get("near"), literal(true)), literal(4f), literal(2.5f))),
            circleRadius(interpolate(linear(), zoom(), stop(10, 10f), stop(16, 15f))),
        ),
    ) }
    guard(problems, "resource-icons") { s.addLayer(
        SymbolLayer("resource-icons", "resources").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.28f), stop(16, 0.4f))),
        ),
    ) }
    s.addLayer(
        SymbolLayer("me", "me").withProperties(
            iconImage("wanderer"), iconSize(interpolate(linear(), zoom(), stop(10, 0.3f), stop(16, 0.45f), stop(18, 0.6f))),
            iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
        ),
    )
}

/** Map images. oad_* are adapted from 0 A.D. (© Wildfire Games, CC BY-SA 3.0); see Settings → Credits. */
private inline fun guard(problems: MutableList<String>, what: String, block: () -> Unit) {
    try { block() } catch (e: Exception) { problems += "$what: ${e.javaClass.simpleName} ${e.message}" }
}

internal val SPRITES: Map<String, Int> get() = MapIcons.images

internal fun addSprites(s: Style, context: Context) {
    val opts = BitmapFactory.Options().apply { inScaled = false }
    val loaded = SPRITES.mapNotNull { (name, res) -> BitmapFactory.decodeResource(context.resources, res, opts)?.let { name to it } }.toMap()
    for ((name, bmp) in loaded) s.addImage(name, bmp)
    // Capital medallions with the star (and monument, once raised) baked in, one image per tier.
    val star = loaded["badge_capital"] ?: return
    val monument = loaded["medal_monument"]
    for (tier in 0..6) {
        val medal = loaded["medal_tier_$tier"] ?: continue
        s.addImage("medal_tier_${tier}_cap", capitalMedal(medal, star, null))
        if (monument != null) s.addImage("medal_tier_${tier}_capmon", capitalMedal(medal, star, monument))
    }
}

/** A settlement medallion with the capital star at its upper right and, if given, a small monument at its upper left. */
private fun capitalMedal(medal: Bitmap, star: Bitmap, monument: Bitmap?): Bitmap {
    val pad = 26
    val out = Bitmap.createBitmap(medal.width + 2 * pad, medal.height + 2 * pad, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    c.drawBitmap(medal, pad.toFloat(), pad.toFloat(), paint)
    val sz = (medal.width * 0.42f)
    if (monument != null) c.drawBitmap(monument, null, RectF(2f, 2f, 2f + sz, 2f + sz * monument.height / monument.width), paint)
    c.drawBitmap(star, null, RectF(out.width - sz - 2f, 2f, out.width - 2f, 2f + sz), paint)
    return out
}

/** The base map's own font stack; text in any other font fails to load and hides the whole layer. */
internal var FONT: Array<String> = arrayOf("Noto Sans Regular")

/** Recolors the base map toward parchment and hides its labels: names appear only once you discover them. */
internal fun restyleBasemap(s: Style) {
    s.layers.filterIsInstance<SymbolLayer>().firstNotNullOfOrNull { it.textFont.value?.takeIf { f -> f.isNotEmpty() } }?.let { FONT = it }
    for (layer in s.layers) {
        val id = layer.id.lowercase()
        when (layer) {
            is BackgroundLayer -> layer.setProperties(backgroundColor("#EFE6CF"))
            is FillLayer -> when {
                // Clear shallow water (0 A.D. sea floor, tinted). The fog dims it where you haven't been, so charted
                // water looks vivid, the way explored ground turns to grass.
                "water" in id -> layer.setProperties(fillColor("#7FB3C8"), fillPattern("water_sea"))
                "sand" in id || "beach" in id -> layer.setProperties(fillPattern("ground_sand"))
                listOf("park", "wood", "grass", "landcover", "forest", "garden").any { it in id } -> layer.setProperties(fillColor("#D3DEB0"))
                "building" in id -> layer.setProperties(fillColor("#E0CFAE"))
                "landuse" in id -> layer.setProperties(fillColor("#E9DEC2"))
            }
            is LineLayer -> if (listOf("road", "highway", "street", "path", "bridge", "tunnel", "rail").any { it in id }) {
                layer.setProperties(lineColor("#E6D8B8"))
            }
            is SymbolLayer -> layer.setProperties(visibility(Property.NONE))
        }
    }
}
