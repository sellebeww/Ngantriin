package org.umn.ngantriin.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.umn.ngantriin.data.local.dao.NotificationDao
import org.umn.ngantriin.data.local.dao.PendingActionDao
import org.umn.ngantriin.data.local.dao.QueueDao
import org.umn.ngantriin.data.local.dao.RestaurantDao
import org.umn.ngantriin.data.local.dao.ReviewDao
import org.umn.ngantriin.data.local.entity.NotificationEntity
import org.umn.ngantriin.data.local.entity.PendingActionEntity
import org.umn.ngantriin.data.local.entity.QueueEntryEntity
import org.umn.ngantriin.data.local.entity.QueueStatsEntity
import org.umn.ngantriin.data.local.entity.RestaurantEntity
import org.umn.ngantriin.data.local.entity.ReviewEntity

@Database(
    entities = [
        RestaurantEntity::class,
        QueueStatsEntity::class,
        QueueEntryEntity::class,
        ReviewEntity::class,
        NotificationEntity::class,
        PendingActionEntity::class
    ],
    // v2: tambah QueueEntryEntity.checkInPhotoUrl (foto check-in menggantikan GPS).
    // v3: tambah RestaurantEntity.availableSeats (walk-in "kursi kosong").
    // v4: alur TABLE_READY / EXPIRED, yaitu batas check-in, waktu meja siap dan
    // kedaluwarsa, serta jendela check-in per restoran.
    // v5: RestaurantEntity.isActive. Restoran yang dijeda tetap di-cache untuk
    // tiket dan staf yang butuh, tapi hilang dari katalog.
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NgantriinDatabase : RoomDatabase() {

    abstract fun restaurantDao(): RestaurantDao
    abstract fun queueDao(): QueueDao
    abstract fun reviewDao(): ReviewDao
    abstract fun notificationDao(): NotificationDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        private const val NAME = "ngantriin.db"

        /** File sendiri untuk mode demo, supaya data demo tidak bercampur dengan cache proyek asli. */
        private const val DEMO_NAME = "ngantriin-demo.db"

        @Volatile
        private var instance: NgantriinDatabase? = null

        /** Menentukan file cache yang dipakai: mode demo tidak pernah berbagi dengan proyek asli. */
        fun fileName(demo: Boolean): String = if (demo) DEMO_NAME else NAME

        fun get(context: Context, demo: Boolean = false): NgantriinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NgantriinDatabase::class.java,
                    fileName(demo)
                )
                    // Cache ini boleh dibuang, jadi perubahan skema cukup membangunnya ulang
                    // tanpa menulis migrasi untuk tiap rilis.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
