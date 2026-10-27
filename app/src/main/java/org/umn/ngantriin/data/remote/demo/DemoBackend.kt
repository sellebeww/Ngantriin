package org.umn.ngantriin.data.remote.demo

import org.umn.ngantriin.domain.model.ProofType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.umn.ngantriin.data.local.NgantriinDatabase
import org.umn.ngantriin.data.local.prefs.UserPreferencesDataStore
import org.umn.ngantriin.data.remote.dto.RestaurantDto
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.mapper.toDto
import org.umn.ngantriin.data.mapper.toEntity
import org.umn.ngantriin.domain.model.ApplicantRole
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueMath
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.RestaurantRegistration
import org.umn.ngantriin.domain.model.Review
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * An in-process stand-in for the Supabase project, used when no credentials
 * are configured so that the app is runnable end to end on a fresh checkout.
 *
 * It is not a shortcut around the real backend. It enforces the same rules the
 * SQL functions do — server-allocated ticket numbers, one active queue per
 * customer, the queue transition table, the check-in window and its
 * expiry, early check-in going straight to TABLE_READY when called — and it
 * pushes change notifications through the same Flow contract Supabase
 * Realtime uses. Swapping the two out changes nothing above the data source.
 *
 * State is mirrored into Room so a demo queue survives a restart.
 */
