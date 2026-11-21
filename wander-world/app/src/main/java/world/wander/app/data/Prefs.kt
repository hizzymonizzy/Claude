package world.wander.app.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

/** Small player settings. Everything that shapes the world is replayed from the database. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("wander", Context.MODE_PRIVATE)

    /** Epoch day the world began. Set on first launch. */
    var startDay: Int
        get() = sp.getInt("startDay", -1).takeIf { it >= 0 } ?: LocalDate.now().toEpochDay().toInt().also { startDay = it }
        set(v) = sp.edit { putInt("startDay", v) }

    var homeLat: Double?
        get() = sp.getString("homeLat", null)?.toDouble()
        set(v) = sp.edit { putString("homeLat", v?.toString()) }
    var homeLng: Double?
        get() = sp.getString("homeLng", null)?.toDouble()
        set(v) = sp.edit { putString("homeLng", v?.toString()) }
    var homeAreaId: String?
        get() = sp.getString("homeAreaId", null)
        set(v) = sp.edit { putString("homeAreaId", v) }

    /** "auto" = activity detection starts tracking; "allday" = a service stays up all day. */
    var trackingMode: String
        get() = sp.getString("trackingMode", "allday")!!
        set(v) = sp.edit { putString("trackingMode", v) }

    var autoTrackingEnabled: Boolean
        get() = sp.getBoolean("autoTracking", false)
        set(v) = sp.edit { putBoolean("autoTracking", v) }

    /** Pointers on the map toward the nearest wood, food, stone and metal. */
    var resourcePointers: Boolean
        get() = sp.getBoolean("resourcePointers", true)
        set(v) = sp.edit { putBoolean("resourcePointers", v) }

    /** Cosmetics you've chosen to wear / fly (must be owned; ownership comes from purchases in the world). */
    var outfit: String?
        get() = sp.getString("outfit", null)
        set(v) = sp.edit { putString("outfit", v) }
    var banner: String?
        get() = sp.getString("banner", null)
        set(v) = sp.edit { putString("banner", v) }

    var goalLocked: Boolean
        get() = sp.getBoolean("goalLocked", false)
        set(v) = sp.edit { putBoolean("goalLocked", v) }

    /** Rest periods as "start-end" epoch-day pairs; an open period has end = -1. */
    var restPeriods: List<Pair<Int, Int>>
        get() = sp.getString("rest", "")!!.split(';').filter { it.isNotBlank() }.map {
            val (a, b) = it.split('-').let { p -> p[0] to p.getOrElse(1) { "-1" } }
            a.toInt() to (b.toIntOrNull() ?: -1)
        }
        set(v) = sp.edit { putString("rest", v.joinToString(";") { "${it.first}-${it.second}" }) }

    val resting: Boolean get() = restPeriods.any { it.second == -1 }

    var dispatchHour: Int
        get() = sp.getInt("dispatchHour", 7)
        set(v) = sp.edit { putInt("dispatchHour", v) }
    var dispatchMinute: Int
        get() = sp.getInt("dispatchMinute", 30)
        set(v) = sp.edit { putInt("dispatchMinute", v) }

    var lastDispatchDay: Int
        get() = sp.getInt("lastDispatchDay", -1)
        set(v) = sp.edit { putInt("lastDispatchDay", v) }

    /** Bumped when walk processing changes, so stored walks are re-processed once with the new rules. */
    var processingVersion: Int
        get() = sp.getInt("processingVersion", 1)
        set(v) = sp.edit { putInt("processingVersion", v) }

    var lastHealthSyncMs: Long
        get() = sp.getLong("lastHealthSync", 0)
        set(v) = sp.edit { putLong("lastHealthSync", v) }
}
