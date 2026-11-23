package world.wander.engine

/** The development story's data (0.13): encounters, looks, achievements. Rules live in sim/Story.kt. */
object Story {
    /** Rare looks: never sold; from encounters and feats. */
    val RARE_BANNERS = listOf("ironbound", "spring", "summer", "harvest", "winter", "tidewalker", "evergreen", "wonder_1", "wonder_2", "wonder_3", "wonder_4")
    val RARE_OUTFITS = listOf("patchwork", "pathfinder", "wayfarer", "laurel", "cartographer")
    val LOOK_NAMES = mapOf(
        "banner_ironbound" to "Ironbound banner", "banner_spring" to "Blossom banner", "banner_summer" to "Midsummer banner",
        "banner_harvest" to "Harvest banner", "banner_winter" to "Frost banner", "banner_tidewalker" to "Tidewalker banner",
        "banner_evergreen" to "Evergreen banner", "banner_wonder_1" to "Foundation banner", "banner_wonder_2" to "Rampart banner",
        "banner_wonder_3" to "Spire banner", "banner_wonder_4" to "Crown banner",
        "outfit_patchwork" to "Patchwork cloak", "outfit_pathfinder" to "Pathfinder cloak", "outfit_wayfarer" to "Wayfarer coat",
        "outfit_laurel" to "Laurel robe", "outfit_cartographer" to "Cartographer's hat",
    )
    /** How to earn each rare look, shown where a price would be. */
    val LOOK_HOW = mapOf(
        "banner_ironbound" to "Trade with a collector", "outfit_patchwork" to "Trade with a lost peddler",
        "banner_spring" to "A spring festival", "banner_summer" to "A summer festival", "banner_harvest" to "An autumn festival",
        "banner_winter" to "A winter festival", "banner_tidewalker" to "Paddle 10 km", "banner_evergreen" to "Discover 10 woods or parks",
        "outfit_pathfinder" to "Chart 25 trails", "outfit_wayfarer" to "Find 5 waystones", "outfit_cartographer" to "Chart 250 roads",
        "outfit_laurel" to "12 weeks in a row at your goal",
        "banner_wonder_1" to "Wonder: Foundation", "banner_wonder_2" to "Wonder: Walls", "banner_wonder_3" to "Wonder: Spire",
        "banner_wonder_4" to "Wonder: Crown",
    )

    /** Achievement ladders: id, name, unit, bronze/silver/gold targets, and the rare look at a tier (1-based). */
    data class Ladder(val id: String, val name: String, val unit: String, val targets: List<Double>, val reward: String? = null, val rewardAt: Int = -1)
    val LADDERS = listOf(
        Ladder("long_road", "Long road", "km", listOf(50.0, 250.0, 1000.0)),
        Ladder("cartographer", "Cartographer", "roads", listOf(50.0, 250.0, 1000.0), "outfit_cartographer", 2),
        Ladder("discoverer", "Discoverer", "places", listOf(10.0, 25.0, 100.0)),
        Ladder("explorer", "Explorer", "hexes", listOf(500.0, 2500.0, 10000.0)),
        Ladder("pathfinder", "Pathfinder", "trails", listOf(10.0, 25.0, 100.0), "outfit_pathfinder", 2),
        Ladder("founder", "Founder", "settlements", listOf(1.0, 3.0, 10.0)),
        Ladder("wayfarer", "Wayfarer", "waystones", listOf(1.0, 5.0, 20.0), "outfit_wayfarer", 2),
        Ladder("tidewalker", "Tidewalker", "km paddled", listOf(2.0, 10.0, 50.0), "banner_tidewalker", 2),
        Ladder("woodsman", "Woodsman", "woods and parks", listOf(3.0, 10.0, 30.0), "banner_evergreen", 2),
        Ladder("steadfast", "Steadfast", "weeks in a row at your goal", listOf(4.0, 12.0, 26.0), "outfit_laurel", 2),
    )

    fun season(month: Int): String = when (month) { 3, 4, 5 -> "spring"; 6, 7, 8 -> "summer"; 9, 10, 11 -> "harvest"; else -> "winter" }
}
