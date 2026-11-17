package com.putrasurya.kaizen.todos.domain

import com.putrasurya.kaizen.core.time.Dates
import java.time.LocalDate

/**
 * Todo rules (same as the web app): a checklist per weekday that comes back
 * every week. A tick only counts for the week it was made in, so every week
 * starts unchecked again; nothing needs to reset on Monday.
 */
object TodoMath {

    const val MAX_NAMES_SPOKEN = 5

    fun isDone(doneOn: LocalDate?, today: LocalDate): Boolean =
        doneOn != null && !doneOn.isBefore(Dates.weekStart(today)) && !doneOn.isAfter(today)

    /**
     * What the evening reminder says (Indonesian, the voice's language), or
     * null when everything is done.
     */
    fun reminderMessage(unfinished: List<String>): String? {
        if (unfinished.isEmpty()) return null
        val shown = unfinished.take(MAX_NAMES_SPOKEN)
        val rest = unfinished.size - shown.size
        val names = when {
            rest > 0 -> shown.joinToString(", ") + ", dan $rest lainnya"
            shown.size == 1 -> shown[0]
            else -> shown.dropLast(1).joinToString(", ") + " dan " + shown.last()
        }
        return "Kamu masih punya ${unfinished.size} tugas yang belum selesai hari ini: $names. " +
            "Kalau tidak sempat, catat di jurnal ya."
    }
}
