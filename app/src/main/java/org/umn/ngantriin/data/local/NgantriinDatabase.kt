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
    // v2: added QueueEntryEntity.checkInPhotoUrl (photo check-in replaces GPS).
    // v3: added RestaurantEntity.availableSeats ("kursi kosong" walk-ins).
    version = 3,
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

        @Volatile
        private var instance: NgantriinDatabase? = null

        fun get(context: Context): NgantriinDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NgantriinDatabase::class.java,
                    NAME
                )
                    // The cache is disposable; a schema change can rebuild it
                    // rather than shipping a migration for every release.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
