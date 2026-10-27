package org.umn.ngantriin.domain.model

import androidx.annotation.StringRes
import org.umn.ngantriin.R

/** Pilihan urutan di layar pencarian. */
enum class RestaurantSort(@param:StringRes val labelRes: Int) {
    NEAREST(R.string.sort_nearest),
    SHORTEST_WAIT(R.string.sort_shortest_wait),
    HIGHEST_RATED(R.string.sort_highest_rated),
    MOST_POPULAR(R.string.sort_most_popular)
}

/** Kumpulan filter. Semua field opsional dan digabung dengan AND. */
data class RestaurantFilters(
    val query: String = "",
    val category: String? = null,
    /** Sembunyikan yang lebih jauh dari ini, dalam meter. */
    val maxDistanceMeters: Double? = null,
    /** Sembunyikan yang antreannya lebih banyak dari ini. */
    val maxQueueLength: Int? = null,
    /** Sembunyikan yang estimasinya lebih lama dari ini. */
    val maxWaitMinutes: Int? = null,
    val minRating: Double? = null,
    val openNowOnly: Boolean = false,
    val sort: RestaurantSort = RestaurantSort.NEAREST
) {
    /** Menentukan titik "filter aktif" di tombol filter. */
    val activeFilterCount: Int
        get() = listOfNotNull(
            category,
            maxDistanceMeters,
            maxQueueLength,
            maxWaitMinutes,
            minRating,
            true.takeIf { openNowOnly }
        ).size
}
