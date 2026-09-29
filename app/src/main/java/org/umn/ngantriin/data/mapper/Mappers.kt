package org.umn.ngantriin.data.mapper

import org.umn.ngantriin.data.local.entity.NotificationEntity
import org.umn.ngantriin.data.local.entity.QueueEntryEntity
import org.umn.ngantriin.data.local.entity.QueueHistoryRow
import org.umn.ngantriin.data.local.entity.QueueStatsEntity
import org.umn.ngantriin.data.local.entity.RestaurantEntity
import org.umn.ngantriin.data.local.entity.ReviewEntity
import org.umn.ngantriin.data.remote.dto.NotificationDto
import org.umn.ngantriin.data.remote.dto.QueueDto
import org.umn.ngantriin.data.remote.dto.QueueStatsDto
import org.umn.ngantriin.data.remote.dto.RestaurantDto
import org.umn.ngantriin.data.remote.dto.ReviewDto
import org.umn.ngantriin.data.remote.dto.UserDto
import org.umn.ngantriin.domain.model.AppNotification
import org.umn.ngantriin.domain.model.NotificationType
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueueHistoryItem
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.QueueStatus
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.Review
import org.umn.ngantriin.domain.model.User
import org.umn.ngantriin.domain.model.UserRole
import java.time.Instant
import java.time.LocalTime

// -----------------------------------------------------------------------------
// Remote -> domain
// -----------------------------------------------------------------------------

fun UserDto.toDomain() = User(
    id = id,
    name = name,
    email = email,
    phone = phone,
    avatarUrl = avatarUrl,
    role = UserRole.fromWire(role)
)

fun RestaurantDto.toDomain() = Restaurant(
    id = id,
    name = name,
    description = description,
    category = category,
    address = address,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    rating = rating,
    ratingCount = ratingCount,
    openingTime = parseTime(openingTime, LocalTime.of(10, 0)),
    closingTime = parseTime(closingTime, LocalTime.of(22, 0)),
    isOpen = isOpen,
    averageServiceMinutes = averageServiceMinutes.coerceAtLeast(1),
    queuePrefix = queuePrefix,
    queueCapacity = queueCapacity,
    checkInRadiusMeters = checkInRadiusMeters,
    availableSeats = availableSeats
)

fun QueueStatsDto.toDomain() = QueueStats(
    restaurantId = restaurantId,
    currentServingNumber = currentServingNumber,
    currentServingSequence = currentServingSequence,
    waitingCount = waitingCount,
    estimatedWaitMinutes = estimatedWaitMinutes,
    updatedAt = parseTimestamp(updatedAt)
)

fun QueueDto.toDomain() = QueueEntry(
    id = id,
    restaurantId = restaurantId,
    userId = userId,
    queueNumber = queueNumber,
    ticketSequence = ticketSequence,
    partySize = partySize,
    status = QueueStatus.fromWire(status),
    note = note,
    joinedAt = parseTimestamp(joinedAt, Instant.now()),
    calledAt = parseTimestampOrNull(calledAt),
    checkedInAt = parseTimestampOrNull(checkedInAt),
    completedAt = parseTimestampOrNull(completedAt),
    cancelledAt = parseTimestampOrNull(cancelledAt),
    checkInPhotoUrl = checkInPhotoUrl
)

fun ReviewDto.toDomain(authorName: String? = null) = Review(
    id = id,
    restaurantId = restaurantId,
    userId = userId,
    queueId = queueId,
    rating = rating,
    comment = review,
    createdAt = parseTimestamp(createdAt, Instant.now()),
    authorName = authorName
)

fun NotificationDto.toDomain() = AppNotification(
    id = id,
    userId = userId,
    queueId = queueId,
    title = title,
    body = body,
    type = NotificationType.fromWire(type),
    isRead = isRead,
    createdAt = parseTimestamp(createdAt, Instant.now())
)

// -----------------------------------------------------------------------------
// Domain -> local
// -----------------------------------------------------------------------------

