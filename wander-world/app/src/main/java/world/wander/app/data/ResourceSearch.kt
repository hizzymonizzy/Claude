package world.wander.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import world.wander.app.geo.Overpass
import world.wander.app.health.HealthSync
import world.wander.app.work.ProcessWorker
import world.wander.engine.AreaAssigner
import world.wander.engine.AreaInfo
import world.wander.engine.CellPresence
import world.wander.engine.Cells
import world.wander.engine.DayIntensity
import world.wander.engine.DayMovement
import world.wander.engine.Dispatch
import world.wander.engine.DispatchWriter
import world.wander.engine.EdgeCoverage
import world.wander.engine.EdgeState
import world.wander.engine.GraphEdge
import world.wander.engine.LatLng
import world.wander.engine.Landmark
import world.wander.engine.LandmarkInfo
import world.wander.engine.LandmarkVisit
import world.wander.engine.TroddenCell
import world.wander.engine.Arrival
import world.wander.engine.ProjectDecision
import world.wander.engine.Proposal
import world.wander.engine.Geo
import world.wander.engine.Mode
import world.wander.engine.Place
import world.wander.engine.TrackPoint
import world.wander.engine.TrackProcessor
import world.wander.engine.WorldEngine
import world.wander.engine.WorldInput
import world.wander.engine.WorldState
import world.wander.engine.Yields
import world.wander.engine.Purchase
import world.wander.engine.WorldEvent
import world.wander.engine.ResourceFinder
import world.wander.engine.ResourceTarget
import java.time.LocalDate
import java.time.ZoneId

// Split out of Repository.kt (review M2): resource nodes near you, from map data already on the phone.

suspend fun Repository.nearestSources(from: LatLng): List<ResourceTarget> = withContext(Dispatchers.IO) {
    // Map data for where you are now, even if you haven't walked here yet (one tile, same request a walk makes).
    val here = Cells.tile(from.lat, from.lng)
    // Where you're standing matters now: retry its places within 5 minutes, not 30.
    if (ensureTiles(listOf(here))) ensureLandmarks(listOf(here), maxWaitMs = 5 * 60_000L)
    val geo = Cells.disk(here, 2).map { tileGeo(it) }
    val places = geo.flatMap { it.places }.distinctBy { it.id }
    val placeIds = places.mapTo(HashSet()) { it.id }
    val water = geo.flatMap { it.water }.distinctBy { it.id }.filter { it.id !in placeIds } // a named lake is already a place
    ResourceFinder.find(from, places, water, world.value, coast = geo.flatMap { it.coast }, fields = geo.flatMap { it.fields },
        trails = geo.flatMap { it.trails })
}

/** A tile's decoded places, water, coast and field lines. */
internal class TileGeo(
    val stamp: Long, val places: List<Landmark>, val water: List<Landmark>, val coast: List<List<LatLng>>, val fields: List<List<LatLng>>,
    val trails: List<Triple<String, String?, List<LatLng>>>,
)
private val tileGeoCache = java.util.concurrent.ConcurrentHashMap<String, TileGeo>()  // one per process, like Repository

/**
 * Decoded once per download (review H4): the resource search runs every 20 s while recording and used to re-read
 * and decode all of this each time. Tiles whose places aren't downloaded yet aren't cached, so they're read again.
 */
private fun Repository.tileGeo(tile: String): TileGeo {
    val stamp = dao.landmarkTile(tile)?.fetchedMs
    tileGeoCache[tile]?.let { if (stamp != null && it.stamp == stamp) return it }
    val one = listOf(tile)
    val geo = TileGeo(
        stamp ?: -1,
        dao.landmarksInTiles(one).mapNotNull { try { Landmark(it.id, it.name, it.kind, Overpass.decodeRings(it.geometry)) } catch (_: Exception) { null } },
        dao.waterAreasInTiles(one).mapNotNull { try { Landmark(it.id, "", "water", Overpass.decodeRings(it.geometry)) } catch (_: Exception) { null } },
        coastLines(one), fieldLines(one),
        dao.edgesInTiles(one).filter { Yields.isTrail(it.highway) }.map { Triple(it.id, it.name, Overpass.decode(it.geometry)) },
    )
    if (stamp != null) tileGeoCache[tile] = geo
    return geo
}
