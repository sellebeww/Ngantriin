package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.QueueDto
import org.umn.ngantriin.data.remote.dto.QueuePositionDto
import org.umn.ngantriin.domain.model.QueueEntry
import org.umn.ngantriin.domain.model.QueuePosition
import org.umn.ngantriin.domain.model.QueueStatus

/**
 * Every state change goes through an RPC rather than a table write. The
 * functions in 0002_queue_functions.sql own ticket allocation and the
 * transition rules, so a compromised or simply buggy client cannot invent a
 * queue number or force a check-in without a photo.
 */
class SupabaseQueueRemoteDataSource(
    private val client: SupabaseClient
) : QueueRemoteDataSource {

    private val checkInPhotosBucket = "checkin-photos"

    private val activeStatuses = listOf(
        QueueStatus.WAITING.wireValue,
        QueueStatus.ALMOST_THERE.wireValue,
        QueueStatus.CALLED.wireValue,
        QueueStatus.CHECKED_IN.wireValue
    )

    override suspend fun joinQueue(
        restaurantId: String,
        partySize: Int,
        note: String?
    ): QueueEntry = client.postgrest.rpc(
        function = "join_queue",
        parameters = buildJsonObject {
            put("p_restaurant_id", restaurantId)
            put("p_party_size", partySize)
            if (!note.isNullOrBlank()) put("p_note", note.trim())
        }
    ).decodeAs<QueueDto>().toDomain()

    override suspend fun leaveQueue(queueId: String): QueueEntry = client.postgrest.rpc(
        function = "cancel_queue",
        parameters = buildJsonObject { put("p_queue_id", queueId) }
    ).decodeAs<QueueDto>().toDomain()

    override suspend fun checkIn(queueId: String, restaurantId: String, photo: ByteArray): QueueEntry {
        val path = "$restaurantId/$queueId/${System.currentTimeMillis()}.jpg"
        client.storage.from(checkInPhotosBucket).upload(path, photo) { upsert = true }

        return client.postgrest.rpc(
            function = "check_in_queue",
            parameters = buildJsonObject {
                put("p_queue_id", queueId)
                put("p_photo_url", path)
            }
        ).decodeAs<QueueDto>().toDomain()
    }

    override suspend fun resolveCheckInPhotoUrl(photoUrl: String): String =
        client.storage.from(checkInPhotosBucket).createSignedUrl(photoUrl, 1.hours)

    override suspend fun callNext(restaurantId: String): QueueEntry = client.postgrest.rpc(
        function = "call_next",
        parameters = buildJsonObject { put("p_restaurant_id", restaurantId) }
    ).decodeAs<QueueDto>().toDomain()

    override suspend fun updateStatus(queueId: String, status: QueueStatus): QueueEntry =
        client.postgrest.rpc(
            function = "staff_update_queue_status",
            parameters = buildJsonObject {
                put("p_queue_id", queueId)
                put("p_status", status.wireValue)
            }
        ).decodeAs<QueueDto>().toDomain()

    override suspend fun fetchQueue(queueId: String): QueueEntry =
        client.from("queues")
            .select { filter { eq("id", queueId) } }
            .decodeSingle<QueueDto>()
            .toDomain()

    override suspend fun fetchPosition(queueId: String): QueuePosition =
        client.postgrest.rpc(
            function = "queue_position",
            parameters = buildJsonObject { put("p_queue_id", queueId) }
        ).decodeList<QueuePositionDto>()
            .firstOrNull()
            ?.let {
                QueuePosition(
                    peopleAhead = it.peopleAhead,
                    position = it.position,
                    estimatedWaitMinutes = it.estimatedWait,
                    currentServingNumber = it.currentServing,
                    waitingCount = it.waitingCount
                )
            }
            ?: error("QUEUE_NOT_FOUND")

    override suspend fun fetchActiveQueue(userId: String): QueueEntry? =
        client.from("queues")
            .select {
                filter {
                    eq("user_id", userId)
                    isIn("status", activeStatuses)
                }
                order("joined_at", Order.DESCENDING)
                limit(1)
            }
            .decodeList<QueueDto>()
            .firstOrNull()
            ?.toDomain()

    override suspend fun fetchHistory(userId: String): List<QueueEntry> =
        client.from("queues")
            .select {
                filter { eq("user_id", userId) }
                order("joined_at", Order.DESCENDING)
                limit(100)
            }
            .decodeList<QueueDto>()
            .map { it.toDomain() }

    override suspend fun fetchRestaurantQueue(restaurantId: String): List<QueueEntry> =
        client.from("queues")
            .select {
                filter { eq("restaurant_id", restaurantId) }
                order("ticket_sequence", Order.ASCENDING)
            }
            .decodeList<QueueDto>()
            .map { it.toDomain() }

    override fun observeUserQueueChanges(userId: String): Flow<Unit> =
        client.postgresChanges(
            channelId = "queues:user:$userId",
            table = "queues",
            filterColumn = "user_id",
            filterOperator = FilterOperator.EQ,
            filterValue = userId
        )

    override fun observeRestaurantQueueChanges(restaurantId: String): Flow<Unit> =
        client.postgresChanges(
            channelId = "queues:restaurant:$restaurantId",
            table = "queues",
            filterColumn = "restaurant_id",
            filterOperator = FilterOperator.EQ,
            filterValue = restaurantId
        )
}
