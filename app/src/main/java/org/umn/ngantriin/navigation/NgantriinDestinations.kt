package org.umn.ngantriin.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import org.umn.ngantriin.R

/**
 * Every route in one place (no route strings scattered through
 * composables). Routes that take an argument expose a `create(...)` helper so
 * callers cannot build a malformed path.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESET_PASSWORD = "reset_password"

    const val HOME = "home"
    const val SEARCH = "search"
    const val ACTIVE_QUEUE = "active_queue"
    const val HISTORY = "history"
    const val PROFILE = "profile"

    const val SETTINGS = "settings"
    const val ABOUT = "about"
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

    const val HISTORY_DETAIL = "history_detail/{$ARG_QUEUE_ID}"
    fun historyDetail(queueId: String) = "history_detail/$queueId"

    const val REVIEW = "review/{$ARG_QUEUE_ID}"
    fun review(queueId: String) = "review/$queueId"

    const val STAFF_DASHBOARD = "staff_dashboard"

    const val STAFF_QUEUE = "staff_queue/{$ARG_RESTAURANT_ID}"
    fun staffQueue(restaurantId: String) = "staff_queue/$restaurantId"

    const val STAFF_CUSTOMER = "staff_customer/{$ARG_QUEUE_ID}"
    fun staffCustomer(queueId: String) = "staff_customer/$queueId"
}

/**
 * The three customer tabs: find a place, hold your ticket, you. Search opens
 * from Explore and history from the ticket tab — neither is somewhere people
 * go on their own often enough to earn a tab.
 */
enum class BottomTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector
) {
    HOME(Routes.HOME, R.string.nav_explore, Icons.Filled.Storefront, Icons.Outlined.Storefront),
    QUEUE(
        Routes.ACTIVE_QUEUE,
        R.string.nav_ticket,
        Icons.Filled.ConfirmationNumber,
        Icons.Outlined.ConfirmationNumber
    ),
    PROFILE(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person);

    companion object {
        val routes: Set<String> = entries.map { it.route }.toSet()
    }
}
