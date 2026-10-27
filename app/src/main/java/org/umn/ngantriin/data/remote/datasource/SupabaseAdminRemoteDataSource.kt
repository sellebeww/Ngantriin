package org.umn.ngantriin.data.remote.datasource

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.umn.ngantriin.data.mapper.toDomain
import org.umn.ngantriin.data.remote.dto.ApplicationDocumentDto
import org.umn.ngantriin.data.remote.dto.RestaurantApplicationDto
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import kotlin.time.Duration.Companion.minutes

/**
 * Talks to the `admin_*` functions from 0021. They check `is_admin()` inside the
 * database, so nothing here decides who may review.
 */
class SupabaseAdminRemoteDataSource(
    private val client: SupabaseClient
) : AdminRemoteDataSource {

    override suspend fun fetchApplications(status: VerificationStatus): List<RestaurantApplication> =
        client.postgrest.rpc(
            function = "admin_list_applications",
            parameters = buildJsonObject { put("p_status", status.name) }
        ).decodeList<RestaurantApplicationDto>().map { it.toDomain() }

    override suspend fun fetchDocuments(restaurantId: String): List<ApplicationDocument> =
        client.postgrest.rpc(
            function = "admin_application_documents",
            parameters = buildJsonObject { put("p_restaurant_id", restaurantId) }
        ).decodeList<ApplicationDocumentDto>().map { it.toDomain() }

    // Private bucket: a signed URL that lapses in minutes, minted per view. The
    // storage policy lets only an admin sign a proof path.
    override suspend fun documentUrl(document: ApplicationDocument): String =
        client.storage.from(PROOFS_BUCKET).createSignedUrl(document.path, 5.minutes)

    override suspend fun review(restaurantId: String, approve: Boolean, note: String?) {
        client.postgrest.rpc(
            function = "admin_review_restaurant",
            parameters = buildJsonObject {
                put("p_restaurant_id", restaurantId)
                put("p_approve", approve)
                put("p_note", note)
            }
        )
    }

    private companion object {
        const val PROOFS_BUCKET = "restaurant-proofs"
    }
}
