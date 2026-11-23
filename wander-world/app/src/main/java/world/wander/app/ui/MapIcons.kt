package world.wander.app.ui

import world.wander.app.R

/**
 * Every image the map uses, by name, and how a place's category maps to its medallion. Shared by the map,
 * the Atlas and the legend so they never disagree. oad_* images are adapted from 0 A.D. (CC BY-SA 3.0).
 */
object MapIcons {
    val images: Map<String, Int> = mapOf(
        // Settlements
        "medal_tier_0" to R.drawable.oad_medal_tier_0, "medal_tier_1" to R.drawable.oad_medal_tier_1,
        "medal_tier_2" to R.drawable.oad_medal_tier_2, "medal_tier_3" to R.drawable.oad_medal_tier_3,
        "medal_tier_4" to R.drawable.oad_medal_tier_4, "medal_tier_5" to R.drawable.oad_medal_tier_5,
        "medal_tier_6" to R.drawable.oad_medal_tier_6, "badge_capital" to R.drawable.badge_capital,
        // Markers
        "medal_waypoint" to R.drawable.oad_medal_waypoint,
        "medal_waystone" to R.drawable.medal_waystone, "medal_waystone_great" to R.drawable.medal_waystone_great,
        // Places
        "medal_place_park" to R.drawable.oad_medal_place_park, "medal_place_wood" to R.drawable.oad_medal_place_wood,
        "medal_place_store" to R.drawable.oad_medal_place_store, "medal_place_generic" to R.drawable.oad_medal_place_generic,
        "medal_place_water" to R.drawable.oad_medal_place_water, "medal_place_beach" to R.drawable.oad_medal_place_beach,
        "medal_poi_hospital" to R.drawable.medal_poi_hospital, "medal_poi_government" to R.drawable.oad_medal_poi_government,
        "medal_poi_emergency" to R.drawable.oad_medal_poi_emergency, "medal_poi_civic" to R.drawable.oad_medal_poi_civic,
        "medal_poi_school" to R.drawable.oad_medal_poi_school, "medal_poi_library" to R.drawable.oad_medal_poi_library,
        "medal_poi_worship" to R.drawable.oad_medal_poi_worship, "medal_poi_culture" to R.drawable.oad_medal_poi_culture,
        "medal_poi_sports" to R.drawable.oad_medal_poi_sports, "medal_poi_transport" to R.drawable.oad_medal_poi_transport,
        "medal_poi_harbour" to R.drawable.oad_medal_poi_harbour, "medal_poi_lighthouse" to R.drawable.oad_medal_poi_lighthouse,
        "medal_poi_historic" to R.drawable.oad_medal_poi_historic,
        // Ground and roads
        "ground_grass" to R.drawable.oad_ground_grass, "ground_forest" to R.drawable.oad_ground_forest,
        "ground_sand" to R.drawable.oad_ground_sand, "water_ripple" to R.drawable.water_ripple,
        "water_sea" to R.drawable.oad_water_sea, "ground_sand_wet" to R.drawable.oad_ground_sand_wet,
        "road_dirt" to R.drawable.oad_road_dirt, "road_cobble" to R.drawable.oad_road_cobble,
        "fog_parchment" to R.drawable.fog_parchment,
        // Economy
        "medal_gold" to R.drawable.oad_medal_gold, "medal_monument" to R.drawable.oad_medal_monument,
        "res_wood" to R.drawable.oad_res_wood, "res_food" to R.drawable.oad_res_food,
        "res_stone" to R.drawable.oad_res_stone, "res_metal" to R.drawable.oad_res_metal,
        // You (and your outfits)
        "wanderer" to R.drawable.sprite_wanderer, "wanderer_paddle" to R.drawable.sprite_wanderer_paddle,
        "wanderer_crimson" to R.drawable.sprite_wanderer_crimson, "wanderer_paddle_crimson" to R.drawable.sprite_wanderer_paddle_crimson,
        "wanderer_navy" to R.drawable.sprite_wanderer_navy, "wanderer_paddle_navy" to R.drawable.sprite_wanderer_paddle_navy,
        "wanderer_ochre" to R.drawable.sprite_wanderer_ochre, "wanderer_paddle_ochre" to R.drawable.sprite_wanderer_paddle_ochre,
        "wanderer_hat" to R.drawable.sprite_wanderer_hat, "wanderer_paddle_hat" to R.drawable.sprite_wanderer_paddle_hat,
        // Villages (0.11): the trader, banners, and buildings drawn at settlements
        "trader" to R.drawable.sprite_trader,
        "banner_crimson" to R.drawable.banner_crimson, "banner_royal" to R.drawable.banner_royal, "banner_forest" to R.drawable.banner_forest,
        "banner_gold" to R.drawable.banner_gold, "banner_violet" to R.drawable.banner_violet,
        "bldg_campfire" to R.drawable.bldg_campfire, "bldg_well" to R.drawable.bldg_well,
        "bldg_fountain" to R.drawable.bldg_fountain, "bldg_bridge" to R.drawable.bldg_bridge,
        "bldg_meeting_hall" to R.drawable.oad_medal_bldg_meeting_hall, "bldg_town_hall" to R.drawable.oad_medal_bldg_town_hall,
        "bldg_market" to R.drawable.oad_medal_bldg_market, "bldg_market_hall" to R.drawable.oad_medal_bldg_market_hall,
        "bldg_inn" to R.drawable.oad_medal_bldg_inn, "bldg_pier" to R.drawable.oad_medal_bldg_pier,
        "bldg_lighthouse" to R.drawable.oad_medal_bldg_lighthouse, "bldg_walls" to R.drawable.oad_medal_bldg_walls,
        "bldg_great_hall" to R.drawable.oad_medal_bldg_great_hall,
        "ground_farm" to R.drawable.oad_ground_farm,
        // 0.13 rare looks (encounters and feats)
        "banner_ironbound" to R.drawable.banner_ironbound, "banner_spring" to R.drawable.banner_spring,
        "banner_summer" to R.drawable.banner_summer, "banner_harvest" to R.drawable.banner_harvest,
        "banner_winter" to R.drawable.banner_winter, "banner_tidewalker" to R.drawable.banner_tidewalker,
        "banner_evergreen" to R.drawable.banner_evergreen, "banner_wonder_1" to R.drawable.banner_wonder_1,
        "banner_wonder_2" to R.drawable.banner_wonder_2, "banner_wonder_3" to R.drawable.banner_wonder_3,
        "banner_wonder_4" to R.drawable.banner_wonder_4,
        "wanderer_patchwork" to R.drawable.sprite_wanderer_patchwork, "wanderer_paddle_patchwork" to R.drawable.sprite_wanderer_paddle_patchwork,
        "wanderer_pathfinder" to R.drawable.sprite_wanderer_pathfinder, "wanderer_paddle_pathfinder" to R.drawable.sprite_wanderer_paddle_pathfinder,
        "wanderer_wayfarer" to R.drawable.sprite_wanderer_wayfarer, "wanderer_paddle_wayfarer" to R.drawable.sprite_wanderer_paddle_wayfarer,
        "wanderer_laurel" to R.drawable.sprite_wanderer_laurel, "wanderer_paddle_laurel" to R.drawable.sprite_wanderer_paddle_laurel,
        "wanderer_cartographer" to R.drawable.sprite_wanderer_cartographer, "wanderer_paddle_cartographer" to R.drawable.sprite_wanderer_paddle_cartographer,
        // 0.13 hex symbols (tools/gen_hex_icons.py)
        "bldg_hex_bridge" to R.drawable.bldg_hex_bridge,
        "bldg_hex_campfire" to R.drawable.bldg_hex_campfire,
        "bldg_hex_forager_cache" to R.drawable.bldg_hex_forager_cache,
        "bldg_hex_fountain" to R.drawable.bldg_hex_fountain,
        "bldg_hex_great_hall" to R.drawable.bldg_hex_great_hall,
        "bldg_hex_guildhall" to R.drawable.bldg_hex_guildhall,
        "bldg_hex_harbour" to R.drawable.bldg_hex_harbour,
        "bldg_hex_inn" to R.drawable.bldg_hex_inn,
        "bldg_hex_lean_to" to R.drawable.bldg_hex_lean_to,
        "bldg_hex_library" to R.drawable.bldg_hex_library,
        "bldg_hex_lighthouse" to R.drawable.bldg_hex_lighthouse,
        "bldg_hex_market" to R.drawable.bldg_hex_market,
        "bldg_hex_market_hall" to R.drawable.bldg_hex_market_hall,
        "bldg_hex_meeting_hall" to R.drawable.bldg_hex_meeting_hall,
        "bldg_hex_mill" to R.drawable.bldg_hex_mill,
        "bldg_hex_pier" to R.drawable.bldg_hex_pier,
        "bldg_hex_shrine" to R.drawable.bldg_hex_shrine,
        "bldg_hex_signpost" to R.drawable.bldg_hex_signpost,
        "bldg_hex_town_hall" to R.drawable.bldg_hex_town_hall,
        "bldg_hex_vegetable_patch" to R.drawable.bldg_hex_vegetable_patch,
        "bldg_hex_walls" to R.drawable.bldg_hex_walls,
        "bldg_hex_well" to R.drawable.bldg_hex_well,
        "bldg_hex_wonder_1" to R.drawable.bldg_hex_wonder_1,
        "bldg_hex_wonder_2" to R.drawable.bldg_hex_wonder_2,
        "bldg_hex_wonder_3" to R.drawable.bldg_hex_wonder_3,
        "bldg_hex_wonder_4" to R.drawable.bldg_hex_wonder_4,
        "bldg_hex_woodshed" to R.drawable.bldg_hex_woodshed,
        "cellart_bridge" to R.drawable.cellart_bridge,
        "cellart_campfire" to R.drawable.cellart_campfire,
        "cellart_forager_cache" to R.drawable.cellart_forager_cache,
        "cellart_fountain" to R.drawable.cellart_fountain,
        "cellart_great_hall" to R.drawable.cellart_great_hall,
        "cellart_guildhall" to R.drawable.cellart_guildhall,
        "cellart_harbour" to R.drawable.cellart_harbour,
        "cellart_inn" to R.drawable.cellart_inn,
        "cellart_lean_to" to R.drawable.cellart_lean_to,
        "cellart_library" to R.drawable.cellart_library,
        "cellart_lighthouse" to R.drawable.cellart_lighthouse,
        "cellart_market" to R.drawable.cellart_market,
        "cellart_market_hall" to R.drawable.cellart_market_hall,
        "cellart_meeting_hall" to R.drawable.cellart_meeting_hall,
        "cellart_mill" to R.drawable.cellart_mill,
        "cellart_pier" to R.drawable.cellart_pier,
        "cellart_shrine" to R.drawable.cellart_shrine,
        "cellart_signpost" to R.drawable.cellart_signpost,
        "cellart_town_hall" to R.drawable.cellart_town_hall,
        "cellart_walls" to R.drawable.cellart_walls,
        "cellart_well" to R.drawable.cellart_well,
        "cellart_wonder_1" to R.drawable.cellart_wonder_1,
        "cellart_wonder_2" to R.drawable.cellart_wonder_2,
        "cellart_wonder_3" to R.drawable.cellart_wonder_3,
        "cellart_wonder_4" to R.drawable.cellart_wonder_4,
        "cellart_woodshed" to R.drawable.cellart_woodshed,
        "hex_tier_0" to R.drawable.hex_tier_0,
        "hex_tier_0_cap" to R.drawable.hex_tier_0_cap,
        "hex_tier_0_capmon" to R.drawable.hex_tier_0_capmon,
        "hex_tier_1" to R.drawable.hex_tier_1,
        "hex_tier_1_cap" to R.drawable.hex_tier_1_cap,
        "hex_tier_1_capmon" to R.drawable.hex_tier_1_capmon,
        "hex_tier_2" to R.drawable.hex_tier_2,
        "hex_tier_2_cap" to R.drawable.hex_tier_2_cap,
        "hex_tier_2_capmon" to R.drawable.hex_tier_2_capmon,
        "hex_tier_3" to R.drawable.hex_tier_3,
        "hex_tier_3_cap" to R.drawable.hex_tier_3_cap,
        "hex_tier_3_capmon" to R.drawable.hex_tier_3_capmon,
        "hex_tier_4" to R.drawable.hex_tier_4,
        "hex_tier_4_cap" to R.drawable.hex_tier_4_cap,
        "hex_tier_4_capmon" to R.drawable.hex_tier_4_capmon,
        "hex_tier_5" to R.drawable.hex_tier_5,
        "hex_tier_5_cap" to R.drawable.hex_tier_5_cap,
        "hex_tier_5_capmon" to R.drawable.hex_tier_5_capmon,
        "hex_tier_6" to R.drawable.hex_tier_6,
        "hex_tier_6_cap" to R.drawable.hex_tier_6_cap,
        "hex_tier_6_capmon" to R.drawable.hex_tier_6_capmon,
        "hexframe_best" to R.drawable.hexframe_best,
        "hexframe_gathered" to R.drawable.hexframe_gathered,
        "hexframe_near" to R.drawable.hexframe_near,
        "hexframe_new" to R.drawable.hexframe_new,
        "hexframe_plain" to R.drawable.hexframe_plain,
        "hexframe_thinned" to R.drawable.hexframe_thinned,
    )

