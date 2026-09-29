package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.Review

interface ReviewRepository {

    fun observeReviews(restaurantId: String): Flow<List<Review>>

    suspend fun refreshReviews(restaurantId: String): Outcome<Unit>

    /**
     * Section 20. Rejected with
     * [org.umn.ngantriin.core.AppError.ReviewNotAllowed] unless the ticket
     * belongs to the caller and is COMPLETED.
     */
    suspend fun submitReview(
        queueId: String,
        restaurantId: String,
        rating: Int,
        comment: String
    ): Outcome<Review>

    suspend fun reviewForQueue(queueId: String): Outcome<Review?>
}
