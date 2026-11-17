package com.putrasurya.kaizen.todos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodoMathTest {

    private val wednesday = LocalDate.of(2026, 11, 18)

    @Test
    fun tickCountsOnlyThisWeek() {
        assertTrue(TodoMath.isDone(wednesday.minusDays(2), wednesday)) // this Monday
        assertFalse(TodoMath.isDone(wednesday.minusDays(3), wednesday)) // last Sunday
        assertFalse(TodoMath.isDone(null, wednesday))
    }

    @Test
    fun nothingToSayWhenAllDone() {
        assertNull(TodoMath.reminderMessage(emptyList()))
    }

    @Test
    fun namesOneTask() {
        assertEquals(
            "Kamu masih punya 1 tugas yang belum selesai hari ini: olahraga. Kalau tidak sempat, catat di jurnal ya.",
            TodoMath.reminderMessage(listOf("olahraga")),
        )
    }

    @Test
    fun joinsTheLastTwoWithDan() {
        val message = TodoMath.reminderMessage(listOf("olahraga", "baca buku", "belanja"))!!
        assertTrue(message.contains("3 tugas"))
        assertTrue(message.contains("olahraga, baca buku dan belanja."))
    }

    @Test
    fun longListsAreShortened() {
        val message = TodoMath.reminderMessage((1..7).map { "t$it" })!!
        assertTrue(message.contains("7 tugas"))
        assertTrue(message.contains("t1, t2, t3, t4, t5, dan 2 lainnya."))
    }
}
