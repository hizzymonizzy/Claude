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
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.linePattern
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
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
import org.maplibre.android.style.layers.PropertyFactory.iconOpacity
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
    "explored", "green-places", "shore", "water-over", "fog", "fog-edge", "peek-edge", "peek", "roads-edge", "roads-trail-edge", "roads-trail", "roads-dirt", "roads-cobble",
    "roads-trees-l", "roads-trees-r", "wild-1", "wild-2", "wild-3", "wild-gravel", "wild-cairns-l", "wild-cairns-r",
    "sand-places", "water-route", "water-light",
    "road-names", "track", "waypoints", "landmark-icons", "waystones", "settlements", "settlement-labels",
    "resource-rings", "resource-icons", "me",
    "heat-hood", "heat-block", "heat-county", "region-links", "region-nodes", "buildings", "trader",
)

internal fun addWorldLayers(s: Style, problems: MutableList<String>) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    for (id in listOf("explored", "green-places", "sand-places", "shore", "water", "fog", "peek", "wild", "roads", "landmarks", "track", "waypoints", "waystones", "settlements", "resources", "me",
        "heat-block", "heat-hood", "heat-county", "region-nodes", "region-links", "buildings", "trader")) s.addSource(GeoJsonSource(id, empty))

    // Explored ground: real terrain texture (0 A.D. grass / forest floor) laid OVER the base map's streets and
    // buildings, so inside explored land every street is drawn by its status (walked: trail…avenue; unwalked:
    // dashed), not by the base map. Under the fog the base map's faint streets still show the lay of the land.
    val ground = FillLayer("explored", "explored").withProperties(fillPattern("ground_grass"), fillOpacity(1f))
    val woods = FillLayer("green-places", "green-places").withProperties(fillPattern("ground_forest"), fillOpacity(0.85f))
    val sand = FillLayer("sand-places", "sand-places").withProperties(fillPattern("ground_sand"), fillOpacity(0.9f))
    // Wet sand along coastlines, above beaches, under the water copy below (so only the land side shows).
    val shore = FillLayer("shore", "shore").withProperties(fillPattern("ground_sand_wet"), fillOpacity(0.9f))
    s.addLayer(ground); s.addLayer(woods); s.addLayer(sand); s.addLayer(shore)
    // The base map's water, drawn again above explored ground so lakes and sea still show through it.
    s.layers.filterIsInstance<FillLayer>().firstOrNull { "water" in it.id.lowercase() }?.let { base ->
        guard(problems, "water-over") {
            val over = FillLayer("water-over", base.sourceId).withProperties(fillColor("#7FB3C8"), fillPattern("water_sea"))
            over.sourceLayer = base.sourceLayer
            base.filter?.let { over.setFilter(it) }
            s.addLayerAbove(over, "shore")
        }
    }

    // The heat map (0.12, ADR 0007): hexes textured by ground and heat step, no outlines (same ground merges).
    // Block hexes (~20 m) up close with a faint grid, neighbourhood hexes (~130 m) in between.
    guard(problems, "heat") {
        s.addLayer(FillLayer("heat-hood", "heat-hood").withProperties(fillPattern(get("pattern")), fillOpacity(0.92f)).apply {
            minZoom = 12.5f; maxZoom = 15.5f
        })
        s.addLayer(FillLayer("heat-block", "heat-block").withProperties(
            fillPattern(get("pattern")), fillOpacity(0.92f), fillOutlineColor("rgba(30,40,20,0.18)"),
        ).apply { minZoom = 15.5f })
    }

    // Fog: parchment you can see the land through, thicker when zoomed out so explored areas pop.
    s.addLayer(
        FillLayer("fog", "fog").withProperties(
            fillPattern("fog_parchment"),
            fillOpacity(interpolate(linear(), zoom(), stop(8, 0.85f), stop(13, 0.75f), stop(17, 0.62f))),
        ),
    )
    // Soft edge: a wide blurred line along the fog boundary.
    s.addLayer(LineLayer("fog-edge", "fog").withProperties(lineColor(FOG), lineWidth(12f), lineBlur(10f), lineOpacity(0.6f)))
    // County (~1 km hexes) is the overview board, drawn over the fog; beyond it, regions are nodes.
    guard(problems, "heat-county") {
        s.addLayer(FillLayer("heat-county", "heat-county").withProperties(
            fillPattern(get("pattern")), fillOpacity(0.95f), fillOutlineColor("rgba(92,74,50,0.45)"),
        ).apply { minZoom = 8.5f; maxZoom = 12.5f })
        s.addLayer(LineLayer("region-links", "region-links").withProperties(
            lineColor("#5A82BE"), lineWidth(3f), lineDasharray(arrayOf(1.5f, 1.5f)), lineOpacity(0.85f),
        ).apply { maxZoom = 8.5f })
    }
    s.addLayer(
        // Unwalked streets in explored land: a clearly dashed, muted path. Walked roads are always solid.
        // 0.12: light dashes with a dark edge so they read on grass (the old muted brown disappeared on it).
        // Dash lengths scale with line width, so the top dashes are lengthened to line up with the edge's.
        LineLayer("peek-edge", "peek").withProperties(
            lineColor("#4A3A28"), lineOpacity(0.7f), lineWidth(4.5f), lineDasharray(arrayOf(2f, 2f)),
        ),
    )
    s.addLayer(
        LineLayer("peek", "peek").withProperties(
            lineColor("#F2E6C8"), lineOpacity(0.95f), lineWidth(2.5f), lineDasharray(arrayOf(3.6f, 3.6f)),
        ),
    )
    // Charted roads, five stages by distinct days walked (see the Legend):
    // 1 Trail (solid sand path with a soft edge) · 2 Footpath (dirt) · 3 Lane (dirt + edge) · 4 Cobbled road · 5 Avenue (cobbles, tree-lined).
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
    guard(problems, "roads-trail-edge") {
        s.addLayer(
            LineLayer("roads-trail-edge", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND), lineColor("#9C8458"), lineOpacity(0.55f),
                lineWidth(6f), lineBlur(1.5f),
            ).also { it.setFilter(level(1)) },
        )
    }
    guard(problems, "roads-trail") {
        s.addLayer(
            LineLayer("roads-trail", "roads").withProperties(
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND), lineColor("#E8D4A6"), lineWidth(3.5f),
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
        ).apply { minZoom = 8.5f },
    )
    guard(problems, "settlement-labels") { s.addLayer(
        SymbolLayer("settlement-labels", "settlements").withProperties(
            textField(get("label")), textFont(FONT), textSize(interpolate(linear(), zoom(), stop(10, 11f), stop(16, 15f))),
            textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f), textAllowOverlap(true), textIgnorePlacement(false),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 2.4f)),
        ).apply { minZoom = 8.5f },
    ) }
    // Buildings at each settlement (latest of each upgrade chain); a market opens the shop when tapped.
    guard(problems, "buildings") { s.addLayer(
        SymbolLayer("buildings", "buildings").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(12, 0.0f), stop(13, 0.22f), stop(15, 0.34f), stop(17, 0.5f))),
        ),
    ) }
    // The travelling trader, by the village you visited last. Tap to trade.
    guard(problems, "trader") { s.addLayer(
        SymbolLayer("trader", "trader").withProperties(
            iconImage("trader"), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.3f), stop(16, 0.45f), stop(18, 0.6f))),
            textField("Trader"), textFont(FONT), textSize(11f), textColor("#6B4A1F"), textHaloColor("#F4ECD8"), textHaloWidth(1.5f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 0.3f)), textAllowOverlap(true),
            textOptional(true),
        ),
    ) }
    guard(problems, "region-nodes") { s.addLayer(
        SymbolLayer("region-nodes", "region-nodes").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true),
            iconSize(interpolate(linear(), get("tier"), stop(0, 0.3f), stop(6, 0.75f))),
            textField(get("label")), textFont(FONT), textSize(12f), textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 2.2f)), textAllowOverlap(true),
        ).apply { maxZoom = 8.5f },
    ) }
    // Resource nodes: fixed spots on places and streets, drawn in the map so they stay pinned to the ground.
    // Ring: gold = fresh, bright gold + thicker = the one the arrow points to, green = you're in its zone,
    // grey-green with a tick = gathered today, faded = thinned.
    guard(problems, "resource-rings") { s.addLayer(
        CircleLayer("resource-rings", "resources").withProperties(
            circleColor(match(get("state"), literal("#F4ECD8"), stop("gathered", "#DCE8D2"))),
            circleOpacity(match(get("state"), literal(0.9f), stop("thinned", 0.5f))),
            circleStrokeColor(switchCase(
                eq(get("near"), literal(true)), literal("#3F9D4A"),
                eq(get("state"), literal("gathered")), literal("#6E8F68"),
                eq(get("best"), literal(true)), literal("#D9A520"),
                literal("#B89A5A"),
            )),
            circleStrokeWidth(switchCase(eq(get("near"), literal(true)), literal(4f), eq(get("best"), literal(true)), literal(3f), literal(2f))),
            circleRadius(interpolate(linear(), zoom(), stop(10, 16f), stop(16, 26f))),
        ),
    ) }
    guard(problems, "resource-icons") { s.addLayer(
        SymbolLayer("resource-icons", "resources").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.5f), stop(16, 0.75f))),
            iconOpacity(match(get("state"), literal(1f), stop("gathered", 0.55f), stop("thinned", 0.5f))),
            // State always; the node's name too once you're zoomed in close enough to read it.
            textField(step(zoom(), match(get("state"), literal(""), stop("gathered", "✓"), stop("new", "new")),
                stop(15, concat(get("name"), literal("\n"), match(get("state"), literal(""), stop("gathered", "✓ gathered"), stop("new", "new"), stop("thinned", "thinned")))))),
            textFont(FONT), textSize(12f), textOffset(arrayOf(0f, 1.6f)), textColor("#3F6B3A"), textHaloColor("#F4ECD8"), textHaloWidth(1.5f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textAllowOverlap(true),
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
    // Heat steps (ADR 0007): each ground texture in five tints, faded → rich, like the roads' five stages.
    for ((ground, tex) in HeatLayers.TEXTURES) {
        val base = loaded[tex] ?: continue
        for (step in 1..5) s.addImage("heat_${ground}_$step", heatTint(base, step))
    }
    // Capital medallions with the star (and monument, once raised) baked in, one image per tier.
    val star = loaded["badge_capital"] ?: return
    val monument = loaded["medal_monument"]
    for (tier in 0..6) {
        val medal = loaded["medal_tier_$tier"] ?: continue
        s.addImage("medal_tier_${tier}_cap", capitalMedal(medal, star, null))
        if (monument != null) s.addImage("medal_tier_${tier}_capmon", capitalMedal(medal, star, monument))
        // The capital with a banner you've bought flying from it.
        for (b in MapIcons.BANNERS) {
            val flag = loaded["banner_$b"] ?: continue
            s.addImage("medal_tier_${tier}_cap_b$b", capitalMedal(medal, star, null, flag))
            if (monument != null) s.addImage("medal_tier_${tier}_capmon_b$b", capitalMedal(medal, star, monument, flag))
        }
    }
}

