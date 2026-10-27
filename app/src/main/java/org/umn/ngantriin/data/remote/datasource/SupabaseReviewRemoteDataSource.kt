package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.ReviewDto
import org.umn.ngantriin.data.remote.dto.ReviewInsertDto
import org.umn.ngantriin.data.remote.dto.ReviewUpdateDto
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

    override suspend fun fetchUserReviews(userId: String): List<Review> =
        client.from("reviews")
            .select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<ReviewDto>()
            .map { it.toDomain() }

    /** Column grant (0012) hanya mengizinkan rating dan review yang berubah. */
    override suspend fun updateReview(reviewId: String, rating: Int, comment: String): Review =
        client.from("reviews")
            .update(ReviewUpdateDto(rating = rating.coerceIn(1, 5), review = comment.trim())) {
                filter { eq("id", reviewId) }
                select()
            }
            .decodeSingle<ReviewDto>()
            .toDomain()

    /**
     * Insert hanya diizinkan policy `reviews_insert_completed_only`, yang
     * mengecek ulang bahwa tiket milik pemanggil dan berstatus COMPLETED.
     * Tidak ada guard di client yang bisa dilewati dengan melompati UI.
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
