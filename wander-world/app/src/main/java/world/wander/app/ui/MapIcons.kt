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
        // You
        "wanderer" to R.drawable.sprite_wanderer, "wanderer_paddle" to R.drawable.sprite_wanderer_paddle,
    )

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
