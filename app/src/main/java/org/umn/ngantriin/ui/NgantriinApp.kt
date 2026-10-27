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
 * Sesi dan flag onboarding menentukan kamu ada di area aplikasi yang mana,
 * layar tidak pernah berpindah melewati batas itu sendiri. Jadi logout di mana
 * pun mendarat di login, dan login mendarat di beranda yang sesuai role.
 */
/** Ketukan notifikasi: tiket mana, dan apa yang terjadi padanya. */
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

            val recoveryPending by container.authRepository.recoveryPending
                .collectAsStateWithLifecycle()

            val area = resolveArea(session, onboardingCompleted, recoveryPending)

            // Minta izin notifikasi hanya setelah pengguna benar-benar di dalam
            // aplikasi dan update antrean mungkin penting baginya.
            RequestNotificationPermissionOnce(enabled = session is SessionState.SignedIn)

            if (area == null) {
                SplashScreen()
                return@NgantriinTheme
            }

            var currentArea by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(area) {
                val previous = currentArea
                currentArea = area
                // NavHost sudah mulai di area pertama, hanya perubahan berikutnya
                // yang butuh navigasi sungguhan.
                if (previous != null && previous != area) {
                    navController.navigate(area) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            // Dibuka dari notifikasi: langsung ke hal yang dimaksud.
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

            // Mendaftar sebagai staf restoran: pengajuan jadi langkah berikutnya.
            // Dicocokkan dengan email supaya akun lain di HP ini tidak ikut terseret.
            val applicantEmail by container.userPreferencesRepository.staffApplicantEmail
                .collectAsStateWithLifecycle(null)
            val sessionEmail = (session as? SessionState.SignedIn)?.user?.email
            LaunchedEffect(area, currentArea, applicantEmail, sessionEmail) {
                if (area == Routes.HOME && currentArea == area && applicantEmail != null &&
                    sessionEmail.equals(applicantEmail, ignoreCase = true)
                ) {
                    container.userPreferencesRepository.setStaffApplicantEmail(null)
                    navController.navigate(Routes.REGISTER_RESTAURANT) { launchSingleTop = true }
                }
            }

            // Backend menulis push dengan bahasa yang sedang ditampilkan aplikasi.
            // Dijalankan ulang di setiap composition root, karena ganti bahasa
            // membuat activity dibuat ulang, jadi ini ikut berubah.
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

            // Tiket aktif tetap dilacak setelah aplikasi keluar dari layar.
            // Dimulai dari sini karena Android hanya mengizinkan foreground
            // service dimulai saat aplikasi terlihat.
            val liveTicketId = activeQueue?.entry?.id
            LaunchedEffect(liveTicketId) {
                if (liveTicketId != null) QueueTrackingService.start(context)
            }

            // Bereaksi saat tiket *berakhir*: selesai -> terima kasih dan ulasan,
            // kedaluwarsa atau dihapus -> beri tahu, jangan diam-diam
            // mengosongkan tab antrean.
            val latestTicket by remember(container, signedInUserId) {
                signedInUserId?.let { container.queueRepository.observeLatestTicket(it) }
                    ?: flowOf(null)
            }.collectAsStateWithLifecycle(null)
            var lastSeenTicket by remember { mutableStateOf<QueueEntry?>(null) }
            // Diambil saat composition (seperti error di NgantriinNavHost) supaya
            // teksnya ikut berubah saat ganti bahasa dan tidak basi.
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
                    // Window-nya edge to edge, jadi keyboard tidak mengubah ukurannya.
                    // Tanpa ini IME menutupi apa pun yang ada di bawah layar,
                    // biasanya tombol utamanya.
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
 * Null berarti "belum diputuskan": splash tetap tampil, supaya login tidak
 * berkedip di depan orang yang sebenarnya sudah masuk.
 */
private fun resolveArea(
    session: SessionState,
    onboardingCompleted: Boolean?,
    recoveryPending: Boolean
): String? = when {
    onboardingCompleted == null || session is SessionState.Loading -> null
    !onboardingCompleted -> Routes.ONBOARDING
    // Tautan reset membuat orangnya login. Tahan di layar password baru,
    // jangan lepas ke aplikasi dengan password lama.
    recoveryPending && session is SessionState.SignedIn -> Routes.RESET_PASSWORD
    session is SessionState.SignedOut -> Routes.LOGIN
    // Hanya routing. Apa yang bisa dilakukan admin ditentukan database.
    session is SessionState.SignedIn && session.user.role == UserRole.ADMIN ->
        Routes.ADMIN_REVIEW
    session is SessionState.SignedIn && session.user.role == UserRole.STAFF ->
        Routes.STAFF_DASHBOARD
    else -> Routes.HOME
}
