package world.wander.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// ---- Raw capture ----

@Entity(tableName = "segment")
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    /** "phone" or "watch". */
    val source: String,
    /** "FOOT", "CYCLE" or null when unknown. */
    val modeHint: String? = null,
    val processed: Boolean = false,
    val note: String? = null,
)

@Entity(tableName = "track_point", indices = [Index("segmentId")])
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val timeMs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
)

/** A finished walk's points, packed by [PointArchive] once the walk is a week old (kept forever, on the phone). */
@Entity(tableName = "track_archive")
class TrackArchiveEntity(
    @PrimaryKey val segmentId: Long,
    val data: ByteArray,
    val count: Int,
)

// ---- Observations (source of truth for the world) ----

@Entity(tableName = "edge_coverage", indices = [Index("edgeId"), Index("segmentId")])
data class EdgeCoverageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val edgeId: String,
    val day: Int,
    val fromM: Double,
    val toM: Double,
    val mode: String,
)

@Entity(tableName = "cell_presence", indices = [Index("segmentId")])
data class CellPresenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val cell: String,
    val day: Int,
    val seconds: Double,
    val mode: String,
)

@Entity(tableName = "day_movement", indices = [Index("segmentId")])
data class DayMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val day: Int,
    val footMeters: Double,
    val cycleMeters: Double,
    @ColumnInfo(defaultValue = "0") val waterMeters: Double = 0.0,
)

/** A water area (lake, pond, river, bay), named or not; used to tell when you're paddling. Rings joined by '|'. */
@Entity(tableName = "water_area", indices = [Index("tile")])
data class WaterAreaEntity(@PrimaryKey val id: String, val tile: String, val geometry: String)

/** Whether a tile touches a mapped coastline (the open sea isn't mapped as an area). */
/**
 * Ground the map paints but you don't "discover": beaches and sand (named or not, as areas) and the coastline
 * (as lines, clipped to the tile). kind = "sand" or "coast". Rings or lines joined by '|'.
 */
@Entity(tableName = "terrain_area", indices = [Index("tile")])
data class TerrainAreaEntity(@PrimaryKey val id: String, val tile: String, val kind: String, val geometry: String)

@Entity(tableName = "water_tile")
data class WaterTileEntity(@PrimaryKey val tile: String, val coastal: Boolean)

/** Your approval or decline of a project proposal: a player choice, replayed like walks. */
@Entity(tableName = "project_decision")
data class ProjectDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Int,
    val areaId: String,
    val projectId: String,
    val approve: Boolean,
    val madeMs: Long,
)

/** Something bought from the travelling trader or a market (a resource, or a cosmetic). Replayed like decisions. */
@Entity(tableName = "purchase")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Int,
    val seller: String,
    val item: String,
    val qty: Int,
    val madeMs: Long,
)

/** A route paddled on the water (paddleboard, canoe, kayak, rowing, open-water swim). */
@Entity(tableName = "water_trail", indices = [Index("segmentId")])
data class WaterTrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val day: Int,
    val geometry: String,
)

@Entity(tableName = "wake", indices = [Index("segmentId")])
data class WakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val cell: String,
    val day: Int,
)

/** Fine cells walked along a shore (food, like stone from roads); recorded at the shoreline itself. */
/** Where the phone was at about 3 am after [day] (one quiet location check; never leaves the phone). Founds camps. */
@Entity(tableName = "night_stay")
data class NightStayEntity(
    @PrimaryKey val day: Int,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val madeMs: Long,
)

/** A heat map hex (H3 res 12, ~20 m) walked on a day (ADR 0007). */
@Entity(tableName = "heat_cell", indices = [Index("segmentId")])
data class HeatCellEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val cell: String,
    val day: Int,
)

@Entity(tableName = "shore", indices = [Index("segmentId")])
data class ShoreEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val segmentId: Long, val cell: String, val day: Int)

