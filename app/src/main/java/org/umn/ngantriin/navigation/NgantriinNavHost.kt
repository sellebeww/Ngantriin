package org.umn.ngantriin.navigation

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
import org.umn.ngantriin.ui.auth.LoginScreen
import org.umn.ngantriin.ui.auth.RegisterScreen
import org.umn.ngantriin.ui.history.HistoryScreen
import org.umn.ngantriin.ui.home.HomeScreen
import org.umn.ngantriin.ui.notifications.NotificationsScreen
import org.umn.ngantriin.ui.onboarding.OnboardingScreen
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
import org.umn.ngantriin.ui.staff.StaffCustomerDetailScreen
import org.umn.ngantriin.ui.staff.StaffDashboardScreen
import org.umn.ngantriin.ui.staff.StaffQueueScreen

/**
 * Section 35. One graph for the whole app; which area you land in is decided
 * by the session (see NgantriinApp), not by each screen navigating manually.
 */
@Composable
fun NgantriinNavHost(
    navController: NavHostController,
    startDestination: String,
    snackbarHostState: SnackbarHostState,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Failures surface as a snackbar. The copy is resolved with
    // stringResource in composition rather than from a Context, so it follows
    // a configuration change instead of going stale.
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
        modifier = modifier
    ) {
        composable(Routes.SPLASH) { SplashScreen() }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = onOnboardingFinished)
        }

        // --- Auth ------------------------------------------------------------

        composable(Routes.LOGIN) {
            LoginScreen(onNavigateToRegister = { navController.navigate(Routes.REGISTER) })
        }

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
                onRate = { navController.navigate(Routes.review(it)) },
                onMessage = showError
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onFindRestaurants = { navController.navigateToTab(Routes.SEARCH) },
                onRate = { navController.navigate(Routes.review(it)) },
                onRestaurantClick = { navController.navigate(Routes.restaurantDetail(it)) }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenStaffDashboard = { navController.navigate(Routes.STAFF_DASHBOARD) }
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
                        // The detail screen has served its purpose; coming back
                        // to it from the confirmation would offer "Join Queue"
                        // again for a queue the customer is already in.
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
                onViewMyQueue = { navController.navigateToTab(Routes.ACTIVE_QUEUE) },
                onBackToHome = { navController.navigateToTab(Routes.HOME) }
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
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        // --- Staff -----------------------------------------------------------

        composable(Routes.STAFF_DASHBOARD) {
            StaffDashboardScreen(
                onOpenQueue = { navController.navigate(Routes.staffQueue(it)) },
                onMessage = showError
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
 * Tab navigation semantics: one instance per tab, state preserved, and the
 * back button always returns to Home rather than walking the tab history.
 */
fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
