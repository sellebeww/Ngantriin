package org.umn.ngantriin.di

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.umn.ngantriin.core.ConnectivityObserver
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.SupabaseClientFactory
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
 * Manual dependency injection.
 *
 * The graph is small, fixed and known at startup, so a container of `by lazy`
 * properties gives the same wiring a DI framework would without an annotation
 * processor in the build or generated code to step through. Everything is
 * constructor-injected below this point, so swapping an implementation in a
 * test is a matter of calling a different constructor.
 */
class AppContainer(private val context: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * True when no Supabase credentials are configured. Everything still
     * works, backed by [DemoBackend]; the UI says so rather than pretending
     * it is talking to a server.
     */
    val isDemoMode: Boolean = !SupabaseClientFactory.isConfigured

    private val supabase: SupabaseClient? by lazy { SupabaseClientFactory.createOrNull() }

    val database: NgantriinDatabase by lazy { NgantriinDatabase.get(context) }

    val preferences: UserPreferencesDataStore by lazy { UserPreferencesDataStore(context) }

    val userPreferencesRepository: UserPreferencesRepository get() = preferences

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(context) }

    val locationProvider: LocationProvider by lazy { LocationProvider(context) }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper(context) }

    val locationTracker: LocationTracker by lazy {
        LocationTracker(locationProvider, userPreferencesRepository, applicationScope)
    }

    private val demoBackend: DemoBackend by lazy { DemoBackend(database) }

    // --- Remote data sources -------------------------------------------------

    private val authRemote: AuthRemoteDataSource by lazy {
        supabase?.let { SupabaseAuthRemoteDataSource(it) } ?: DemoAuthRemoteDataSource(preferences)
    }

    private val restaurantRemote: RestaurantRemoteDataSource by lazy {
        supabase?.let { SupabaseRestaurantRemoteDataSource(it) }
            ?: DemoRestaurantRemoteDataSource(demoBackend)
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
            notificationRepository = notificationRepository,
            notificationDao = database.notificationDao(),
            notificationHelper = notificationHelper
        )
    }
}
