package org.umn.ngantriin.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalTime

class Converters {

    @TypeConverter
    fun instantToEpoch(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** Stored as seconds-of-day so times sort correctly in SQL. */
    @TypeConverter
    fun localTimeToSeconds(value: LocalTime?): Int? = value?.toSecondOfDay()

    @TypeConverter
    fun secondsToLocalTime(value: Int?): LocalTime? =
        value?.let { LocalTime.ofSecondOfDay(it.toLong()) }
}