/** A settlement medallion with the capital star at its upper right and, if given, a small monument at its upper left. */
private fun capitalMedal(medal: Bitmap, star: Bitmap, monument: Bitmap?, banner: Bitmap? = null): Bitmap {
    val pad = 26
    val out = Bitmap.createBitmap(medal.width + 2 * pad, medal.height + 2 * pad, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    c.drawBitmap(medal, pad.toFloat(), pad.toFloat(), paint)
    val sz = (medal.width * 0.42f)
    if (monument != null) c.drawBitmap(monument, null, RectF(2f, 2f, 2f + sz, 2f + sz * monument.height / monument.width), paint)
    c.drawBitmap(star, null, RectF(out.width - sz - 2f, 2f, out.width - 2f, 2f + sz), paint)
    if (banner != null) {
        val bh = medal.height * 0.5f; val bw = bh * banner.width / banner.height
        c.drawBitmap(banner, null, RectF(2f, out.height - bh - 2f, 2f + bw, out.height - 2f), paint)
    }
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

/** A ground texture at heat step 1–5: brightness, saturation and contrast from faded (1) to rich (5). */
private fun heatTint(src: Bitmap, step: Int): Bitmap {
    val (bright, sat, contrast) = listOf(Triple(1.30f, 0.28f, 1.0f), Triple(1.14f, 0.6f, 1.0f), Triple(1.0f, 1.0f, 1.0f),
        Triple(0.9f, 1.25f, 1.06f), Triple(0.8f, 1.45f, 1.14f))[step - 1]
    val cm = android.graphics.ColorMatrix().apply { setSaturation(sat) }
    val t = (1 - contrast) * 128f
    val scale = bright * contrast
    cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
        scale, 0f, 0f, 0f, t, 0f, scale, 0f, 0f, t, 0f, 0f, scale, 0f, t, 0f, 0f, 0f, 1f, 0f,
    )))
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(cm) })
    return out
}
