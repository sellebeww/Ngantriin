package org.umn.ngantriin.domain.model

import java.time.Instant

data class Review(
    val id: String,
    val restaurantId: String,
    val userId: String,
    /** Ulasan selalu terikat ke satu tiket yang sudah selesai. */
    val queueId: String,
    val rating: Int,
    val comment: String,
    val createdAt: Instant,
    val authorName: String? = null
)
