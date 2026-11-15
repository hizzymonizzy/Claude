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
        val roads = events.filterIsInstance<WorldEvent.RoadsCharted>().flatMap { it.edgeIds }
        val roadNames = roads.mapNotNull { state.edges[it]?.name }.filter { it.isNotBlank() }.distinct()
        if (km >= 0.05 || cycleKm >= 0.05) {
            val parts = mutableListOf<String>()
            if (km >= 0.05) parts += "You walked %.1f km".format(km)
            if (cycleKm >= 0.05) parts += (if (parts.isEmpty()) "You rode" else "rode") + " %.1f km".format(cycleKm)
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
            is WorldEvent.GoalChanged -> lines += if (e.to > e.from)
                "You've been steady. Your weekly goal rises to ${e.to} minutes."
            else "Your weekly goal eases to ${e.to} minutes. Rest is part of the journey."
            else -> {}
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
