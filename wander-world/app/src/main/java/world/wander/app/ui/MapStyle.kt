package world.wander.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.height
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.exponential
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
import org.maplibre.android.style.layers.PropertyFactory.symbolSpacing
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.linePattern
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
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
    "explored", "green-places", "shore", "water-over", "fog", "fog-edge", "peek-edge", "peek", "roads-trail-edge", "roads-trail", "roads-dirt",
    "roads-lane-edge", "roads-rut-l", "roads-cobble-4", "roads-cobble-5", "roads-trees", "wild-1", "wild-2", "wild-3", "wild-gravel", "wild-cairns",
    "sand-places", "water-route-3", "water-light-1", "water-buoys",
    "road-names", "track", "waypoints", "landmark-icons", "waystones", "settlements", "settlement-labels",
    "resource-rings", "resource-icons", "me",
    "heat-hood", "heat-block", "heat-county", "region-links", "region-nodes", "buildings", "trader", "encounters", "building-cells", "building-art",
)

internal fun addWorldLayers(s: Style, problems: MutableList<String>) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    for (id in listOf("explored", "green-places", "sand-places", "shore", "water", "fog", "peek", "wild", "roads", "landmarks", "track", "waypoints", "waystones", "settlements", "resources", "me",
        "heat-block", "heat-hood", "heat-county", "region-nodes", "region-links", "buildings", "trader", "encounters", "building-cells")) s.addSource(GeoJsonSource(id, empty))

    // Explored ground: real terrain texture (0 A.D. grass / forest floor) laid OVER the base map's streets and
    // buildings, so inside explored land every street is drawn by its status (walked: trail…avenue; unwalked:
    // dashed), not by the base map. Under the fog the base map's faint streets still show the lay of the land.
    // Explored ground not reached by any path's heat (pockets, small places, waystones) is pale, like a heat ring's edge.
    val ground = FillLayer("explored", "explored").withProperties(fillPattern("heat_grass_0"), fillOpacity(1f))
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
            lineColor("#4A3A28"), lineOpacity(0.6f), lineDasharray(arrayOf(2f, 2f)),
            lineWidth(interpolate(exponential(2f), zoom(), stop(12, 3.5f), stop(17, 9f), stop(22, 9f * 32))),
        ),
    )
    s.addLayer(
        LineLayer("peek", "peek").withProperties(
            lineColor("#F2E6C8"), lineOpacity(0.9f), lineDasharray(arrayOf(3.2f, 3.2f)),
            lineWidth(interpolate(exponential(2f), zoom(), stop(12, 2f), stop(17, 5.6f), stop(22, 5.6f * 32))),
        ),
    )
    // 0.14.2: roads, your own paths and water routes are drawn at their real width: widths double with each zoom level
    // (like the ground), with a floor further out so they never vanish. Each stage is its own material and shape
    // (see docs/art/mockup_roads.png and the Legend). Widths below are at zoom 17 (street level), in dp.
    fun grow(at17: Float, min: Float): Expression =
        interpolate(exponential(2f), zoom(), stop(12, min), stop(17, at17), stop(22, at17 * 32))
    fun line(id: String, src: String, filter: Expression, vararg props: org.maplibre.android.style.layers.PropertyValue<*>) =
        guard(problems, id) { s.addLayer(LineLayer(id, src).withProperties(lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND), *props).also { it.setFilter(filter) }) }
    /** Roadside pairs (trees, cairns, buoys) along a line, scaled with it; [across] is the road's width plus margin, in dp at z17. */
    fun roadside(id: String, src: String, filter: Expression, icon: String, across: Float, every: Float, fromZoom: Float) =
        guard(problems, id) { s.addLayer(SymbolLayer(id, src).withProperties(
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE), iconImage(icon), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true), iconIgnorePlacement(true),
            // The pair's centres are 144 px apart in the image; scale so they sit just outside the road.
            iconSize(interpolate(exponential(2f), zoom(), stop(fromZoom, across / 144f / Math.pow(2.0, (17 - fromZoom).toDouble()).toFloat()), stop(17, across / 144f), stop(22, across / 144f * 32))),
            symbolSpacing(interpolate(exponential(2f), zoom(), stop(fromZoom, every / Math.pow(2.0, (17 - fromZoom).toDouble()).toFloat().coerceAtLeast(1f)), stop(17, every), stop(22, every * 32))),
        ).also { it.setFilter(filter); it.minZoom = fromZoom }) }
    fun level(n: Int) = eq(get("level"), literal(n))

    // Charted roads, five stages by distinct days walked: 1 Trail (trodden earth) · 2 Footpath (packed dirt) ·
    // 3 Lane (dirt, verges, wheel ruts) · 4 Cobbled road (cobbles between stone kerbs) · 5 Avenue (wide, kerbs, trees).
    val roadW = floatArrayOf(0f, 6f, 8f, 12f, 16f, 20f)
    val roadMin = floatArrayOf(0f, 1.5f, 2f, 2.5f, 3f, 3.5f)
    fun rw(n: Int, k: Float = 1f) = grow(roadW[n] * k, roadMin[n] * k)
    line("roads-trail-edge", "roads", level(1), lineColor("#8E6F48"), lineOpacity(0.5f), lineWidth(rw(1, 2f)), lineBlur(rw(1, 0.7f)))
    line("roads-trail", "roads", level(1), lineColor("#CDB088"), linePattern("road_dirt"), lineWidth(rw(1)), lineBlur(rw(1, 0.15f)))
    line("roads-path-edge", "roads", level(2), lineColor("#6E5A37"), lineOpacity(0.55f), lineWidth(rw(2, 1.6f)), lineBlur(rw(2, 0.35f)))
    line("roads-lane-edge", "roads", level(3), lineColor("#5E4E2E"), lineOpacity(0.8f), lineWidth(rw(3, 1.3f)), lineBlur(rw(3, 0.12f)))
    line("roads-dirt", "roads", all(gte(get("level"), literal(2)), lt(get("level"), literal(4))),
        lineColor("#D2B88A"), linePattern("road_dirt"), lineWidth(interpolate(exponential(2f), zoom(),
            stop(12, match(get("level"), literal(roadMin[3]), stop(2, roadMin[2]))),
            stop(17, match(get("level"), literal(roadW[3]), stop(2, roadW[2]))),
            stop(22, match(get("level"), literal(roadW[3] * 32), stop(2, roadW[2] * 32))))))
    for ((id, k) in listOf("roads-rut-l" to -0.22f, "roads-rut-r" to 0.22f)) {
        line(id, "roads", level(3), lineColor("#5A4428"), lineOpacity(0.6f), lineWidth(rw(3, 0.11f)), lineOffset(rw(3, k)))
    }
    for (n in 4..5) {
        line("roads-kerb-$n", "roads", level(n), lineColor("#463F3A"), lineWidth(rw(n, 1.16f)))
        line("roads-kerbstone-$n", "roads", level(n), lineColor("#C9C3B4"), lineWidth(rw(n, 1.08f)))
        line("roads-cobble-$n", "roads", level(n), lineColor("#8E8A80"), linePattern("road_cobble"), lineWidth(rw(n, 0.88f)))
    }
    roadside("roads-trees", "roads", level(5), "roadside_trees", roadW[5] + 22f, 28f, 14f)

    // Your own paths: narrower and rougher than roads. 1 Faint track (scuffed grass) · 2 Trodden path ·
    // 3 Dirt track · 4 Gravel trail · 5 Ranger trail (gravel, cairns either side).
    val wildW = floatArrayOf(0f, 4f, 5f, 6f, 7f, 8f)
    val wildMin = floatArrayOf(0f, 1.2f, 1.5f, 2f, 2.5f, 3f)
    fun ww(n: Int, k: Float = 1f) = grow(wildW[n] * k, wildMin[n] * k)
    line("wild-1", "wild", level(1), lineColor("#B9A577"), lineOpacity(0.7f), lineWidth(ww(1)), lineBlur(ww(1, 0.6f)), lineDasharray(arrayOf(1.2f, 0.8f)))
    line("wild-2-edge", "wild", level(2), lineColor("#7E6644"), lineOpacity(0.45f), lineWidth(ww(2, 1.6f)), lineBlur(ww(2, 0.5f)))
    line("wild-2", "wild", level(2), lineColor("#C8AE82"), lineWidth(ww(2)), lineBlur(ww(2, 0.2f)))
    line("wild-3", "wild", level(3), lineColor("#C8AC7E"), linePattern("road_dirt"), lineWidth(ww(3)), lineBlur(ww(3, 0.1f)))
    line("wild-edge", "wild", gte(get("level"), literal(4)), lineColor("#6E5638"), lineOpacity(0.6f),
        lineWidth(interpolate(exponential(2f), zoom(), stop(12, wildMin[4] * 1.3f), stop(17, match(get("level"), literal(wildW[5] * 1.3f), stop(4, wildW[4] * 1.3f))), stop(22, wildW[5] * 1.3f * 32))))
    line("wild-gravel", "wild", gte(get("level"), literal(4)), lineColor("#D8C9A6"), linePattern("ground_sand"),
        lineWidth(interpolate(exponential(2f), zoom(), stop(12, wildMin[4]), stop(17, match(get("level"), literal(wildW[5]), stop(4, wildW[4]))), stop(22, wildW[5] * 32))))
    roadside("wild-cairns", "wild", level(5), "roadside_cairns", wildW[5] + 10f, 40f, 14.5f)

    // Water routes you paddle: 1 wake · 2 paddle line · 3 paddle route · 4 marked route (buoys) · 5 waterway.
    val waterW = floatArrayOf(0f, 4f, 5f, 7f, 9f, 12f)
    val waterMin = floatArrayOf(0f, 1.2f, 1.5f, 2f, 2.5f, 3f)
    fun aw(n: Int, k: Float = 1f) = grow(waterW[n] * k, waterMin[n] * k)
    for (n in 1..2) line("water-light-$n", "water", level(n), lineColor(if (n == 1) "#E4F3F9" else "#CBE6F2"), lineOpacity(0.85f),
        lineWidth(aw(n)), lineBlur(aw(n, 0.4f)), lineDasharray(arrayOf(1f, 1.4f)))
    for (n in 3..5) {
        line("water-edge-$n", "water", level(n), lineColor("#3E7FA3"), lineOpacity(0.55f), lineWidth(aw(n, 1.4f)), lineBlur(aw(n, 0.25f)))
        line("water-route-$n", "water", level(n), lineColor(if (n == 3) "#CBE6F2" else "#A6D3E8"), lineWidth(aw(n)), lineBlur(aw(n, 0.08f)))
    }
    roadside("water-buoys", "water", gte(get("level"), literal(4)), "roadside_buoys", waterW[5] + 14f, 60f, 14f)

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
            // 0.13: hex settlements (tools/gen_hex_icons.py), about twice the old size.
            iconSize(interpolate(linear(), zoom(), stop(8, 0.55f), stop(12, 0.75f), stop(15, 0.95f), stop(17, 1.2f))),
            iconAnchor(Property.ICON_ANCHOR_CENTER),
        ).apply { minZoom = 8.5f },
    )
    guard(problems, "settlement-labels") { s.addLayer(
        SymbolLayer("settlement-labels", "settlements").withProperties(
            textField(get("label")), textFont(FONT), textSize(interpolate(linear(), zoom(), stop(10, 11f), stop(16, 15f))),
            textColor(INK), textHaloColor("#F4ECD8"), textHaloWidth(2f), textAllowOverlap(true), textIgnorePlacement(false),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 3.4f)),
        ).apply { minZoom = 8.5f },
    ) }
    // Buildings at each settlement (latest of each upgrade chain); a market opens the shop when tapped.
    // 0.13: framed hex badges further out; up close each building is its own developed hex on the ground, drawn to
    // the hex's real size (art grows ×2 per zoom level, like the map).
    guard(problems, "buildings") { s.addLayer(
        SymbolLayer("buildings", "buildings").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(12, 0.0f), stop(13, 0.45f), stop(15, 0.65f), stop(16.5f, 0.8f))),
        ).apply { maxZoom = 16.5f },
    ) }
    guard(problems, "building-cells") {
        s.addLayer(FillLayer("building-cells", "building-cells").withProperties(fillPattern(get("pattern")), fillOpacity(1f))
            .apply { minZoom = 16.5f })
        s.addLayer(SymbolLayer("building-art", "buildings").withProperties(
            iconImage(get("art")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(exponential(2f), zoom(), stop(16.5f, 0.26f), stop(20, 2.9f))),
        ).apply { minZoom = 16.5f })
    }
    // The travelling trader, by the village you visited last. Tap to trade.
    guard(problems, "trader") { s.addLayer(
        SymbolLayer("trader", "trader").withProperties(
            iconImage("trader"), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.55f), stop(16, 0.9f), stop(18, 1.2f))),
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
    guard(problems, "encounters") { s.addLayer(
        SymbolLayer("encounters", "encounters").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.55f), stop(16, 0.9f), stop(18, 1.2f))),
            textField(get("label")), textFont(FONT), textSize(12f), textColor("#6B4A1F"), textHaloColor("#F4ECD8"), textHaloWidth(1.5f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 0.3f)), textAllowOverlap(true), textOptional(true),
        ),
    ) }
    // Resource nodes: fixed spots on places and streets, drawn in the map so they stay pinned to the ground.
    // Ring: gold = fresh, bright gold + thicker = the one the arrow points to, green = you're in its zone,
    // grey-green with a tick = gathered today, faded = thinned.
    // 0.13: a hex frame whose colour is the state (gold best, green in its zone, tan new, pale gathered/thinned).
    guard(problems, "resource-rings") { s.addLayer(
        SymbolLayer("resource-rings", "resources").withProperties(
            iconImage(get("frame")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.75f), stop(16, 1.25f))),
        ),
    ) }
    guard(problems, "resource-icons") { s.addLayer(
        SymbolLayer("resource-icons", "resources").withProperties(
            iconImage(get("icon")), iconAllowOverlap(true), iconIgnorePlacement(true), iconAnchor(Property.ICON_ANCHOR_CENTER),
            iconSize(interpolate(linear(), zoom(), stop(10, 0.7f), stop(16, 1.15f))),
            iconOpacity(match(get("state"), literal(1f), stop("gathered", 0.55f), stop("thinned", 0.5f))),
            // State always; the node's name too once you're zoomed in close enough to read it.
            textField(step(zoom(), match(get("state"), literal(""), stop("gathered", "✓"), stop("new", "new")),
                stop(15, concat(get("name"), literal("\n"), match(get("state"), literal(""), stop("gathered", "✓ gathered"), stop("new", "new"), stop("thinned", "thinned")))))),
            textFont(FONT), textSize(13f), textOffset(arrayOf(0f, 2.6f)), textColor("#3F6B3A"), textHaloColor("#F4ECD8"), textHaloWidth(1.5f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textAllowOverlap(true),
        ),
    ) }
    s.addLayer(
        SymbolLayer("me", "me").withProperties(
            iconImage("wanderer"), iconSize(interpolate(linear(), zoom(), stop(10, 0.45f), stop(16, 0.7f), stop(18, 0.9f))),
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
        for (step in 0..5) s.addImage("heat_${ground}_$step", heatTint(base, step))
    }
    // 0.13: settlements are pre-made hex images (hex_tier_N[_cap|_capmon], tools/gen_hex_icons.py); 0.14: the worn
    // banner is composited onto the capital's hex at runtime (capitalComposite).
}

/**
 * The capital's hex with the worn banner planted on its right shoulder (0.14), added to the style once per combination.
 * The canvas is widened on both sides so the hex stays centred: the symbol's anchor is still the capital's position.
 */
internal fun capitalComposite(s: Style, context: Context, base: String, banner: String): String {
    val id = MapLayersBuilder.capitalIcon(base, banner)
    if (s.getImage(id) != null) return id
    val opts = BitmapFactory.Options().apply { inScaled = false }
    val hex = MapIcons.images[base]?.let { BitmapFactory.decodeResource(context.resources, it, opts) } ?: return id
    val flag = MapIcons.images["banner_$banner"]?.let { BitmapFactory.decodeResource(context.resources, it, opts) } ?: return id
    val side = hex.width * 2 / 5
    val out = Bitmap.createBitmap(hex.width + 2 * side, hex.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawBitmap(hex, side.toFloat(), 0f, null)
    val fh = hex.height * 0.5f
    val fw = fh * flag.width / flag.height
    val left = side + hex.width * 0.88f - fw / 2
    val top = hex.height * 0.08f
    c.drawBitmap(flag, null, android.graphics.RectF(left, top, left + fw, top + fh), Paint().apply { isFilterBitmap = true })
    s.addImage(id, out)
    return id
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

/**
 * A ground texture at heat level 0–5 (0.12.2): 0 is pale explored ground at the edge of a path's reach, 1 the normal
 * texture (a hex walked once), 2–5 progressively richer, so the more you walk somewhere the greener it gets.
 */
internal val HEAT_TINTS = listOf(Triple(1.30f, 0.28f, 1.0f), Triple(1.0f, 1.0f, 1.0f), Triple(0.95f, 1.15f, 1.03f),
    Triple(0.9f, 1.3f, 1.06f), Triple(0.85f, 1.42f, 1.1f), Triple(0.8f, 1.55f, 1.14f))

private fun heatTint(src: Bitmap, step: Int): Bitmap {
    val (bright, sat, contrast) = HEAT_TINTS[step]
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
