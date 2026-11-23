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
                pick(rnd, "You spent the night at ${name(e.areaId)}. By morning your tent had become a camp.",
                    "A night under the stars at ${name(e.areaId)}: a camp is born.")
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
                "You've been walking past your goal. It grows to ${e.to} minutes (${WorldEngine.band(e.to)})."
            else "Your weekly goal eases to ${e.to} minutes (${WorldEngine.band(e.to)}). Rest is part of the journey."
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

        // Tuesday: last week's scoring (ADR 0008/0011). Calm, seasonal words; say what you kept, never "lost".
        events.filterIsInstance<WorldEvent.WeekScored>().firstOrNull()?.let { w ->
            val head = "Last week: ${w.minutes.roundToInt()} active minutes."
            lines += when {
                w.loss >= 0.2 -> "$head A quiet week: frost crept over the outer lanes. Everything you built still stands."
                w.loss >= 0.1 -> "$head A gentle week: a light frost on the edges of your world."
                w.loss > 0 -> "$head Nearly there: barely a chill in the air."
                w.recovery > 0 -> "$head A bright week: faded places warmed back" + if (w.recoveredRoads > 0) ", and ${w.recoveredRoads} ${plural(w.recoveredRoads, "road")} came back to life." else "."
                else -> "$head Your world held steady."
            }
            if (w.fadedRoads.isNotEmpty()) lines += w.fadedRoads.take(3).joinToString(", ") + (if (w.fadedRoads.size > 3) " and others" else "") +
                " settled back a step; a walk along them brings them back."
            if (w.settlementsDropped.isNotEmpty()) lines += w.settlementsDropped.joinToString { name(it) } + " grew quieter; visit to wake it."
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
