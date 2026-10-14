package org.umn.ngantriin.data.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.umn.ngantriin.core.AppError
import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.runCatchingOutcome
import org.umn.ngantriin.data.local.dao.QueueDao
import org.umn.ngantriin.data.local.dao.ReviewDao
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.mapper.toEntity
import org.umn.ngantriin.data.remote.datasource.AuthRemoteDataSource
import org.umn.ngantriin.data.remote.datasource.ReviewRemoteDataSource
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.repository.ReviewRepository
import org.umn.ngantriin.work.OfflineWriteQueue
import org.umn.ngantriin.work.PendingAction

class ReviewRepositoryImpl(
    private val remote: ReviewRemoteDataSource,
    private val auth: AuthRemoteDataSource,
    private val reviewDao: ReviewDao,
    private val queueDao: QueueDao,
    private val offlineWriteQueue: OfflineWriteQueue
) : ReviewRepository {

    override fun observeReviews(restaurantId: String): Flow<List<Review>> = channelFlow {
        launch {
            reviewDao.observeForRestaurant(restaurantId)
                .map { entities -> entities.map { it.toDomain() } }
                .distinctUntilChanged()
                .collect { send(it) }
        }
        launch { refreshReviews(restaurantId) }
    }

    override suspend fun refreshReviews(restaurantId: String): Outcome<Unit> =
        runCatchingOutcome {
            val reviews = remote.fetchReviews(restaurantId)
            reviewDao.upsert(reviews.map { it.toEntity() })
        }

    override suspend fun submitReview(
        queueId: String,
        restaurantId: String,
        rating: Int,
        comment: String
    ): Outcome<Review> {
        val userId = auth.currentUserId()
            ?: return Outcome.Failure(AppError.SessionExpired)

        // Section 20. The backend enforces this too; checking here just means
        // the user gets a clear message instead of a policy violation.
        val ticket = queueDao.findById(queueId)?.toDomain()
        if (ticket != null && ticket.status != QueueStatus.COMPLETED) {
            return Outcome.Failure(AppError.ReviewNotAllowed)
        }

        val outcome = runCatchingOutcome {
            // One review per visit (reviews.queue_id is unique): a second
            // submit edits the first instead of failing on the constraint.
            val existing = reviewDao.findByQueue(queueId)?.toDomain()
                ?: runCatching { remote.fetchReviewForQueue(queueId) }.getOrNull()
            val saved = if (existing != null) {
                remote.updateReview(existing.id, rating, comment)
            } else {
                remote.submitReview(queueId, restaurantId, userId, rating, comment)
            }
            saved.also { reviewDao.upsert(it.toEntity()) }
        }

        if (outcome is Outcome.Failure && outcome.error is AppError.Network) {
            offlineWriteQueue.park(
                PendingAction.SubmitReview(queueId, restaurantId, rating, comment)
            )
            return Outcome.Failure(AppError.OfflineActionQueued)
        }
        return outcome
    }

    override fun observeReviewForQueue(queueId: String): Flow<Review?> =
        reviewDao.observeByQueue(queueId)
            .map { it?.toDomain() }
            .distinctUntilChanged()

    override suspend fun refreshUserReviews(userId: String): Outcome<Unit> = runCatchingOutcome {
        val reviews = remote.fetchUserReviews(userId)
        if (reviews.isNotEmpty()) reviewDao.upsert(reviews.map { it.toEntity() })
    }

    override suspend fun reviewForQueue(queueId: String): Outcome<Review?> = runCatchingOutcome {
        reviewDao.findByQueue(queueId)?.toDomain()
            ?: runCatching { remote.fetchReviewForQueue(queueId) }
                .onFailure { Log.i("Ngantriin", "Review lookup failed", it) }
                .getOrNull()
                ?.also { reviewDao.upsert(it.toEntity()) }
    }
}
