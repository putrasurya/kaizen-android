package com.putrasurya.kaizen.milestones.domain

import java.time.LocalDate

/** A milestone reached ([achievedOn] set) or a goal for [year] (same as the web app). */
data class Milestone(
    val id: Long,
    val title: String,
    val note: String,
    val achievedOn: LocalDate?,
    val year: Int,
    /** An unreached goal from an earlier year that was left there ("not reached"). */
    val kept: Boolean,
) {
    val isGoal: Boolean get() = achievedOn == null
}

data class YearView(
    val achieved: List<Milestone>,
    val goals: List<Milestone>,
    /** Unreached goals from earlier years still waiting for "bring in" or "leave". */
    val unsettled: List<Milestone>,
)

object MilestoneMath {

    const val TITLE_MAX = 120
    const val NOTE_MAX = 500

    /** What one year shows. Unsettled goals are only offered when looking at [currentYear]. */
    fun yearView(all: List<Milestone>, year: Int, currentYear: Int): YearView {
        val achieved = all.filter { !it.isGoal && it.year == year }.sortedBy { it.achievedOn?.toEpochDay() }
        val goals = all.filter { it.isGoal && it.year == year }.sortedBy { it.id }
        val unsettled = if (year == currentYear) {
            all.filter { it.isGoal && !it.kept && it.year < currentYear }.sortedBy { it.year }
        } else {
            emptyList()
        }
        return YearView(achieved, goals, unsettled)
    }

    /** After settling: carried goals move to [toYear]; left ones stay as "not reached". */
    fun settle(goal: Milestone, toYear: Int, carry: Boolean): Milestone = when {
        !goal.isGoal || goal.kept || goal.year >= toYear -> goal
        carry -> goal.copy(year = toYear)
        else -> goal.copy(kept = true)
    }

    /** Marking a goal achieved (or moving a milestone's date) sets its year too. */
    fun achieve(milestone: Milestone, date: LocalDate): Milestone =
        milestone.copy(achievedOn = date, year = date.year, kept = false)
}