fun Restaurant.toEntity(cachedAt: Instant = Instant.now()) = RestaurantEntity(
    id = id,
    name = name,
    description = description,
    category = category,
    address = address,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    rating = rating,
    ratingCount = ratingCount,
    openingTime = openingTime,
    closingTime = closingTime,
    isOpen = isOpen,
    averageServiceMinutes = averageServiceMinutes,
    queuePrefix = queuePrefix,
    queueCapacity = queueCapacity,
    checkInRadiusMeters = checkInRadiusMeters,
    availableSeats = availableSeats,
    cachedAt = cachedAt
)

fun QueueStats.toEntity() = QueueStatsEntity(
    restaurantId = restaurantId,
    currentServingNumber = currentServingNumber,
    currentServingSequence = currentServingSequence,
    waitingCount = waitingCount,
    estimatedWaitMinutes = estimatedWaitMinutes,
    updatedAt = updatedAt
)

fun QueueEntry.toEntity() = QueueEntryEntity(
    id = id,
    restaurantId = restaurantId,
    userId = userId,
    queueNumber = queueNumber,
    ticketSequence = ticketSequence,
    partySize = partySize,
    status = status.wireValue,
    note = note,
    joinedAt = joinedAt,
    calledAt = calledAt,
    checkedInAt = checkedInAt,
    completedAt = completedAt,
    cancelledAt = cancelledAt,
    checkInPhotoUrl = checkInPhotoUrl
)

fun Review.toEntity() = ReviewEntity(
    id = id,
    restaurantId = restaurantId,
    userId = userId,
    queueId = queueId,
    rating = rating,
    comment = comment,
    createdAt = createdAt,
    authorName = authorName
)

fun AppNotification.toEntity() = NotificationEntity(
    id = id,
    userId = userId,
    queueId = queueId,
    title = title,
    body = body,
    type = type.name,
    isRead = isRead,
    createdAt = createdAt
)

// -----------------------------------------------------------------------------
// Local -> domain
// -----------------------------------------------------------------------------

fun RestaurantEntity.toDomain() = Restaurant(
    id = id,
    name = name,
    description = description,
    category = category,
    address = address,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    rating = rating,
    ratingCount = ratingCount,
    openingTime = openingTime,
    closingTime = closingTime,
    isOpen = isOpen,
    averageServiceMinutes = averageServiceMinutes,
    queuePrefix = queuePrefix,
    queueCapacity = queueCapacity,
    checkInRadiusMeters = checkInRadiusMeters,
    availableSeats = availableSeats
)

fun QueueStatsEntity.toDomain() = QueueStats(
    restaurantId = restaurantId,
    currentServingNumber = currentServingNumber,
    currentServingSequence = currentServingSequence,
    waitingCount = waitingCount,
    estimatedWaitMinutes = estimatedWaitMinutes,
    updatedAt = updatedAt
)

fun QueueEntryEntity.toDomain() = QueueEntry(
    id = id,
    restaurantId = restaurantId,
    userId = userId,
    queueNumber = queueNumber,
    ticketSequence = ticketSequence,
    partySize = partySize,
    status = QueueStatus.fromWire(status),
    note = note,
    joinedAt = joinedAt,
    calledAt = calledAt,
    checkedInAt = checkedInAt,
    completedAt = completedAt,
    cancelledAt = cancelledAt,
    checkInPhotoUrl = checkInPhotoUrl
)

fun ReviewEntity.toDomain() = Review(
    id = id,
    restaurantId = restaurantId,
    userId = userId,
    queueId = queueId,
    rating = rating,
    comment = comment,
    createdAt = createdAt,
    authorName = authorName
)

fun NotificationEntity.toDomain() = AppNotification(
    id = id,
    userId = userId,
    queueId = queueId,
    title = title,
    body = body,
    type = NotificationType.fromWire(type),
    isRead = isRead,
    createdAt = createdAt
)

fun QueueHistoryRow.toDomain() = QueueHistoryItem(
    entry = queue.toDomain(),
    restaurantName = restaurantName ?: "Restaurant",
    restaurantImageUrl = restaurantImageUrl,
    hasReview = hasReview
)
