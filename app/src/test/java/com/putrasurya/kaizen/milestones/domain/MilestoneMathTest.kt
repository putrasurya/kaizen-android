package com.putrasurya.kaizen.milestones.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MilestoneMathTest {

    private fun goal(id: Long, year: Int, kept: Boolean = false) =
        Milestone(id, "Goal $id", "", achievedOn = null, year = year, kept = kept)

    private fun reached(id: Long, date: LocalDate) =
        Milestone(id, "Done $id", "", achievedOn = date, year = date.year, kept = false)

    @Test
    fun yearShowsItsAchievementsInDateOrderAndItsGoals() {
        val all = listOf(
            reached(1, LocalDate.of(2026, 9, 1)),
            reached(2, LocalDate.of(2026, 3, 1)),
            reached(3, LocalDate.of(2025, 5, 1)),
            goal(4, 2026),
        )
        val view = MilestoneMath.yearView(all, year = 2026, currentYear = 2026)
        assertEquals(listOf(2L, 1L), view.achieved.map { it.id })
        assertEquals(listOf(4L), view.goals.map { it.id })
    }

    @Test
    fun oldUnreachedGoalsWaitForADecision() {
        val all = listOf(goal(1, 2025), goal(2, 2024, kept = true), goal(3, 2026))
        val view = MilestoneMath.yearView(all, year = 2026, currentYear = 2026)
        assertEquals(listOf(1L), view.unsettled.map { it.id })
        // Looking back at an earlier year doesn't ask.
        assertTrue(MilestoneMath.yearView(all, year = 2025, currentYear = 2026).unsettled.isEmpty())
    }

    @Test
    fun settleCarriesOrKeeps() {
        val old = goal(1, 2025)
        assertEquals(2026, MilestoneMath.settle(old, 2026, carry = true).year)
        val left = MilestoneMath.settle(old, 2026, carry = false)
        assertEquals(2025, left.year)
        assertTrue(left.kept)
    }

    @Test
    fun achievingSetsTheYear() {
        val done = MilestoneMath.achieve(goal(1, 2025, kept = true), LocalDate.of(2026, 1, 10))
        assertEquals(2026, done.year)
        assertEquals(false, done.kept)
    }
}
