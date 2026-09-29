package org.umn.ngantriin.domain.model

import androidx.annotation.StringRes
import org.umn.ngantriin.R

/** Section 9 — sorting options offered on the discovery screen. */
enum class RestaurantSort(@param:StringRes val labelRes: Int) {
    NEAREST(R.string.sort_nearest),
    SHORTEST_WAIT(R.string.sort_shortest_wait),
    HIGHEST_RATED(R.string.sort_highest_rated),
    MOST_POPULAR(R.string.sort_most_popular)
}

/** Section 9 — the filter set. All fields are optional and combine with AND. */
data class RestaurantFilters(
    val query: String = "",
    val category: String? = null,
    /** Hide anything further away than this, in metres. */
    val maxDistanceMeters: Double? = null,
    /** Hide anything with more groups waiting than this. */
    val maxQueueLength: Int? = null,
    /** Hide anything estimated to take longer than this. */
    val maxWaitMinutes: Int? = null,
    val minRating: Double? = null,
    val openNowOnly: Boolean = false,
    val sort: RestaurantSort = RestaurantSort.NEAREST
) {
    /** Drives the "filters active" dot on the filter button. */
    val activeFilterCount: Int
        get() = listOfNotNull(
            category,
            maxDistanceMeters,
            maxQueueLength,
            maxWaitMinutes,
            minRating,
            true.takeIf { openNowOnly }
        ).size

    val isDefault: Boolean get() = activeFilterCount == 0 && sort == RestaurantSort.NEAREST
}
