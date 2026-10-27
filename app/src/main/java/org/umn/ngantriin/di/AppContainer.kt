package org.umn.ngantriin.di

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import org.umn.ngantriin.core.ConnectivityObserver
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.SupabaseClientFactory
import org.umn.ngantriin.data.remote.datasource.AdminRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.SupabaseAdminRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoAdminRemoteDataSource
import org.umn.ngantriin.data.repository.AdminRepositoryImpl
import org.umn.ngantriin.domain.repository.AdminRepository
import org.umn.ngantriin.data.remote.datasource.AuthRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.NotificationRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.QueueRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.RestaurantRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.ReviewRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.SupabaseAuthRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.SupabaseNotificationRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.SupabaseQueueRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.SupabaseRestaurantRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.SupabaseReviewRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoAuthRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoBackend
import org.umn.ngantriin.data.remote.demo.DemoNotificationRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoQueueRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoRestaurantRemoteDataSource
import org.umn.ngantriin.data.remote.demo.DemoReviewRemoteDataSource
import org.umn.ngantriin.data.repository.AuthRepositoryImpl
import org.umn.ngantriin.data.repository.NotificationRepositoryImpl
import org.umn.ngantriin.data.repository.QueueRepositoryImpl
import org.umn.ngantriin.data.repository.RestaurantRepositoryImpl
import org.umn.ngantriin.data.repository.ReviewRepositoryImpl
import org.umn.ngantriin.domain.repository.AuthRepository
import org.umn.ngantriin.domain.repository.NotificationRepository
import org.umn.ngantriin.domain.repository.QueueRepository
import org.umn.ngantriin.domain.repository.RestaurantRepository
import org.umn.ngantriin.domain.repository.ReviewRepository
import org.umn.ngantriin.domain.repository.UserPreferencesRepository
import org.umn.ngantriin.location.LocationProvider
import org.umn.ngantriin.location.LocationTracker
import org.umn.ngantriin.notification.NotificationHelper
import org.umn.ngantriin.notification.PushTokenSync
import org.umn.ngantriin.notification.QueueNotificationScheduler
import org.umn.ngantriin.work.OfflineWriteQueue
import org.umn.ngantriin.work.PendingActionQueue

/**
 * Dependency injection manual.
 *
 * Grafnya kecil, tetap, dan sudah diketahui saat startup, jadi container berisi
 * properti `by lazy` memberi wiring yang sama dengan framework DI, tanpa annotation
 * processor di build atau kode hasil generate yang harus ditelusuri. Semua di bawah
 * ini memakai constructor injection, jadi mengganti implementasi di tes cukup dengan
 * memanggil constructor lain.
 */
class AppContainer(private val context: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * True kalau kredensial Supabase tidak diatur. Semuanya tetap jalan dengan
     * [DemoBackend], dan UI mengatakannya terus terang, bukan pura-pura
     * terhubung ke server.
     */
    val isDemoMode: Boolean = !SupabaseClientFactory.isConfigured

    private val supabase: SupabaseClient? by lazy { SupabaseClientFactory.createOrNull() }

    // Mode contoh punya file database sendiri, supaya restoran dan tiket demo
    // tidak pernah ada di cache build yang terhubung ke proyek asli.
    val database: NgantriinDatabase by lazy { NgantriinDatabase.get(context, demo = isDemoMode) }

    val preferences: UserPreferencesDataStore by lazy { UserPreferencesDataStore(context) }

    val userPreferencesRepository: UserPreferencesRepository get() = preferences

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(context) }

    val locationProvider: LocationProvider by lazy { LocationProvider(context) }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper(context) }

    val locationTracker: LocationTracker by lazy {
        LocationTracker(locationProvider, userPreferencesRepository, applicationScope)
    }

    private val demoBackend: DemoBackend by lazy { DemoBackend(database, preferences) }

    // --- Remote data sources -------------------------------------------------

    private val authRemote: AuthRemoteDataSource by lazy {
        supabase?.let { SupabaseAuthRemoteDataSource(it) } ?: DemoAuthRemoteDataSource(preferences)
    }

    private val restaurantRemote: RestaurantRemoteDataSource by lazy {
        supabase?.let { SupabaseRestaurantRemoteDataSource(it) }
            ?: DemoRestaurantRemoteDataSource(
                demoBackend,
                authRemote,
                preferences,
                File(context.filesDir, "restaurant-photos")
            )
    }

    private val adminRemote: AdminRemoteDataSource by lazy {
        supabase?.let { SupabaseAdminRemoteDataSource(it) }
            ?: DemoAdminRemoteDataSource(demoBackend, authRemote)
    }

    private val queueRemote: QueueRemoteDataSource by lazy {
        supabase?.let { SupabaseQueueRemoteDataSource(it) }
            ?: DemoQueueRemoteDataSource(demoBackend, authRemote)
    }

    private val reviewRemote: ReviewRemoteDataSource by lazy {
        supabase?.let { SupabaseReviewRemoteDataSource(it) }
            ?: DemoReviewRemoteDataSource(demoBackend)
    }

    private val notificationRemote: NotificationRemoteDataSource by lazy {
        supabase?.let { SupabaseNotificationRemoteDataSource(it) }
            ?: DemoNotificationRemoteDataSource()
    }

    // --- Offline write queue -------------------------------------------------

    val pendingActionQueue: PendingActionQueue by lazy {
        PendingActionQueue(database.pendingActionDao())
    }

    private val offlineWriteQueue: OfflineWriteQueue by lazy {
        OfflineWriteQueue(context, pendingActionQueue)
    }

    // --- Repositories --------------------------------------------------------

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(authRemote, database, applicationScope)
    }

    val restaurantRepository: RestaurantRepository by lazy {
        RestaurantRepositoryImpl(restaurantRemote, database.restaurantDao())
    }

    val queueRepository: QueueRepository by lazy {
        QueueRepositoryImpl(
            remote = queueRemote,
            restaurantRemote = restaurantRemote,
            queueDao = database.queueDao(),
            restaurantDao = database.restaurantDao(),
            offlineWriteQueue = offlineWriteQueue
        )
    }

    val adminRepository: AdminRepository by lazy { AdminRepositoryImpl(adminRemote) }

    val reviewRepository: ReviewRepository by lazy {
        ReviewRepositoryImpl(
            remote = reviewRemote,
            auth = authRemote,
            reviewDao = database.reviewDao(),
            queueDao = database.queueDao(),
            offlineWriteQueue = offlineWriteQueue
        )
    }

    val notificationRepository: NotificationRepository by lazy {
        NotificationRepositoryImpl(notificationRemote, database.notificationDao())
    }

    // --- Services ------------------------------------------------------------

    val pushTokenSync: PushTokenSync by lazy {
        PushTokenSync(authRepository, notificationHelper)
    }

    val queueNotificationScheduler: QueueNotificationScheduler by lazy {
        QueueNotificationScheduler(
            context = context,
            authRepository = authRepository,
            queueRepository = queueRepository,
            restaurantRepository = restaurantRepository,
            notificationRepository = notificationRepository,
            notificationDao = database.notificationDao(),
            notificationHelper = notificationHelper,
            preferences = userPreferencesRepository
        )
    }
}