/** The path actually walked (simplified), kept for good: fog clears along it. */
@Entity(tableName = "trail", indices = [Index("segmentId")])
data class TrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val day: Int,
    val geometry: String,
)

/** A stretch walked off the mapped network: a path you made yourself. */
@Entity(tableName = "wild_trail", indices = [Index("segmentId")])
data class WildTrailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val day: Int,
    val geometry: String,
)

@Entity(tableName = "trodden", indices = [Index("segmentId")])
data class TroddenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val cell: String,
    val day: Int,
)

/** A named area (park, wood, beach, campus, big store). Rings joined by '|'. */
@Entity(tableName = "landmark", indices = [Index("tile")])
data class LandmarkEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val geometry: String,
    val areaM2: Double,
    val tile: String,
)

@Entity(tableName = "landmark_tile")
data class LandmarkTileEntity(@PrimaryKey val tile: String, val fetchedMs: Long, val count: Int)

@Entity(tableName = "landmark_visit", indices = [Index("segmentId")])
data class LandmarkVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val landmarkId: String,
    val day: Int,
    val seconds: Double,
)

/** Where walking resumed after a car ride or flight (source of waystones and new regions). */
@Entity(tableName = "arrival", indices = [Index("segmentId")])
data class ArrivalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: Long,
    val day: Int,
    val lat: Double,
    val lng: Double,
)

@Entity(tableName = "daily_intensity")
data class DailyIntensityEntity(
    @PrimaryKey val day: Int,
    val moderateMin: Double,
    val vigorousMin: Double,
    val source: String,
)

// ---- Map data cache (OpenStreetMap) ----

@Entity(tableName = "geo_tile")
data class GeoTileEntity(@PrimaryKey val tile: String, val fetchedMs: Long, val edgeCount: Int)

@Entity(tableName = "edge", indices = [Index("tile"), Index("startNode"), Index("endNode")])
data class EdgeEntity(
    @PrimaryKey val id: String,
    val wayId: Long,
    val name: String?,
    val highway: String?,
    val startNode: Long,
    val endNode: Long,
    val lengthM: Double,
    /** "lat,lng;lat,lng;…" */
    val geometry: String,
    val tile: String,
)

@Entity(tableName = "place")
data class PlaceEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
)

@Entity(tableName = "cell_area")
data class CellAreaEntity(@PrimaryKey val cell: String, val areaId: String)

@Entity(tableName = "area")
data class AreaEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val placeType: String?,
    val frozenMs: Long,
)

// ---- Output and diagnostics ----

@Entity(tableName = "dispatch")
data class DispatchEntity(@PrimaryKey val day: Int, val title: String, val body: String, val createdMs: Long)

@Entity(tableName = "diag_log")
data class DiagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val timeMs: Long, val tag: String, val message: String)

data class SegmentCell(val segmentId: Long, val cell: String)

