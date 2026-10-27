package org.umn.ngantriin.domain.repository

import org.umn.ngantriin.core.Outcome
import org.umn.ngantriin.domain.model.ApplicationDocument
import org.umn.ngantriin.domain.model.RestaurantApplication
import org.umn.ngantriin.domain.model.VerificationStatus

/**
 * Review restoran untuk admin. Aplikasi tidak pernah menentukan siapa admin:
 * setiap panggilan dicek database, jadi non-admin mendapat `NOT_ADMIN` di sini
 * bagaimanapun cara masuknya.
 */
interface AdminRepository {

    /** Pengajuan dalam satu status, yang pending terlama dulu. */
    suspend fun applications(status: VerificationStatus): Outcome<List<RestaurantApplication>>

    /** Semua berkas bukti sebuah pengajuan, kiriman terbaru dulu. */
    suspend fun documents(restaurantId: String): Outcome<List<ApplicationDocument>>

    /** URL berumur pendek yang bisa dibuka, berkas bukti bersifat privat. */
    suspend fun documentUrl(document: ApplicationDocument): Outcome<String>

    /** Menyetujui, atau menolak dengan alasan yang akan dibaca pendaftar. */
    suspend fun review(restaurantId: String, approve: Boolean, note: String?): Outcome<Unit>
}
