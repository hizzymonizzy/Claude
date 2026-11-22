package world.wander.engine

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

data class Dispatch(val day: Int, val title: String, val lines: List<String>) {
    val text: String get() = (listOf(title) + lines).joinToString("\n")
}

/**
 * Writes the daily dispatch from one day's world events. Tone: warm and observational,
 * never guilt-tripping.
 */
object DispatchWriter {

    fun write(state: WorldState, day: Int): Dispatch {
        val rnd = Random(day)
        val date = LocalDate.ofEpochDay(day.toLong())
        val title = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}'s Dispatch"
        val lines = mutableListOf<String>()
        val events = state.eventsOn(day)
        val summary = state.days[day]
        fun name(areaId: String) = state.areaNames[areaId] ?: "an unnamed place"

        // Movement.
        val km = (summary?.footMeters ?: 0.0) / 1000.0
        val cycleKm = (summary?.cycleMeters ?: 0.0) / 1000.0
        val waterKm = (summary?.waterMeters ?: 0.0) / 1000.0
        val roads = events.filterIsInstance<WorldEvent.RoadsCharted>().flatMap { it.edgeIds }
        val roadNames = roads.mapNotNull { state.edges[it]?.name }.filter { it.isNotBlank() }.distinct()
        if (km >= 0.05 || cycleKm >= 0.05 || waterKm >= 0.05) {
            val parts = mutableListOf<String>()
            if (km >= 0.05) parts += "You walked %.1f km".format(km)
            if (cycleKm >= 0.05) parts += (if (parts.isEmpty()) "You rode" else "rode") + " %.1f km".format(cycleKm)
            if (waterKm >= 0.05) parts += (if (parts.isEmpty()) "You paddled" else "paddled") + " %.1f km".format(waterKm)
            var line = parts.joinToString(" and ") + "."
            if (roads.isNotEmpty()) {
                val shown = roadNames.take(3)
                val extra = roads.size - shown.size
                line += when {
                    shown.isEmpty() -> " Charted ${roads.size} unnamed ${plural(roads.size, "path")}."
                    extra > 0 -> " Charted ${roads.size} new roads: ${shown.joinToString(", ")} and $extra more."
                    else -> " Charted ${plural(shown.size, "a new road", "${shown.size} new roads")}: ${shown.joinToString(", ")}."
                }
            }
            lines += line
        } else {
            lines += pick(rnd, "A quiet day. The world rests, and so can you.", "No travels yesterday. The roads keep their own company.")
        }

        // Places discovered.
        val found = events.filterIsInstance<WorldEvent.LandmarkDiscovered>()
            .mapNotNull { e -> state.landmarks.firstOrNull { it.id == e.landmarkId }?.name }.distinct()
        if (found.isNotEmpty()) {
            val shown = found.take(3)
            lines += "Discovered " + shown.joinToString(", ") + (if (found.size > 3) " and ${found.size - 3} more." else ".")
        }

        // Settlements.
        for (e in events) when (e) {
            is WorldEvent.Founded -> lines += if (e.capital)
                "Your capital, ${name(e.areaId)}, is founded. A fire is lit at home."
            else
                pick(rnd, "A camp has been raised at ${name(e.areaId)}.", "Travelers have pitched tents at ${name(e.areaId)}. A camp is born.")
            is WorldEvent.TierChanged -> lines += if (e.to.ordinal > e.from.ordinal) {
                if (e.from == Tier.RUINS || e.to.ordinal <= tierPeakHint(state, e.areaId))
                    "${name(e.areaId)} is rebuilt as a ${e.to.label}."
                else "${e.from.label} of ${name(e.areaId)} grew into a ${e.to.label}."
            } else {
                if (e.to == Tier.RUINS) "Moss has claimed ${name(e.areaId)}. Its ruins wait for you."
                else "${name(e.areaId)} has quieted to a ${e.to.label}."
            }
            is WorldEvent.RegionFounded -> lines += if (e.fromRegionId != null)
                "A new land: ${state.regionName(e.regionId)}. A great waystone now links it to ${state.regionName(e.fromRegionId)}."
            else "A new land: ${state.regionName(e.regionId)}."
            is WorldEvent.RegionWoke -> lines += "${state.regionName(e.regionId)} stirs. The townspeople remember you."
            is WorldEvent.WaystoneFound, is WorldEvent.WaystoneReturned -> {} // summarized below
            is WorldEvent.ProjectBuilt, is WorldEvent.MonumentRaised, is WorldEvent.ProjectProposed -> {} // economy, below
            is WorldEvent.GoalChanged -> lines += if (e.to > e.from)
                "You've been steady. Your weekly goal rises to ${e.to} minutes."
            else "Your weekly goal eases to ${e.to} minutes. Rest is part of the journey."
            else -> {}
        }