@Dao
interface WanderDao {
    // Segments and points
    @Insert fun insertSegment(s: SegmentEntity): Long
    @Query("UPDATE segment SET endMs = :endMs WHERE id = :id") fun endSegment(id: Long, endMs: Long)
    @Query("SELECT * FROM segment WHERE id = :id") fun segment(id: Long): SegmentEntity?
    @Query("UPDATE segment SET processed = :processed, note = :note WHERE id = :id") fun markProcessed(id: Long, processed: Boolean, note: String?)
    @Query("SELECT * FROM segment WHERE processed = 0 AND endMs IS NOT NULL ORDER BY startMs") fun pendingSegments(): List<SegmentEntity>
    @Query("SELECT * FROM segment WHERE endMs IS NULL") fun openSegments(): List<SegmentEntity>
    @Query("SELECT * FROM segment WHERE startMs < :endMs AND (endMs IS NULL OR endMs > :startMs)") fun segmentsOverlapping(startMs: Long, endMs: Long): List<SegmentEntity>
    @Query("SELECT COUNT(*) FROM segment") fun segmentCount(): Int
    @Query("UPDATE segment SET processed = 0 WHERE endMs IS NOT NULL") fun resetProcessed(): Int
    @Query("SELECT * FROM segment ORDER BY startMs DESC LIMIT :limit") fun recentSegments(limit: Int): List<SegmentEntity>
    @Insert fun insertPoints(p: List<TrackPointEntity>)
    @Query("SELECT * FROM track_point WHERE segmentId = :segmentId ORDER BY timeMs") fun points(segmentId: Long): List<TrackPointEntity>
    @Query("SELECT (SELECT COUNT(*) FROM track_point WHERE segmentId = :segmentId) + IFNULL((SELECT count FROM track_archive WHERE segmentId = :segmentId), 0)")
    fun pointCount(segmentId: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertArchive(a: TrackArchiveEntity)
    @Insert fun insertHeat(h: List<HeatCellEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertNight(n: NightStayEntity)
    @Query("SELECT * FROM night_stay") fun allNights(): List<NightStayEntity>
    @Query("DELETE FROM heat_cell WHERE segmentId = :segmentId") fun deleteHeat(segmentId: Long)
    @Query("SELECT * FROM heat_cell") fun allHeat(): List<HeatCellEntity>
    @Query("SELECT * FROM track_archive WHERE segmentId = :segmentId") fun archive(segmentId: Long): TrackArchiveEntity?
    @Query("DELETE FROM track_point WHERE segmentId = :segmentId") fun deletePoints(segmentId: Long)
    /** Finished, processed walks that ended before this time and still have loose point rows. */
    @Query("SELECT id FROM segment WHERE processed = 1 AND endMs IS NOT NULL AND endMs < :beforeMs AND id IN (SELECT DISTINCT segmentId FROM track_point)")
    fun segmentsToArchive(beforeMs: Long): List<Long>

    // Observations
    @Insert fun insertCoverage(c: List<EdgeCoverageEntity>)
    @Insert fun insertPresence(c: List<CellPresenceEntity>)
    @Insert fun insertMovement(c: List<DayMovementEntity>)
    @Query("DELETE FROM edge_coverage WHERE segmentId = :segmentId") fun deleteCoverage(segmentId: Long)
    @Query("DELETE FROM cell_presence WHERE segmentId = :segmentId") fun deletePresence(segmentId: Long)
    @Query("DELETE FROM day_movement WHERE segmentId = :segmentId") fun deleteMovement(segmentId: Long)
    @Query("SELECT * FROM edge_coverage") fun allCoverage(): List<EdgeCoverageEntity>
    @Query("SELECT * FROM cell_presence") fun allPresence(): List<CellPresenceEntity>
    @Query("SELECT DISTINCT segmentId, cell FROM cell_presence") fun allPresenceCells(): List<SegmentCell>
    @Query("SELECT * FROM day_movement") fun allMovement(): List<DayMovementEntity>
    @Insert fun insertTrails(t: List<TrailEntity>)
    @Query("DELETE FROM trail WHERE segmentId = :segmentId") fun deleteTrails(segmentId: Long)
    @Query("SELECT * FROM trail") fun allTrails(): List<TrailEntity>
    @Insert fun insertWild(t: List<WildTrailEntity>)
    @Query("DELETE FROM wild_trail WHERE segmentId = :segmentId") fun deleteWild(segmentId: Long)
    @Query("SELECT * FROM wild_trail") fun allWild(): List<WildTrailEntity>
    /** Own paths from walks that started before [startMs] (0.14: a new walk merges onto them). */
    @Query("SELECT w.* FROM wild_trail w JOIN segment s ON s.id = w.segmentId WHERE s.startMs < :startMs") fun wildBefore(startMs: Long): List<WildTrailEntity>
    @Insert fun insertTrodden(t: List<TroddenEntity>)
    @Query("DELETE FROM trodden WHERE segmentId = :segmentId") fun deleteTrodden(segmentId: Long)
    @Query("SELECT * FROM trodden") fun allTrodden(): List<TroddenEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertLandmarks(l: List<LandmarkEntity>)
    @Query("SELECT * FROM landmark WHERE tile IN (:tiles)") fun landmarksInTiles(tiles: List<String>): List<LandmarkEntity>
    @Query("SELECT * FROM landmark WHERE id IN (:ids)") fun landmarksById(ids: List<String>): List<LandmarkEntity>
    @Query("SELECT * FROM landmark_tile WHERE tile = :tile") fun landmarkTile(tile: String): LandmarkTileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertLandmarkTile(t: LandmarkTileEntity)
    @Insert fun insertVisits(v: List<LandmarkVisitEntity>)
    @Query("DELETE FROM landmark_visit WHERE segmentId = :segmentId") fun deleteVisits(segmentId: Long)
    @Query("SELECT * FROM landmark_visit") fun allVisits(): List<LandmarkVisitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertWaterAreas(w: List<WaterAreaEntity>)
    @Query("SELECT * FROM water_area WHERE tile IN (:tiles)") fun waterAreasInTiles(tiles: List<String>): List<WaterAreaEntity>
    @Query("SELECT * FROM water_area WHERE id IN (:ids)") fun waterAreasById(ids: List<String>): List<WaterAreaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTerrain(t: List<TerrainAreaEntity>)
    @Query("SELECT * FROM terrain_area WHERE tile IN (:tiles)") fun terrainInTiles(tiles: List<String>): List<TerrainAreaEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertWaterTile(t: WaterTileEntity)
    @Query("SELECT * FROM water_tile WHERE tile IN (:tiles)") fun waterTiles(tiles: List<String>): List<WaterTileEntity>
    @Insert fun insertDecision(d: ProjectDecisionEntity)
    @Query("SELECT * FROM project_decision ORDER BY madeMs") fun allDecisions(): List<ProjectDecisionEntity>
    @Insert fun insertPurchase(p: PurchaseEntity)
    @Query("SELECT * FROM purchase ORDER BY madeMs") fun allPurchases(): List<PurchaseEntity>
    @Insert fun insertWaterTrails(t: List<WaterTrailEntity>)
    @Query("DELETE FROM water_trail WHERE segmentId = :segmentId") fun deleteWaterTrails(segmentId: Long)
    @Query("SELECT * FROM water_trail") fun allWaterTrails(): List<WaterTrailEntity>
    @Insert fun insertWake(t: List<WakeEntity>)
    @Query("DELETE FROM wake WHERE segmentId = :segmentId") fun deleteWake(segmentId: Long)
    @Query("SELECT * FROM wake") fun allWake(): List<WakeEntity>
    @Insert fun insertShore(t: List<ShoreEntity>)
    @Query("DELETE FROM shore WHERE segmentId = :segmentId") fun deleteShore(segmentId: Long)
    @Query("SELECT * FROM shore") fun allShore(): List<ShoreEntity>
    @Insert fun insertArrivals(a: List<ArrivalEntity>)
    @Query("DELETE FROM arrival WHERE segmentId = :segmentId") fun deleteArrivals(segmentId: Long)
    @Query("SELECT * FROM arrival") fun allArrivals(): List<ArrivalEntity>
    @Query("SELECT * FROM segment WHERE endMs IS NOT NULL AND endMs <= :beforeMs AND id != :excludeId ORDER BY endMs DESC LIMIT 1") fun previousSegment(beforeMs: Long, excludeId: Long): SegmentEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertIntensity(d: List<DailyIntensityEntity>)
    @Query("SELECT * FROM daily_intensity") fun allIntensity(): List<DailyIntensityEntity>

    // Geo
    @Query("SELECT * FROM geo_tile WHERE tile = :tile") fun tile(tile: String): GeoTileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertTile(t: GeoTileEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertEdges(e: List<EdgeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertPlaces(p: List<PlaceEntity>)
    @Query("SELECT * FROM edge WHERE tile IN (:tiles)") fun edgesInTiles(tiles: List<String>): List<EdgeEntity>
    @Query("SELECT * FROM edge WHERE id IN (:ids)") fun edgesById(ids: List<String>): List<EdgeEntity>
    @Query("SELECT * FROM edge WHERE startNode IN (:nodes) OR endNode IN (:nodes)") fun edgesTouching(nodes: List<Long>): List<EdgeEntity>
    @Query("SELECT * FROM place") fun allPlaces(): List<PlaceEntity>
    @Query("SELECT * FROM cell_area WHERE cell = :cell") fun cellArea(cell: String): CellAreaEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertCellArea(c: CellAreaEntity)
    @Query("SELECT * FROM cell_area") fun allCellAreas(): List<CellAreaEntity>
    @Query("SELECT * FROM cell_area WHERE areaId LIKE 'r7:%'") fun fallbackCellAreas(): List<CellAreaEntity>
    @Query("UPDATE cell_area SET areaId = :areaId WHERE cell = :cell") fun reassignCell(cell: String, areaId: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertArea(a: AreaEntity)
    @Query("UPDATE area SET name = :name WHERE id = :id AND name IS NULL") fun freezeAreaName(id: String, name: String)
    @Query("SELECT * FROM area") fun allAreas(): List<AreaEntity>

    // Dispatch and diagnostics
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertDispatch(d: DispatchEntity)
    @Query("SELECT * FROM dispatch ORDER BY day DESC") fun dispatches(): Flow<List<DispatchEntity>>
    @Query("SELECT * FROM dispatch WHERE day = :day") fun dispatch(day: Int): DispatchEntity?
    @Insert fun log(d: DiagEntity)
    @Query("SELECT * FROM diag_log ORDER BY id DESC LIMIT 300") fun diagnostics(): Flow<List<DiagEntity>>
    @Query("DELETE FROM diag_log WHERE timeMs < :beforeMs") fun pruneDiagnostics(beforeMs: Long)
    @Query("SELECT * FROM diag_log ORDER BY id DESC LIMIT :limit") fun recentLogs(limit: Int): List<DiagEntity>
}

@Database(
    entities = [
        SegmentEntity::class, TrackPointEntity::class, EdgeCoverageEntity::class, CellPresenceEntity::class,
        DayMovementEntity::class, DailyIntensityEntity::class, GeoTileEntity::class, EdgeEntity::class,
        PlaceEntity::class, CellAreaEntity::class, AreaEntity::class, DispatchEntity::class, DiagEntity::class,
        TrailEntity::class, WildTrailEntity::class, TroddenEntity::class, LandmarkEntity::class,
        LandmarkTileEntity::class, LandmarkVisitEntity::class, ArrivalEntity::class, WaterTrailEntity::class, WakeEntity::class,
        WaterAreaEntity::class, WaterTileEntity::class, ProjectDecisionEntity::class, TerrainAreaEntity::class, ShoreEntity::class, PurchaseEntity::class,
        TrackArchiveEntity::class, HeatCellEntity::class, NightStayEntity::class,
    ],
    version = DbMigrations.LATEST,
    exportSchema = true,
)
abstract class WanderDb : RoomDatabase() {
    abstract fun dao(): WanderDao

    companion object {
        /** Each step of [DbMigrations] as a Room migration. */
        private val MIGRATIONS = DbMigrations.steps.map { (from, sql) ->
            object : Migration(from, from + 1) {
                override fun migrate(db: SupportSQLiteDatabase) = sql.forEach(db::execSQL)
            }
        }.toTypedArray()

        fun create(context: Context): WanderDb =
            Room.databaseBuilder(context, WanderDb::class.java, "wander.db")
                .addMigrations(*MIGRATIONS).build()
    }
}
