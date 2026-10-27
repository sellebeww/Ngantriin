package org.umn.ngantriin.domain.repository

import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus

/**
 * Restaurant review for administrators. The app never decides who is an admin:
 * every call is checked by the database, so a non-admin gets `NOT_ADMIN` here
 * however they reached it.
 */
interface AdminRepository {

    /** Applications in one state; pending ones oldest first. */
    suspend fun applications(status: VerificationStatus): Outcome<List<RestaurantApplication>>

    /** Every proof file of an application, newest submission first. */
    suspend fun documents(restaurantId: String): Outcome<List<ApplicationDocument>>

    /** A short-lived URL a viewer can open; proof files are private. */
    suspend fun documentUrl(document: ApplicationDocument): Outcome<String>

    /** Approves, or rejects with a reason the applicant will read. */
    suspend fun review(restaurantId: String, approve: Boolean, note: String?): Outcome<Unit>
}
