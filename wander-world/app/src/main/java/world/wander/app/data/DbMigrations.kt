package world.wander.app.data

/**
 * Every database upgrade, as plain SQL by starting version. Kept free of Android classes so
 * `MigrationTest` can replay exactly these statements on a real SQLite database built from the exported
 * schemas in `app/schemas/`. A schema change adds a step here, bumps `WanderDb` version and commits the new
 * schema JSON. Never drop player data (walks, decisions); derived data may be cleared and rebuilt.
 */
object DbMigrations {
    /** Statements that take the database from version `from` to `from + 1`. */
    val steps: Map<Int, List<String>> = mapOf(
        // v2 adds walked trails; existing walks are re-processed once so their trails exist too.
        1 to listOf(
            "CREATE TABLE IF NOT EXISTS `trail` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `geometry` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_trail_segmentId` ON `trail` (`segmentId`)",
            "UPDATE segment SET processed = 0 WHERE endMs IS NOT NULL",
        ),
        // v3 adds named areas (landmarks) and your own paths; walks are re-processed once to find them.
        2 to listOf(
            "CREATE TABLE IF NOT EXISTS `wild_trail` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `geometry` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_wild_trail_segmentId` ON `wild_trail` (`segmentId`)",
            "CREATE TABLE IF NOT EXISTS `trodden` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `cell` TEXT NOT NULL, `day` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_trodden_segmentId` ON `trodden` (`segmentId`)",
            "CREATE TABLE IF NOT EXISTS `landmark` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `geometry` TEXT NOT NULL, `areaM2` REAL NOT NULL, `tile` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_landmark_tile` ON `landmark` (`tile`)",
            "CREATE TABLE IF NOT EXISTS `landmark_tile` (`tile` TEXT NOT NULL, `fetchedMs` INTEGER NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`tile`))",
            "CREATE TABLE IF NOT EXISTS `landmark_visit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `landmarkId` TEXT NOT NULL, `day` INTEGER NOT NULL, `seconds` REAL NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_landmark_visit_segmentId` ON `landmark_visit` (`segmentId`)",
            "UPDATE segment SET processed = 0 WHERE endMs IS NOT NULL",
        ),
        // v4 adds arrivals (waystones, regions); walks are re-processed once to find them.
        3 to listOf(
            "CREATE TABLE IF NOT EXISTS `arrival` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `lat` REAL NOT NULL, `lng` REAL NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_arrival_segmentId` ON `arrival` (`segmentId`)",
            "UPDATE segment SET processed = 0 WHERE endMs IS NOT NULL",
        ),
        // v5: paddling (water routes, wake, paddled distance); places re-fetched for the new categories.
        4 to listOf(
            "CREATE TABLE IF NOT EXISTS `water_trail` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `day` INTEGER NOT NULL, `geometry` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_water_trail_segmentId` ON `water_trail` (`segmentId`)",
            "CREATE TABLE IF NOT EXISTS `wake` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `cell` TEXT NOT NULL, `day` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_wake_segmentId` ON `wake` (`segmentId`)",
            "ALTER TABLE `day_movement` ADD COLUMN `waterMeters` REAL NOT NULL DEFAULT 0",
            "DELETE FROM `landmark_tile`",
            "DELETE FROM `landmark`",
            "UPDATE segment SET processed = 0 WHERE endMs IS NOT NULL",
        ),
        // v6: water areas and coast flags (automatic paddling), project decisions (economy).
        5 to listOf(
            "CREATE TABLE IF NOT EXISTS `water_area` (`id` TEXT NOT NULL, `tile` TEXT NOT NULL, `geometry` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_water_area_tile` ON `water_area` (`tile`)",
            "CREATE TABLE IF NOT EXISTS `water_tile` (`tile` TEXT NOT NULL, `coastal` INTEGER NOT NULL, PRIMARY KEY(`tile`))",
            "CREATE TABLE IF NOT EXISTS `project_decision` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, `areaId` TEXT NOT NULL, `projectId` TEXT NOT NULL, `approve` INTEGER NOT NULL, `madeMs` INTEGER NOT NULL)",
            "DELETE FROM `landmark_tile`",
            "UPDATE segment SET processed = 0 WHERE endMs IS NOT NULL",
        ),
        // v7: terrain (beaches, sand, coastline) so shores draw as sand; places re-fetched to collect it.
        6 to listOf(
            "CREATE TABLE IF NOT EXISTS `terrain_area` (`id` TEXT NOT NULL, `tile` TEXT NOT NULL, `kind` TEXT NOT NULL, `geometry` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_terrain_area_tile` ON `terrain_area` (`tile`)",
            "DELETE FROM `landmark_tile`",
        ),
        // v8: shore walking (food is gathered along shores, like stone along roads); walks re-processed via PROCESSING_VERSION.
        7 to listOf(
            "CREATE TABLE IF NOT EXISTS `shore` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `segmentId` INTEGER NOT NULL, `cell` TEXT NOT NULL, `day` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_shore_segmentId` ON `shore` (`segmentId`)",
        ),
        // v9: no schema change; places re-fetched to collect fields, orchards and gardens (inland food) and markets.
        8 to listOf(
            "DELETE FROM `landmark_tile`",
        ),
        // v10: purchases from the travelling trader and markets (resources and cosmetics), replayed like decisions.
        9 to listOf(
            "CREATE TABLE IF NOT EXISTS `purchase` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `day` INTEGER NOT NULL, `seller` TEXT NOT NULL, `item` TEXT NOT NULL, `qty` INTEGER NOT NULL, `madeMs` INTEGER NOT NULL)",
        ),
        // v11 keeps every walk's points, packed per walk (review H1); nothing is deleted any more.
        10 to listOf(
            "CREATE TABLE IF NOT EXISTS `track_archive` (`segmentId` INTEGER NOT NULL, `data` BLOB NOT NULL, `count` INTEGER NOT NULL, PRIMARY KEY(`segmentId`))",
        ),
    )

    const val LATEST = 11
}
