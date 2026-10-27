package org.umn.ngantriin.data.remote.datasource

import org.umn.ngantriin.domain.model.ProofDocument
import org.umn.ngantriin.domain.model.ApplicantRole
import kotlinx.serialization.json.JsonArray
import io.ktor.http.ContentType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.QueueStatsDto
import org.umn.ngantriin.data.remote.dto.RestaurantActiveUpdateDto
import org.umn.ngantriin.data.remote.dto.RestaurantDto
import org.umn.ngantriin.data.remote.dto.RestaurantStaffDto
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.Restaurant
import org.umn.ngantriin.domain.model.RestaurantRegistration

class SupabaseRestaurantRemoteDataSource(
    private val client: SupabaseClient
) : RestaurantRemoteDataSource {

    override suspend fun fetchRestaurants(): List<Restaurant> =
        // Explicit even though RLS also hides paused venues: staff and past
        // ticket holders can still read them, and they must not show up in search.
        client.from("restaurants")
            .select {
                filter { eq("is_active", true) }
                order("name", Order.ASCENDING)
            }
            .decodeList<RestaurantDto>()
            .map { it.toDomain() }

    override suspend fun fetchRestaurant(restaurantId: String): Restaurant =
        client.from("restaurants")
            .select { filter { eq("id", restaurantId) } }
            .decodeSingle<RestaurantDto>()
            .toDomain()

    override suspend fun fetchAllQueueStats(): List<QueueStats> =
        client.from("queue_stats")
            .select()
            .decodeList<QueueStatsDto>()
            .map { it.toDomain() }

    override suspend fun fetchQueueStats(restaurantId: String): QueueStats? =
        client.from("queue_stats")
            .select { filter { eq("restaurant_id", restaurantId) } }
            .decodeSingleOrNull<QueueStatsDto>()
            ?.toDomain()

    private companion object {
        const val RESTAURANT_PHOTOS_BUCKET = "restaurant-photos"
        const val PROOFS_BUCKET = "restaurant-proofs"
    }

    override suspend fun fetchManagedRestaurants(userId: String): List<Restaurant> {
        val memberships = client.from("restaurant_staff")
            .select { filter { eq("user_id", userId) } }
            .decodeList<RestaurantStaffDto>()
        if (memberships.isEmpty()) return emptyList()

        return client.from("restaurants")
            .select {
                filter { isIn("id", memberships.map { it.restaurantId }) }
                order("name", Order.ASCENDING)
            }
            .decodeList<RestaurantDto>()
            .map { it.toDomain() }
    }

    override suspend fun registerRestaurant(registration: RestaurantRegistration): Restaurant {
        val userId = client.auth.currentUserOrNull()?.id ?: error("NOT_AUTHENTICATED")
        // Uploaded first: the RPC stores only the public URL. A failed RPC
        // leaves an orphan photo in the user's own folder, which is harmless.
        val proofs = uploadProofs(userId, registration.documents)
        val imageUrl = registration.photo?.let { bytes ->
            val bucket = client.storage.from(RESTAURANT_PHOTOS_BUCKET)
            val path = "$userId/${System.currentTimeMillis()}.jpg"
            bucket.upload(path, bytes) { upsert = false }
            bucket.publicUrl(path)
        }

        return client.postgrest.rpc(
            function = "register_restaurant",
            parameters = buildJsonObject {
                put("p_name", registration.name)
                put("p_address", registration.address)
                put("p_category", registration.category)
                put("p_description", registration.description)
                put("p_image_url", imageUrl)
                put("p_latitude", registration.latitude)
                put("p_longitude", registration.longitude)
                put("p_opening_time", registration.openingTime.toString())
                put("p_closing_time", registration.closingTime.toString())
                put("p_average_service_minutes", registration.averageServiceMinutes)
                put("p_queue_capacity", registration.queueCapacity)
                put("p_contact_phone", registration.contactPhone)
                put("p_applicant_role", registration.applicantRole.name)
                put("p_documents", proofs)
            }
        ).decodeAs<RestaurantDto>().toDomain()
    }

    override suspend fun resubmitRestaurant(
        restaurantId: String,
        contactPhone: String,
        applicantRole: ApplicantRole,
        documents: List<ProofDocument>
    ): Restaurant {
        val userId = client.auth.currentUserOrNull()?.id ?: error("NOT_AUTHENTICATED")
        val proofs = uploadProofs(userId, documents)
        return client.postgrest.rpc(
            function = "resubmit_restaurant",
            parameters = buildJsonObject {
                put("p_restaurant_id", restaurantId)
                put("p_contact_phone", contactPhone)
                put("p_applicant_role", applicantRole.name)
                put("p_documents", proofs)
            }
        ).decodeAs<RestaurantDto>().toDomain()
    }

    /**
     * Proofs go to a private bucket, inside the uploader's own folder; the RPC
     * then checks each path really exists there before recording it.
     */
    private suspend fun uploadProofs(userId: String, documents: List<ProofDocument>): JsonArray {
        val bucket = client.storage.from(PROOFS_BUCKET)
        val stamp = System.currentTimeMillis()
        val entries = documents.mapIndexed { index, doc ->
            val path = "$userId/$stamp-$index.${doc.extension}"
            bucket.upload(path, doc.bytes) {
                upsert = false
                contentType = if (doc.isPdf) ContentType.Application.Pdf else ContentType.Image.JPEG
            }
            buildJsonObject {
                put("type", doc.type.name)
                put("path", path)
            }
        }
        return JsonArray(entries)
    }

    override suspend fun setRestaurantActive(restaurantId: String, active: Boolean): Restaurant =
        client.from("restaurants")
            .update(RestaurantActiveUpdateDto(active)) {
                filter { eq("id", restaurantId) }
                select()
            }
            // RLS filters out venues the caller does not staff, so a foreign id
            // comes back empty rather than updated.
            .decodeList<RestaurantDto>()
            .firstOrNull()
            ?.toDomain()
            ?: error("NOT_RESTAURANT_STAFF")

    override fun observeQueueStatsChanges(restaurantId: String): Flow<Unit> =
        client.postgresChanges(
            channelId = "queue_stats:$restaurantId",
            table = "queue_stats",
            filterColumn = "restaurant_id",
            filterOperator = FilterOperator.EQ,
            filterValue = restaurantId
        )

    override fun observeAllQueueStatsChanges(): Flow<Unit> =
        client.postgresChanges(channelId = "queue_stats:all", table = "queue_stats")
}
