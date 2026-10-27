package org.umn.ngantriin.data.repository

import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.core.runCatchingOutcome
import org.umn.ngantriin.data.remote.datasource.AdminRemoteDataSource
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus
import org.umn.ngantriin.domain.repository.AdminRepository

/** Tidak di-cache: keputusan review harus selalu berdasarkan data server saat ini. */
class AdminRepositoryImpl(
    private val remote: AdminRemoteDataSource
) : AdminRepository {

    override suspend fun applications(status: VerificationStatus): Outcome<List<RestaurantApplication>> =
        runCatchingOutcome { remote.fetchApplications(status) }

    override suspend fun documents(restaurantId: String): Outcome<List<ApplicationDocument>> =
        runCatchingOutcome { remote.fetchDocuments(restaurantId) }

    override suspend fun documentUrl(document: ApplicationDocument): Outcome<String> =
        runCatchingOutcome { remote.documentUrl(document) }

    override suspend fun review(restaurantId: String, approve: Boolean, note: String?): Outcome<Unit> =
        runCatchingOutcome { remote.review(restaurantId, approve, note?.trim()?.takeIf { it.isNotEmpty() }) }
}
