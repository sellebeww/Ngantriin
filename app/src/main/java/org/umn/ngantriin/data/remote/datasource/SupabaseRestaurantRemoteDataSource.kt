package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.QueueStatsDto
import org.umn.ngantriin.data.remote.dto.RestaurantDto
import org.umn.ngantriin.data.remote.dto.RestaurantStaffDto
import org.umn.ngantriin.domain.model.QueueStats
import org.umn.ngantriin.domain.model.Restaurant

class SupabaseRestaurantRemoteDataSource(
    private val client: SupabaseClient
) : RestaurantRemoteDataSource {

    override suspend fun fetchRestaurants(): List<Restaurant> =
        client.from("restaurants")
            .select { order("name", Order.ASCENDING) }
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
