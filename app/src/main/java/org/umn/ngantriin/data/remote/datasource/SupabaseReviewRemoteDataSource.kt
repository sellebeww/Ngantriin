package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.ReviewDto
import org.umn.ngantriin.data.remote.dto.ReviewInsertDto
import org.umn.ngantriin.domain.model.Review

class SupabaseReviewRemoteDataSource(
    private val client: SupabaseClient
) : ReviewRemoteDataSource {

    override suspend fun fetchReviews(restaurantId: String): List<Review> =
        client.from("reviews")
            .select {
                filter { eq("restaurant_id", restaurantId) }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<ReviewDto>()
            .map { it.toDomain() }

    override suspend fun fetchReviewForQueue(queueId: String): Review? =
        client.from("reviews")
            .select { filter { eq("queue_id", queueId) } }
            .decodeSingleOrNull<ReviewDto>()
            ?.toDomain()

    /**
     * The insert is allowed only by the `reviews_insert_completed_only` policy,
     * which re-checks that the ticket is the caller's and is COMPLETED
     * (section 20). No client-side guard can be bypassed by skipping the UI.
     */
    override suspend fun submitReview(
        queueId: String,
        restaurantId: String,
        userId: String,
        rating: Int,
        comment: String
    ): Review = client.from("reviews")
        .insert(
            ReviewInsertDto(
                restaurantId = restaurantId,
                userId = userId,
                queueId = queueId,
                rating = rating.coerceIn(1, 5),
                review = comment.trim()
            )
        ) { select() }
        .decodeSingle<ReviewDto>()
        .toDomain()
}
