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
 * Memanggil fungsi `admin_*` dari 0021. Fungsi itu mengecek `is_admin()` di dalam
 * database, jadi tidak ada yang menentukan siapa boleh me-review di sini.
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

    // Bucket privat: signed URL yang habis dalam hitungan menit, dibuat tiap kali
    // dilihat. Policy storage hanya membolehkan admin menandatangani path bukti.
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
