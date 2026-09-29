package org.umn.ngantriin.domain.model

import java.time.Instant

data class Review(
    val id: String,
    val restaurantId: String,
    val userId: String,
    /** Section 20: a review is always anchored to one completed ticket. */
    val queueId: String,
    val rating: Int,
    val comment: String,
    val createdAt: Instant,
    val authorName: String? = null
)
