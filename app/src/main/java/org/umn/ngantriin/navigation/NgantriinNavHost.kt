package org.umn.ngantriin.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.umn.ngantriin.core.AppError
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
import org.umn.ngantriin.ui.staff.StaffCustomerDetailScreen
import org.umn.ngantriin.ui.staff.StaffDashboardScreen
import org.umn.ngantriin.ui.staff.StaffQueueScreen
import org.umn.ngantriin.ui.theme.Motion

/**
 * One graph for the whole app; which area you land in is decided
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
        modifier = modifier,
        // Short, quiet transitions: a fade with a small drift in the direction
        // of travel, so moving between screens reads as movement without
        // making anyone wait for it.
        enterTransition = {
            fadeIn(tween(Motion.ENTER_MS)) + slideInHorizontally(tween(Motion.ENTER_MS)) { it / 12 }
        },
        exitTransition = { fadeOut(tween(Motion.EXIT_MS)) },
        // Back (and the predictive-back gesture, which scrubs these): the
        // screen you leave shrinks a little and fades over the one beneath,
        // which is already in place — the system's own back motion.
        popEnterTransition = { fadeIn(tween(Motion.ENTER_MS), initialAlpha = 0.6f) },
        popExitTransition = {
            scaleOut(tween(Motion.ENTER_MS), targetScale = 0.92f) + fadeOut(tween(Motion.ENTER_MS))
        }
    ) {
        destination(Routes.SPLASH) { SplashScreen() }

        destination(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = onOnboardingFinished)
        }

        // --- Auth ------------------------------------------------------------

        destination(Routes.LOGIN) {
            LoginScreen(onNavigateToRegister = { navController.navigate(Routes.REGISTER) })
        }

        destination(Routes.RESET_PASSWORD) { ResetPasswordScreen() }

        destination(Routes.REGISTER) {
            RegisterScreen(onBack = { navController.popBackStack() })
        }

        // --- Customer tabs ---------------------------------------------------

        destination(Routes.HOME) {
            HomeScreen(
                onRestaurantClick = { navController.navigate(Routes.restaurantDetail(it)) },
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        destination(Routes.SEARCH) {
            SearchScreen(
                onRestaurantClick = { navController.navigate(Routes.restaurantDetail(it)) },
                onBack = { navController.popBackStack() }
            )
        }

        destination(Routes.ACTIVE_QUEUE) {
            ActiveQueueScreen(
                onFindRestaurants = { navController.navigateToTab(Routes.HOME) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                onViewRestaurant = { navController.navigate(Routes.restaurantDetail(it)) },
                onCheckIn = { navController.navigate(Routes.checkIn(it)) },
                onMessage = showError
            )
        }

        destination(Routes.HISTORY) {
            HistoryScreen(
                onFindRestaurants = { navController.navigateToTab(Routes.HOME) },
                onRate = { navController.navigate(Routes.review(it)) },
                onOpenVisit = { navController.navigate(Routes.historyDetail(it)) },
                onBack = { navController.popBackStack() }
            )
        }

        destination(
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

        destination(Routes.PROFILE) {
            ProfileScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenStaffDashboard = { navController.navigate(Routes.STAFF_DASHBOARD) }
            )
        }

        // --- Customer details ------------------------------------------------

        destination(
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

        destination(
            route = Routes.QUEUE_CONFIRMATION,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            JoinQueueConfirmationScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                // Close the confirmation first. Left on the stack it would be saved
                // with the tab it sits on and come back the next time that tab
                // is selected, as if the customer had just joined again.
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

        destination(
            route = Routes.CHECK_IN,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            CheckInScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onCheckedIn = { navController.navigateToTab(Routes.ACTIVE_QUEUE) }
            )
        }

        destination(
            route = Routes.QUEUE_COMPLETED,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            val queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty()
            QueueCompletedScreen(
                queueId = queueId,
                onRate = { navController.navigate(Routes.review(it)) },
                onViewHistory = { navController.navigateToHistory() }
            )
        }

        destination(
            route = Routes.REVIEW,
            arguments = listOf(navArgument(Routes.ARG_QUEUE_ID) { type = NavType.StringType })
        ) { entry ->
            ReviewScreen(
                queueId = entry.arguments?.getString(Routes.ARG_QUEUE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.navigateToHistory() }
            )
        }

        destination(Routes.NOTIFICATIONS) {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenQueue = { navController.navigateToTab(Routes.ACTIVE_QUEUE) }
            )
        }

        destination(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }

        destination(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // --- Staff -----------------------------------------------------------

        destination(Routes.STAFF_DASHBOARD) {
            StaffDashboardScreen(
                onOpenQueue = { navController.navigate(Routes.staffQueue(it)) },
                onMessage = showError
            )
        }

        destination(
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

        destination(
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
    // The tab is already on the back stack with something on top of it — the
    // check-in screen over My Queue, a review over History. Pop back down to
    // it. Navigating with saveState/restoreState instead would save the whole
    // popped stack under this tab and restore it right back, so "Done" would
    // land on the very screen it was pressed on.
    if (popBackStack(route, inclusive = false)) return

    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** History lives under the ticket tab, so back from it lands on the ticket. */
fun NavHostController.navigateToHistory() {
    navigateToTab(Routes.ACTIVE_QUEUE)
    navigate(Routes.HISTORY) { launchSingleTop = true }
}

/**
 * A destination drawn on the page colour. Screens are otherwise transparent,
 * and two of them overlap mid-transition (most visibly while the predictive
 * back gesture is held), so each needs its own ground.
 */
private fun NavGraphBuilder.destination(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit
) = composable(route, arguments) { entry ->
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { content(entry) }
}