        // The economy.
        summary?.earned?.takeIf { !it.isEmpty }?.let { lines += "Earned $it." }
        events.filterIsInstance<WorldEvent.SourceThinning>()
            .mapNotNull { e -> state.landmarks.firstOrNull { it.id == e.landmarkId }?.name }.distinct().take(2).forEach {
                lines += "$it is thinning from all your harvesting. Give it a rest and it will grow back; fresh ground yields more."
            }
        for (e in events) when (e) {
            is WorldEvent.ProjectBuilt -> Projects.byId(e.projectId)?.let { lines += "Built: ${it.name.lowercase().replaceFirstChar(Char::uppercase)} in ${name(e.areaId)}." }
            is WorldEvent.Purchased -> lines += if (e.item in Shop.COSMETICS) "Bought a new look (${e.item.substringAfter('_')}) for ${e.price} gold."
                else "Bought ${e.qty} ${e.item} from ${if (e.seller == Shop.TRADER) "the travelling trader" else "the market in ${name(e.seller)}"} for ${e.price} gold."
            is WorldEvent.MonumentRaised -> Monuments.byId(e.monumentId).let { lines += "A monument rises at your capital: ${it.name}. ${it.description}" }
            is WorldEvent.ProjectProposed -> Projects.byId(e.projectId)?.let {
                lines += "Proposal: ${name(e.areaId)} asks for a ${it.name.lowercase()} (${it.cost}). Approve or decline in the Atlas."
            }
            else -> {}
        }

        // Waystones, summarized per region.
        events.filterIsInstance<WorldEvent.WaystoneFound>().filter { !it.great }.groupBy { it.regionId }.forEach { (rid, found) ->
            lines += if (found.size == 1) "You found a waystone in ${state.regionName(rid)}. Walk from it to join it to the lands you know."
            else "You found ${found.size} waystones in ${state.regionName(rid)}. Walk between them to join them to the lands you know."
        }
        val returned = events.count { it is WorldEvent.WaystoneReturned }
        if (returned > 0) lines += if (returned == 1) "A waystone hums again as you return." else "$returned waystones hum again as you return."

        // Tuesday: last week's scoring (ADR 0008).
        events.filterIsInstance<WorldEvent.WeekScored>().firstOrNull()?.let { w ->
            val head = "Last week: ${w.minutes.roundToInt()} / ${w.goal} active minutes."
            lines += when {
                w.loss > 0 && w.fadedRoads.isNotEmpty() -> "$head ${w.fadedRoads.size} ${plural(w.fadedRoads.size, "road")} faded a step (" +
                    w.fadedRoads.take(3).joinToString(", ") + (if (w.fadedRoads.size > 3) ", …" else "") + ")." +
                    if (w.settlementsDropped.isNotEmpty()) " ${w.settlementsDropped.joinToString { name(it) }} lost a tier." else ""
                w.loss > 0 -> "$head The edges of your world cooled a little."
                w.recovery > 0 -> "$head Strong week: cooled places came back" + if (w.recoveredRoads > 0) " (${w.recoveredRoads} ${plural(w.recoveredRoads, "road")} regained a step)." else "."
                else -> "$head Goal met: nothing faded."
            }
        }

        // Goal line.
        if (summary != null) {
            val mins = (summary.ratio * summary.goal).roundToInt()
            val status = when (summary.protection) {
                Protection.RESTING -> "Your world is resting peacefully."
                Protection.GRACE -> "Your new world is protected while it takes root."
                Protection.RESTORING -> "Your world holds strong, and old places are being rebuilt."
                Protection.HOLDING -> "Your world holds strong."
                Protection.SLOWED -> "Barely a whisper of weather."
                Protection.DECAYING -> "Nature is stirring at the edges."
            }
            lines += "Weekly goal: $mins / ${summary.goal} min. $status"
        }
        return Dispatch(day, title, lines)
    }

    private fun tierPeakHint(state: WorldState, areaId: String): Int =
        state.settlements.firstOrNull { it.areaId == areaId }?.peakTier?.ordinal?.minus(1) ?: -1

    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many
    private fun pick(rnd: Random, vararg options: String) = options[rnd.nextInt(options.size)]
}
