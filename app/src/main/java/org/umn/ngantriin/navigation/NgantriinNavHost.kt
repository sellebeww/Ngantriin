package org.umn.ngantriin.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.ui.admin.AdminApplicationScreen
import org.umn.ngantriin.ui.admin.AdminReviewScreen
import org.umn.ngantriin.ui.auth.LoginScreen
import org.umn.ngantriin.ui.auth.RegisterScreen
import org.umn.ngantriin.ui.auth.ResetPasswordScreen
import org.umn.ngantriin.ui.history.HistoryDetailScreen
import org.umn.ngantriin.ui.history.HistoryScreen
import org.umn.ngantriin.ui.home.HomeScreen
import org.umn.ngantriin.ui.notifications.NotificationsScreen
import org.umn.ngantriin.ui.onboarding.OnboardingScreen
import org.umn.ngantriin.ui.profile.AboutScreen
import org.umn.ngantriin.ui.profile.ProfileScreen
import org.umn.ngantriin.ui.profile.SettingsScreen
import org.umn.ngantriin.ui.queue.ActiveQueueScreen
import org.umn.ngantriin.ui.queue.CheckInScreen
import org.umn.ngantriin.ui.queue.JoinQueueConfirmationScreen
import org.umn.ngantriin.ui.queue.QueueCompletedScreen
import org.umn.ngantriin.ui.restaurant.RestaurantDetailScreen
import org.umn.ngantriin.ui.review.ReviewScreen
import org.umn.ngantriin.ui.search.SearchScreen
import org.umn.ngantriin.ui.splash.SplashScreen
import org.umn.ngantriin.ui.staff.RegisterRestaurantScreen
import org.umn.ngantriin.ui.staff.StaffCustomerDetailScreen
import org.umn.ngantriin.ui.staff.StaffDashboardScreen
import org.umn.ngantriin.ui.staff.StaffQueueScreen

/**
 * Satu graf untuk seluruh aplikasi. Area tujuan ditentukan oleh sesi
 * (lihat NgantriinApp), bukan oleh tiap layar yang berpindah sendiri.
 */
