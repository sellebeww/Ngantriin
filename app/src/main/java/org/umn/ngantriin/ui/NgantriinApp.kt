package org.umn.ngantriin.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.umn.ngantriin.R
import org.umn.ngantriin.core.LanguageManager
import org.umn.ngantriin.di.AppContainer
import org.umn.ngantriin.di.LocalAppContainer
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.UserRole
import org.umn.ngantriin.notification.QueueTrackingService
import org.umn.ngantriin.domain.repository.SessionState
import org.umn.ngantriin.navigation.BottomTab
import org.umn.ngantriin.navigation.NgantriinBottomBar
import org.umn.ngantriin.navigation.NgantriinNavHost
import org.umn.ngantriin.navigation.Routes
import org.umn.ngantriin.navigation.navigateToTab
import org.umn.ngantriin.ui.components.RequestNotificationPermissionOnce
import org.umn.ngantriin.ui.splash.SplashScreen
import org.umn.ngantriin.ui.theme.NgantriinTheme

/**
 * The session and the onboarding flag decide which area of the app
 * you are in; individual screens never navigate across that boundary
 * themselves. Signing out anywhere therefore lands on login, and signing in
 * lands on the right home for the role.
 */
/** A notification tap: which ticket, and what happened to it. */
data class NotificationLaunch(val queueId: String, val type: NotificationType?)

@Composable
fun NgantriinApp(
    container: AppContainer,
    pendingLaunch: NotificationLaunch? = null,
    onLaunchHandled: () -> Unit = {}
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        NgantriinTheme {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val context = LocalContext.current

            val session by container.authRepository.sessionState
                .collectAsStateWithLifecycle(SessionState.Loading)

            val onboardingFlow = remember(container) {
                container.userPreferencesRepository.onboardingCompleted.map { it as Boolean? }
            }
            val onboardingCompleted by onboardingFlow.collectAsStateWithLifecycle(null)

            val area = resolveArea(session, onboardingCompleted)

            // Ask for notification permission only once the user is actually
            // in the app and a queue update could matter to them.
            RequestNotificationPermissionOnce(enabled = session is SessionState.SignedIn)

            if (area == null) {
                SplashScreen()
                return@NgantriinTheme
            }

            var currentArea by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(area) {
                val previous = currentArea
                currentArea = area
                // The NavHost already starts in the first area; only later
                // changes need an actual navigation.
                if (previous != null && previous != area) {
                    navController.navigate(area) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            // Opened from a notification: jump straight to what it is about.
            LaunchedEffect(pendingLaunch, area) {
                val launch = pendingLaunch ?: return@LaunchedEffect
                if (area != Routes.HOME) return@LaunchedEffect
                when (launch.type) {
                    NotificationType.COMPLETED ->
                        navController.navigate(Routes.queueCompleted(launch.queueId)) { launchSingleTop = true }
                    NotificationType.EXPIRED, NotificationType.CANCELLED ->
                        navController.navigate(Routes.historyDetail(launch.queueId)) { launchSingleTop = true }
                    else -> navController.navigateToTab(Routes.ACTIVE_QUEUE)
                }
                onLaunchHandled()
            }

            val signedInUserId = (session as? SessionState.SignedIn)?.user?.id

            // The backend writes pushes in the language the app is showing.
            // Re-run on every (re)composition root — a language change
            // recreates the activity, so this follows it.
            LaunchedEffect(signedInUserId) {
                if (signedInUserId != null) {
                    container.authRepository.syncLocale(LanguageManager.effective(context))
                }
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val showBottomBar = currentRoute in BottomTab.routes

            val activeQueue by remember(container, signedInUserId) {
                signedInUserId?.let { container.queueRepository.observeActiveQueue(it) }
                    ?: flowOf(null)
            }.collectAsStateWithLifecycle(null)

            // A live ticket keeps tracking after the app leaves
            // the screen. Started from here because Android only allows a
            // foreground service to start while the app is visible.
            val liveTicketId = activeQueue?.entry?.id
            LaunchedEffect(liveTicketId) {
                if (liveTicketId != null) QueueTrackingService.start(context)
            }

            // React to the ticket *ending*: completed -> thank-you and review,
            // expired or removed -> say so instead of silently emptying the
            // queue tab.
            val latestTicket by remember(container, signedInUserId) {
                signedInUserId?.let { container.queueRepository.observeLatestTicket(it) }
                    ?: flowOf(null)
            }.collectAsStateWithLifecycle(null)
            var lastSeenTicket by remember { mutableStateOf<QueueEntry?>(null) }
            // Resolved in composition (like NgantriinNavHost's errors) so the
            // copy follows a language change instead of going stale.
            var outcomeMessage by remember { mutableStateOf<Pair<Int, String>?>(null) }
            val outcomeText = outcomeMessage?.let { (res, number) -> stringResource(res, number) }
            LaunchedEffect(outcomeText) {
                if (outcomeText != null) {
                    snackbarHostState.showSnackbar(outcomeText)
                    outcomeMessage = null
                }
            }
            LaunchedEffect(latestTicket) {
                val previous = lastSeenTicket
                val current = latestTicket
                lastSeenTicket = current
                if (area != Routes.HOME || current == null || previous == null) return@LaunchedEffect
                if (previous.id != current.id || !previous.isActive || current.isActive) return@LaunchedEffect

                when (current.status) {
                    QueueStatus.COMPLETED ->
                        navController.navigate(Routes.queueCompleted(current.id)) { launchSingleTop = true }
                    QueueStatus.EXPIRED ->
                        outcomeMessage = R.string.ticket_expired_message to current.queueNumber
                    QueueStatus.CANCELLED -> if (!container.queueRepository.wasLeftByCustomer(current.id)) {
                        outcomeMessage = R.string.ticket_cancelled_by_restaurant to current.queueNumber
                    }
                    else -> Unit
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (showBottomBar) {
                        NgantriinBottomBar(
                            currentRoute = currentRoute,
                            hasActiveQueue = activeQueue != null,
                            onTabSelected = { tab -> navController.navigateToTab(tab.route) }
                        )
                    }
                }
            ) { padding ->
                NgantriinNavHost(
                    navController = navController,
                    startDestination = area,
                    snackbarHostState = snackbarHostState,
                    onOnboardingFinished = {
                        scope.launch {
                            container.userPreferencesRepository.setOnboardingCompleted(true)
                        }
                    },
                    // The window is edge to edge, so the keyboard does not
                    // resize it. Without this the IME sits on top of whatever
                    // is at the bottom of a screen — usually its main button.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding()
                )
            }
        }
    }
}

/**
 * Null means "not decided yet" — the splash stays up rather than flashing the
 * login screen at someone who is already signed in.
 */
private fun resolveArea(session: SessionState, onboardingCompleted: Boolean?): String? = when {
    onboardingCompleted == null || session is SessionState.Loading -> null
    !onboardingCompleted -> Routes.ONBOARDING
    session is SessionState.SignedOut -> Routes.LOGIN
    session is SessionState.SignedIn && session.user.role == UserRole.STAFF ->
        Routes.STAFF_DASHBOARD
    else -> Routes.HOME
}
