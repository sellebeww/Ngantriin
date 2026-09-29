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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.umn.ngantriin.di.AppContainer
import org.umn.ngantriin.di.LocalAppContainer
import org.umn.ngantriin.domain.model.UserRole
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
 * Section 35. The session and the onboarding flag decide which area of the app
 * you are in; individual screens never navigate across that boundary
 * themselves. Signing out anywhere therefore lands on login, and signing in
 * lands on the right home for the role.
 */
@Composable
fun NgantriinApp(
    container: AppContainer,
    pendingQueueId: String? = null
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        NgantriinTheme {
            val navController = rememberNavController()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

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

            // Opened from a push: jump straight to the ticket it is about.
            LaunchedEffect(pendingQueueId, area) {
                if (pendingQueueId != null && area == Routes.HOME) {
                    navController.navigateToTab(Routes.ACTIVE_QUEUE)
                }
            }

            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.route
            val showBottomBar = currentRoute in BottomTab.routes

            val activeQueue by remember(container, session) {
                when (val state = session) {
                    is SessionState.SignedIn ->
                        container.queueRepository.observeActiveQueue(state.user.id)
                    else -> kotlinx.coroutines.flow.flowOf(null)
                }
            }.collectAsStateWithLifecycle(null)

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
