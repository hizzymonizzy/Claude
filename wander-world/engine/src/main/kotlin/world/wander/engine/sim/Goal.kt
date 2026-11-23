package world.wander.engine.sim

import world.wander.engine.WeekRecord
import world.wander.engine.WorldEvent
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 6. Weekly goal: last Monday-to-Sunday week is recorded each Tuesday (a day late, so late watch syncs count); the goal
 * rises slowly after strong weeks and eases quickly after weak ones. Measured in active minutes (see effortByDay).
 */
internal fun Simulation.weeklyGoal(d: Simulation.Day) {
    val day = d.day
    if (LocalDate.ofEpochDay(day.toLong()).dayOfWeek == DayOfWeek.TUESDAY && day - 8 >= startDay) {
        val weekStart = day - 8
        val weekMinutes = (weekStart..day - 2).sumOf { effortByDay[it] ?: 0.0 }
        weeks += WeekRecord(weekStart, goal, weekMinutes)
        // Only real weeks move the goal: not rest weeks, nor the new-world grace (ADR 0011).
        val restingWeek = (weekStart..day).any { isResting(it) }
        val eligible = weeks.filter { w -> w.weekStartDay >= startDay && w.weekStartDay + 6 - startDay >= p.graceDays &&
            (w.weekStartDay..w.weekStartDay + 6).none { isResting(it) } }
        if (!input.goalLocked && !restingWeek) {
            val old = goal
            val last4 = eligible.takeLast(4)
            val last2 = eligible.takeLast(2)
            val cooledDown = weeks.size - 1 - lastRaiseWeek >= p.goalUpCooldownWeeks
            if (cooledDown && last4.size == 4 && last4.count { it.minutes >= p.goalUpRatio * goal } >= p.goalUpWeeksNeeded) {
                // Rise at most 10% per four weeks, and never past what you've actually been doing (median + 10%).
                val median = last4.map { it.minutes }.sorted().let { (it[1] + it[2]) / 2 }
                goal = min(p.goalCeiling, min(round10(goal * p.goalUpStep), maxOf(goal, round10(median * 1.1))))
                if (goal != old) lastRaiseWeek = weeks.size - 1
            } else if (last2.size == 2 && last2.all { it.minutes < p.goalDownRatio * goal }) {
                val lowered = max(p.goalFloor, round10(last2.map { it.minutes }.average()))
                if (lowered < goal) goal = lowered
            }
            if (goal != old) events += WorldEvent.GoalChanged(day, old, goal)
            bestGoal = maxOf(bestGoal, goal)
        }
    }
    if (input.goalLocked && input.lockedGoal != null) goal = input.lockedGoal
}

private fun round10(v: Double): Int = ((v / 10.0).roundToInt() * 10)
