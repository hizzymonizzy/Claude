package world.wander.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Replays every database upgrade on a real SQLite database. For each starting version: build that version's
 * schema from the exported Room schema (app/schemas/), fill it with player data, run [DbMigrations] up to the
 * latest version, then check that the result has exactly the latest schema (what Room validates on open) and
 * that the player's walks, points and decisions are all still there.
 */
class MigrationTest {
    private val schemaDir = File("schemas/world.wander.app.data.WanderDb")

    private fun schema(version: Int) = JSONObject(File(schemaDir, "$version.json").readText()).getJSONObject("database")

    private fun create(version: Int): Connection {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        val db = schema(version)
        val entities = db.getJSONArray("entities")
        c.createStatement().use { st ->
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                st.execute(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val idx = e.optJSONArray("indices") ?: continue
                for (k in 0 until idx.length()) st.execute(idx.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        return c
    }

    /** Table → (columns with type/notnull/pk, index names) as SQLite reports them. */
    private fun shape(c: Connection): Map<String, Pair<Set<String>, Set<String>>> {
        val tables = c.createStatement().executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%'",
        ).use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
        return tables.associateWith { t ->
            val cols = c.createStatement().executeQuery("PRAGMA table_info(`$t`)").use { rs ->
                generateSequence { if (rs.next()) "${rs.getString("name")}:${rs.getString("type")}:${rs.getInt("notnull")}:${rs.getInt("pk")}:${rs.getString("dflt_value")}" else null }.toSet()
            }
            val idx = c.createStatement().executeQuery("PRAGMA index_list(`$t`)").use { rs ->
                generateSequence { if (rs.next()) rs.getString("name") else null }.filter { !it.startsWith("sqlite_autoindex") }.toSet()
            }
            cols to idx
        }
    }

    private fun count(c: Connection, sql: String) = c.createStatement().executeQuery(sql).use { it.next(); it.getInt(1) }

    /** Player data that must survive every upgrade, written with columns that exist since version 1. */
    private fun seed(c: Connection) {
        c.createStatement().use { st ->
            st.execute("INSERT INTO segment (id, startMs, endMs, source, processed) VALUES (1, 1000, 2000, 'phone', 1)")
            for (i in 0 until 50) st.execute("INSERT INTO track_point (segmentId, timeMs, lat, lng, accuracyM) VALUES (1, ${1000 + i}, 1.0, 2.0, 5.0)")
        }
    }

    @Test
    fun everyVersionUpgradesToTheLatestSchemaKeepingPlayerData() {
        val latest = DbMigrations.LATEST
        val expected = create(latest).use { shape(it) }
        assertTrue("the schema for v$latest must be exported", File(schemaDir, "$latest.json").exists())
        for (from in 1 until latest) {
            create(from).use { c ->
                seed(c)
                for (v in from until latest) {
                    val steps = DbMigrations.steps[v] ?: error("no migration from v$v")
                    c.createStatement().use { st -> steps.forEach(st::execute) }
                }
                assertEquals("schema after upgrading from v$from", expected, shape(c))
                assertEquals("walk points kept (from v$from)", 50, count(c, "SELECT COUNT(*) FROM track_point"))
                // Walks are queued for re-processing under the new rules, never deleted.
                assertEquals(1, count(c, "SELECT COUNT(*) FROM segment"))
                assertEquals(0, count(c, "SELECT processed FROM segment WHERE id = 1"))
            }
        }
    }

    @Test
    fun everyVersionHasAMigrationAndAnExportedSchema() {
        for (v in 1 until DbMigrations.LATEST) assertTrue("migration from v$v", DbMigrations.steps.containsKey(v))
        for (v in 1..DbMigrations.LATEST) assertTrue("schema v$v exported", File(schemaDir, "$v.json").exists())
    }

    @Test
    fun projectDecisionsSurviveTheLatestUpgradeOnward() {
        // Decisions arrived in v6; once made they are player data like walks.
        create(DbMigrations.LATEST).use { c ->
            c.createStatement().execute("INSERT INTO project_decision (day, areaId, projectId, approve, madeMs) VALUES (1, 'a', 'well', 1, 0)")
            assertEquals(1, count(c, "SELECT COUNT(*) FROM project_decision"))
        }
    }
}