class DemoBackend(
    private val database: NgantriinDatabase,
    private val preferences: UserPreferencesDataStore
) {

    private val mutex = Mutex()
    private var hydrated = false

    private val restaurants = LinkedHashMap<String, Restaurant>()
    private val queues = LinkedHashMap<String, QueueEntry>()
    private val reviews = LinkedHashMap<String, Review>()
    private val lastIssuedSequence = HashMap<String, Int>()

    /** `userId|restaurantId`: who may run which venue. Nothing else grants access. */
    private val staffMemberships = LinkedHashSet<String>()

    /** restaurantId -> who applied and which proof files they sent (`restaurant_documents`). */
    private val applicationRecords = LinkedHashMap<String, DemoApplication>()

    private val _changes = MutableSharedFlow<Change>(extraBufferCapacity = 64)

    /** Mirrors what Supabase Realtime would publish. */
    val changes: SharedFlow<Change> = _changes

    data class Change(val restaurantId: String?, val userIds: Set<String>)

    /** A proof file already stored on the device; [uri] is where the reviewer opens it. */
    class ProofFile(val type: ProofType, val uri: String)

    // -------------------------------------------------------------------------
    // Hydration
    // -------------------------------------------------------------------------

    suspend fun ensureReady() {
        if (hydrated) return
        mutex.withLock {
            if (hydrated) return
            val dao = database.restaurantDao()
            val cachedRestaurants = DemoSeed.RESTAURANTS.mapNotNull { seed ->
                dao.findById(seed.id)?.toDomain()
            }

            if (cachedRestaurants.size == DemoSeed.RESTAURANTS.size) {
                cachedRestaurants.forEach { restaurants[it.id] = it }
            } else {
                DemoSeed.RESTAURANTS.forEach { restaurants[it.id] = it }
                database.restaurantDao().upsertRestaurants(restaurants.values.map { it.toEntity() })
            }

            applyRegistry()

            restaurants.keys.forEach { restaurantId ->
                val cached = database.queueDao().findAllForRestaurant(restaurantId)
                if (cached.isEmpty()) {
                    DemoSeed.seedQueue(restaurants.getValue(restaurantId)).forEach {
                        queues[it.id] = it
                    }
                } else {
                    cached.forEach { entity -> queues[entity.id] = entity.toDomain() }
                }
            }

            restaurants.keys.forEach { id ->
                lastIssuedSequence[id] = queues.values
                    .filter { it.restaurantId == id }
                    .maxOfOrNull { it.ticketSequence } ?: 0
            }

            database.queueDao().upsert(queues.values.map { it.toEntity() })
            hydrated = true
        }
    }

    /**
     * Venues staff registered, who staffs what, and which sample venues were
     * paused. Stored in preferences because Room only ever holds the active
     * catalogue and would forget a paused venue.
     */
    private suspend fun applyRegistry() {
        val saved = preferences.demoRestaurantsJson.first()
            ?.let { runCatching { json.decodeFromString<DemoRegistry>(it) }.getOrNull() }
        saved?.registered?.forEach { restaurants[it.id] = it.toDomain() }
        saved?.applications?.forEach { applicationRecords[it.restaurantId] = it }
        saved?.pausedSeedIds?.forEach { id ->
            restaurants[id]?.let { restaurants[id] = it.copy(isActive = false) }
        }

        DemoSeed.STAFF_ACCOUNTS.forEach { (email, restaurantId) ->
            staffMemberships += "${DemoSeed.demoUserId(email)}|$restaurantId"
        }
        staffMemberships += preferences.demoStaffMemberships.first()

        // Room is the customer catalogue: active venues only.
        database.restaurantDao().upsertRestaurants(
            restaurants.values.filter { it.isActive }.map { it.toEntity() }
        )
    }

    private suspend fun persistRegistry() {
        val seedIds = DemoSeed.RESTAURANTS.map { it.id }.toSet()
        val registry = DemoRegistry(
            registered = restaurants.values.filter { it.id !in seedIds }.map { it.toDto() },
            pausedSeedIds = restaurants.values.filter { it.id in seedIds && !it.isActive }.map { it.id },
            applications = applicationRecords.values.toList()
        )
        preferences.setDemoRestaurantsJson(json.encodeToString(registry))
    }

    @Serializable
    private data class DemoRegistry(
        val registered: List<RestaurantDto> = emptyList(),
        val pausedSeedIds: List<String> = emptyList(),
        val applications: List<DemoApplication> = emptyList()
    )

    @Serializable
    private data class DemoApplication(
        val restaurantId: String,
        val applicantId: String,
        val applicantName: String,
        val applicantEmail: String,
        val contactPhone: String,
        val applicantRole: String,
        val submittedAt: String,
        val documents: List<DemoDocument> = emptyList()
    )

    @Serializable
    private data class DemoDocument(
        val id: String,
        val type: String,
        val uri: String,
        val round: Int,
        val createdAt: String
    )

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /** The customer catalogue: only approved venues, and paused ones are not listed. */
    suspend fun allRestaurants(): List<Restaurant> {
        ensureReady()
        return restaurants.values.filter { it.isActive && it.isApproved }.sortedBy { it.name }
    }

    /** Like `is_restaurant_staff()`: the link counts only once the venue is approved. */
    suspend fun isStaff(userId: String, restaurantId: String): Boolean {
        ensureReady()
        return "$userId|$restaurantId" in staffMemberships && restaurants[restaurantId]?.isApproved == true
    }

    /** Every venue this account runs, paused ones included. */
    suspend fun managedRestaurants(userId: String): List<Restaurant> {
        ensureReady()
        return restaurants.values
            .filter { "$userId|${it.id}" in staffMemberships }
            .sortedBy { it.name }
    }

    /** An unreviewed venue is visible to its applicant only, like the RLS policy on `restaurants`. */
    suspend fun restaurant(id: String, viewerId: String? = null): Restaurant {
        ensureReady()
        val venue = restaurants[id] ?: error("RESTAURANT_NOT_FOUND")
        if (!venue.isApproved && (viewerId == null || "$viewerId|$id" !in staffMemberships)) {
            error("RESTAURANT_NOT_FOUND")
        }
        return venue
    }

    suspend fun allStats(): List<QueueStats> {
        ensureReady()
        val listed = restaurants.values.filter { it.isActive }.map { it.id }
        listed.forEach { expireOverdue(it) }
        return listed.map { statsFor(it) }
    }

    suspend fun stats(restaurantId: String): QueueStats {
        ensureReady()
        expireOverdue(restaurantId)
        return statsFor(restaurantId)
    }

    suspend fun queue(queueId: String): QueueEntry {
        ensureReady()
        return queues[queueId] ?: error("QUEUE_NOT_FOUND")
    }

    suspend fun activeQueueFor(userId: String): QueueEntry? {
        ensureReady()
        return queues.values
            .filter { it.userId == userId && it.status.isActive }
            .maxByOrNull { it.joinedAt }
    }

    suspend fun historyFor(userId: String): List<QueueEntry> {
        ensureReady()
        return queues.values.filter { it.userId == userId }.sortedByDescending { it.joinedAt }
    }

    suspend fun queueFor(restaurantId: String): List<QueueEntry> {
        ensureReady()
        expireOverdue(restaurantId)
        return queues.values
            .filter { it.restaurantId == restaurantId }
            .sortedBy { it.ticketSequence }
    }

    suspend fun position(queueId: String): QueuePosition {
        ensureReady()
        queues[queueId]?.let { expireOverdue(it.restaurantId) }
        val entry = queues[queueId] ?: error("QUEUE_NOT_FOUND")
        val restaurant = restaurants.getValue(entry.restaurantId)
        val live = queues.values.filter { it.restaurantId == entry.restaurantId }
        val ahead = QueueMath.peopleAhead(live, entry.ticketSequence)
        return QueuePosition(
            peopleAhead = ahead,
            position = ahead + 1,
            estimatedWaitMinutes = QueueMath.estimatedWaitMinutes(
                ahead,
                restaurant.averageServiceMinutes
            ),
            currentServingNumber = QueueMath.currentServing(live)?.queueNumber,
            waitingCount = QueueMath.waitingCount(live)
        )
    }

    suspend fun reviewsFor(restaurantId: String): List<Review> {
        ensureReady()
        return reviews.values
            .filter { it.restaurantId == restaurantId }
            .sortedByDescending { it.createdAt }
    }

    suspend fun reviewForQueue(queueId: String): Review? {
        ensureReady()
        return reviews.values.firstOrNull { it.queueId == queueId }
    }

    suspend fun reviewsByUser(userId: String): List<Review> {
        ensureReady()
        return reviews.values.filter { it.userId == userId }.sortedByDescending { it.createdAt }
    }

    // -------------------------------------------------------------------------
    // Writes — these mirror 0012_check_in_window_and_table_ready.sql
    // -------------------------------------------------------------------------

    suspend fun joinQueue(
        userId: String,
        restaurantId: String,
        partySize: Int,
        note: String?
    ): QueueEntry = mutate(restaurantId, userId) {
        val restaurant = restaurants[restaurantId] ?: error("RESTAURANT_NOT_FOUND")
        if (!restaurant.isActive || !restaurant.isAcceptingQueue()) error("RESTAURANT_CLOSED")
        if (restaurant.hasAvailableSeats) error("SEATS_AVAILABLE")
        if (queues.values.any { it.userId == userId && it.status.isActive }) error("ALREADY_IN_QUEUE")
        val recentlyCancelled = queues.values.any {
            it.userId == userId && it.restaurantId == restaurantId &&
                it.status == QueueStatus.CANCELLED &&
                it.cancelledAt?.isAfter(Instant.now().minusSeconds(JOIN_COOLDOWN_SECONDS)) == true
        }
        if (recentlyCancelled) error("JOIN_COOLDOWN")

        val live = queues.values.filter { it.restaurantId == restaurantId }
        if (QueueMath.waitingCount(live) >= restaurant.queueCapacity) error("QUEUE_FULL")

        val sequence = (lastIssuedSequence[restaurantId] ?: 0) + 1
        lastIssuedSequence[restaurantId] = sequence

        QueueEntry(
            id = UUID.randomUUID().toString(),
            restaurantId = restaurantId,
            userId = userId,
            queueNumber = "${restaurant.queuePrefix}-${sequence.toString().padStart(3, '0')}",
            ticketSequence = sequence,
            partySize = partySize.coerceAtLeast(1),
            status = QueueStatus.WAITING,
            note = note?.takeIf { it.isNotBlank() },
            joinedAt = Instant.now()
        ).also { queues[it.id] = it }
    }

    suspend fun cancelQueue(userId: String, queueId: String): QueueEntry {
        val existing = queue(queueId)
        if (existing.userId != userId) error("QUEUE_NOT_FOUND")
        if (!existing.status.canTransitionTo(QueueStatus.CANCELLED)) error("QUEUE_NOT_CANCELLABLE")
        return transition(existing, QueueStatus.CANCELLED)
    }

    suspend fun checkIn(userId: String, queueId: String, photoUrl: String): QueueEntry {
        if (photoUrl.isBlank()) error("PHOTO_REQUIRED")
        val existing = queue(queueId)
        if (existing.userId != userId) error("QUEUE_NOT_FOUND")
        if (existing.isCheckInOverdue()) error("CHECK_IN_EXPIRED")
        if (!existing.status.allowsCheckIn) error("CHECK_IN_NOT_ALLOWED")

        return transition(existing, QueueStatus.CHECKED_IN, checkInPhotoUrl = photoUrl)
    }

    suspend fun callNext(restaurantId: String): QueueEntry {
        ensureReady()
        expireOverdue(restaurantId)
        val next = QueueMath.nextToCall(queues.values.filter { it.restaurantId == restaurantId })
            ?: error("QUEUE_EMPTY")
        // Already at the restaurant: nothing to wait for, the table is next.
        val target = if (next.status == QueueStatus.CHECKED_IN) {
            QueueStatus.TABLE_READY
        } else {
            QueueStatus.CALLED
        }
        return transition(next, target)
    }

    /** Mirrors `expire_overdue_queues`: CALLED past its window -> EXPIRED. */
    suspend fun expireOverdue(restaurantId: String) {
        ensureReady()
        val now = Instant.now()
        val overdue = queues.values.filter {
            it.restaurantId == restaurantId && it.status == QueueStatus.CALLED &&
                it.checkInDeadline?.let { deadline -> !deadline.isAfter(now) } == true
        }
        overdue.forEach { transition(it, QueueStatus.EXPIRED) }
    }

    suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry =
        transition(queue(queueId), status)

    /**
     * Mirrors `register_restaurant`: the venue is created PENDING_VERIFICATION and
     * unlisted, and stays that way until an admin approves it ([reviewRestaurant]).
     */
    suspend fun registerRestaurant(
        userId: String,
        registration: RestaurantRegistration,
        imageUrl: String?,
        proofs: List<ProofFile>,
        applicantName: String,
        applicantEmail: String
    ): Restaurant {
        ensureReady()
        if (DemoSeed.isAdminId(userId)) error("ADMIN_CANNOT_REGISTER")
        val name = registration.name.trim()
        val valid = name.length in 2..80 &&
            registration.address.trim().length >= 5 &&
            registration.category.isNotBlank() &&
            registration.latitude in -90.0..90.0 && registration.longitude in -180.0..180.0 &&
            registration.averageServiceMinutes in 1..60 &&
            registration.queueCapacity in 1..500 &&
            registration.contactPhone.filter { it.isDigit() }.length in 8..15
        if (!valid) error("INVALID_RESTAURANT")
        if (!hasValidProof(registration.documents)) error("PROOF_REQUIRED")

        val created = mutex.withLock {
            val owned = staffMemberships.count { it.startsWith("$userId|") && it.substringAfter('|') !in SEED_IDS }
            if (owned >= MAX_REGISTERED_PER_USER) error("TOO_MANY_RESTAURANTS")
            // One application at a time, as in the database.
            val hasPending = restaurants.values.any {
                it.verificationStatus == VerificationStatus.PENDING_VERIFICATION &&
                    "$userId|${it.id}" in staffMemberships
            }
            if (hasPending) error("APPLICATION_PENDING")

            Restaurant(
                id = UUID.randomUUID().toString(),
                name = name,
                description = registration.description.trim(),
                category = registration.category.trim(),
                address = registration.address.trim(),
                latitude = registration.latitude,
                longitude = registration.longitude,
                imageUrl = imageUrl,
                rating = 0.0,
                ratingCount = 0,
                openingTime = registration.openingTime,
                closingTime = registration.closingTime,
                isOpen = true,
                averageServiceMinutes = registration.averageServiceMinutes,
                queuePrefix = name.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
                    ?.uppercaseChar()?.toString() ?: "A",
                queueCapacity = registration.queueCapacity,
                checkInRadiusMeters = 150,
                isActive = false,
                verificationStatus = VerificationStatus.PENDING_VERIFICATION
            ).also {
                restaurants[it.id] = it
                lastIssuedSequence[it.id] = 0
                staffMemberships += "$userId|${it.id}"
                preferences.addDemoStaffMembership(userId, it.id)
                val now = Instant.now().toString()
                applicationRecords[it.id] = DemoApplication(
                    restaurantId = it.id,
                    applicantId = userId,
                    applicantName = applicantName,
                    applicantEmail = applicantEmail,
                    contactPhone = registration.contactPhone.filter { c -> c.isDigit() },
                    applicantRole = registration.applicantRole.name,
                    submittedAt = now,
                    documents = proofs.map { proof -> proofRecord(proof, 1, now) }
                )
                persistRegistry()
                // Room only ever holds the catalogue: an application is not in it.
            }
        }
        emitChange(null, emptySet())
        return created
    }

    /** Mirrors `resubmit_restaurant`: a REJECTED application goes back to review. */
    suspend fun resubmitRestaurant(
        userId: String,
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>,
        proofs: List<ProofFile>
    ): Restaurant {
        ensureReady()
        val phone = contactPhone.filter { it.isDigit() }
        if (phone.length !in 8..15) error("INVALID_RESTAURANT")
        if (!hasValidProof(documents)) error("PROOF_REQUIRED")

        val updated = mutex.withLock {
            val venue = restaurants[restaurantId]
            val record = applicationRecords[restaurantId]
            if (venue == null || record == null || "$userId|$restaurantId" !in staffMemberships ||
                venue.verificationStatus != VerificationStatus.REJECTED
            ) {
                error("NOT_RESUBMITTABLE")
            }
            val now = Instant.now().toString()
            val round = (record.documents.maxOfOrNull { it.round } ?: 0) + 1
            applicationRecords[restaurantId] = record.copy(
                contactPhone = phone,
                applicantRole = applicantRole.name,
                submittedAt = now,
                documents = record.documents + proofs.map { proof -> proofRecord(proof, round, now) }
            )
            venue.copy(verificationStatus = VerificationStatus.PENDING_VERIFICATION, verificationNote = null)
                .also {
                    restaurants[restaurantId] = it
                    persistRegistry()
                }
        }
        emitChange(null, emptySet())
        return updated
    }

    /**
     * Mirrors `admin_review_restaurant`: only an admin, only a PENDING application,
     * and a reason is required to reject. Approving lists the venue.
     */
    suspend fun reviewRestaurant(adminId: String, restaurantId: String, approve: Boolean, note: String?) {
        ensureReady()
        requireAdmin(adminId)
        mutex.withLock {
            val venue = restaurants[restaurantId] ?: error("NO_SUCH_RESTAURANT")
            if (venue.verificationStatus != VerificationStatus.PENDING_VERIFICATION) error("NOT_PENDING")
            val hasLicense = applicationRecords[restaurantId]?.documents
                ?.any { it.type == ProofType.BUSINESS_LICENSE.name } == true
            if (approve && !hasLicense) error("PROOF_REQUIRED")
            val reason = note?.trim().orEmpty()
            if (!approve && reason.isEmpty()) error("REJECTION_NOTE_REQUIRED")

            venue.copy(
                verificationStatus = if (approve) VerificationStatus.APPROVED else VerificationStatus.REJECTED,
                verificationNote = if (approve) null else reason,
                isActive = approve
            ).also {
                restaurants[restaurantId] = it
                persistRegistry()
                if (approve) database.restaurantDao().upsertRestaurants(listOf(it.toEntity()))
            }
        }
        emitChange(null, emptySet())
    }

    /** Mirrors `admin_list_applications`: pending ones oldest first, the rest newest first. */
    suspend fun listApplications(adminId: String, status: VerificationStatus): List<RestaurantApplication> {
        ensureReady()
        requireAdmin(adminId)
        val rows = applicationRecords.values.mapNotNull { record ->
            val venue = restaurants[record.restaurantId]?.takeIf { it.verificationStatus == status }
                ?: return@mapNotNull null
            val newest = record.documents.maxOfOrNull { it.round }
            RestaurantApplication(
                restaurantId = venue.id,
                name = venue.name,
                address = venue.address,
                category = venue.category,
                description = venue.description,
                imageUrl = venue.imageUrl,
                status = venue.verificationStatus,
                note = venue.verificationNote,
                applicantName = record.applicantName,
                applicantEmail = record.applicantEmail,
                contactPhone = record.contactPhone,
                applicantRole = ApplicantRole.entries.firstOrNull { it.name == record.applicantRole },
                documentCount = record.documents.count { it.round == newest },
                submittedAt = runCatching { Instant.parse(record.submittedAt) }.getOrNull()
            )
        }
        return if (status == VerificationStatus.PENDING_VERIFICATION) {
            rows.sortedBy { it.submittedAt }
        } else {
            rows.sortedByDescending { it.submittedAt }
        }
    }

    /** Mirrors `admin_application_documents`: newest submission first. */
    suspend fun applicationDocuments(adminId: String, restaurantId: String): List<ApplicationDocument> {
        ensureReady()
        requireAdmin(adminId)
        return applicationRecords[restaurantId]?.documents.orEmpty()
            .sortedByDescending { it.round }
            .map { doc ->
                ApplicationDocument(
                    id = doc.id,
                    type = ProofType.entries.firstOrNull { it.name == doc.type } ?: ProofType.OWNERSHIP_PROOF,
                    path = doc.uri,
                    round = doc.round,
                    uploadedAt = runCatching { Instant.parse(doc.createdAt) }.getOrNull()
                )
            }
    }

    private fun requireAdmin(userId: String) {
        if (!DemoSeed.isAdminId(userId)) error("NOT_ADMIN")
    }

    /** Same rule as the SQL: at least one business document, and every file readable. */
    private fun hasValidProof(documents: List<ProofDocument>): Boolean =
        documents.any { it.type == ProofType.BUSINESS_LICENSE } && documents.all { it.isValid }

    private fun proofRecord(proof: ProofFile, round: Int, createdAt: String) =
        DemoDocument(UUID.randomUUID().toString(), proof.type.name, proof.uri, round, createdAt)

    /** Pause or resume a venue; only its own staff may. */
    suspend fun setRestaurantActive(userId: String, restaurantId: String, active: Boolean): Restaurant {
        ensureReady()
        if (!isStaff(userId, restaurantId)) error("NOT_RESTAURANT_STAFF")
        val updated = mutex.withLock {
            val venue = restaurants[restaurantId] ?: error("RESTAURANT_NOT_FOUND")
            venue.copy(isActive = active).also {
                restaurants[restaurantId] = it
                persistRegistry()
                if (active) database.restaurantDao().upsertRestaurants(listOf(it.toEntity()))
            }
        }
        emitChange(null, emptySet())
        return updated
    }

    suspend fun submitReview(
        userId: String,
        queueId: String,
        restaurantId: String,
        rating: Int,
        comment: String
    ): Review {
        val entry = queue(queueId)
        if (entry.userId != userId || entry.status != QueueStatus.COMPLETED) {
            error("REVIEW_NOT_ALLOWED")
        }
        if (reviews.values.any { it.queueId == queueId }) error("REVIEW_EXISTS")
        val review = Review(
            id = UUID.randomUUID().toString(),
            restaurantId = restaurantId,
            userId = userId,
            queueId = queueId,
            rating = rating.coerceIn(1, 5),
            comment = comment.trim(),
            createdAt = Instant.now()
        )
        mutex.withLock {
            reviews[review.id] = review
            recomputeRating(restaurantId)
            database.reviewDao().upsert(review.toEntity())
        }
        emitChange(restaurantId, setOf(userId))
        return review
    }

    suspend fun updateReview(reviewId: String, rating: Int, comment: String): Review {
        ensureReady()
        val existing = reviews[reviewId] ?: error("REVIEW_NOT_FOUND")
        val updated = existing.copy(rating = rating.coerceIn(1, 5), comment = comment.trim())
        mutex.withLock {
            reviews[reviewId] = updated
            recomputeRating(existing.restaurantId)
            database.reviewDao().upsert(updated.toEntity())
        }
        emitChange(existing.restaurantId, setOf(existing.userId))
        return updated
    }

    private suspend fun recomputeRating(restaurantId: String) {
        val venue = restaurants.getValue(restaurantId)
        val venueReviews = reviews.values.filter { it.restaurantId == restaurantId }
        restaurants[restaurantId] = venue.copy(
            rating = venueReviews.map { it.rating }.average(),
            ratingCount = venueReviews.size
        )
        database.restaurantDao().upsertRestaurants(listOf(restaurants.getValue(restaurantId).toEntity()))
        persistRegistry()
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private suspend fun transition(
        entry: QueueEntry,
        next: QueueStatus,
        checkInPhotoUrl: String? = null
    ): QueueEntry {
        if (!entry.status.canTransitionTo(next)) {
            error("ILLEGAL_TRANSITION: ${entry.status} -> $next")
        }
        val now = Instant.now()
        val window = restaurants[entry.restaurantId]?.checkInWindowMinutes
            ?: QueueMath.DEFAULT_CHECK_IN_WINDOW_MINUTES
        val updated = entry.copy(
            status = next,
            calledAt = when (next) {
                QueueStatus.CALLED -> now
                QueueStatus.TABLE_READY -> entry.calledAt ?: now
                else -> entry.calledAt
            },
            checkInDeadline = if (next == QueueStatus.CALLED) {
                now.plus(Duration.ofMinutes(window.toLong()))
            } else {
                entry.checkInDeadline
            },
            checkedInAt = if (next == QueueStatus.CHECKED_IN) now else entry.checkedInAt,
            tableReadyAt = if (next == QueueStatus.TABLE_READY) now else entry.tableReadyAt,
            completedAt = if (next == QueueStatus.COMPLETED) now else entry.completedAt,
            cancelledAt = if (next == QueueStatus.CANCELLED) now else entry.cancelledAt,
            expiredAt = if (next == QueueStatus.EXPIRED) now else entry.expiredAt,
            checkInPhotoUrl = checkInPhotoUrl ?: entry.checkInPhotoUrl
        )
        return mutate(entry.restaurantId, entry.userId) { updated.also { queues[it.id] = it } }
    }

    /**
     * Runs [block] under the lock, persists the resulting ticket and publishes
     * one change event — the same shape a Postgres trigger plus Realtime would
     * produce.
     */
    private suspend fun mutate(
        restaurantId: String,
        userId: String,
        block: suspend () -> QueueEntry
    ): QueueEntry {
        ensureReady()
        val result = mutex.withLock {
            val entry = block()
            promoteAlmostThere(restaurantId)
            database.queueDao().upsert(
                queues.values.filter { it.restaurantId == restaurantId }.map { it.toEntity() }
            )
            entry
        }
        emitChange(restaurantId, affectedUsers(restaurantId) + userId)
        return result
    }

    /** Tickets near the front move to ALMOST_THERE on their own. */
    private fun promoteAlmostThere(restaurantId: String) {
        val live = queues.values.filter { it.restaurantId == restaurantId }
        live.filter { it.status == QueueStatus.WAITING }.forEach { entry ->
            val ahead = QueueMath.peopleAhead(live, entry.ticketSequence)
            if (ahead <= QueueMath.ALMOST_THERE_THRESHOLD) {
                queues[entry.id] = entry.copy(status = QueueStatus.ALMOST_THERE)
            }
        }
    }

    private companion object {
        const val JOIN_COOLDOWN_SECONDS = 60L
        const val MAX_REGISTERED_PER_USER = 5
        val SEED_IDS = DemoSeed.RESTAURANTS.map { it.id }.toSet()
        val json = Json { ignoreUnknownKeys = true }
    }

    private fun affectedUsers(restaurantId: String): Set<String> =
        queues.values.filter { it.restaurantId == restaurantId }.map { it.userId }.toSet()

    private fun emitChange(restaurantId: String?, userIds: Set<String>) {
        _changes.tryEmit(Change(restaurantId, userIds))
    }

    private fun statsFor(restaurantId: String): QueueStats {
        val restaurant = restaurants[restaurantId] ?: return QueueStats.empty(restaurantId)
        val live = queues.values.filter { it.restaurantId == restaurantId }
        val serving = QueueMath.currentServing(live)
        val waiting = QueueMath.waitingCount(live)
        return QueueStats(
            restaurantId = restaurantId,
            currentServingNumber = serving?.queueNumber,
            currentServingSequence = serving?.ticketSequence ?: 0,
            waitingCount = waiting,
            estimatedWaitMinutes = QueueMath.estimatedWaitForNewGroup(
                live,
                restaurant.averageServiceMinutes
            ),
            updatedAt = Instant.now()
        )
    }
}