    /** The figure standing on the map for each kind of encounter (0.13; reusing the wanderer and trader art). */
    val ENCOUNTER_ICONS = mapOf("bard" to "wanderer_hat", "courier" to "wanderer_navy", "lost_pet" to "wanderer_wayfarer",
        "peddler" to "wanderer_patchwork", "collector" to "trader", "festival" to "trader")

    val BANNERS = listOf("crimson", "royal", "forest", "gold", "violet") + world.wander.engine.Story.RARE_BANNERS
    val OUTFITS = listOf("crimson", "navy", "ochre", "hat") + world.wander.engine.Story.RARE_OUTFITS

    val WOODED = setOf("wood", "forest", "nature_reserve", "wetland", "heath")
    val GREEN = setOf("park", "garden", "common", "recreation_ground", "village_green", "meadow", "grassland",
        "dog_park", "golf_course", "playground", "cemetery")

    /** Medallion name for a place category (see Overpass.categorize). */
    fun placeIcon(kind: String?): String = when (kind) {
        in WOODED -> "medal_place_wood"
        in GREEN -> "medal_place_park"
        "beach" -> "medal_place_beach"
        "water" -> "medal_place_water"
        "store" -> "medal_place_store"
        "market" -> "medal_poi_civic"
        "community_garden" -> "medal_place_park"
        "hospital", "government", "emergency", "civic", "school", "library", "worship", "culture", "sports",
        "transport", "harbour", "lighthouse", "historic" -> "medal_poi_$kind"
        else -> "medal_place_generic"
    }

    fun drawable(name: String): Int = images[name] ?: R.drawable.oad_medal_place_generic
}