@Composable
fun NgantriinNavHost(
    navController: NavHostController,
    startDestination: String,
    snackbarHostState: SnackbarHostState,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Kegagalan tampil sebagai snackbar. Teksnya diambil dengan
    // stringResource saat composition, bukan dari Context, jadi ikut berubah
    // saat konfigurasi berganti dan tidak basi.
    var pendingError by remember { mutableStateOf<AppError?>(null) }
    val errorMessage = pendingError?.let {
        stringResource(it.messageRes, *it.formatArgs.toTypedArray())
    }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            pendingError = null
        }
    }
    val showError: (AppError) -> Unit = { pendingError = it }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Transisi singkat dan tenang: fade dengan sedikit geser searah gerakan,
        // jadi pindah layar terasa bergerak tanpa membuat siapa pun menunggu.
        enterTransition = { fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 12 } },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(240)) },
        popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { it / 12 } }
    ) {
        composable(Routes.SPLASH) { SplashScreen() }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = onOnboardingFinished)
        }

        // --- Auth ------------------------------------------------------------

        composable(Routes.LOGIN) {
            LoginScreen(onNavigateToRegister = { navController.navigate(Routes.REGISTER) })
        }

        composable(Routes.RESET_PASSWORD) { ResetPasswordScreen() }

        composable(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.popBackStack() })
        }

        // --- Customer tabs ---------------------------------------------------

        composable(Routes.HOME) {
            HomeScreen(
                onRestaurantClick = { navController.navigate(Routes.restaurantDetail(it)) },
                onSearchClick = { navController.navigateToTab(Routes.SEARCH) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onActiveQueueClick = { navController.navigateToTab(Routes.ACTIVE_QUEUE) }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onRestaurantClick = { navController.navigate(Routes.restaurantDetail(it)) }
            )
        }

        composable(Routes.ACTIVE_QUEUE) {
            ActiveQueueScreen(
                onFindRestaurants = { navController.navigateToTab(Routes.SEARCH) },
                onViewRestaurant = { navController.navigate(Routes.restaurantDetail(it)) },
                onCheckIn = { navController.navigate(Routes.checkIn(it)) },
                onMessage = showError
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onFindRestaurants = { navController.navigateToTab(Routes.SEARCH) },
                onRate = { navController.navigate(Routes.review(it)) },
                onOpenVisit = { navController.navigate(Routes.historyDetail(it)) }
            )
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            HistoryDetailScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onRate = { navController.navigate(Routes.review(it)) },
                onViewRestaurant = { navController.navigate(Routes.restaurantDetail(it)) },
                onOpenLiveQueue = { navController.navigateToTab(Routes.ACTIVE_QUEUE) }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenStaffDashboard = { navController.navigate(Routes.STAFF_DASHBOARD) },
                onRegisterRestaurant = { navController.navigate(Routes.REGISTER_RESTAURANT) }
            )
        }

        // --- Customer details ------------------------------------------------

        composable(
            route = Routes.RESTAURANT_DETAIL,
            arguments = listOf(navArgument(Routes.ARG_RESTAURANT_ID) { type = NavType.StringType })
        ) { entry ->
            val restaurantId = entry.arguments?.getString(Routes.ARG_RESTAURANT_ID).orEmpty()
            RestaurantDetailScreen(
                restaurantId = restaurantId,
                onBack = { navController.popBackStack() },
                onJoined = { queueId ->
                    navController.navigate(Routes.queueConfirmation(queueId)) {
                        // Layar detail sudah selesai tugasnya. Kembali ke sana dari konfirmasi
                        // akan menawarkan "Gabung Antrean" lagi untuk antrean yang sudah diikuti.
                        popUpTo(Routes.RESTAURANT_DETAIL) { inclusive = true }
                    }
                },
                onViewActiveQueue = { navController.navigateToTab(Routes.ACTIVE_QUEUE) },
                onError = showError
            )
        }

        composable(
            route = Routes.QUEUE_CONFIRMATION,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            JoinQueueConfirmationScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                // Tutup konfirmasi dulu. Kalau dibiarkan di stack, ia ikut tersimpan
                // bersama tab-nya dan muncul lagi saat tab itu dipilih berikutnya,
                // seolah pelanggan baru bergabung lagi.
                onViewMyQueue = {
                    navController.popBackStack()
                    navController.navigateToTab(Routes.ACTIVE_QUEUE)
                },
                onBackToHome = {
                    navController.popBackStack()
                    navController.navigateToTab(Routes.HOME)
                }
            )
        }

        composable(
            route = Routes.CHECK_IN,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            CheckInScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onCheckedIn = { navController.navigateToTab(Routes.ACTIVE_QUEUE) }
            )
        }

        composable(
            route = Routes.QUEUE_COMPLETED,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            val queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty()
            QueueCompletedScreen(
                queueId = queueId,
                onRate = { navController.navigate(Routes.review(it)) },
                onViewHistory = { navController.navigateToTab(Routes.HISTORY) }
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            ReviewScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.navigateToTab(Routes.HISTORY) }
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenQueue = { navController.navigateToTab(Routes.ACTIVE_QUEUE) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // --- Admin -----------------------------------------------------------

        composable(Routes.ADMIN_REVIEW) {
            AdminReviewScreen(
                onOpenApplication = { id, status ->
                    navController.navigate(Routes.adminApplication(id, status.name))
                },
                onMessage = showError
            )
        }

        composable(
            route = Routes.ADMIN_APPLICATION,
            arguments = listOf(
                navArgument(Routes.ARG_RESTAURANT_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_STATUS) { type = NavType.StringType }
            )
        ) { entry ->
            AdminApplicationScreen(
                restaurantId = entry.arguments?.getString(Routes.ARG_RESTAURANT_ID).orEmpty(),
                status = VerificationStatus.fromWire(entry.arguments?.getString(Routes.ARG_STATUS)),
                onBack = { navController.popBackStack() },
                onMessage = showError
            )
        }

        // --- Staff -----------------------------------------------------------

        composable(Routes.STAFF_DASHBOARD) {
            StaffDashboardScreen(
                onOpenQueue = { navController.navigate(Routes.staffQueue(it)) },
                onRegisterRestaurant = { navController.navigate(Routes.REGISTER_RESTAURANT) },
                onResubmit = { navController.navigate("${Routes.REGISTER_RESTAURANT}?resubmitId=$it") },
                onMessage = showError
            )
        }

        composable(
            route = "${Routes.REGISTER_RESTAURANT}?resubmitId={resubmitId}",
            arguments = listOf(
                navArgument("resubmitId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            RegisterRestaurantScreen(
                resubmitId = entry.arguments?.getString("resubmitId"),
                onBack = { navController.popBackStack() },
                // Pelanggan yang mendaftar menjadi staf, lalu aplikasi berpindah area
                // sendiri sehingga tidak ada yang bisa di-pop. Kalau tetap di-pop,
                // aplikasi tertutup.
                onRegistered = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.STAFF_QUEUE,
            arguments = listOf(navArgument(Routes.ARG_RESTAURANT_ID) { type = NavType.StringType })
        ) { entry ->
            val restaurantId = entry.arguments?.getString(Routes.ARG_RESTAURANT_ID).orEmpty()
            StaffQueueScreen(
                restaurantId = restaurantId,
                onBack = { navController.popBackStack() },
                onOpenCustomer = { queueId ->
                    navController.navigate("${Routes.staffCustomer(queueId)}?restaurantId=$restaurantId")
                },
                onMessage = showError
            )
        }

        composable(
            route = "${Routes.STAFF_CUSTOMER}?restaurantId={${Routes.ARG_RESTAURANT_ID}}",
            arguments = listOf(
                navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_RESTAURANT_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            StaffCustomerDetailScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                restaurantId = entry.arguments?.getString(Routes.ARG_RESTAURANT_ID).orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Semantik navigasi tab: satu instance per tab, state dipertahankan, dan
 * tombol back selalu kembali ke Beranda, bukan menelusuri riwayat tab.
 */
fun NavHostController.navigateToTab(route: String) {
    // Tab sudah ada di back stack dengan sesuatu di atasnya, misalnya layar
    // check-in di atas Antrean Saya, atau ulasan di atas Riwayat. Pop kembali
    // ke tab itu. Kalau memakai saveState/restoreState, seluruh stack yang
    // di-pop akan tersimpan di tab ini lalu dipulihkan lagi, sehingga "Selesai"
    // mendarat di layar yang sama dengan tempat tombolnya ditekan.
    if (popBackStack(route, inclusive = false)) return

    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
