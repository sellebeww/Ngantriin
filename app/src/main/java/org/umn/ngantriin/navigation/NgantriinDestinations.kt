package org.umn.ngantriin.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.vector.ImageVector
import org.umn.ngantriin.R

/**
 * Every route in one place (section 43: no route strings scattered through
 * composables). Routes that take an argument expose a `create(...)` helper so
 * callers cannot build a malformed path.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"

    const val HOME = "home"
    const val SEARCH = "search"
    const val ACTIVE_QUEUE = "active_queue"
    const val HISTORY = "history"
    const val PROFILE = "profile"

    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "notifications"

    const val ARG_RESTAURANT_ID = "restaurantId"
    const val ARG_QUEUE_ID = "queueId"

    const val RESTAURANT_DETAIL = "restaurant/{$ARG_RESTAURANT_ID}"
    fun restaurantDetail(restaurantId: String) = "restaurant/$restaurantId"

    const val QUEUE_CONFIRMATION = "queue_confirmation/{$ARG_QUEUE_ID}"
    fun queueConfirmation(queueId: String) = "queue_confirmation/$queueId"

    const val CHECK_IN = "check_in/{$ARG_QUEUE_ID}"
    fun checkIn(queueId: String) = "check_in/$queueId"

    const val QUEUE_COMPLETED = "queue_completed/{$ARG_QUEUE_ID}"
    fun queueCompleted(queueId: String) = "queue_completed/$queueId"

    const val REVIEW = "review/{$ARG_QUEUE_ID}"
    fun review(queueId: String) = "review/$queueId"

    const val STAFF_DASHBOARD = "staff_dashboard"

    const val STAFF_QUEUE = "staff_queue/{$ARG_RESTAURANT_ID}"
    fun staffQueue(restaurantId: String) = "staff_queue/$restaurantId"

    const val STAFF_CUSTOMER = "staff_customer/{$ARG_QUEUE_ID}"
    fun staffCustomer(queueId: String) = "staff_customer/$queueId"
}

/** Section 4. The five customer tabs, in order. */
enum class BottomTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH(Routes.SEARCH, R.string.nav_search, Icons.Filled.Search, Icons.Outlined.Search),
    QUEUE(
        Routes.ACTIVE_QUEUE,
        R.string.nav_queue,
        Icons.Filled.ConfirmationNumber,
        Icons.Outlined.ConfirmationNumber
    ),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.Filled.History, Icons.Outlined.History),
    PROFILE(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person);

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}
