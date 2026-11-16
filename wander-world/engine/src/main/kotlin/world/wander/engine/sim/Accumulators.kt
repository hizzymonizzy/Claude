package world.wander.engine.sim

import world.wander.engine.EdgeInfo
import world.wander.engine.LatLng
import world.wander.engine.Tier

/** Mutable per-object state while the simulation replays history. Turned into immutable *State by [snapshot]. */
internal class EdgeAcc(val info: EdgeInfo) {
    val intervals = mutableListOf<Pair<Double, Double>>()
    var coveredM = 0.0
    var discoveredDay: Int? = null
    var wear = 0.0
}

internal class SettlementAcc(val areaId: String, val capital: Boolean, val foundedDay: Int) {
    var lastVisit = foundedDay
    var d = 0.0
    var peakD = 0.0
    var tier = Tier.CAMP
    var peakTier = Tier.CAMP
    var position: LatLng? = null
}

internal class RegionAcc(val id: Int, val seed: LatLng, val firstDay: Int) {
    var lastPresence = firstDay
    var firstAreaId: String? = null
    var dormant = false
}

/** A waystone sits at the average of every arrival that found or returned to it: it settles on the car park. */
internal class WaystoneAcc(val id: Int, first: LatLng, val great: Boolean, val regionId: Int, val foundDay: Int) {
    var trips = 1
    private var sumLat = first.lat
    private var sumLng = first.lng
    var center = first; private set
    fun add(pt: LatLng) { trips++; sumLat += pt.lat; sumLng += pt.lng; center = LatLng(sumLat / trips, sumLng / trips) }
}

internal class AreaAcc {
    var d = 0.0
    var lastAreaDay = Int.MIN_VALUE
    var areaDays = 0
    var lastSeenDay = Int.MIN_VALUE
    val footDays = mutableListOf<Int>()
    val cells = mutableSetOf<String>()
}
