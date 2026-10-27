package org.umn.ngantriin.domain.repository

import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.Review

interface ReviewRepository {

    fun observeReviews(restaurantId: String): Flow<List<Review>>

    suspend fun refreshReviews(restaurantId: String): Outcome<Unit>

    /**
     * Membuat ulasan, atau memperbarui rating dan teks kalau kunjungan ini
     * sudah diulas. Ditolak dengan [org.umn.ngantriin.core.AppError.ReviewNotAllowed]
     * kecuali tiket milik pemanggil dan berstatus COMPLETED.
     */
    suspend fun submitReview(
        queueId: String,
        restaurantId: String,
        rating: Int,
        comment: String
    ): Outcome<Review>

    suspend fun reviewForQueue(queueId: String): Outcome<Review?>

    /** Tampilan live ulasan untuk satu kunjungan, kalau ada. */
    fun observeReviewForQueue(queueId: String): Flow<Review?>

    /**
     * Menarik semua ulasan yang pernah ditulis pelanggan ke cache, supaya
     * riwayat menandai kunjungan yang sudah diulas dengan benar di instalasi baru juga.
     */
    suspend fun refreshUserReviews(userId: String): Outcome<Unit>
}
