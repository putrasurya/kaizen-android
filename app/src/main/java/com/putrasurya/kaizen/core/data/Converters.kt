package com.putrasurya.kaizen.core.data

import androidx.room.TypeConverter
import com.putrasurya.kaizen.core.time.Dates
import java.time.LocalDate

/** Lets Room store [LocalDate] as "YYYY-MM-DD" text (ADR-004). */
class Converters {
    @TypeConverter
    fun dateToKey(date: LocalDate?): String? = date?.let(Dates::toKey)

    @TypeConverter
    fun keyToDate(key: String?): LocalDate? = key?.let(Dates::fromKey)
}
